package com.suibian.sunfile.data.remote

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * S3 兼容对象存储（AWS S3 / MinIO / 阿里云 OSS 的 S3 模式 / 七牛等）。
 *
 * 链接写法：`s3://ACCESS_KEY:SECRET_KEY@host/bucket/可选前缀?region=us-east-1`
 * 只填 host 不填 bucket 时，根路径会列出该账号下的所有桶。
 *
 * 签名用 AWS SigV4，上传走 UNSIGNED-PAYLOAD，避免为了算哈希把大文件读两遍。
 */
class S3Fs(private val t: RemoteTarget, timeoutSec: Int) : RemoteFs {

    override val label: String = "S3 · ${t.displayHost}"

    private val timeoutMs = timeoutSec.coerceIn(3, 120) * 1000
    private val https = t.scheme != "s3-http" && t.query["http"] != "1"
    private val scheme = if (https) "https" else "http"
    private val portPart = if (t.port > 0 && !((https && t.port == 443) || (!https && t.port == 80))) ":${t.port}" else ""
    private val hostHeader = "${t.host}$portPart"
    private val region = t.query["region"] ?: t.query["Region"] ?: "us-east-1"
    private val accessKey = t.user
    private val secretKey = t.password

    private val bucket: String
    private val rootPrefix: String

    init {
        val raw = t.path.trim('/')
        bucket = raw.substringBefore('/')
        val rest = if (raw.contains('/')) raw.substringAfter('/').trim('/') else ""
        rootPrefix = if (rest.isEmpty()) "" else "$rest/"
    }

    // ---------------- 请求 ----------------

    private fun objectKey(path: String): String {
        val p = normalizePath(path).trim('/')
        if (p.isEmpty()) return rootPrefix.trimEnd('/')
        return rootPrefix + p
    }

    private fun buildUrl(key: String, query: Map<String, String> = emptyMap(), bucketLevel: Boolean = false): URL {
        val b = if (bucketLevel) "" else "/$bucket"
        val path = if (key.isEmpty()) b else "$b/${encodeKey(key)}"
        val q = if (query.isEmpty()) "" else
            "?" + query.toSortedMap().entries.joinToString("&") {
                "${Uri.encode(it.key)}=${Uri.encode(it.value)}"
            }
        return URL("$scheme://$hostHeader$path$q")
    }

    private fun encodeKey(key: String): String =
        key.split('/').joinToString("/") { Uri.encode(it) }

