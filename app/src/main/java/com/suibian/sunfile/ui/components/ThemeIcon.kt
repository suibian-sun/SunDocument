package com.suibian.sunfile.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.suibian.sunfile.data.IconTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * material-icon-theme 的 SVG 渲染器。
 *
 * 列表里同一张图标会被反复请求，所以按「图标名 + 像素尺寸」缓存位图；
 * 解码放到 IO 线程，首帧先画占位，避免滚动时掉帧。
 */
private object IconCache {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(name: String, px: Int): Bitmap? = cache.get("$name@$px")

    fun load(context: Context, name: String, px: Int): Bitmap? {
        cache.get("$name@$px")?.let { return it }
        val bmp = runCatching {
            context.assets.open("icons/$name.svg").use { input ->
                val svg = SVG.getFromInputStream(input)
                val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                svg.setDocumentWidth(px.toFloat())
                svg.setDocumentHeight(px.toFloat())
                svg.renderToCanvas(Canvas(bitmap))
                bitmap
            }
        }.getOrNull() ?: return null
        cache.put("$name@$px", bmp)
        return bmp
    }
}

private val themeReady = mutableIntStateOf(0)

/** 目录 / 文件统一图标入口，图片与视频缩略图失败时也回落到这里 */
@Composable
fun MaterialFileIcon(
    name: String,
    isDir: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val px = (size.value * density).toInt().coerceIn(12, 512)
    val version = themeReady.intValue
    val iconName = remember(name, isDir, expanded, version) {
        IconTheme.iconForName(name, isDir, expanded)
    }
    val bitmap by produceState(
        initialValue = IconCache.peek(iconName, px),
        iconName, px
    ) {
        if (!IconTheme.isLoaded()) {
            withContext(Dispatchers.IO) { IconTheme.ensureLoaded(context) }
            themeReady.intValue++
        }
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconCache.load(context, iconName, px) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )
    } else {
        // 占位：保持尺寸稳定，不闪白块
        Box(modifier.size(size).clip(RoundedCornerShape(size * 0.2f)))
    }
}

/** 直接给一个图标名（用于抽屉、设置页等非文件场景） */
@Composable
fun MaterialIconByName(icon: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val px = (size.value * density).toInt().coerceIn(12, 512)
    val version = themeReady.intValue
    val bitmap by produceState(initialValue = IconCache.peek(icon, px), icon, px, version) {
        if (!IconTheme.isLoaded()) {
            withContext(Dispatchers.IO) { IconTheme.ensureLoaded(context) }
            themeReady.intValue++
        }
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconCache.load(context, icon, px) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )
    }
}
