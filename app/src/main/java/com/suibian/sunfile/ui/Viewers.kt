package com.suibian.sunfile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.Archives
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.ui.components.FileTypeBadge
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ---------------- 通用外壳 ----------------

/** 全屏查看器外壳：淡入 + 轻微上滑，返回键关闭 */
@Composable
internal fun OverlayShell(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    background: Color = SunColors.Bg,
    /** 返回 true 表示这一层自己处理掉了返回（例如压缩包先回上级目录） */
    onBack: (() -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    BackHandler { if (onBack?.invoke() != true) onClose() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.Base, easing = Motion.EaseOut)) +
            slideInVertically(tween(Motion.Slow, easing = Motion.EaseOut)) { it / 16 },
        exit = fadeOut(tween(Motion.Fast))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(Mt.BarHeight).background(SunColors.Surface1).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(46.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "返回",
                        tint = SunColors.TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = SunColors.TextPrimary,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = SunColors.TextTertiary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions()
            }
            content()
        }
    }
}

// ---------------- 十六进制查看器 ----------------

private const val HEX_PAGE = 512
private const val HEX_PER_LINE = 8

@Composable
fun HexViewerOverlay(file: File, onClose: () -> Unit) {
    val size = remember(file) { file.length() }
    var offset by remember { mutableLongStateOf(0L) }
    var chunk by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(offset, file) {
        chunk = null
        chunk = withContext(Dispatchers.IO) {
            runCatching { FileRepo.readChunk(file, offset, HEX_PAGE) }.getOrDefault(ByteArray(0))
        }
    }

    OverlayShell(
        title = file.name,
        subtitle = "${Fmt.size(size)} · 0x${offset.toString(16).uppercase(Locale.US)}",
        onClose = onClose,
        actions = {
            TextButton(onClick = { offset = 0L }) { Text("开头", color = SunColors.TextSecondary, fontSize = 11.sp) }
            TextButton(
                onClick = { offset = ((size - HEX_PAGE).coerceAtLeast(0) / HEX_PER_LINE) * HEX_PER_LINE }
            ) { Text("末尾", color = SunColors.TextSecondary, fontSize = 11.sp) }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(34.dp).background(SunColors.Surface2).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("偏移", color = SunColors.TextTertiary, fontSize = 9.sp, modifier = Modifier.width(66.dp))
                Text("字节 (hex)", color = SunColors.TextTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("ASCII", color = SunColors.TextTertiary, fontSize = 9.sp, modifier = Modifier.width(74.dp))
            }

            val data = chunk
            when {
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                }

                data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("文件末尾", color = SunColors.TextTertiary, fontSize = 11.sp)
                }

                else -> LazyColumn(Modifier.weight(1f)) {
                    val lines = (data.size + HEX_PER_LINE - 1) / HEX_PER_LINE
                    items(lines) { li ->
                        HexRow(baseOffset = offset + li * HEX_PER_LINE, data = data, start = li * HEX_PER_LINE)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(46.dp).background(SunColors.Surface1),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "◀ 上一页",
                    color = SunColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(enabled = offset > 0) {
                        offset = (offset - HEX_PAGE).coerceAtLeast(0)
                    }
                )
                Text(
                    "${(offset / HEX_PAGE).toInt() + 1} / ${(size / HEX_PAGE) + 1}",
                    color = SunColors.TextTertiary,
                    style = NumberStyle
                )
                Text(
                    "下一页 ▶",
                    color = SunColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(enabled = offset + HEX_PAGE < size) { offset += HEX_PAGE }
                )
            }
        }
    }
}

