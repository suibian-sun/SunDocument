package com.suibian.sunfile.data

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ShellResult(val out: String, val err: String, val code: Int) {
    val ok: Boolean get() = code == 0
}

/** 提权引擎：决定 shell 命令最终由谁执行 */
enum class ShellEngine(val label: String, val detail: String) {
    NONE("未启用", "只能读写普通应用可访问的目录"),
    /** 应用自身 uid 直接跑 /system/bin/sh，不需要任何授权；能读 /、/system、/sdcard 等公开区域 */
    NATIVE("本地", "系统自带 sh，无需授权，可读根目录与只读系统分区"),
    SHIZUKU("Shizuku", "代理给 shell 进程执行，可读根目录与系统分区"),
    SU("Root", "以 uid 0 执行，可读包括 /data/data 在内的全部目录")
}

/**
 * 提权命令行后端。
 *
 * 系统对 /data、/system、Android/data 等目录有 SELinux 与 scoped storage 限制，
 * 普通 API 无法枚举。这里把 ls / cat / cp 这类操作交给提权通道执行：
 *
 * - **Shizuku**：借 Shizuku 服务 fork 一个 shell 身份的进程（uid 2000），
 *   系统看到的是 shell 在操作文件，SELinux 按 shell 域放行。这正是 MT 管理器的做法。
 * - **Root**：直接 `su -c`，能力最强，能进 /data/data。
 *
 * 两条通道都只产出真实文件系统数据，绝不伪造挂载点或占位条目；
 * 且只在普通 File API 失败时才触发，正常浏览不会反复弹授权。
 */
object Shell {

    /**
     * 当前引擎。默认 NATIVE：用应用自己的 uid 跑系统 sh，不需要任何授权，
     * 打开就能看根目录与只读系统分区。需要 /data 时才升级到 Shizuku / Root。
     */
    private val _engine = MutableStateFlow(ShellEngine.NATIVE)
    val engineState: StateFlow<ShellEngine> = _engine

    var engine: ShellEngine
        get() = _engine.value
        private set(value) { _engine.value = value }

    /** 自定义 su 命令（多用户 / 非标准路径的 su），由设置同步进来 */
    @Volatile
    var suCommand: String = "su"

    /** 是否有真正的提权（Shizuku / Root）；普通文件操作仍按老逻辑走 Java API */
    val rootAvailable: Boolean
        get() = engine == ShellEngine.SHIZUKU || engine == ShellEngine.SU

    /** 是否有可用的 shell 通道。NATIVE 永远可用，所以恒为 true */
    val shellReady: Boolean get() = engine != ShellEngine.NONE

    val privileged: Boolean get() = rootAvailable

    val engineLabel: String get() = engine.label

    /** 状态一句话，给设置页和错误提示用 */
    val statusText: String get() = when (engine) {
        ShellEngine.NONE -> "未启用 · 只能走普通 API"
        ShellEngine.NATIVE -> "本地 · 无需授权，可读根目录与系统分区"
        ShellEngine.SHIZUKU -> "Shizuku · 可读根目录与系统分区"
        ShellEngine.SU -> "Root · 可读全部目录"
    }

    /** 启动时恢复通道：只复用已授权过的 Shizuku，不弹框；否则就停在本地 shell */
    fun initDefault() {
        if (engine == ShellEngine.NONE) engine = ShellEngine.NATIVE
        if (engine == ShellEngine.NATIVE && ShizukuBridge.usable()) {
            activate(ShellEngine.SHIZUKU)
        }
    }

    // ---------------- 授权 ----------------

    /**
     * 申请提权，自动选路：已授过权的先用，其次 Shizuku（无弹框、无需 root），
     * 最后才尝试 su。都拿不到就停在本地 shell —— 它本来就能读根目录。
     */
    fun requestAuto(): ShellEngine {
        if (rootAvailable && verify(engine)) return engine
        if (ShizukuBridge.usable() && activate(ShellEngine.SHIZUKU)) return engine
        if (activate(ShellEngine.SU)) return engine
        engine = ShellEngine.NATIVE
        return ShellEngine.NATIVE
    }

    /** 兼容旧调用点：返回是否拿到了提权通道 */
    fun requestRoot(): Boolean = requestAuto().let { it == ShellEngine.SHIZUKU || it == ShellEngine.SU }

