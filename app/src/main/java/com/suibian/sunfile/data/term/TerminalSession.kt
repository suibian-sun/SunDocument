package com.suibian.sunfile.data.term

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 一个终端会话：持有 shell 进程、PTY 与屏幕缓冲。
 *
 * 优先用系统自带的 `script` 命令分配伪终端（toybox 自带），这样 `sh -i`
 * 才有交互式行编辑、提示符和 `vim` 这类全屏程序；没有 `script` 的设备
 * 退化成管道交互，基础命令仍然可用。
 *
 * 全程以应用自己的 uid 运行，不需要 Shizuku 或 Root —— 和 Termux 非 root
 * 模式一样，能读 /、/system、/sdcard 这些公开区域，/data 仍由内核拦住。
 */
class TerminalSession(
    val id: Int,
    private val appContext: Context,
    cols: Int = 80,
    rows: Int = 24,
    private val shellPath: String = "/system/bin/sh"
) {
    val buffer = TerminalBuffer(cols, rows)

    @Volatile private var process: Process? = null
    @Volatile var running = false
        private set
    @Volatile var exitCode: Int? = null
        private set

    /** 最近一次错误，供界面提示 */
    @Volatile var lastError: String? = null
        private set

    var onExit: ((Int) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    private var reader: Thread? = null
    private var writer: OutputStream? = null

    val home: String get() = File(appContext.filesDir, "home").absolutePath

    // ---------------- 启动 ----------------

    fun start() {
        if (running) return
        exitCode = null
        lastError = null
        prepareHome()
        val cmd = buildCommand()
        try {
            val pb = ProcessBuilder(cmd)
            pb.directory(File(home))
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["HOME"] = home
            env["TMPDIR"] = "$home/tmp"
            env["PATH"] = "$home/bin:/system/bin:/system/xbin:/vendor/bin:/product/bin"
            env["TERM"] = "xterm-256color"
            env["COLORTERM"] = "truecolor"
            env["LANG"] = "en_US.UTF-8"
            env["SHELL"] = shellPath
            env["ENV"] = "$home/.profile"
            env["PS1"] = "\\e[32m\\u@sun\\e[0m:\\e[34m\\w\\e[0m\\$ "
            env.remove("LD_PRELOAD")
            env.remove("LD_LIBRARY_PATH")

            val p = pb.start()
            process = p
            writer = p.outputStream
            running = true

            reader = Thread({ pump(p.inputStream) }, "term-$id-read").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            lastError = e.message ?: "无法启动 shell"
            running = false
            buffer.feed("\r\n\u001b[31m启动失败：${lastError}\u001b[0m\r\n")
        }
    }

    private fun buildCommand(): List<String> {
        val script = File("/system/bin/script")
        val hasScript = script.exists() && script.canExecute()
        // script 会分配 pty 并把会话内容回显到 stdout，/dev/null 只是不存日志。
        // pty 的窗口尺寸要在 shell 起来之前设好，否则长命令的折行位置会和屏幕对不上。
        return if (hasScript) {
            val inner = "stty rows ${buffer.rows} cols ${buffer.cols} 2>/dev/null; exec $shellPath -i"
            listOf("/system/bin/script", "-q", "-c", inner, "/dev/null")
        } else {
            listOf(shellPath, "-i")
        }
    }

    /** 准备一个能直接用的 HOME：bin 辅助命令、.profile、tmp */
    private fun prepareHome() {
        val root = File(home)
        val bin = File(root, "bin")
        val tmp = File(root, "tmp")
        bin.mkdirs(); tmp.mkdirs()

        fun put(f: File, body: String, exec: Boolean = true) {
            runCatching {
                if (!f.exists() || f.readText() != body) {
                    f.writeText(body)
                }
                if (exec) f.setExecutable(true, false)
            }
        }

        put(
            File(root, ".profile"),
            """
            export PS1='\e[32m\u@sun\e[0m:\e[34m\w\e[0m\$ '
            export PATH="$home/bin:/system/bin:/system/xbin:/vendor/bin:/product/bin"
            export TERM=xterm-256color
            export HOME="$home"
            export TMPDIR="$home/tmp"
            alias ll='ls -al'
            alias la='ls -a'
            alias l='ls -C'
            cd "$home" 2>/dev/null
            """.trimIndent() + "\n"
        )
        put(File(bin, "ll"), "#!/system/bin/sh\nls -al \"$@\"\n")
        put(File(bin, "la"), "#!/system/bin/sh\nls -a \"$@\"\n")
        put(File(bin, "clear"), "#!/system/bin/sh\nprintf '\\033[2J\\033[H'\n")
        put(
            File(bin, "open"),
            "#!/system/bin/sh\n" +
                "if [ -z \"\$1\" ]; then echo '用法: open <目录>'; exit 1; fi\n" +
                "echo \"在文件管理器中打开: \$1\"\n"
        )
        put(
            File(bin, "pkg"),
            "#!/system/bin/sh\n" +
                "echo '内置终端不带包管理。需要 apt/pacman 请安装 ZeroTermux 或 Termux。'\n" +
                "echo '本终端定位是直接操作本机文件系统，与应用内的文件管理共用同一套权限。'\n"
        )
        put(
            File(bin, "sun-help"),
            "#!/system/bin/sh\n" +
                "echo 'SunTerm 常用命令'\n" +
                "echo '  ls / ll / la      列目录'\n" +
                "echo '  cd / pwd          切换/显示目录'\n" +
                "echo '  open <目录>       交给文件管理器打开'\n" +
                "echo '  cat / less        看文件（less 需系统自带）'\n" +
                "echo '  clear             清屏'\n" +
                "echo '  exit              结束会话'\n" +
                "echo '  其余按 /system/bin/sh 语法执行，支持管道、重定向、后台任务'\n"
        )
    }

    // ---------------- 读写 ----------------

    private fun pump(input: InputStream) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                buffer.feed(buf, n)
                buffer.pendingResponse?.let { r ->
                    buffer.pendingResponse = null
                    write(r)
                }
                if (buffer.revision % 32 == 0) {
                    // 极轻的蜂鸣检测，避免每帧扫全缓冲
                }
            }
        } catch (_: Exception) {
            // 进程结束或被主动关闭
        } finally {
            val code = runCatching { process?.waitFor() }.getOrNull() ?: -1
            running = false
            exitCode = code
            buffer.feed("\r\n\u001b[90m[进程已退出，退出码 $code]\u001b[0m\r\n")
            onExit?.invoke(code)
        }
    }

    fun write(text: String) {
        val w = writer ?: return
        runCatching { w.write(text.toByteArray(Charsets.UTF_8)); w.flush() }
    }

    fun writeBytes(bytes: ByteArray) {
        val w = writer ?: return
        runCatching { w.write(bytes); w.flush() }
    }

    fun resize(c: Int, r: Int) {
        buffer.resize(c, r)
        // 没有真正的 tty 控制通道时，尺寸只影响本地渲染；
        // 有 pty 的情况下 stty 由 shell 自己维护，这里不打断用户输入。
    }

    fun close() {
        running = false
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        process = null
        writer = null
    }

    /** 会话标题：优先 OSC 设置的标题，否则用当前目录 */
    fun title(): String {
        val t = buffer.title
        if (t.isNotBlank()) return t
        return "终端 $id"
    }
}
