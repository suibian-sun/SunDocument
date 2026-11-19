package com.suibian.sunfile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val scope = rememberCoroutineScope()

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

            Text(
                "双指缩放 · 双击放大 · 拖动平移",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.5.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
            )
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
    onClose: () -> Unit,
    onExtractAll: () -> Unit
) {
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
                Text(
                    "点击文件预览，右上角可解压全部",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    modifier = Modifier.weight(1f)
                )
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
                        if (isText) "文本预览（最多 256KB）" else "二进制预览（前 1KB）",
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
                TextButton(onClick = { preview = null }) {
                    Text("关闭", color = SunColors.Accent, fontSize = 13.sp)
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