@Composable
private fun HexRow(baseOffset: Long, data: ByteArray, start: Int) {
    val count = minOf(HEX_PER_LINE, data.size - start)
    val hex = buildString {
        for (i in 0 until count) {
            if (i == 4) append(' ')
            append(String.format(Locale.US, "%02X", data[start + i]))
            append(' ')
        }
    }
    val ascii = buildString {
        for (i in 0 until count) {
            val b = data[start + i].toInt() and 0xff
            append(if (b in 32..126) b.toChar() else '.')
        }
    }
    Row(
        Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format(Locale.US, "%08X", baseOffset),
            color = SunColors.TextTertiary,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(66.dp)
        )
        Text(
            hex,
            color = SunColors.TextPrimary,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            ascii,
            color = SunColors.Accent.copy(alpha = 0.8f),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(74.dp),
            maxLines = 1
        )
    }
}

// ---------------- 图片查看器 ----------------

@Composable
fun ImageViewerOverlay(file: File, playlist: List<File>, onClose: () -> Unit) {
    val list = remember(playlist, file) {
        if (playlist.any { it.absolutePath == file.absolutePath }) playlist else listOf(file)
    }
    var index by remember(file) {
        mutableIntStateOf(list.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0))
    }
    var direction by remember { mutableIntStateOf(0) }
    var exifFor by remember { mutableStateOf<File?>(null) }
    val current = list.getOrElse(index) { file }

    fun go(delta: Int) {
        val next = index + delta
        if (next !in list.indices) return
        direction = delta
        index = next
    }

    OverlayShell(
        title = current.name,
        subtitle = "${index + 1} / ${list.size} · ${Fmt.size(current.length())}",
        onClose = onClose,
        background = Color(0xFF050608),
        actions = {
            IconButton(onClick = { exifFor = current }, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    "详情",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
            ShareButton(current)
        }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val dir = if (direction >= 0) 1 else -1
                    (slideInHorizontally(tween(Motion.Slow, easing = Motion.EaseOut)) { w -> dir * w / 3 } +
                        fadeIn(tween(Motion.Base))) togetherWith
                        (slideOutHorizontally(tween(Motion.Slow, easing = Motion.EaseIn)) { w -> -dir * w / 3 } +
                            fadeOut(tween(Motion.Base)))
                },
                label = "image"
            ) { target ->
                ZoomableImage(target, onSwipe = { go(it) })
            }

            if (list.size > 1) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index > 0) ArrowChip(Icons.Outlined.ArrowBack) { go(-1) }
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        repeat(min(list.size, 9)) { i ->
                            val start = (index - 4).coerceIn(0, (list.size - 9).coerceAtLeast(0))
                            val active = i + start == index
                            Box(
                                Modifier
                                    .padding(horizontal = 2.5.dp)
                                    .size(if (active) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) Color.White else Color.White.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (index < list.lastIndex) ArrowChip(Icons.Outlined.ArrowForward) { go(1) }
                }
            }
        }
    }

    exifFor?.let { target -> ImageInfoDialog(target) { exifFor = null } }
}

/** 图片详情：分辨率、体积与 EXIF（拍摄时间、器材、曝光、位置） */
@Composable
private fun ImageInfoDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rows by produceState(initialValue = emptyList<Pair<String, String>>(), file) {
        value = withContext(Dispatchers.IO) { readImageInfo(context, file) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = {
            Text(
                file.name,
                color = SunColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(k, color = SunColors.TextTertiary, fontSize = 11.5.sp, modifier = Modifier.width(84.dp))
                        Text(v, color = SunColors.TextPrimary, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = SunColors.Accent, fontSize = 13.sp) }
        }
    )
}

private fun readImageInfo(context: android.content.Context, file: File): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds) }
    if (bounds.outWidth > 0) out.add("尺寸" to "${bounds.outWidth} × ${bounds.outHeight}")
    out.add("大小" to Fmt.size(file.length()))
    out.add("格式" to file.extension.uppercase().ifEmpty { "未知" })
    runCatching {
        val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
        fun text(tag: String, label: String) {
            exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { out.add(label to it) }
        }
        text(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, "拍摄时间")
        text(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "品牌")
        text(androidx.exifinterface.media.ExifInterface.TAG_MODEL, "机型")
        text(androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL, "镜头")
        text(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, "光圈")
        text(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, "快门")
        text(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS, "ISO")
        text(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, "焦距")
        text(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, "描述")
        val latLong = FloatArray(2)
        if (exif.getLatLong(latLong)) {
            out.add("位置" to String.format(java.util.Locale.US, "%.5f, %.5f", latLong[0], latLong[1]))
        }
    }
    if (out.size <= 3) out.add("EXIF" to "这张图片没有记录拍摄信息")
    return out
}