    /** 只申请 Shizuku（不碰 su，不会弹 root 授权框） */
    fun requestShizuku(): Boolean = activate(ShellEngine.SHIZUKU)

    /** 只申请 Root */
    fun requestSu(): Boolean = activate(ShellEngine.SU)

    /** 退回本地 shell（放弃提权） */
    fun useNative() {
        engine = ShellEngine.NATIVE
    }

    /**
     * 切到指定引擎并实际验证一次：能让 `id` 跑通才算成功。
     * 验证失败会退回原来的引擎，避免留下「看起来可用但其实跑不动」的状态。
     */
    fun activate(target: ShellEngine): Boolean {
        if (target == ShellEngine.NONE) {
            engine = ShellEngine.NONE
            return false
        }
        if (target == ShellEngine.SHIZUKU && !ShizukuBridge.usable()) return false
        if (target == ShellEngine.NATIVE) {
            engine = ShellEngine.NATIVE
            return true
        }
        val prev = engine
        engine = target
        val ok = verify(target)
        engine = if (ok) target else prev
        return ok
    }

    private fun verify(target: ShellEngine): Boolean {
        val prev = engine
        engine = target
        return try {
            val r = execScript("id", 8_000)
            val out = r.out
            when (target) {
                ShellEngine.SU -> r.ok && out.contains("uid=0")
                // shell 身份是 uid 2000；用 root 启动的 Shizuku 也可能是 0
                ShellEngine.SHIZUKU -> r.ok && (out.contains("uid=2000") || out.contains("uid=0"))
                ShellEngine.NATIVE -> r.ok && out.contains("uid=")
                ShellEngine.NONE -> false
            }
        } catch (e: Exception) {
            false
        } finally {
            engine = prev
        }
    }

    /** 忘记已获得的权限（用户主动断开时调用） */
    fun reset() {
        engine = ShellEngine.NATIVE
    }

    // ---------------- 进程 ----------------

    /** 按指定引擎起一个进程执行整段脚本 */
    private fun startProcess(e: ShellEngine, script: String): Process = when (e) {
        ShellEngine.SU -> ProcessBuilder(suCommand, "-c", script).start()
        ShellEngine.SHIZUKU -> ShizukuBridge.newProcess(arrayOf("/system/bin/sh", "-c", script))
            ?: throw IOException("Shizuku 未能创建进程")
        ShellEngine.NATIVE, ShellEngine.NONE -> ProcessBuilder("/system/bin/sh", "-c", script).start()
    }

    /** 直接跑一个命令行（用于 su 探测，不经过引擎分派） */
    fun exec(cmd: List<String>, timeoutMs: Long = 15_000): ShellResult {
        var process: Process? = null
        return try {
            process = ProcessBuilder(cmd).redirectErrorStream(false).start()
            pump(process, timeoutMs)
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            ShellResult("", e.message ?: "shell error", -1)
        }
    }

    /** 按当前引擎执行脚本 */
    private fun execScript(script: String, timeoutMs: Long = 20_000): ShellResult {
        var process: Process? = null
        return try {
            process = startProcess(engine, script)
            pump(process, timeoutMs)
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            degradeIfBroken()
            ShellResult("", e.message ?: "shell error", -1)
        }
    }

    /**
     * Shizuku 服务被系统回收后 binder 会失效，此时再留着「已授权」只会让每条命令都白跑一次。
     * 检测到链路断了就退回未授权，界面会重新出现授权引导。
     */
    private fun degradeIfBroken() {
        if (engine == ShellEngine.SHIZUKU && !ShizukuBridge.usable()) {
            engine = ShellEngine.NATIVE
        }
    }

    /** 并发抽干 stdout / stderr 并等待退出；缓冲满了进程会阻塞，必须同时读 */
    private fun pump(process: Process, timeoutMs: Long): ShellResult {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val t = Thread { runCatching { process.inputStream.use { it.copyTo(out, 64 * 1024) } } }
        val te = Thread { runCatching { process.errorStream.use { it.copyTo(err, 8 * 1024) } } }
        t.start(); te.start()
        val finished = waitForCompat(process, timeoutMs)
        if (!finished) {
            runCatching { killCompat(process) }
            return ShellResult("", "timeout", -1)
        }
        t.join(1500); te.join(800)
        val code = runCatching { process.exitValue() }.getOrDefault(-1)
        return ShellResult(String(out.toByteArray(), Charsets.UTF_8), String(err.toByteArray(), Charsets.UTF_8), code)
    }

