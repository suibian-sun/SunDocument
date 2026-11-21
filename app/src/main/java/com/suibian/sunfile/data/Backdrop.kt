package com.suibian.sunfile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * 自定义壁纸。
 *
 * 三种来源走同一条通道：
 * - 本地图片（`content://`，系统相册选出来的）
 * - 图片直链（`https://…/a.jpg`）
 * - 图片 API（`https://…/random.php` 每次返回不同图，靠 [load] 的 bust 参数绕过缓存）
 *
 * 不引入第三方图片库：壁纸只需要「一张大图」，用 BitmapFactory 按屏幕尺寸采样
 * 就够了，反而省掉一整套依赖与初始化成本。下载结果落到 cacheDir，重启后直接命中，
 * 不会每次启动都重新拉一遍网络图片。
 */
object Backdrop {

    data class State(
        val bitmap: Bitmap? = null,
        val loading: Boolean = false,
        val error: String? = null,
        /** 当前这张图对应的来源键，用于避免重复加载同一张 */
        val key: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val mem = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sun-backdrop").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    /** 同一时刻只允许一个加载任务，切图时旧任务的结果直接丢弃 */
    @Volatile
    private var token = 0

    private const val MAX_EDGE = 2048

    private fun dir(context: Context): File =
        File(context.cacheDir, "backdrop").apply { mkdirs() }

    private fun hash(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(context: Context, key: String) = File(dir(context), "$key.img")

    /**
     * 加载壁纸。[bust] 变化会强制重新请求（图片 API 换图就靠它）。
     * 传空 source 等价于清空。
     */
    fun load(context: Context, source: String, bust: Long) {
        val src = source.trim()
        if (src.isEmpty()) {
            clear()
            return
        }
        val key = hash("$src|$bust")
        if (_state.value.key == key && (_state.value.bitmap != null || _state.value.loading)) return

        val my = ++token
        mem.get(key)?.let { bmp ->
            _state.value = State(bmp, false, null, key)
            return
        }

        _state.value = State(_state.value.bitmap, true, null, key)
        val app = context.applicationContext
        pool.execute {
            var out: Bitmap? = null
            var err: String? = null
            try {
                val cached = cacheFile(app, key)
                if (cached.exists() && cached.length() > 1024) {
                    out = decodeFile(cached)
                    if (out == null) cached.delete()
                }
                if (out == null) {
                    val bytes = fetch(app, src)
                    if (bytes != null) {
                        out = decode(bytes)
                        if (out != null) runCatching {
                            FileOutputStream(cached).use { it.write(bytes) }
                            prune(app, key)
                        }
                    } else {
                        err = "无法获取图片，检查链接或网络"
                    }
                }
            } catch (e: OutOfMemoryError) {
                err = "图片过大，已放弃解码"
            } catch (e: Exception) {
                err = e.message?.let { "加载失败：$it" } ?: "加载失败"
            }
            if (my != token) return@execute
            if (out != null) mem.put(key, out)
            _state.value = State(out ?: _state.value.bitmap, false, err, if (out != null) key else "")
        }
    }

    fun clear() {
        token++
        _state.value = State()
    }

    /** 只保留最近 4 张，API 换图不会把缓存目录撑爆 */
    private fun prune(context: Context, keep: String) {
        val files = dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(4).forEach { f ->
            if (f.nameWithoutExtension != keep) f.delete()
        }
        // keep 那张可能落在第 5 位之后，单独保一次
        val k = cacheFile(context, keep)
        if (k.exists()) k.setLastModified(System.currentTimeMillis())
    }

    private fun isRemote(src: String): Boolean =
        src.startsWith("http://", true) || src.startsWith("https://", true)

    private fun fetch(context: Context, src: String): ByteArray? {
        if (!isRemote(src)) {
            val uri = Uri.parse(src)
            val stream: InputStream? = when {
                src.startsWith("file://") -> runCatching { File(uri.path!!).inputStream() }.getOrNull()
                else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            }
            return stream?.use { it.readBytes() }
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(src).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                // 部分图床会拦没有 UA 的请求
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SunDocument/2.0")
                setRequestProperty("Accept", "image/*,*/*")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
