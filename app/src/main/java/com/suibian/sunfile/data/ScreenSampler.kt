package com.suibian.sunfile.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface SampleState {
    data object Idle : SampleState
    data object Capturing : SampleState
    data class Ready(val bitmap: Bitmap) : SampleState
    data class Failed(val message: String) : SampleState
}

/**
 * 一次性屏幕取样。
 *
 * Android 14 起，调用 MediaProjection 前必须先起一个 mediaProjection 类型的前台服务，
 * 所以这里只负责「把授权结果变成一张 Bitmap」，前台服务由 ProjectionService 承担。
 * 取到一帧立刻释放 VirtualDisplay 与 projection，不常驻、不录制。
 */
object ScreenSampler {

    private val _state = MutableStateFlow<SampleState>(SampleState.Idle)
    val state: StateFlow<SampleState> = _state

    fun reset() {
        _state.value = SampleState.Idle
    }

    /** 由前台服务调用，在主线程执行 */
    fun capture(context: Context, resultCode: Int, data: Intent) {
        _state.value = SampleState.Capturing
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (projection == null) {
            _state.value = SampleState.Failed("无法获取屏幕投射权限")
            return
        }

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        var reader: ImageReader? = null
        var display: VirtualDisplay? = null

        fun cleanup() {
            runCatching { display?.release() }
            runCatching { reader?.close() }
            runCatching { projection.stop() }
        }

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanup()
            }
        }, Handler(Looper.getMainLooper()))

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = projection.createVirtualDisplay(
            "sun-sample",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            Handler(Looper.getMainLooper())
        )

        // 首帧合成需要一点时间，稍等再取，避免拿到全黑
        Handler(Looper.getMainLooper()).postDelayed({
            val image = runCatching { reader.acquireLatestImage() }.getOrNull()
            if (image == null) {
                _state.value = SampleState.Failed("没有取到画面，请重试")
                cleanup()
                return@postDelayed
            }
            runCatching {
                val plane = image.planes[0]
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val buffer = plane.buffer
                val paddedWidth = rowStride / pixelStride
                val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buffer)
                val cropped = if (paddedWidth > width) {
                    Bitmap.createBitmap(full, 0, 0, width, height).also { full.recycle() }
                } else full
                _state.value = SampleState.Ready(cropped)
            }.onFailure {
                _state.value = SampleState.Failed(it.message ?: "解码失败")
            }
            runCatching { image.close() }
            cleanup()
        }, 450)
    }

    fun startProjectionService(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, com.suibian.sunfile.service.ProjectionService::class.java).apply {
            putExtra("code", resultCode)
            putExtra("data", data)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
