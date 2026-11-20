package com.suibian.sunfile.data

import android.content.Context
import android.os.Build
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 本机 MCP 服务：只监听 127.0.0.1，外部设备连不上。
 *
 * 走 JSON-RPC 2.0 over HTTP，实现 initialize / tools/list / tools/call 三个方法，
 * 工具直接复用应用自己的文件层（有 Root 时自动走 shell，能读写 /data 等目录）。
 * 给 AI 客户端一个能真正操作手机文件的入口，而不是只能看的界面。
 */
object Mcp {

    data class Log(val time: Long, val method: String, val detail: String, val ok: Boolean, val ms: Long)

    data class Status(
        val running: Boolean = false,
        val port: Int = 8686,
        val token: String = "",
        val requests: Int = 0,
        val lastError: String? = null,
        val logs: List<Log> = emptyList()
    )

    private const val PREFS = "sunfile_mcp"
    private const val MAX_BODY = 4 * 1024 * 1024
    private const val MAX_LOG = 60

    private val _state = MutableStateFlow(Status())
    val state: StateFlow<Status> = _state

    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val workers = Executors.newFixedThreadPool(3)
    private val counter = AtomicInteger(0)
    @Volatile private var stopped = true

    // ---------------- 生命周期 ----------------

    fun autoStart(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean("enabled", false)) return
        val port = sp.getInt("port", 8686)
        val token = sp.getString("token", "") ?: ""
        start(context, port, token)
    }

    fun start(context: Context, port: Int, token: String): Boolean {
        stop()
        val app = context.applicationContext
        return runCatching {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16)
            socket.soTimeout = 0
            server = socket
            stopped = false
            _state.value = _state.value.copy(running = true, port = port, token = token, lastError = null, logs = emptyList())
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", true).putInt("port", port).putString("token", token).apply()
            acceptThread = Thread({ acceptLoop(app, socket) }, "mcp-accept").also {
                it.isDaemon = true
                it.start()
            }
            true
        }.getOrElse {
            _state.value = _state.value.copy(running = false, lastError = it.message ?: "启动失败")
            false
        }
    }

    fun stop() {
        stopped = true
        runCatching { server?.close() }
        server = null
        acceptThread = null
        _state.value = _state.value.copy(running = false)
    }

    fun setEnabled(context: Context, enabled: Boolean, port: Int, token: String) {
        if (enabled) start(context, port, token) else {
            stop()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        }
    }

    fun newToken(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        return (1..18).map { chars.random() }.joinToString("")
    }

    // ---------------- HTTP 层 ----------------

    private fun acceptLoop(context: Context, socket: ServerSocket) {
        while (!stopped) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            workers.execute { runCatching { handle(context, client) } }
        }
    }

    private fun handle(context: Context, client: Socket) {
        client.use { c ->
            c.soTimeout = 10_000
            val input = BufferedInputStream(c.getInputStream())
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().split(" ")
            if (requestLine.size < 2) return
            val method = requestLine[0]
            val target = requestLine[1]
            var length = 0
            var auth = ""
            lines.drop(1).forEach { line ->
                val i = line.indexOf(':')
                if (i > 0) {
                    val k = line.substring(0, i).trim().lowercase(Locale.US)
                    val v = line.substring(i + 1).trim()
                    if (k == "content-length") length = v.toIntOrNull() ?: 0
                    if (k == "authorization") auth = v
                }
            }
            if (length > MAX_BODY) {
                respond(c, 413, "application/json", errorJson(null, -32600, "请求体过大"))
                return
            }
            val body = if (length > 0) readBody(input, length) else ByteArray(0)

            if (method == "OPTIONS") {
                respond(c, 204, "text/plain; charset=utf-8", "")
                return
            }
            if (method == "GET") {
                val info = "SunDocument MCP\n" +
                    "endpoint: http://127.0.0.1:${_state.value.port}/mcp\n" +
                    "auth: Bearer <token>\n" +
                    "tools: ${TOOLS.size}\n"
                respond(c, 200, "text/plain; charset=utf-8", info)
                return
            }
            if (method != "POST") {
                respond(c, 405, "text/plain; charset=utf-8", "只接受 POST /mcp")
                return
            }
            val token = _state.value.token
            if (token.isNotEmpty()) {
                val given = auth.removePrefix("Bearer ").trim()
                if (given != token) {
                    log("HTTP", "鉴权失败", false, 0)
                    respond(c, 401, "application/json", errorJson(null, -32001, "token 无效"))
                    return
                }
            }
            if (target.trimEnd('/').let { it != "/mcp" && it != "" }) {
                respond(c, 404, "text/plain; charset=utf-8", "未知路径 $target")
                return
            }

            val started = System.currentTimeMillis()
            val text = String(body, Charsets.UTF_8)
            val req = runCatching { LenientJson(text).parse() as? Map<*, *> }.getOrNull()
            if (req == null) {
                respond(c, 200, "application/json", errorJson(null, -32700, "JSON 解析失败"))
                return
            }
            val id = req["id"]
            val name = req["method"] as? String ?: ""
            if (id == null) {
                log(name, "通知", true, System.currentTimeMillis() - started)
                respond(c, 202, "text/plain; charset=utf-8", "")
                return
            }
            val result = runCatching { dispatch(context, name, req["params"] as? Map<*, *> ?: emptyMap<Any, Any>()) }
            val elapsed = System.currentTimeMillis() - started
            result.onSuccess { payload ->
                log(name, detailOf(name, req["params"] as? Map<*, *>), true, elapsed)
                respond(c, 200, "application/json", """{"jsonrpc":"2.0","id":${json(id)},"result":$payload}""")
            }.onFailure {
                log(name, it.message ?: "失败", false, elapsed)
                respond(
                    c, 200, "application/json",
                    """{"jsonrpc":"2.0","id":${json(id)},"error":{"code":-32603,"message":${json(it.message ?: "内部错误")}}}"""
                )
            }
        }
    }

    private fun readHead(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (out.size() < 16 * 1024) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else String(out.toByteArray(), Charsets.UTF_8)
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun readBody(input: BufferedInputStream, length: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(length, MAX_BODY))
        val buf = ByteArray(8192)
        var left = length
        while (left > 0) {
            val read = input.read(buf, 0, minOf(buf.size, left))
            if (read < 0) break
            out.write(buf, 0, read)
            left -= read
        }
        return out.toByteArray()
    }

    private fun respond(c: Socket, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code ${reason(code)}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Headers: Authorization, Content-Type, Mcp-Protocol-Version\r\n" +
            "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            c.getOutputStream().apply {
                write(header.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
        }
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"
        202 -> "Accepted"
        204 -> "No Content"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        413 -> "Payload Too Large"
        else -> "OK"
    }

    // ---------------- JSON-RPC ----------------

    private fun dispatch(context: Context, method: String, params: Map<*, *>): String = when (method) {
        "initialize" -> {
            val v = params["protocolVersion"] as? String ?: "2024-11-05"
            """{"protocolVersion":${json(v)},"capabilities":{"tools":{"listChanged":false}},"serverInfo":{"name":"SunDocument","version":"${com.suibian.sunfile.BuildConfig.VERSION_NAME}"}}"""
        }
        "ping" -> "{}"
        "tools/list" -> """{"tools":[${TOOLS.joinToString(",") { it.schema }}]}"""
        "tools/call" -> {
            val name = params["name"] as? String ?: ""
            val args = params["arguments"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val tool = TOOLS.firstOrNull { it.name == name } ?: error("未知工具 $name")
            val output = runCatching { tool.run(context, args) }
            output.fold(
                onSuccess = { """{"content":[{"type":"text","text":${json(it)}}],"isError":false}""" },
                onFailure = { """{"content":[{"type":"text","text":${json(it.message ?: "执行失败")}}],"isError":true}""" }
            )
        }
        "resources/list", "prompts/list" -> """{"${method.substringBefore('/')}":[]}"""
        else -> throw IllegalArgumentException("未实现的方法 $method")
    }

    private fun errorJson(id: Any?, code: Int, message: String): String =
        """{"jsonrpc":"2.0","id":${json(id)},"error":{"code":$code,"message":${json(message)}}}"""

    private fun detailOf(method: String, params: Map<*, *>?): String {
        if (params == null) return ""
        if (method == "tools/call") {
            val name = params["name"] as? String ?: ""
            val args = params["arguments"] as? Map<*, *> ?: return name
            val arg = args["path"] ?: args["root"] ?: args["command"]
            return if (arg != null) "$name  ${arg}" else name
        }
        return ""
    }

    private fun log(method: String, detail: String, ok: Boolean, ms: Long) {
        counter.incrementAndGet()
        val entry = Log(System.currentTimeMillis(), method, detail, ok, ms)
        val old = _state.value
        _state.value = old.copy(
            requests = old.requests + 1,
            logs = (listOf(entry) + old.logs).take(MAX_LOG)
        )
    }

    // ---------------- 工具 ----------------

    private class Tool(
        val name: String,
        val description: String,
        val props: String,
        val required: List<String> = emptyList(),
        val run: (Context, Map<*, *>) -> String
    ) {
        val schema: String
            get() = """{"name":${json(name)},"description":${json(description)},"inputSchema":{"type":"object","properties":$props,"required":[${required.joinToString(",") { json(it) }}]}}"""
    }

    private val TOOLS = listOf(
        Tool(
            "device_info",
            "设备与应用信息：型号、系统版本、存储占用、当前工作目录",
            "{}"
        ) { ctx, _ ->
            val stat = File("/").let { android.os.StatFs(it.path) }
            buildString {
                appendLine("model: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
                appendLine("abi: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("root: ${if (Shell.rootAvailable) "可用" else "不可用"}")
                appendLine("storage: ${Fmt.size(stat.blockCountLong * stat.blockSizeLong)} 总 / ${Fmt.size(stat.availableBlocksLong * stat.blockSizeLong)} 可用")
                append("app_dir: ${ctx.filesDir.parentFile?.absolutePath}")
            }
        },
        Tool(
            "list_dir",
            "列出目录内容。未授权目录会自动尝试用 Root shell 读取",
            """{"path":{"type":"string","description":"绝对路径，默认 /"}}"""
        ) { _, args ->
            val path = str(args, "path") ?: "/"
            val dir = File(path)
            val items = dir.listFiles()?.toList()
            if (items != null) {
                val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                sorted.take(500).joinToString("\n") { f ->
                    val kind = if (f.isDirectory) "d" else "-"
                    "%s %10d %s %s".format(Locale.US, kind, if (f.isDirectory) 0L else f.length(), time(f.lastModified()), f.name)
                }.ifEmpty { "(空目录)" }
            } else {
                val out = Shell.sh("ls -la ${Shell.q(path)} 2>&1")
                val text = (out.out + out.err).trim()
                if (!out.ok) error("无法读取 $path：${text.ifEmpty { "权限不足" }}")
                text.ifEmpty { "(空目录)" }
            }
        },
        Tool(
            "read_file",
            "读取文本文件；二进制文件返回 base64",
            """{"path":{"type":"string"},"max_bytes":{"type":"integer","description":"默认 262144"}}""",
            listOf("path")
        ) { _, args ->
            val path = str(args, "path") ?: error("缺少 path")
            val max = (num(args, "max_bytes") ?: 262_144).coerceIn(1, 4 * 1024 * 1024)
            val file = File(path)
            if (!file.exists()) error("文件不存在：$path")
            val bytes = if (file.canRead()) {
                file.inputStream().use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (out.size() < max) {
                        val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } else {
                Shell.readAll(path, max.toLong())
            }
            if (bytes.isEmpty()) return@Tool "(空文件)"
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
            val binary = text.any { it.code < 9 } || text.count { it == '\uFFFD' } > 4
            if (binary) "base64:\n" + Base64.getEncoder().encodeToString(bytes)
            else if (file.length() > max) text + "\n…（已截断，共 ${Fmt.size(file.length())}）"
            else text
        },
        Tool(
            "write_file",
            "写入文本文件（覆盖）。content 以 base64: 开头时按 base64 解码",
            """{"path":{"type":"string"},"content":{"type":"string"}}""",
            listOf("path", "content")
        ) { _, args ->
            val path = str(args, "path") ?: error("缺少 path")
            val content = str(args, "content") ?: ""
            val file = File(path)
            val ok = if (file.parentFile?.canWrite() != false) {
                runCatching { file.writeBytes(decodeContent(content)); true }.getOrDefault(false)
            } else false
            if (!ok) {
                val bytes = decodeContent(content)
                val temp = File.createTempFile("mcp", ".bin")
                temp.writeBytes(bytes)
                val moved = Shell.sh("mkdir -p ${Shell.q(file.parent.orEmpty())} && cp ${Shell.q(temp.absolutePath)} ${Shell.q(path)} && chmod 644 ${Shell.q(path)}")
                temp.delete()
                if (!moved.ok) error("写入失败：${(moved.out + moved.err).trim()}")
            }
            "已写入 $path · ${Fmt.size(file.length().takeIf { it > 0 } ?: content.length.toLong())}"
        },
        Tool(
            "file_info",
            "文件详情：大小、时间、权限、是否可读",
            """{"path":{"type":"string"}}""",
            listOf("path")
        ) { _, args ->
            val path = str(args, "path") ?: error("缺少 path")
            val f = File(path)
            val mode = Shell.modeOf(path)
            buildString {
                appendLine("path: ${f.absolutePath}")
                appendLine("exists: ${f.exists()}")
                appendLine("type: ${if (f.isDirectory) "目录" else "文件"}")
                if (f.exists()) {
                    appendLine("size: ${Fmt.size(if (f.isDirectory) FileRepo.dirSize(f) else f.length())}")
                    appendLine("mtime: ${time(f.lastModified())}")
                }
                appendLine("readable: ${f.canRead()}  writable: ${f.canWrite()}")
                if (mode != null) append("mode: $mode")
            }
        },
        Tool(
            "search",
            "按名称递归搜索，可选内容包含",
            """{"root":{"type":"string"},"query":{"type":"string"},"contains":{"type":"string"},"limit":{"type":"integer","description":"默认 100"}}""",
            listOf("root", "query")
        ) { _, args ->
            val root = File(str(args, "root") ?: "/")
            val q = (str(args, "query") ?: "").lowercase()
            val contains = str(args, "contains")
            val limit = (num(args, "limit") ?: 100).coerceIn(1, 500)
            if (!root.exists()) error("路径不存在")
            val hits = ArrayList<String>()
            val stack = ArrayDeque<File>().apply { add(root) }
            var scanned = 0
            while (stack.isNotEmpty() && hits.size < limit && scanned < 200_000) {
                val dir = stack.removeLast()
                val list = dir.listFiles() ?: continue
                for (f in list) {
                    scanned++
                    if (f.name.lowercase().contains(q)) hits.add(f.absolutePath)
                    if (f.isDirectory && !f.name.startsWith(".")) stack.add(f)
                    if (contains != null && f.isFile && f.length() < 2 * 1024 * 1024) {
                        val text = runCatching { f.readText() }.getOrDefault("")
                        if (text.contains(contains, true)) hits.add(f.absolutePath + "  (内容命中)")
                    }
                    if (hits.size >= limit) break
                }
            }
            if (hits.isEmpty()) "没有匹配项（扫描 $scanned 项）" else hits.distinct().joinToString("\n") + "\n\n共 ${hits.distinct().size} 项"
        },
        Tool(
            "archive_list",
            "列出压缩包内容（zip / 7z / rar / tar 族）",
            """{"path":{"type":"string"}}""",
            listOf("path")
        ) { _, args ->
            val path = str(args, "path") ?: error("缺少 path")
            val items = FileRepo.archiveEntries(File(path))
            items.take(500).joinToString("\n") { "${if (it.isDir) "d" else "-"} ${"%10d".format(Locale.US, it.size)}  ${it.name}" }
        },
        Tool(
            "shell",
            "执行 shell 命令（应用 uid 或 Root）",
            """{"command":{"type":"string"},"as_root":{"type":"boolean"}}""",
            listOf("command")
        ) { _, args ->
            val cmd = str(args, "command") ?: error("缺少 command")
            val root = (args["as_root"] as? Boolean) == true
            val result = Shell.runScript(cmd, asRoot = root && Shell.rootAvailable, timeoutMs = 30_000)
            buildString {
                val text = (result.out + result.err).trim()
                if (text.isNotEmpty()) appendLine(text)
                append("exit ${result.code}")
            }
        },
        Tool(
            "copy_path",
            "复制文件或目录",
            """{"from":{"type":"string"},"to":{"type":"string"},"move":{"type":"boolean"}}""",
            listOf("from", "to")
        ) { _, args ->
            val from = str(args, "from") ?: error("缺少 from")
            val to = str(args, "to") ?: error("缺少 to")
            val move = (args["move"] as? Boolean) == true
            if (!File(from).exists() && !Shell.sh("test -e ${Shell.q(from)}").ok) error("源不存在：$from")
            val ok = if (move) Shell.move(from, to) else Shell.copy(from, to)
            if (!ok) error("操作失败，检查权限") else "完成 → $to"
        },
        Tool(
            "read_package",
            "读取 APK 基本信息：包名、版本、SDK、权限数、组件数",
            """{"path":{"type":"string"}}""",
            listOf("path")
        ) { ctx, args ->
            val path = str(args, "path") ?: error("缺少 path")
            val info = ctx.packageManager.getPackageArchiveInfo(path, 0) ?: error("不是可解析的 APK")
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            buildString {
                appendLine("package: ${info.packageName}")
                appendLine("version: ${info.versionName} ($code)")
                appendLine("sdk: ${info.applicationInfo?.minSdkVersion} → ${info.applicationInfo?.targetSdkVersion}")
                appendLine("permissions: ${info.requestedPermissions?.size ?: 0}")
                append("activities: ${info.activities?.size ?: 0}  services: ${info.services?.size ?: 0}")
            }
        }
    )

    /** 给测试用的工具清单（名称 + schema） */
    internal fun toolSchemas(): List<Pair<String, String>> = TOOLS.map { it.name to it.schema }

    // ---------------- 小工具 ----------------

    private fun str(args: Map<*, *>, key: String): String? = (args[key] as? String)?.takeIf { it.isNotBlank() }

    private fun num(args: Map<*, *>, key: String): Int? = when (val v = args[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

    private fun decodeContent(content: String): ByteArray =
        if (content.startsWith("base64:")) runCatching {
            Base64.getDecoder().decode(content.removePrefix("base64:").trim())
        }.getOrDefault(ByteArray(0)) else content.toByteArray(Charsets.UTF_8)

    private fun time(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    internal fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
        is Boolean, is Number -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { "${json(it.key)}:${json(it.value)}" }
        is List<*> -> v.joinToString(",", "[", "]") { json(it) }
        else -> json(v.toString())
    }
}
