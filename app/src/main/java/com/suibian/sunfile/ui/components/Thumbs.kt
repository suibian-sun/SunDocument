package com.suibian.sunfile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import java.io.File
import java.util.concurrent.Executors

/**
 * 缩略图缓存。
 *
 * 列表里只给图片和视频做首帧，尺寸压到 128px 以内，两线程串行取帧：
 * 既能一眼分辨内容，又不会在滚动时把主线程拖住。
 */
object Thumbs {

    /** 约 24MB 上限，按 Bitmap 实际字节计 */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "sun-thumb").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    private const val TARGET = 160

    private fun key(file: File): String =
        "${file.absolutePath}|${file.lastModified()}|${file.length()}"

    fun cached(file: File): Bitmap? = cache.get(key(file))

    /** 同步取（会在调用线程解码，仅用于已经在后台的场景） */
    fun load(file: File, isVideo: Boolean): Bitmap? {
        val k = key(file)
        cache.get(k)?.let { return it }
        val bmp = runCatching {
            if (isVideo) videoFrame(file) else imageThumb(file)
        }.getOrNull() ?: return null
        cache.put(k, bmp)
        return bmp
    }

    /** 异步取：命中缓存立即返回，否则后台解码一次 */
    fun request(file: File, isVideo: Boolean, onReady: (Bitmap) -> Unit) {
        cached(file)?.let { onReady(it); return }
        pool.execute {
            val bmp = load(file, isVideo) ?: return@execute
            onReady(bmp)
        }
    }

    fun clear() = cache.evictAll()

    private fun imageThumb(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > TARGET * 2 || bounds.outHeight / sample > TARGET * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
    }

    private fun videoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            // 取第 0.5 秒，避开部分视频开头的纯黑帧
            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val max = maxOf(frame.width, frame.height)
            if (max <= TARGET) frame else {
                val scale = TARGET.toFloat() / max
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== frame) frame.recycle() }
            }
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** 组合函数：路径变化时重新取缩略图；离开组合后结果由 LruCache 持有 */
@Composable
fun rememberThumb(file: File, isVideo: Boolean): Bitmap? {
    // 走固定 2 线程的池：一屏几十个视频缩略图不会同时开几十个 MediaMetadataRetriever
    val state = androidx.compose.runtime.remember(file, isVideo) {
        androidx.compose.runtime.mutableStateOf(Thumbs.cached(file))
    }
    androidx.compose.runtime.LaunchedEffect(file, isVideo) {
        if (state.value == null) {
            Thumbs.request(file, isVideo) { bmp ->
                state.value = bmp
            }
        }
    }
    return state.value
}
