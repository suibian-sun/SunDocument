package com.suibian.sunfile.data

import com.suibian.sunfile.util.Fmt
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 局域网 HTTP 文件服务。
 *
 * 手机开热点或与电脑同一 WiFi 时，在浏览器里输入页面给出的地址即可：
 * 浏览目录、下载文件、上传文件（PUT）。不依赖任何第三方库，
 * 只用一个 ServerSocket + 线程池，退出应用时随服务一起关闭。
 */
object RemoteServer {

    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "sun-remote").apply { isDaemon = true }
    }

    private val _running = MutableStateFlow(false)
    val runningFlow: StateFlow<Boolean> = _running

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    var port: Int = 8080
        private set
    var root: File = File("/storage/emulated/0")
        private set

    val isRunning: Boolean get() = running.get()

    fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun start(rootDir: File, portNumber: Int = 8080): Result<String> {
        if (running.get()) return Result.success("已启动")
        stop()
        root = rootDir
        port = portNumber.coerceIn(1024, 65535)
        return try {
            val s = ServerSocket(port)
            server = s
            running.set(true)
            _running.value = true
            log("服务已启动 · 根目录 ${root.absolutePath}")
            acceptThread = Thread({
                while (running.get()) {
                    val socket = runCatching { s.accept() }.getOrNull() ?: break
                    pool.execute { handle(socket) }
                }
            }, "sun-remote-accept").apply { isDaemon = true; start() }
            Result.success("已在 ${port} 端口启动")
        } catch (e: Throwable) {
            running.set(false)
            _running.value = false
            log("启动失败：${e.message}")
            Result.failure(e)
        }
    }

    fun stop() {
        running.set(false)
        _running.value = false
        runCatching { server?.close() }
        server = null
        acceptThread = null
        if (_logs.value.isNotEmpty()) log("服务已停止")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (listOf("$stamp  $line") + _logs.value).take(120)
    }

    // ---------------- 连接处理 ----------------

    private fun handle(socket: Socket) {
        runCatching {
            socket.use {
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val rawPath = parts[1]

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }

                val path = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")
                val target = resolve(path) ?: run {
                    respond(output, 403, "text/plain; charset=utf-8", "非法路径")
                    return
                }

                when (method) {
                    "GET", "HEAD" -> {
                        if (target.isDirectory) {
                            val html = listing(target, path)
                            respond(output, 200, "text/html; charset=utf-8", html, headOnly = method == "HEAD")
                            log("浏览 $path")
                        } else if (target.isFile) {
                            sendFile(output, target, headOnly = method == "HEAD")
                            log("下载 ${target.name}")
                        } else {
                            respond(output, 404, "text/plain; charset=utf-8", "未找到")
                        }
                    }
                    "PUT", "POST" -> {
                        val len = headers["content-length"]?.toLongOrNull() ?: 0L
                        if (len <= 0) {
                            respond(output, 400, "text/plain; charset=utf-8", "缺少 Content-Length")
                            return
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            var remain = len
                            val buf = ByteArray(64 * 1024)
                            while (remain > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), remain).toInt())
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                remain -= n
                            }
                        }
                        log("上传 ${target.name} (${Fmt.size(len)})")
                        respond(output, 200, "text/plain; charset=utf-8", "OK")
                    }
                    else -> respond(output, 405, "text/plain; charset=utf-8", "不支持的方法")
                }
            }
        }.onFailure {
            log("连接异常：${it.message}")
        }
    }

    /** 把 URL 路径映射到 root 下，禁止 ../ 越界 */
    private fun resolve(path: String): File? {
        val clean = path.trimStart('/')
        val candidate = if (clean.isEmpty()) root else File(root, clean)
        val canonicalRoot = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
        val canonical = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
        return if (canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator)) candidate else null
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun listing(dir: File, path: String): String {
        val entries = runCatching { dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) }
            .getOrNull() ?: emptyList()
        val title = if (path == "/") root.absolutePath else path
        val sb = StringBuilder()
        sb.append(
            """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title>
<style>
body{font:14px/1.6 -apple-system,system-ui,"Segoe UI",sans-serif;margin:0;background:#fafafa;color:#222}
header{background:#303030;color:#fff;padding:14px 18px;font-size:15px}
header small{display:block;color:#b9bcc1;font-weight:400;font-size:11px;margin-top:2px}
ul{list-style:none;margin:0;padding:0}
li{border-bottom:1px solid #e6e6e6}
a{display:flex;gap:10px;padding:11px 18px;color:#1565d8;text-decoration:none;align-items:center}
a:active{background:#eef1f6}
.dir{color:#111;font-weight:600}
.meta{margin-left:auto;color:#999;font-size:11px;font-family:ui-monospace,monospace}
.up{color:#666}
form{padding:14px 18px}
input[type=file]{width:100%;margin-bottom:8px}
button{background:#1565d8;color:#fff;border:0;border-radius:4px;padding:9px 16px;font-size:13px}
</style></head><body>
<header>SunDocument 远程管理<small>$title</small></header><ul>"""
        )
        if (path != "/") {
            sb.append("""<li><a class="up" href="${parentOf(path)}">↩ 返回上级</a></li>""")
        }
        entries.forEach { f ->
            val name = f.name
            val href = if (path == "/") "/$name" else "$path/$name"
            val cls = if (f.isDirectory) "dir" else ""
            val meta = if (f.isDirectory) "目录" else Fmt.size(f.length())
            val suffix = if (f.isDirectory) "/" else ""
            sb.append(
                """<li><a class="$cls" href="${escapeAttr(href)}">${if (f.isDirectory) "📁" else "📄"} ${escapeHtml(name)}$suffix<span class="meta">$meta</span></a></li>"""
            )
        }
        sb.append("</ul>")
        sb.append(
            """<form method="put" enctype="text/plain" onsubmit="upload(event)">
<input type="file" id="f" multiple><button type="submit">上传到当前目录</button></form>
<script>
async function upload(e){
  e.preventDefault();
  const files=document.getElementById('f').files;
  for(const file of files){
    await fetch(location.pathname.replace(/\/$/,'')+'/'+encodeURIComponent(file.name),{method:'PUT',body:file});
  }
  location.reload();
}
</script></body></html>"""
        )
        return sb.toString()
    }

    private fun parentOf(path: String): String {
        val p = path.trimEnd('/')
        val idx = p.lastIndexOf('/')
        return if (idx <= 0) "/" else p.substring(0, idx)
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttr(s: String): String = escapeHtml(s).replace("\"", "&quot;")

    private fun sendFile(out: BufferedOutputStream, file: File, headOnly: Boolean) {
        val len = file.length()
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ${mimeOf(file)}\r\n")
            append("Content-Length: $len\r\n")
            append("Content-Disposition: attachment; filename=\"${file.name.replace("\"", "")}\"\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        if (!headOnly) {
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
        }
        out.flush()
    }

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "txt", "md", "log" -> "text/plain; charset=utf-8"
        "html", "htm" -> "text/html; charset=utf-8"
        "json" -> "application/json"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun respond(
        out: BufferedOutputStream,
        code: Int,
        type: String,
        body: String,
        headOnly: Boolean = false
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    @Suppress("unused")
    private fun safeClose(s: Socket) {
        try {
            s.close()
        } catch (_: IOException) {
        }
    }
}