    /** 通用执行：asRoot 且已授权时走提权引擎，否则走普通 sh */
    fun runScript(script: String, asRoot: Boolean = false, timeoutMs: Long = 25_000): ShellResult =
        if (asRoot && engine != ShellEngine.NONE) execScript(script, timeoutMs)
        else exec(listOf("/system/bin/sh", "-c", script), timeoutMs)

    /** 判断目录是否存在并返回真实路径；提权模式下可以解析受限目录 */
    fun resolveDir(path: String): String? {
        if (engine == ShellEngine.NONE) {
            val f = File(path)
            return if (f.isDirectory) runCatching { f.canonicalPath }.getOrDefault(f.absolutePath) else null
        }
        val r = sh("if [ -d ${q(path)} ]; then readlink -f ${q(path)} 2>/dev/null || echo ${q(path)}; fi")
        return r.out.trim().takeIf { it.isNotEmpty() }
    }

    /** 以当前引擎执行一段 shell 脚本（NATIVE 也执行，只是身份是应用自己） */
    fun sh(script: String, timeoutMs: Long = 20_000): ShellResult =
        if (engine == ShellEngine.NONE) ShellResult("", "shell engine disabled", -1)
        else execScript(script, timeoutMs)

    /** 目录是否存在；NATIVE 下 Java 的 exists() 对 /data 会误判，用 shell 再确认 */
    fun shellExists(path: String): Boolean =
        sh("[ -e ${q(path)} ] && echo 1 || echo 0", 6_000).out.trim() == "1"

    /** 二进制安全的读取：脚本 stdout 原样取回，不做任何编码转换 */
    fun execScriptBytes(script: String, timeoutMs: Long = 20_000): ByteArray {
        if (engine == ShellEngine.NONE) return ByteArray(0)
        var process: Process? = null
        return try {
            process = startProcess(engine, script)
            val out = ByteArrayOutputStream()
            val drain = Thread {
                runCatching { process.errorStream.use { it.copyTo(ByteArrayOutputStream(), 4 * 1024) } }
            }
            drain.start()
            process.inputStream.use { it.copyTo(out, 128 * 1024) }
            if (!waitForCompat(process, timeoutMs)) runCatching { killCompat(process) }
            drain.join(500)
            out.toByteArray()
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            degradeIfBroken()
            ByteArray(0)
        }
    }

    /** 兼容旧签名：把一个命令列表拼成脚本执行 */
    fun execBytes(cmd: List<String>, timeoutMs: Long = 20_000): ByteArray {
        if (engine == ShellEngine.NONE) return ByteArray(0)
        return execScriptBytes(cmd.joinToString(" "), timeoutMs)
    }