@Composable
private fun ArrowChip(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ZoomableImage(file: File, onSwipe: (Int) -> Unit) {
    val ctx = LocalContext.current
    val animated = Build.VERSION.SDK_INT >= 28 &&
        file.extension.lowercase() in setOf("gif", "webp", "apng")
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val swipeEnabled = rememberUpdatedState(scale <= 1.01f)

    LaunchedEffect(file, animated) {
        if (animated) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { decodeSampled(file, 2048) }
        if (bmp == null) failed = true else bitmap = bmp
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("这张图片无法解码", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "用其他应用打开",
                    color = SunColors.Accent,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = !animated) { openExternally(ctx, file) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            animated -> AnimatedImage(
                file = file,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            bmp == null -> CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dx = 0f
                            var multi = false
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.size >= 2) {
                                    multi = true
                                    val z = e.calculateZoom()
                                    if (z > 0f && z != 1f) {
                                        scale = (scale * z).coerceIn(1f, 8f)
                                        if (scale <= 1.01f) offset = Offset.Zero
                                        e.changes.forEach { it.consume() }
                                    }
                                } else if (!multi && e.changes.size == 1) {
                                    val d = e.changes[0].positionChange()
                                    if (scale > 1.01f) {
                                        // 放大后单指拖动用来平移画面
                                        offset += d
                                        e.changes[0].consume()
                                    } else {
                                        dx += d.x
                                    }
                                }
                                if (e.changes.none { it.pressed }) break
                            }
                            if (!multi && swipeEnabled.value && abs(dx) > threshold) {
                                onSwipe(if (dx < 0) 1 else -1)
                            }
                        }
                    }
                    .pointerInput(file) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.5f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.6f
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
        }
    }
}

/** 动图（GIF / 动态 WebP / APNG）用系统解码器播放，静态图仍走 Bitmap */
@RequiresApi(28)
@Composable
private fun AnimatedImage(file: File, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            if (view.tag != file.absolutePath) {
                view.tag = file.absolutePath
                runCatching { decodeAnimated(file) }.getOrNull()?.let { view.setImageDrawable(it) }
            }
        },
        modifier = modifier
    )
}

@RequiresApi(28)
private fun decodeAnimated(file: File): Drawable? {
    val source = ImageDecoder.createSource(file)
    val drawable = ImageDecoder.decodeDrawable(source)
    if (drawable is AnimatedImageDrawable) drawable.start()
    return drawable
}

private fun decodeSampled(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    // 大图降为 RGB_565：省一半内存，照片级预览几乎看不出差别
    val huge = bounds.outWidth.toLong() * bounds.outHeight > 8_000_000
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = if (huge) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
}

// ---------------- 网页 / SVG 预览 ----------------

/**
 * SVG 与 HTML 交给系统 WebView 渲染：矢量图、内联样式与简单脚本都能正确显示，
 * 比把源码当文本打开更接近"预览"。
 */
