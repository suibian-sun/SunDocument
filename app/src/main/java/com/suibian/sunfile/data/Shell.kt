package com.suibian.sunfile.data

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ShellResult(val out: String, val err: String, val code: Int) {
    val ok: Boolean get() = code == 0
}

/**
 * Root 命令行后端。
 *
 * 系统对 /data、/system、Android/data 等目录有 SELinux 与 scoped storage 限制，
 * 普通 API 无法枚举。这里通过 `su` 以 root 身份执行 ls/cat/cp，
 * 拿到的是真实文件系统数据，不做任何虚拟挂载或占位条目。
 *
 * 只有普通 File API 失败时才触发，避免正常浏览时弹授权框。
 */
object Shell {

    /**
     * Root 会话状态。默认关闭：不在浏览时自动执行 su，
     * 否则未 root 的机器每次进受限目录都会弹授权框并阻塞数秒。
     * 由用户在设置里主动开启。
     */
    @Volatile
    var enabled: Boolean = false
        private set

    val rootAvailable: Boolean get() = enabled

    /** 主动申请 root：执行一次 su 探测，成功则后续所有受限操作走 shell */
    fun requestRoot(): Boolean {
        val ok = runCatching {
            val r = exec(listOf("su", "-c", "id"), 8_000)
            r.ok && r.out.contains("uid=0")
        }.getOrDefault(false)
        enabled = ok
        return ok
    }

    fun reset() {
        enabled = false
    }

    fun exec(cmd: List<String>, timeoutMs: Long = 15_000): ShellResult {
        var process: Process? = null
        return try {
            val pb = ProcessBuilder(cmd).redirectErrorStream(false)
            process = pb.start()
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val t = Thread {
                runCatching {
                    process.inputStream.use { it.copyTo(out, 64 * 1024) }
                }
            }
            val te = Thread {
                runCatching {
                    process.errorStream.use { it.copyTo(err, 8 * 1024) }
                }
            }
            t.start(); te.start()
            val finished = waitForCompat(process, timeoutMs)
            if (!finished) {
                runCatching { killCompat(process) }
                return ShellResult("", "timeout", -1)
            }
            t.join(1500); te.join(800)
            ShellResult(
                String(out.toByteArray(), Charsets.UTF_8),
                String(err.toByteArray(), Charsets.UTF_8),
                process.exitValue()
            )
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            ShellResult("", e.message ?: "shell error", -1)
        }
    }

    /** 通用执行：asRoot 且已授权时走 su，否则走普通 sh */
    fun runScript(script: String, asRoot: Boolean = false, timeoutMs: Long = 25_000): ShellResult =
        if (asRoot && enabled) exec(listOf("su", "-c", script), timeoutMs)
        else exec(listOf("/system/bin/sh", "-c", script), timeoutMs)

    /** 判断目录是否存在并返回真实路径；root 模式下可以解析受限目录 */
    fun resolveDir(path: String): String? {
        if (!enabled) {
            val f = File(path)
            return if (f.isDirectory) runCatching { f.canonicalPath }.getOrDefault(f.absolutePath) else null
        }
        val r = sh("if [ -d ${q(path)} ]; then readlink -f ${q(path)} 2>/dev/null || echo ${q(path)}; fi")
        return r.out.trim().takeIf { it.isNotEmpty() }
    }

    /** 以 root 执行一段 shell 脚本 */
    fun sh(script: String, timeoutMs: Long = 20_000): ShellResult =
        if (!enabled) ShellResult("", "root disabled", -1)
        else exec(listOf("su", "-c", script), timeoutMs)

    /** 执行二进制安全的读取（不经 UTF-8 转换） */
    fun execBytes(cmd: List<String>, timeoutMs: Long = 20_000): ByteArray {
        if (!enabled) return ByteArray(0)
        var process: Process? = null
        return try {
            process = ProcessBuilder(cmd).start()
            val out = ByteArrayOutputStream()
            val drain = Thread {
                runCatching { process.errorStream.use { it.copyTo(ByteArrayOutputStream(), 4 * 1024) } }
            }
            drain.start()
            process.inputStream.use { it.copyTo(out, 128 * 1024) }
            val finished = waitForCompat(process, timeoutMs)
            if (!finished) runCatching { killCompat(process) }
            drain.join(500)
            out.toByteArray()
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            ByteArray(0)
        }
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
     * 用 `ls -la` 列出真实目录内容。返回 null 表示连 root 都读不到。
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
        return execBytes(listOf("su", "-c", "tail -c +$start -- ${q(path)} | head -c $length"))
    }

    /** 读取整个文件（超长会截断到 [max]） */
    fun readAll(path: String, max: Long): ByteArray =
        execBytes(listOf("su", "-c", "head -c $max -- ${q(path)}"))

    // ---------------- 写 ----------------

    /** 覆盖写入：通过 stdin 送入 cat，避免临时文件与参数长度限制 */
    fun writeAll(path: String, bytes: ByteArray): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "cat > ${q(path)}").start()
        process.outputStream.use { it.write(bytes) }
        val ok = waitForCompat(process, 30_000)
        if (!ok) runCatching { killCompat(process) }
        ok && process.exitValue() == 0
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

    /** root 下删除（root 可删系统目录内容） */
    fun delete(path: String): Boolean = sh("rm -rf ${q(path)}").ok

    fun mkdir(path: String): Boolean = sh("mkdir -p ${q(path)}").ok

    fun copy(src: String, dest: String): Boolean = sh("cp -R ${q(src)} ${q(dest)}").ok

    fun move(src: String, dest: String): Boolean = sh("mv ${q(src)} ${q(dest)}").ok

    fun rename(src: String, dest: String): Boolean = sh("mv ${q(src)} ${q(dest)}").ok
}