    /** API 24/25 没有带超时的 waitFor，用轮询退避模拟 */
    private fun waitForCompat(process: Process, timeoutMs: Long): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (e: IllegalThreadStateException) {
                runCatching { Thread.sleep(25) }
            }
        }
        return false
    }

    private fun killCompat(process: Process) {
        if (Build.VERSION.SDK_INT >= 26) process.destroyForcibly() else process.destroy()
    }

    /** 单引号转义，防止路径中的空格与特殊字符破坏命令 */
    fun q(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    // ---------------- 目录枚举 ----------------

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val DATE_FMT_SEC = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * 用 `ls -la` 列出真实目录内容。返回 null 表示连提权通道都读不到。
     */
    fun list(dir: String, showHidden: Boolean): List<FileEntry>? {
        val r = sh("ls -la --color=never ${q(dir)} 2>/dev/null || ls -la ${q(dir)}")
        if (!r.ok || r.out.isBlank()) return null
        val entries = ArrayList<FileEntry>()
        for (line in r.out.lineSequence()) {
            if (line.isBlank()) continue
            val trimmed = line.trimStart()
            if (trimmed.startsWith("total ") || trimmed.startsWith("总共")) continue
            val parsed = parseLine(dir, line) ?: continue
            if (!showHidden && parsed.name.startsWith(".")) continue
            entries.add(parsed)
        }
        return entries
    }

    private fun parseLine(dir: String, line: String): FileEntry? {
        val parts = line.trim().split(Regex("\\s+"), limit = 8)
        if (parts.size < 8) return null
        val perms = parts[0]
        if (perms.length < 10) return null
        val mode = perms[0]
        val nameRaw = parts[7]
        // 符号链接显示为 "name -> target"，只取名称部分
        val name = nameRaw.substringBefore(" -> ")
        if (name.isEmpty() || name == "." || name == "..") return null
        val size = parts[4].toLongOrNull() ?: 0L
        val time = parseTime(parts[5], parts[6])
        val file = File(dir, name)
        val isLink = mode == 'l'
        val isDir = if (isLink) {
            runCatching { file.isDirectory }.getOrDefault(false)
        } else {
            mode == 'd'
        }
        return FileEntry(
            file = file,
            name = name,
            isDir = isDir,
            size = if (isDir) 0L else size,
            lastModified = time,
            isHidden = name.startsWith("."),
            canRead = perms[1] == 'r' || perms[4] == 'r' || perms[7] == 'r',
            canWrite = perms[2] == 'w' || perms[5] == 'w' || perms[8] == 'w',
            mode = perms
        )
    }

    private fun parseTime(date: String, time: String): Long {
        val stamp = "$date $time"
        return runCatching { DATE_FMT.parse(stamp)?.time ?: 0L }.getOrNull()
            ?: runCatching { DATE_FMT_SEC.parse(stamp)?.time ?: 0L }.getOrDefault(0L)
    }

    // ---------------- 读 ----------------

    /** 读取任意偏移的字节（1-based tail，二进制安全） */
    fun readRange(path: String, offset: Long, length: Int): ByteArray {
        val start = offset + 1
        return execScriptBytes("tail -c +$start -- ${q(path)} | head -c $length")
    }

    /** 读取整个文件（超长会截断到 [max]） */
    fun readAll(path: String, max: Long): ByteArray =
        execScriptBytes("head -c $max -- ${q(path)}")

    // ---------------- 写 ----------------

    /** 覆盖写入：内容从 stdin 送进 cat，避免临时文件与参数长度限制 */
    fun writeAll(path: String, bytes: ByteArray): Boolean = runCatching {
        if (engine == ShellEngine.NONE) return false
        val process = startProcess(engine, "cat > ${q(path)}")
        // stdout 必须同时抽干，否则大文件写到一半会把管道堵死
        val drain = Thread { runCatching { process.inputStream.use { it.copyTo(ByteArrayOutputStream(), 8 * 1024) } } }
        val drainErr = Thread { runCatching { process.errorStream.use { it.copyTo(ByteArrayOutputStream(), 4 * 1024) } } }
        drain.start(); drainErr.start()
        val okWrite = runCatching {
            process.outputStream.use { it.write(bytes) }
            true
        }.getOrDefault(false)
        val ok = waitForCompat(process, 30_000)
        if (!ok) runCatching { killCompat(process) }
        drain.join(600); drainErr.join(400)
        okWrite && ok && runCatching { process.exitValue() }.getOrDefault(-1) == 0
    }.getOrDefault(false)

    /** 修改权限位，如 "644" */
    fun chmod(path: String, mode: String): Boolean {
        if (!mode.matches(Regex("[0-7]{3,4}"))) return false
        return sh("chmod $mode ${q(path)}").ok
    }

    /** 读取权限位，如 "rw-r--r--" */
    fun modeOf(path: String): String? {
        val r = sh("stat -c %A ${q(path)}")
        return r.out.trim().takeIf { r.ok && it.length >= 10 }
    }

    fun delete(path: String): Boolean = sh("rm -rf ${q(path)}").ok

    fun mkdir(path: String): Boolean = sh("mkdir -p ${q(path)}").ok

    fun copy(src: String, dest: String): Boolean = sh("cp -R ${q(src)} ${q(dest)}").ok

    fun move(src: String, dest: String): Boolean = sh("mv ${q(src)} ${q(dest)}").ok

    fun rename(src: String, dest: String): Boolean = sh("mv ${q(src)} ${q(dest)}").ok
}
