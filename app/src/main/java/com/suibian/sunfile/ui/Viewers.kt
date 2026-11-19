package com.suibian.sunfile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.ui.theme.Motion
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

// ---------------- 通用外壳 ----------------

/** 全屏查看器外壳：淡入 + 轻微上滑，返回键关闭 */
@Composable
private fun OverlayShell(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    background: Color = SunColors.Bg,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { visible = true }
    BackHandler { onClose() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = Motion.EaseOut)) +
            slideInVertically(tween(300, easing = Motion.EaseOut)) { it / 18 }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "返回",
                        tint = SunColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = SunColors.TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = SunColors.TextTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
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
        subtitle = "${Fmt.size(size)} · 偏移 0x${offset.toString(16).uppercase(Locale.US)}",
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
                Text(
                    "字节 (hex)",
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
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
                        val start = li * HEX_PER_LINE
                        HexRow(baseOffset = offset + start, data = data, start = start)
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
                    modifier = Modifier.clickable(enabled = offset + HEX_PAGE < size) {
                        offset += HEX_PAGE
                    }
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
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 12.dp),
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
fun ImageViewerOverlay(file: File, onClose: () -> Unit) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file) {
        val bmp = withContext(Dispatchers.IO) { decodeSampled(file, 2400) }
        if (bmp == null) failed = true else bitmap = bmp
    }

    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + pan
    }

    OverlayShell(
        title = file.name,
        subtitle = bitmap?.let { "${it.width} × ${it.height} · ${Fmt.size(file.length())}" }
            ?: Fmt.size(file.length()),
        onClose = onClose,
        background = Color(0xFF07090C)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            when {
                failed -> Text("这张图片无法解码", color = SunColors.TextSecondary, fontSize = 12.sp)

                bmp == null -> CircularProgressIndicator(
                    Modifier.size(22.dp),
                    color = SunColors.Accent,
                    strokeWidth = 2.dp
                )

                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
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
}

private fun decodeSampled(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
}

// ---------------- 压缩包浏览器 ----------------

private data class ArcNode(val name: String, val full: String, val isDir: Boolean, val size: Long)

@Composable
fun ArchiveOverlay(
    file: File,
    destDir: File,
    onClose: () -> Unit,
    onExtractAll: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var entries by remember(file) { mutableStateOf<List<FileRepo.ZipItem>?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var prefix by remember(file) { mutableStateOf("") }
    var preview by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) { runCatching { FileRepo.zipEntries(file) } }
        result.onSuccess { entries = it }.onFailure { error = it.message ?: "无法读取压缩包" }
    }

    val list = entries
    val nodes = remember(list, prefix) { list?.let { immediateChildren(it, prefix) } ?: emptyList() }

    OverlayShell(
        title = file.name,
        subtitle = list?.let { "${it.size} 个条目 · ${Fmt.size(file.length())}" } ?: "读取中…",
        onClose = onClose,
        actions = {
            IconButton(onClick = onExtractAll, modifier = Modifier.size(40.dp)) {
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
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = SunColors.Danger, fontSize = 11.5.sp, textAlign = TextAlign.Center)
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
                                .clickable {
                                    if (node.isDir) {
                                        prefix = node.full
                                    } else {
                                        scope.launch {
                                            val data = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    FileRepo.readZipEntry(file, node.full, 256 * 1024)
                                                }.getOrNull()
                                            }
                                            if (data != null) {
                                                val isText = looksTextual(data)
                                                val content = if (isText) {
                                                    String(data, Charsets.UTF_8)
                                                } else {
                                                    hexPreview(data)
                                                }
                                                preview = Triple(node.full, content, isText)
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (node.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                null,
                                tint = if (node.isDir) SunColors.Folder else SunColors.TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
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
                                Text(
                                    Fmt.size(node.size),
                                    color = SunColors.TextTertiary,
                                    style = NumberStyle
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(46.dp).background(SunColors.Surface1).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Archive, null, tint = SunColors.TextTertiary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "解压全部",
                    color = SunColors.Accent,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onExtractAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }

    preview?.let { (name, content, isText) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = {
                Text(
                    name.substringAfterLast('/'),
                    color = SunColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text(
                        if (isText) "文本" else "二进制",
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        content,
                        color = SunColors.TextSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            runCatching { FileRepo.extractZipEntry(file, name, destDir) }.getOrNull()
                        }
                        android.widget.Toast.makeText(
                            context,
                            if (out != null) "已解压到 ${out.parentFile?.name ?: "/"}" else "解压失败",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("解压此文件", color = SunColors.Accent, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) {
                    Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }
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

private fun looksTextual(data: ByteArray): Boolean {
    if (data.isEmpty()) return true
    var nul = 0
    for (i in 0 until minOf(data.size, 512)) if (data[i] == 0.toByte()) nul++
    return nul == 0
}

private fun hexPreview(data: ByteArray): String {
    val sb = StringBuilder()
    val max = minOf(data.size, 1024)
    var i = 0
    while (i < max) {
        sb.append(String.format(Locale.US, "%08X  ", i))
        val end = minOf(i + 16, max)
        for (j in i until end) sb.append(String.format(Locale.US, "%02X ", data[j]))
        for (j in end until i + 16) sb.append("   ")
        sb.append(' ')
        for (j in i until end) {
            val b = data[j].toInt() and 0xff
            sb.append(if (b in 32..126) b.toChar() else '.')
        }
        sb.append('\n')
        i += 16
    }
    return sb.toString()
}

// ---------------- 视频播放器 ----------------

private fun clock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
fun VideoViewerOverlay(file: File, onClose: () -> Unit) {
    var video by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }

    // 控制条几秒后自动隐去，只留画面
    LaunchedEffect(controls, playing) {
        if (controls) {
            delay(3000)
            controls = false
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = video?.currentPosition ?: 0
            delay(320)
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { video?.stopPlayback() } }
    }

    OverlayShell(
        title = file.name,
        subtitle = Fmt.size(file.length()),
        onClose = onClose,
        background = Color.Black
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controls = !controls } }
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnPreparedListener { mp ->
                            duration = mp.duration
                            mp.isLooping = false
                            start()
                            playing = true
                        }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { _, _, _ ->
                            failure = "这个视频无法解码"
                            true
                        }
                        setVideoPath(file.absolutePath)
                        video = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            failure?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = controls,
                enter = androidx.compose.animation.fadeIn(tween(160)),
                exit = androidx.compose.animation.fadeOut(tween(220)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
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
                                video?.seekTo(it.toInt())
                            },
                            valueRange = 0f..(duration.coerceAtLeast(1)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = SunColors.Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Text(clock(duration), color = Color.White.copy(alpha = 0.8f), style = NumberStyle)
                    }
                }
            }
        }
    }
}

// ---------------- 音频播放器 ----------------

@Composable
fun AudioViewerOverlay(file: File, onClose: () -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        cover = withContext(Dispatchers.IO) { embeddedCover(file) }
    }
    DisposableEffect(Unit) {
        val mp = MediaPlayer()
        runCatching { mp.setDataSource(file.absolutePath) }
            .onFailure { failure = it.message ?: "无法播放" }
        mp.setOnPreparedListener { duration = it.duration }
        mp.setOnErrorListener { _, _, _ ->
            failure = "这个音频无法解码"
            true
        }
        mp.setOnCompletionListener { playing = false }
        runCatching { mp.prepareAsync() }
        player = mp
        onDispose { runCatching { mp.release() } }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(300)
        }
    }

    OverlayShell(
        title = file.name,
        subtitle = Fmt.size(file.length()),
        onClose = onClose,
        background = SunColors.Bg
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(210.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(SunColors.Surface2),
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
                        tint = SunColors.Accent.copy(alpha = 0.75f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                file.nameWithoutExtension,
                color = SunColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                failure ?: file.parent?.substringAfterLast('/').orEmpty(),
                color = if (failure != null) SunColors.Danger else SunColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(22.dp))
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
                    inactiveTrackColor = SunColors.Surface4
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(position), color = SunColors.TextTertiary, style = NumberStyle)
                Text(clock(duration), color = SunColors.TextTertiary, style = NumberStyle)
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SunColors.Accent)
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
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
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