@Composable
fun WebPreviewOverlay(file: File, onClose: () -> Unit) {
    var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                web?.stopLoading()
                web?.destroy()
            }
        }
    }
    OverlayShell(
        title = file.name,
        subtitle = "网页预览 · ${Fmt.size(file.length())}",
        onClose = onClose,
        actions = {}
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    web = this
                    runCatching { loadUrl(file.toURI().toString()) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------- 压缩包浏览器 ----------------

private data class ArcNode(val name: String, val full: String, val isDir: Boolean, val size: Long)

/**
 * 压缩包浏览：点击文件不再弹只读预览，而是解压到缓存后直接交给编辑器 / 打开方式，
 * 与 MT 的操作习惯一致。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ArchiveOverlay(
    file: File,
    destDir: File,
    opVersion: Int = 0,
    initialPrefix: String = "",
    onPrefixChange: (String) -> Unit = {},
    onClose: () -> Unit,
    onExtractAll: (String?) -> Unit,
    onOpenEntry: (File, String) -> Unit,
    onRenameRequest: (String) -> Unit = {},
    onDeleteEntry: (String) -> Unit = {},
    onAddFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    var entries by remember(file) { mutableStateOf<List<FileRepo.ZipItem>?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var prefix by remember(file) { mutableStateOf(initialPrefix) }
    var opening by remember { mutableStateOf<String?>(null) }
    var menuNode by remember { mutableStateOf<ArcNode?>(null) }
    var password by remember(file) { mutableStateOf("") }
    var askPassword by remember(file) { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var passwordPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(prefix) { onPrefixChange(prefix) }

    LaunchedEffect(file, opVersion, password, attempt) {
        entries = null
        val result = withContext(Dispatchers.IO) {
            runCatching { Archives.entries(file, password.ifEmpty { null }) }
        }
        result.onSuccess {
            entries = it
            error = null
            passwordError = false
            askPassword = false
        }.onFailure {
            error = it.message ?: "无法读取压缩包"
            if (Archives.needsPassword(file) || (it.message ?: "").contains("password", true) ||
                (it.message ?: "").contains("密码")
            ) {
                askPassword = true
                passwordError = password.isNotEmpty()
            }
        }
    }

    val list = entries
    val nodes = remember(list, prefix) { list?.let { immediateChildren(it, prefix) } ?: emptyList() }

    fun openEntry(node: ArcNode) {
        if (opening != null) return
        opening = node.full
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "archive/${file.nameWithoutExtension}")
                    dir.mkdirs()
                    Archives.extractEntry(file, node.full, dir, password.ifEmpty { null })
                }.getOrNull()
            }
            opening = null
            if (out != null) {
                onOpenEntry(out, node.full)
            } else if (password.isEmpty() &&
                (Archives.kindOf(file) == Archives.Kind.ZIP || Archives.kindOf(file) == Archives.Kind.RAR)
            ) {
                // 条目已经列出来了，说明只是内容被加密：就地要密码，不打断浏览
                passwordPrompt = true
            } else {
                android.widget.Toast.makeText(context, "无法解压这个文件", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    OverlayShell(
        title = file.name,
        subtitle = when {
            list == null -> "读取中…"
            prefix.isEmpty() -> "${list.size} 个条目 · ${Fmt.size(file.length())} · ${kindLabel(file)}"
            else -> "/" + prefix.removeSuffix("/")
        },
        // 压缩包内的文件夹按返回先回上级，到根层才退出预览
        onBack = {
            if (prefix.isEmpty()) false else {
                prefix = parentPrefix(prefix)
                true
            }
        },
        onClose = {
            if (prefix.isEmpty()) onClose() else prefix = parentPrefix(prefix)
        },
        actions = {
            if (FileKinds.isZipReadable(file) && Archives.kindOf(file) == Archives.Kind.ZIP) {
                IconButton(onClick = onAddFiles, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Add, "添加文件", tint = SunColors.TextSecondary, modifier = Modifier.size(19.dp))
                }
            }
            IconButton(onClick = { onExtractAll(password.ifEmpty { null }) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Unarchive, "全部解压", tint = SunColors.Accent, modifier = Modifier.size(19.dp))
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (prefix.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(SunColors.Surface2)
                        .clickable { prefix = parentPrefix(prefix) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("..", color = SunColors.Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "/" + prefix.removeSuffix("/"),
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when {
                error != null -> Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (askPassword) {
                        Text("需要密码", color = SunColors.TextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        SunTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "压缩包密码",
                            password = true,
                            modifier = Modifier.fillMaxWidth(0.8f),
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            onDone = { attempt++ }
                        )
                        if (passwordError) {
                            Text("密码不正确", color = SunColors.Danger, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "解锁",
                            color = SunColors.Accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(SunColors.AccentSoft)
                                .clickable { attempt++ }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    } else {
                        Text("无法打开这个压缩包", color = SunColors.TextSecondary, fontSize = 12.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error ?: "",
                            color = SunColors.TextTertiary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }

                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                }

                nodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这个目录是空的", color = SunColors.TextTertiary, fontSize = 11.5.sp)
                }

                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
                    items(nodes, key = { it.full }) { node ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .combinedClickable(
                                    enabled = opening == null,
                                    onLongClick = { menuNode = node },
                                    onClick = { if (node.isDir) prefix = node.full else openEntry(node) }
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (node.isDir) {
                                com.suibian.sunfile.ui.components.MaterialFileIcon(
                                    name = node.name,
                                    isDir = true,
                                    size = 21.dp
                                )
                            } else {
                                FileTypeBadge(File(node.full), isDir = false, size = 22.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                node.name,
                                color = SunColors.TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!node.isDir) {
                                if (opening == node.full) {
                                    CircularProgressIndicator(
                                        Modifier.size(12.dp),
                                        color = SunColors.Accent,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(Fmt.size(node.size.coerceAtLeast(0)), color = SunColors.TextTertiary, style = NumberStyle)
                            }
                        }
                    }
                }
            }

        }
    }

    if (passwordPrompt) {
        var input by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { passwordPrompt = false },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = { Text("压缩包已加密", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column {
                    Text("输入密码后重新点击文件即可打开", color = SunColors.TextTertiary, fontSize = 10.5.sp)
                    Spacer(Modifier.height(10.dp))
                    SunTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "密码",
                        password = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    password = input
                    passwordPrompt = false
                    attempt++
                }) { Text("解锁", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { passwordPrompt = false }) {
                    Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    menuNode?.let { node ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { menuNode = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = {
                Text(node.name, color = SunColors.TextPrimary, fontSize = 13.sp, maxLines = 1)
            },
            text = {
                Column {
                    Text("压缩包内路径", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                    Text(
                        "/" + node.full,
                        color = SunColors.TextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    if (!node.isDir) {
                        Text(
                            "重命名（改完整路径即移动）",
                            color = SunColors.TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { menuNode = null; onRenameRequest(node.full) }
                                .padding(vertical = 9.dp)
                        )
                    }
                    Text(
                        "从压缩包删除",
                        color = SunColors.Danger,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { menuNode = null; onDeleteEntry(node.full) }
                            .padding(vertical = 9.dp)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { menuNode = null }) {
                    Text("关闭", color = SunColors.Accent, fontSize = 12.sp)
                }
            }
        )
    }
}

private fun kindLabel(file: File): String = when (Archives.kindOf(file)) {
    Archives.Kind.ZIP -> "ZIP"
    Archives.Kind.RAR -> "RAR"
    Archives.Kind.SEVEN_Z -> "7Z"
    Archives.Kind.TAR -> "TAR"
    Archives.Kind.TAR_GZ -> "TAR.GZ"
    Archives.Kind.TAR_BZ2 -> "TAR.BZ2"
    Archives.Kind.TAR_XZ -> "TAR.XZ"
    Archives.Kind.GZIP -> "GZ"
    Archives.Kind.BZIP2 -> "BZ2"
    Archives.Kind.XZ -> "XZ"
    Archives.Kind.UNSUPPORTED -> ""
    else -> ""
}

private fun immediateChildren(entries: List<FileRepo.ZipItem>, prefix: String): List<ArcNode> {
    val map = LinkedHashMap<String, ArcNode>()
    for (e in entries) {
        if (!e.name.startsWith(prefix)) continue
        val rest = e.name.removePrefix(prefix)
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            val dir = rest.substring(0, slash)
            if (dir.isNotEmpty()) {
                val full = prefix + dir + "/"
                map.putIfAbsent("D:$full", ArcNode(dir, full, true, 0))
            }
        } else if (!e.isDir) {
            map["F:${e.name}"] = ArcNode(rest, e.name, false, e.size)
        }
    }
    return map.values.sortedWith(compareByDescending<ArcNode> { it.isDir }.thenBy { it.name.lowercase() })
}

private fun parentPrefix(prefix: String): String {
    val trimmed = prefix.removeSuffix("/")
    val idx = trimmed.lastIndexOf('/')
    return if (idx < 0) "" else prefix.substring(0, idx + 1)
}

// ---------------- 视频播放器 ----------------

private fun clock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

@Composable
fun VideoViewerOverlay(file: File, playlist: List<File>, onClose: () -> Unit) {
    val list = remember(playlist, file) {
        if (playlist.any { it.absolutePath == file.absolutePath }) playlist else listOf(file)
    }
    var index by remember(file) {
        mutableIntStateOf(list.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0))
    }
    var direction by remember { mutableIntStateOf(0) }
    var exifFor by remember { mutableStateOf<File?>(null) }
    val current = list.getOrElse(index) { file }

    fun go(delta: Int) {
        val next = index + delta
        if (next !in list.indices) return
        direction = delta
        index = next
    }

    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var video by remember { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(controls, playing) {
        if (controls) {
            delay(3600)
            controls = false
        }
    }
    LaunchedEffect(playing, current) {
        while (playing) {
            position = runCatching { video?.currentPosition ?: 0 }.getOrDefault(0)
            delay(320)
        }
    }
    DisposableEffect(current) {
        playing = false
        duration = 0
        position = 0
        failure = null
        controls = true
        onDispose { runCatching { video?.stopPlayback() } }
    }

    OverlayShell(
        title = current.name,
        subtitle = if (list.size > 1) "${index + 1} / ${list.size}" else Fmt.size(current.length()),
        onClose = onClose,
        background = Color.Black,
        actions = { ShareButton(current) }
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val dir = if (direction >= 0) 1 else -1
                    (slideInHorizontally(tween(Motion.Slow, easing = Motion.EaseOut)) { w -> dir * w / 4 } +
                        fadeIn(tween(Motion.Base))) togetherWith
                        (slideOutHorizontally(tween(Motion.Slow, easing = Motion.EaseIn)) { w -> -dir * w / 4 } +
                            fadeOut(tween(Motion.Base)))
                },
                label = "video"
            ) { target ->
                key(target.absolutePath) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setOnPreparedListener { mp ->
                                    duration = mp.duration
                                    mp.isLooping = false
                                    start()
                                    playing = true
                                }
                                setOnCompletionListener {
                                    playing = false
                                    if (index < list.lastIndex) go(1)
                                }
                                setOnErrorListener { _, _, _ ->
                                    failure = "这个视频无法解码"
                                    true
                                }
                                setVideoPath(target.absolutePath)
                                video = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 手势层：点按显隐控制条，左右滑动换片
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(current) {
                        detectTapGestures { controls = !controls }
                    }
                    .pointerInput(current) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (abs(total) > size.width * 0.16f) go(if (total < 0) 1 else -1)
                            },
                            onHorizontalDrag = { _, dx -> total += dx }
                        )
                    }
            )

            failure?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            AnimatedVisibility(
                visible = controls,
                enter = fadeIn(tween(Motion.Fast)),
                exit = fadeOut(tween(Motion.Base)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val v = video ?: return@IconButton
                                if (v.isPlaying) {
                                    v.pause()
                                    playing = false
                                } else {
                                    v.start()
                                    playing = true
                                }
                                controls = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(clock(position), color = Color.White.copy(alpha = 0.8f), style = NumberStyle)
                        Slider(
                            value = position.toFloat(),
                            onValueChange = {
                                position = it.toInt()
                                runCatching { video?.seekTo(it.toInt()) }
                            },
                            valueRange = 0f..(duration.coerceAtLeast(1)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = SunColors.Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(clock(duration), color = Color.White.copy(alpha = 0.8f), style = NumberStyle)
                    }
                }
            }
        }
    }
}

// ---------------- 音频播放器（底部弹窗，不占满屏） ----------------

@Composable
fun AudioSheetOverlay(file: File, playlist: List<File>, onClose: () -> Unit) {
    val context = LocalContext.current
    val list = remember(playlist, file) {
        if (playlist.any { it.absolutePath == file.absolutePath }) playlist else listOf(file)
    }
    var index by remember(file) {
        mutableIntStateOf(list.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0))
    }
    val current = list.getOrElse(index) { file }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    BackHandler { onClose() }

    // 换曲：释放上一首，重新准备
    DisposableEffect(current) {
        loading = true
        playing = false
        position = 0
        duration = 0
        failure = null
        val mp = MediaPlayer()
        runCatching { mp.setDataSource(current.absolutePath) }
            .onFailure { failure = it.message ?: "无法播放" }
        mp.setOnPreparedListener {
            duration = it.duration
            loading = false
            runCatching { it.start() }
            playing = true
        }
        mp.setOnErrorListener { _, _, _ ->
            failure = "这个音频无法解码"
            loading = false
            true
        }
        mp.setOnCompletionListener {
            playing = false
            if (index < list.lastIndex) index += 1
        }
        runCatching { mp.prepareAsync() }
        player = mp
        onDispose {
            runCatching {
                mp.stop()
                mp.release()
            }
        }
    }

    LaunchedEffect(current) {
        cover = withContext(Dispatchers.IO) { embeddedCover(current) }
    }
    LaunchedEffect(playing, current) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(300)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.Base)),
        exit = fadeOut(tween(Motion.Fast))
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩：吃掉所有点击与滑动，不关闭；只有关闭按钮与系统返回能退出
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (SunColors.dark) 0.62f else 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SunColors.Surface3)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SunColors.Surface1),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = cover
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Audiotrack,
                                null,
                                tint = SunColors.Accent.copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.nameWithoutExtension,
                            color = SunColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        val subtitle = failure?.let { "$it · 点此用其他应用打开" }
                            ?: if (list.size > 1) "${index + 1} / ${list.size}"
                            else current.parent?.substringAfterLast('/').orEmpty()
                        Text(
                            subtitle,
                            color = if (failure != null) SunColors.Danger else SunColors.TextTertiary,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (failure != null) Modifier.clickable { openExternally(context, current) } else Modifier
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Close,
                            "关闭",
                            tint = SunColors.TextTertiary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Slider(
                    value = position.toFloat(),
                    onValueChange = {
                        position = it.toInt()
                        runCatching { player?.seekTo(it.toInt()) }
                    },
                    valueRange = 0f..(duration.coerceAtLeast(1)).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = SunColors.Accent,
                        activeTrackColor = SunColors.Accent,
                        inactiveTrackColor = SunColors.Surface1
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = SunColors.TextTertiary, style = NumberStyle)
                    Text(clock(duration), color = SunColors.TextTertiary, style = NumberStyle)
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (index > 0) index -= 1 },
                        enabled = index > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            "上一首",
                            tint = if (index > 0) SunColors.TextSecondary else SunColors.TextTertiary.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(if (failure == null) SunColors.Accent else SunColors.Surface1)
                            .clickable(enabled = failure == null) {
                                val mp = player ?: return@clickable
                                if (playing) {
                                    runCatching { mp.pause() }
                                    playing = false
                                } else {
                                    runCatching { mp.start() }
                                    playing = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(
                        onClick = { if (index < list.lastIndex) index += 1 },
                        enabled = index < list.lastIndex,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ArrowForward,
                            "下一首",
                            tint = if (index < list.lastIndex) SunColors.TextSecondary
                            else SunColors.TextTertiary.copy(alpha = 0.35f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ShareButton(file: File) {
    val context = LocalContext.current
    IconButton(
        onClick = { shareFile(context, file) },
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            Icons.Outlined.Share,
            "分享",
            tint = SunColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun embeddedCover(file: File): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val data = retriever.embeddedPicture
        if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()
