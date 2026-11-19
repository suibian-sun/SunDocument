package com.suibian.sunfile.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.suibian.sunfile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val publishedAt: String,
    val htmlUrl: String
)

object GithubApi {
    const val OWNER = "suibian-sun"
    const val REPO = "SunDocument"
    const val API_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"

    /** 请求最新 Release，无 Release 时返回 null */
    suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SunFile/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return@withContext null
            if (code !in 200..299) throw IOException("GitHub 接口返回 $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name")
            val assets = obj.optJSONArray("assets")
            var apkUrl = ""
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name")
                    if (n.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkSize = a.optLong("size")
                        if (n.contains("release", ignoreCase = true)) break
                    }
                }
            }
            if (apkUrl.isEmpty()) return@withContext null
            ReleaseInfo(
                tag = tag,
                version = tag.trimStart('v', 'V'),
                name = obj.optString("name").ifEmpty { tag },
                notes = obj.optString("body").ifEmpty { "本次更新未提供说明。" },
                apkUrl = apkUrl,
                size = apkSize,
                publishedAt = obj.optString("published_at"),
                htmlUrl = obj.optString("html_url").ifEmpty { RELEASES_PAGE }
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 比较版本号，remote 比 local 新时返回 true
     */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V').split('.', '-', '+')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * GitHub 下载加速器：内置多个公共镜像，按顺序探测，
 * 任一线路可用即开始下载；失败自动切换到下一条线路。
 */
object Accelerator {

    data class Route(val label: String, val url: String)

    private val mirrors = listOf(
        "ghfast.top" to "https://ghfast.top/",
        "gh-proxy.com" to "https://gh-proxy.com/",
        "ghproxy.net" to "https://ghproxy.net/",
        "gh.llkk.cc" to "https://gh.llkk.cc/"
    )

    fun routes(originalUrl: String, useAccelerator: Boolean): List<Route> {
        val list = ArrayList<Route>()
        if (useAccelerator) {
            mirrors.forEachIndexed { i, (name, prefix) ->
                list.add(Route("加速线路 ${i + 1} · $name", prefix + originalUrl))
            }
        }
        list.add(Route(if (useAccelerator) "GitHub 直连（备用）" else "GitHub 直连", originalUrl))
        return list
    }

    fun hostOf(url: String): String = runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)
}

object Installer {

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls()
        else true

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    /** 调起系统安装程序；返回 false 表示需要先授予"安装未知来源应用"权限 */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0) return false
        if (!canInstall(context)) {
            openInstallPermission(context)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

/** 下载器：多线路回退 + 进度/速度回调 */
class ApkDownloader(private val context: Context) {

    fun targetFile(version: String): File {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        // 清理旧安装包
        runCatching { dir.listFiles()?.forEach { if (!it.name.contains(version)) it.delete() } }
        return File(dir, "SunFile-$version.apk")
    }

    suspend fun download(
        info: ReleaseInfo,
        useAccelerator: Boolean,
        onStart: (routeIndex: Int, routeCount: Int, label: String) -> Unit,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val routes = Accelerator.routes(info.apkUrl, useAccelerator)
        val target = targetFile(info.version)
        var lastError: Exception? = null
        for ((index, route) in routes.withIndex()) {
            onStart(index, routes.size, route.label)
            val tmp = File(target.parentFile, "${target.name}.part")
            runCatching {
                if (tmp.exists()) tmp.delete()
                val conn = (URL(route.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 25_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 SunFile-Updater")
                    setRequestProperty("Accept", "*/*")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw IOException("线路返回 HTTP $code")
                    var total = conn.contentLengthLong
                    if (total <= 0) total = info.size
                    var downloaded = 0L
                    var windowStart = System.currentTimeMillis()
                    var windowBytes = 0L
                    var speed = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                downloaded += read
                                windowBytes += read
                                val now = System.currentTimeMillis()
                                if (now - windowStart >= 400) {
                                    speed = windowBytes * 1000 / (now - windowStart)
                                    windowStart = now
                                    windowBytes = 0
                                    onProgress(downloaded, total, speed)
                                }
                            }
                            output.flush()
                        }
                    }
                    onProgress(downloaded, total, speed)
                    if (downloaded < 1024 || (total > 0 && downloaded < total * 0.98)) {
                        throw IOException("下载不完整（${downloaded}/${total} 字节）")
                    }
                } finally {
                    conn.disconnect()
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw IOException("无法保存安装包")
                target
            }.onSuccess {
                return@withContext it
            }.onFailure {
                lastError = it as? Exception ?: IOException(it.message)
            }
            runCatching { if (tmp.exists()) tmp.delete() }
        }
        throw lastError ?: IOException("所有下载线路均不可用")
    }
}
