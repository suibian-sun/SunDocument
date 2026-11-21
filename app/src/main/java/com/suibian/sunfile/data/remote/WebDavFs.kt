package com.suibian.sunfile.data.remote

import android.net.Uri
import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * WebDAV：PROPFIND 列目录，GET/PUT 读写，MKCOL 建目录，MOVE 改名。
 *
 * `webdav://` 与 `webdavs://` 分别映射到 http / https；
 * 如果直接填 `http(s)://`，也按 WebDAV 处理。
 */
class WebDavFs(private val t: RemoteTarget, timeoutSec: Int) : RemoteFs {

    override val label: String = "WebDAV · ${t.displayHost}"

    private val timeoutMs = timeoutSec.coerceIn(3, 120) * 1000
    private val secure = t.scheme == "https" || t.scheme == "webdavs"
    private val base: String
    private val authHeader: String?

    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    init {
        val scheme = if (secure) "https" else "http"
        val portPart = if (t.port > 0 && !((secure && t.port == 443) || (!secure && t.port == 80))) ":${t.port}" else ""
        val p = t.path.trimEnd('/')
        base = "$scheme://${t.host}$portPart$p"
        authHeader = if (t.user.isNotBlank()) {
            "Basic " + Base64.encodeToString("${t.user}:${t.password}".toByteArray(), Base64.NO_WRAP)
        } else null
        // 先探一次，把「地址写错」和「没有权限」区分开
        try {
            propfind("/", 0)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException("WebDAV 连接失败：${e.message}", e)
        }
    }

    private fun urlFor(path: String): String {
        val p = normalizePath(path)
        if (p == "/") return "$base/"
        val enc = p.trim('/').split('/').joinToString("/") { Uri.encode(it) }
        return "$base/$enc"
    }

    private fun open(method: String, path: String): HttpURLConnection {
        val conn = URL(urlFor(path)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        authHeader?.let { conn.setRequestProperty("Authorization", it) }
        conn.setRequestProperty("User-Agent", "SunDocument/1.9")
        return conn
    }

    private fun propfind(path: String, depth: Int): List<RemoteEntry> {
        val conn = open("PROPFIND", path)
        conn.setRequestProperty("Depth", depth.toString())
        conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop>
            <d:displayname/><d:getcontentlength/><d:getlastmodified/><d:resourcetype/>
            </d:prop></d:propfind>""".trimIndent()
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val msg = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
            conn.disconnect()
            throw RemoteException("WebDAV 返回 $code${if (msg.isNullOrBlank()) "" else "：${msg.take(120)}"}")
        }
        val result = ArrayList<RemoteEntry>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(conn.inputStream, "UTF-8")
            var href: String? = null
            var isDir = false
            var size = 0L
            var modified = 0L
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "response" -> { href = null; isDir = false; size = 0L; modified = 0L }
                        "href" -> href = parser.nextText()
                        "collection" -> isDir = true
                        "getcontentlength" -> size =
                            runCatching { parser.nextText().trim().toLongOrNull() }.getOrNull() ?: 0L
                        "getlastmodified" ->
                            modified = runCatching { parseDate(parser.nextText()) }.getOrDefault(0L)
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name.equals("response", true)) {
                    val h = href
                    if (!h.isNullOrBlank()) {
                        val rawPath = hrefToPath(h)
                        val selfPath = normalizePath(path)
                        if (normalizePath(rawPath) != selfPath) {
                            val name = Uri.decode(rawPath.trimEnd('/').substringAfterLast('/'))
                            if (name.isNotBlank()) {
                                result.add(
                                    RemoteEntry(
                                        name = name,
                                        path = joinPath(selfPath, name),
                                        isDir = isDir,
                                        size = if (isDir) 0 else size,
                                        modified = modified
                                    )
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } finally {
            runCatching { conn.disconnect() }
        }
        result.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        return result
    }

    private fun hrefToPath(href: String): String {
        val p = if (href.startsWith("http://") || href.startsWith("https://")) {
            runCatching { Uri.parse(href).path }.getOrNull() ?: href
        } else href
        // 去掉 base 的路径前缀，只保留相对部分
        val basePath = runCatching { Uri.parse(base).path }.getOrNull() ?: ""
        val rel = if (basePath.isNotBlank() && p.startsWith(basePath)) p.removePrefix(basePath) else p
        return if (rel.isBlank()) "/" else rel
    }

    private fun parseDate(s: String): Long =
        runCatching { dateFmt.parse(s.trim())?.time ?: 0L }.getOrDefault(0L)

    override fun list(path: String): List<RemoteEntry> = propfind(path, 1)

    override fun stat(path: String): RemoteEntry? {
        if (normalizePath(path) == "/") return RemoteEntry("/", "/", true)
        return runCatching { propfind(path, 0).firstOrNull() }.getOrNull()
    }

    override fun openInput(path: String): InputStream {
        val conn = open("GET", path)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw RemoteException("下载失败：HTTP $code")
        }
        return conn.inputStream
    }

    override fun openOutput(path: String, append: Boolean): OutputStream {
        val conn = open("PUT", path)
        conn.doOutput = true
        conn.setChunkedStreamingMode(64 * 1024)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        val raw = conn.outputStream
        return object : FilterOutputStream(raw) {
            override fun close() {
                runCatching { flush() }
                runCatching { super.close() }
                val code = runCatching { conn.responseCode }.getOrDefault(-1)
                runCatching { conn.disconnect() }
                if (code !in 200..299) throw RemoteException("上传失败：HTTP $code")
            }
        }
    }

    override fun mkdir(path: String) {
        val p = normalizePath(path)
        if (p == "/") return
        var cur = ""
        for (seg in p.trim('/').split('/')) {
            cur += "/$seg"
            val conn = open("MKCOL", cur)
            conn.doOutput = true
            conn.outputStream.use { }
            val code = conn.responseCode
            conn.disconnect()
            // 405 = 已存在，忽略
            if (code !in 200..299 && code != 405) throw RemoteException("建立目录失败：HTTP $code")
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val p = normalizePath(path)
        if (p == "/") throw RemoteException("不能删除根目录")
        val conn = open("DELETE", p)
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299 && code != 404) throw RemoteException("删除失败：HTTP $code")
    }

    override fun rename(from: String, to: String) {
        val conn = open("MOVE", from)
        conn.setRequestProperty("Destination", urlFor(to))
        conn.setRequestProperty("Overwrite", "T")
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw RemoteException("重命名失败：HTTP $code")
    }

    override fun close() = Unit
}