    private fun sign(
        conn: HttpURLConnection,
        method: String,
        canonicalUri: String,
        query: Map<String, String>,
        payloadHash: String
    ) {
        if (accessKey.isBlank()) return
        val now = Date()
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val amzDate = fmt.format(now)
        val dateStamp = day.format(now)

        val canonicalQuery = query.toSortedMap().entries.joinToString("&") {
            "${awsEncode(it.key)}=${awsEncode(it.value)}"
        }
        val canonicalHeaders = "host:$hostHeader\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = listOf(
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        val scope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest.toByteArray())
        ).joinToString("\n")

        var key = ("AWS4$secretKey").toByteArray()
        for (part in listOf(dateStamp, region, "s3", "aws4_request")) {
            key = hmac(key, part.toByteArray())
        }
        val signature = hmacHex(key, stringToSign.toByteArray())

        conn.setRequestProperty("x-amz-date", amzDate)
        conn.setRequestProperty("x-amz-content-sha256", payloadHash)
        conn.setRequestProperty(
            "Authorization",
            "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        )
    }

    private fun open(
        method: String,
        key: String,
        query: Map<String, String> = emptyMap(),
        bucketLevel: Boolean = false,
        payloadHash: String = EMPTY_SHA256
    ): HttpURLConnection {
        val url = buildUrl(key, query, bucketLevel)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        val canonicalUri = if (url.path.isEmpty()) "/" else url.path
        sign(conn, method, canonicalUri, query, payloadHash)
        return conn
    }

    private fun ensureOk(conn: HttpURLConnection, action: String) {
        val code = conn.responseCode
        if (code in 200..299) return
        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
        val msg = Regex("<Message>(.*?)</Message>").find(body)?.groupValues?.get(1) ?: body.take(160)
        conn.disconnect()
        throw RemoteException("$action 失败：HTTP $code${if (msg.isBlank()) "" else " · $msg"}")
    }

    // ---------------- 列表 ----------------

    override fun list(path: String): List<RemoteEntry> {
        val p = normalizePath(path)
        // 根且没指定桶：列出所有桶
        if (bucket.isBlank() && p == "/") return listBuckets()

        val prefix = if (p == "/") rootPrefix else objectKey(p).let { if (it.isEmpty()) "" else "$it/" }
        val query = mapOf(
            "list-type" to "2",
            "delimiter" to "/",
            "prefix" to prefix,
            "max-keys" to "1000"
        )
        val conn = open("GET", "", query, bucketLevel = bucket.isBlank())
        ensureOk(conn, "列出对象")
        val out = ArrayList<RemoteEntry>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(conn.inputStream, "UTF-8")
            var inContents = false
            var inCommon = false
            var key = ""
            var size = 0L
            var modified = 0L
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Contents" -> { inContents = true; key = ""; size = 0L; modified = 0L }
                        "CommonPrefixes" -> { inCommon = true; key = "" }
                        "Key" -> if (inContents) key = parser.nextText()
                        "Prefix" -> if (inCommon) key = parser.nextText()
                        "Size" -> if (inContents) size = parser.nextText().trim().toLongOrNull() ?: 0L
                        "LastModified" -> if (inContents) modified = parseIso(parser.nextText())
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "Contents" -> {
                            inContents = false
                            val rel = stripPrefix(key)
                            if (rel.isNotEmpty() && !rel.endsWith("/")) {
                                out.add(
                                    RemoteEntry(
                                        name = rel.substringAfterLast('/'),
                                        path = joinPath(p, rel),
                                        isDir = false,
                                        size = size,
                                        modified = modified
                                    )
                                )
                            }
                        }
                        "CommonPrefixes" -> {
                            inCommon = false
                            val rel = stripPrefix(key).trimEnd('/')
                            if (rel.isNotEmpty()) {
                                out.add(
                                    RemoteEntry(
                                        name = rel.substringAfterLast('/'),
                                        path = joinPath(p, rel),
                                        isDir = true
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
        out.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        return out
    }

    private fun listBuckets(): List<RemoteEntry> {
        val conn = open("GET", "", emptyMap(), bucketLevel = true)
        ensureOk(conn, "列出存储桶")
        val out = ArrayList<RemoteEntry>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(conn.inputStream, "UTF-8")
            var inBucket = false
            var name = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Bucket" -> { inBucket = true; name = "" }
                        "Name" -> if (inBucket) name = parser.nextText()
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "Bucket") {
                    inBucket = false
                    if (name.isNotBlank()) out.add(RemoteEntry(name, "/$name", true))
                }
                event = parser.next()
            }
        } finally {
            runCatching { conn.disconnect() }
        }
        out.sortBy { it.name.lowercase() }
        return out
    }

    private fun stripPrefix(key: String): String {
        val pre = rootPrefix
        return if (pre.isNotEmpty() && key.startsWith(pre)) key.removePrefix(pre) else key
    }

    private fun parseIso(s: String): Long = runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fmt.parse(s.trim())?.time ?: 0L
    }.getOrDefault(0L)

    override fun stat(path: String): RemoteEntry? {
        val p = normalizePath(path)
        if (p == "/") return RemoteEntry("/", "/", true)
        val key = objectKey(p)
        val conn = open("HEAD", key)
        val code = conn.responseCode
        val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
        val lm = conn.getHeaderField("Last-Modified")
        conn.disconnect()
        if (code !in 200..299) return null
        return RemoteEntry(baseName(p), p, key.endsWith("/"), len, parseRfc1123(lm))
    }

    private fun parseRfc1123(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(s)?.time ?: 0L
        }.getOrDefault(0L)
    }

    override fun openInput(path: String): InputStream {
        val conn = open("GET", objectKey(path))
        ensureOk(conn, "下载")
        return conn.inputStream
    }

    override fun openOutput(path: String, append: Boolean): OutputStream {
        val key = objectKey(path)
        val conn = open("PUT", key, payloadHash = "UNSIGNED-PAYLOAD")
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
        val key = objectKey(p).trimEnd('/') + "/"
        val conn = open("PUT", key, payloadHash = EMPTY_SHA256)
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(0)
        conn.outputStream.use { }
        ensureOk(conn, "建立目录")
        conn.disconnect()
    }

    override fun delete(path: String, isDir: Boolean) {
        val p = normalizePath(path)
        if (isDir) {
            // 递归删除前缀下的所有对象
            list(p).forEach { delete(it.path, it.isDir) }
            runCatching { deleteObject(objectKey(p).trimEnd('/') + "/") }
        } else {
            deleteObject(objectKey(p))
        }
    }

    private fun deleteObject(key: String) {
        val conn = open("DELETE", key, payloadHash = EMPTY_SHA256)
        ensureOk(conn, "删除")
        conn.disconnect()
    }

    override fun rename(from: String, to: String) {
        val src = objectKey(from)
        val dst = objectKey(to)
        val conn = open("PUT", dst, payloadHash = EMPTY_SHA256)
        conn.setRequestProperty("x-amz-copy-source", "/$bucket/${encodeKey(src)}")
        val code = conn.responseCode
        val body = runCatching { conn.inputStream?.bufferedReader()?.readText() }.getOrNull()
        conn.disconnect()
        if (code !in 200..299) {
            throw RemoteException("复制失败：HTTP $code ${body?.take(120) ?: ""}")
        }
        deleteObject(src)
    }

    override fun close() = Unit

    companion object {
        val EMPTY_SHA256 = sha256Hex(ByteArray(0))

        fun sha256Hex(data: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(data).joinToString("") { "%02x".format(it) }
        }

        fun hmac(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        fun hmacHex(key: ByteArray, data: ByteArray): String =
            hmac(key, data).joinToString("") { "%02x".format(it) }

        /** SigV4 的编码规则：除 unreserved 外全部百分号编码，空格是 %20 */
        fun awsEncode(s: String): String {
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                    sb.append(ch)
                } else {
                    sb.append('%').append("%02X".format(c))
                }
            }
            return sb.toString()
        }
    }
}
