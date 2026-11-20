package com.suibian.sunfile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.NinePatch
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 点九图编辑器：边框上的 1 像素在画布上放大成可点的格子。
 * 上边与左边是拉伸区，右边与下边是内容留白，规则与 aapt 一致。
 */
@Composable
fun NinePatchOverlay(file: File, onClose: () -> Unit) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var marks by remember(file) { mutableStateOf<NinePatch.Marks?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(file) {
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true)
            }.getOrNull()
        }
        if (bmp == null) message = "无法解码这张图" else {
            bitmap = bmp
            marks = NinePatch.marksOf(bmp)
        }
    }

    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            delay(2000)
            message = null
        }
    }

    fun toggle(region: Int, index: Int) {
        val bmp = bitmap ?: return
        val w = bmp.width
        val h = bmp.height
        val color = bmp.getPixel(
            when (region) {
                0, 3 -> index + 1
                else -> if (region == 1) 0 else w - 1
            },
            when (region) {
                0 -> 0
                1, 2 -> index + 1
                else -> h - 1
            }
        )
        val on = (color ushr 24) != 0
        val next = if (on) 0 else android.graphics.Color.BLACK
        when (region) {
            0 -> bmp.setPixel(index + 1, 0, next)
            1 -> bmp.setPixel(0, index + 1, next)
            2 -> bmp.setPixel(w - 1, index + 1, next)
            else -> bmp.setPixel(index + 1, h - 1, next)
        }
        marks = NinePatch.marksOf(bmp)
        version++
    }

    fun save() {
        val bmp = bitmap ?: return
        val m = marks ?: return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val bytes = NinePatch.encode(bmp, m.xDivs, m.yDivs) ?: return@withContext false
                runCatching { file.writeBytes(bytes); true }.getOrDefault(false)
            }
            saving = false
            message = if (ok) "已保存" else "保存失败"
        }
    }

    val m = marks
    OverlayShell(
        title = file.name,
        subtitle = if (m == null) "解码中…" else
            "${m.width}×${m.height} · 拉伸 ${m.xDivs.size / 2}×${m.yDivs.size / 2}",
        onClose = onClose,
        actions = {
            TextButton(onClick = { save() }) {
                Text(if (saving) "保存中" else "保存", color = SunColors.Accent, fontSize = 12.sp)
            }
            TextButton(onClick = {
                val bmp = bitmap ?: return@TextButton
                // 整条拉伸：上边与左边全部涂黑
                for (x in 1 until bmp.width - 1) bmp.setPixel(x, 0, android.graphics.Color.BLACK)
                for (y in 1 until bmp.height - 1) bmp.setPixel(0, y, android.graphics.Color.BLACK)
                for (x in 1 until bmp.width - 1) bmp.setPixel(x, bmp.height - 1, 0)
                for (y in 1 until bmp.height - 1) bmp.setPixel(bmp.width - 1, y, 0)
                marks = NinePatch.marksOf(bmp)
                version++
            }) { Text("整条", color = SunColors.TextSecondary, fontSize = 12.sp) }
            TextButton(onClick = {
                val bmp = bitmap ?: return@TextButton
                for (x in 1 until bmp.width - 1) {
                    bmp.setPixel(x, 0, 0)
                    bmp.setPixel(x, bmp.height - 1, 0)
                }
                for (y in 1 until bmp.height - 1) {
                    bmp.setPixel(0, y, 0)
                    bmp.setPixel(bmp.width - 1, y, 0)
                }
                marks = NinePatch.marksOf(bmp)
                version++
            }) { Text("清空", color = SunColors.TextSecondary, fontSize = 12.sp) }
        }
    ) {
        when {
            m == null || bitmap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NinePatchCanvas(
                    bitmap = bitmap!!,
                    marks = m,
                    version = version,
                    onToggle = { region, index -> toggle(region, index) }
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Legend(SunColors.Accent, "拉伸区")
                    Legend(SunColors.Success, "内容留白")
                }
                Text(
                    "点四边的格子切换：上/左为拉伸，右/下为留白",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                val pad = m.padding()
                Text(
                    "内容区 ${pad[0]},${pad[1]} → ${m.width - 2 - pad[2]},${m.height - 2 - pad[3]}",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        message?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg,
                    color = SunColors.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 34.dp)
                        .background(SunColors.Surface4, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
        Text(label, color = SunColors.TextTertiary, fontSize = 9.5.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun NinePatchCanvas(
    bitmap: Bitmap,
    marks: NinePatch.Marks,
    version: Int,
    onToggle: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val frame = with(density) { 20.dp.toPx() }
    val contentW = marks.contentWidth
    val contentH = marks.contentHeight
    val scale = if (contentW > 320) 2.2f else if (contentW > 160) 3.4f else 5f
    val width = contentW * scale + frame * 2
    val height = contentH * scale + frame * 2
    val image = remember(bitmap, version) { bitmap.asImageBitmap() }
    // 颜色在组合期取好，DrawScope 里不能用 @Composable 的取色器
    val bgColor = SunColors.Surface2
    val stretchColor = SunColors.Accent

    Box(
        Modifier.fillMaxWidth().height(with(density) { height.toDp() })
            .horizontalScroll(rememberScrollState())
    ) {
        Canvas(
            Modifier.size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .pointerInput(version, marks) {
                    detectTapGestures { pos ->
                        val x = pos.x
                        val y = pos.y
                        val right = frame + contentW * scale
                        val bottom = frame + contentH * scale
                        when {
                            y < frame && x >= frame - 4 && x <= right + 4 -> {
                                val i = ((x - frame) / scale).toInt().coerceIn(0, contentW - 1)
                                onToggle(0, i)
                            }
                            y > bottom && x >= frame - 4 && x <= right + 4 -> {
                                val i = ((x - frame) / scale).toInt().coerceIn(0, contentW - 1)
                                onToggle(3, i)
                            }
                            x < frame && y >= frame - 4 && y <= bottom + 4 -> {
                                val i = ((y - frame) / scale).toInt().coerceIn(0, contentH - 1)
                                onToggle(1, i)
                            }
                            x > right && y >= frame - 4 && y <= bottom + 4 -> {
                                val i = ((y - frame) / scale).toInt().coerceIn(0, contentH - 1)
                                onToggle(2, i)
                            }
                        }
                    }
                }
        ) {
            val contentRect = Rect(frame, frame, frame + contentW * scale, frame + contentH * scale)
            drawRect(bgColor, topLeft = Offset.Zero, size = size)

            // 棋盘底：透明区域一眼能看出来
            val cell = 8f
            var cx = contentRect.left
            var col = 0
            while (cx < contentRect.right) {
                var cy = contentRect.top
                var row = 0
                while (cy < contentRect.bottom) {
                    if ((row + col) % 2 == 0) {
                        drawRect(
                            Color(0xFF262629),
                            topLeft = Offset(cx, cy),
                            size = Size(minOf(cell, contentRect.right - cx), minOf(cell, contentRect.bottom - cy))
                        )
                    }
                    cy += cell
                    row++
                }
                cx += cell
                col++
            }

            drawImage(
                image = image,
                srcOffset = androidx.compose.ui.unit.IntOffset(1, 1),
                srcSize = androidx.compose.ui.unit.IntSize(contentW, contentH),
                dstOffset = androidx.compose.ui.unit.IntOffset(contentRect.left.toInt(), contentRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(contentRect.width.toInt(), contentRect.height.toInt()),
                filterQuality = FilterQuality.None
            )

            // 拉伸区：上边与左边
            marks.xDivs.chunked(2).forEach { run ->
                if (run.size == 2) {
                    val l = contentRect.left + run[0] * scale
                    val r = contentRect.left + run[1] * scale
                    drawRect(
                        stretchColor.copy(alpha = 0.20f),
                        topLeft = Offset(l, contentRect.top),
                        size = Size(r - l, contentRect.height)
                    )
                }
            }
            marks.yDivs.chunked(2).forEach { run ->
                if (run.size == 2) {
                    val t = contentRect.top + run[0] * scale
                    val b = contentRect.top + run[1] * scale
                    drawRect(
                        stretchColor.copy(alpha = 0.13f),
                        topLeft = Offset(contentRect.left, t),
                        size = Size(contentRect.width, b - t)
                    )
                }
            }

            // 留白框
            val pad = marks.padding()
            drawRect(
                Color(0xFF3FB950).copy(alpha = 0.55f),
                topLeft = Offset(contentRect.left + pad[0] * scale, contentRect.top + pad[1] * scale),
                size = Size(
                    ((marks.width - 2 - pad[2]) - pad[0]) * scale,
                    ((marks.height - 2 - pad[3]) - pad[1]) * scale
                ),
                style = Stroke(width = 1.2f)
            )

            // 四边格子
            drawStrip(marks.top, contentW, frame, scale, horizontal = true, top = true)
            drawStrip(marks.bottom, contentW, frame, scale, horizontal = true, top = false, bottomAt = contentRect.bottom)
            drawStrip(marks.left, contentH, frame, scale, horizontal = false, top = true)
            drawStrip(marks.right, contentH, frame, scale, horizontal = false, top = false, rightAt = contentRect.right)
        }
    }
}

private fun DrawScope.drawStrip(
    marks: BooleanArray,
    count: Int,
    frame: Float,
    scale: Float,
    horizontal: Boolean,
    top: Boolean,
    bottomAt: Float = 0f,
    rightAt: Float = 0f
) {
    for (i in 0 until count) {
        val on = marks.getOrNull(i + 1) == true
        if (!on) continue
        if (horizontal) {
            val y = if (top) 0f else bottomAt
            drawRect(
                Color.Black,
                topLeft = Offset(frame + i * scale, y),
                size = Size(scale, frame)
            )
            drawRect(
                Color(0xFF3B86F7).copy(alpha = 0.35f),
                topLeft = Offset(frame + i * scale, y),
                size = Size(scale, frame)
            )
        } else {
            val x = if (top) 0f else rightAt
            drawRect(
                Color.Black,
                topLeft = Offset(x, frame + i * scale),
                size = Size(frame, scale)
            )
            drawRect(
                Color(0xFF3B86F7).copy(alpha = 0.35f),
                topLeft = Offset(x, frame + i * scale),
                size = Size(frame, scale)
            )
        }
    }
}
