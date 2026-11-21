package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suibian.sunfile.data.SampleState
import com.suibian.sunfile.data.ScreenSampler
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ==================== 流媒体播放 ====================

@Composable
fun StreamPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAll() {
        runCatching { videoView?.stopPlayback() }
        runCatching { audioPlayer?.release() }
        audioPlayer = null
        playing = false
    }

    ToolScaffold(
        title = "流媒体播放",
        subtitle = "HTTP / HLS / RTSP 地址",
        onBack = onBack,
        actions = {
            if (playing) {
                BarIcon(Icons.Outlined.Stop, "停止") { stopAll() }
            }
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("媒体地址", color = SunColors.TextTertiary, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            SunField(url, { url = it }, "https://example.com/live.m3u8", mono = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("播放", modifier = Modifier.weight(1f), enabled = url.isNotBlank()) {
                    stopAll()
                    message = null
                    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull()
                    if (uri == null) {
                        message = "地址无效"
                    } else {
                        // 常见音频扩展用 MediaPlayer，其余交给 VideoView（内部同样是 MediaPlayer + Surface）
                        val isAudio = url.substringBefore('?').lowercase()
                            .let { it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".aac") || it.endsWith(".ogg") }
                        if (isAudio) {
                            runCatching {
                                val p = MediaPlayer()
                                p.setDataSource(context, uri)
                                p.setOnPreparedListener { it.start(); playing = true }
                                p.setOnErrorListener { _, what, extra ->
                                    message = "播放失败（$what/$extra）"; playing = false; true
                                }
                                p.prepareAsync()
                                audioPlayer = p
                            }.onFailure { message = "无法播放：${it.message}" }
                        } else {
                            val vv = videoView
                            if (vv == null) {
                                message = "播放器尚未就绪，请稍后重试"
                            } else {
                                runCatching {
                                    vv.setVideoURI(uri)
                                    vv.setOnPreparedListener { playing = true; it.start() }
                                    vv.setOnErrorListener { _, what, extra ->
                                        message = "播放失败（$what/$extra）"; playing = false; true
                                    }
                                    vv.start()
                                }.onFailure { message = "无法播放：${it.message}" }
                            }
                        }
                    }
                }
                GhostButton("停止", modifier = Modifier.width(90.dp)) { stopAll() }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SunColors.Danger, fontSize = 11.sp)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            "支持的协议取决于系统解码器：HTTP/HTTPS 的音视频、HLS（.m3u8）通常可用；" +
                "RTSP 需要设备内置对应组件。",
            color = SunColors.TextTertiary,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// ==================== 远程 Payload ====================

private data class HttpResult(
    val code: Int,
    val headers: String,
    val body: String,
    val costMs: Long,
    val error: String? = null
)

@Composable
fun PayloadPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<HttpResult?>(null) }

    ToolScaffold(
        title = "远程 Payload",
        subtitle = "构造请求并查看原始响应",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                    val on = m == method
                    Text(
                        m,
                        color = if (on) Color.White else SunColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) SunColors.Accent else SunColors.Surface2)
                            .clickable { method = m }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SunField(url, { url = it }, "https://api.example.com/endpoint", mono = true)
            Spacer(Modifier.height(10.dp))
            Text("请求头（每行 Key: Value）", color = SunColors.TextTertiary, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            SunField(
                headers,
                { headers = it },
                "Content-Type: application/json",
                mono = true,
                singleLine = false
            )
            Spacer(Modifier.height(10.dp))
            Text("请求体", color = SunColors.TextTertiary, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            SunField(
                body,
                { body = it },
                "{\"key\":\"value\"}",
                mono = true,
                singleLine = false
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                if (busy) "发送中…" else "发送请求",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && url.isNotBlank()
            ) {
                busy = true
                result = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        send(method, url.trim(), headers, body)
                    }
                    result = r
                    busy = false
                }
            }

            result?.let { r ->
                Spacer(Modifier.height(16.dp))
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.code}",
                            color = if (r.code in 200..299) SunColors.Success else if (r.code == 0) SunColors.Danger else SunColors.Warning,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${r.costMs} ms", color = SunColors.TextTertiary, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.ContentCopy,
                            "复制响应",
                            tint = SunColors.TextSecondary,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("response", r.body))
                                    toast(context, "响应已复制")
                                }
                                .padding(5.dp)
                        )
                    }
                    if (r.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(r.error, color = SunColors.Danger, fontSize = 11.sp)
                    }
                    if (r.headers.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("响应头", color = SunColors.TextTertiary, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            r.headers,
                            color = SunColors.TextSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("响应体", color = SunColors.TextTertiary, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            r.body.ifBlank { "（空）" },
                            color = SunColors.TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun send(method: String, url: String, headers: String, body: String): HttpResult {
    val started = System.currentTimeMillis()
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        headers.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) conn.setRequestProperty(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
        }
        if (method != "GET" && body.isNotBlank()) {
            conn.doOutput = true
            val bytes = body.toByteArray()
            if (conn.getRequestProperty("Content-Type") == null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val head = buildString {
            conn.headerFields.forEach { (k, v) ->
                if (!k.isNullOrBlank() && !k.startsWith(":")) append("$k: ${v.joinToString(", ")}\n")
            }
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (sb.length < 200_000) {
                val n = reader.read(buf)
                if (n <= 0) break
                sb.append(buf, 0, n)
            }
            sb.toString()
        } ?: ""
        HttpResult(code, head.trim(), text, System.currentTimeMillis() - started)
    } catch (e: Throwable) {
        HttpResult(0, "", "", System.currentTimeMillis() - started, e.message ?: e.javaClass.simpleName)
    } finally {
        runCatching { conn?.disconnect() }
    }
}

// ==================== 屏幕取色 ====================

@Composable
fun ColorPickerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by ScreenSampler.state.collectAsState()
    var picked by remember { mutableStateOf<Offset?>(null) }
    var boxSize by remember { mutableStateOf(Offset.Zero) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == android.app.Activity.RESULT_OK && data != null) {
            ScreenSampler.startProjectionService(context, res.resultCode, data)
        }
    }

    fun requestCapture() {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        ScreenSampler.reset()
        picked = null
        launcher.launch(manager.createScreenCaptureIntent())
    }

    // 进入页面直接拉起一次授权，少一次点击
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (state is SampleState.Idle) requestCapture()
    }

    ToolScaffold(
        title = "屏幕取色",
        subtitle = "点选画面任意位置读取色值",
        onBack = onBack,
        actions = {
            BarIcon(Icons.Outlined.PlayArrow, "重新截取") { requestCapture() }
        }
    ) {
        when (val s = state) {
            SampleState.Idle, SampleState.Capturing -> {
                EmptyState(
                    if (s is SampleState.Capturing) "正在截取屏幕…" else "等待屏幕捕获授权",
                    "授权后点击画面任意位置即可取色"
                )
            }
            is SampleState.Failed -> EmptyState("取色失败", s.message)
            is SampleState.Ready -> {
                val bmp = s.bitmap
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .background(Color.Black)
                        .pointerInput(bmp) {
                            detectTapGestures { pos ->
                                boxSize = Offset(size.width.toFloat(), size.height.toFloat())
                                picked = pos
                            }
                        }
                ) {
                    Image(
                        bmp.asImageBitmap(),
                        null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    picked?.let { p ->
                        val vx = (p.x / boxSize.x * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                        val vy = (p.y / boxSize.y * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                        // 取色框跟随手指：px 偏移，保证在缩放后的画面上位置准确
                        Box(
                            Modifier
                                .offset { androidx.compose.ui.unit.IntOffset((p.x - 11f).toInt(), (p.y - 11f).toInt()) }
                                .size(22.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(50))
                        )
                    }
                }

                val p = picked
                if (p == null) {
                    Text(
                        "点击上方画面取色",
                        color = SunColors.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val vx = (p.x / boxSize.x * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                    val vy = (p.y / boxSize.y * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                    val argb = bmp.getPixel(vx, vy)
                    val r = android.graphics.Color.red(argb)
                    val g = android.graphics.Color.green(argb)
                    val b = android.graphics.Color.blue(argb)
                    val hex = "#%02X%02X%02X".format(r, g, b)
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(argb, hsv)
                    val cmyk = cmykOf(r, g, b)
                    val contrast = if ((0.299 * r + 0.587 * g + 0.114 * b) > 150) "偏亮" else "偏暗"

                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(r, g, b))
                                    .border(1.dp, SunColors.Hairline, RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hex,
                                    color = SunColors.TextPrimary,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "($vx, $vy) · $contrast",
                                    color = SunColors.TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                            Icon(
                                Icons.Outlined.ContentCopy,
                                "复制",
                                tint = SunColors.TextSecondary,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "color",
                                                "$hex  rgb($r, $g, $b)"
                                            )
                                        )
                                        toast(context, "已复制 $hex")
                                    }
                                    .padding(5.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        KeyValue("HEX", hex, mono = true)
                        KeyValue("RGB", "$r, $g, $b", mono = true)
                        KeyValue("CMYK", cmyk, mono = true)
                        KeyValue(
                            "HSV",
                            "${(hsv[0]).toInt()}°, ${(hsv[1] * 100).toInt()}%, ${(hsv[2] * 100).toInt()}%",
                            mono = true
                        )
                        Spacer(Modifier.height(12.dp))
                        // 放大镜：取 15x15 区域放成一张小图，方便看清边缘
                        val cropSize = 15
                        val cx = (vx - cropSize / 2).coerceIn(0, (bmp.width - cropSize).coerceAtLeast(0))
                        val cy = (vy - cropSize / 2).coerceIn(0, (bmp.height - cropSize).coerceAtLeast(0))
                        val crop = runCatching {
                            Bitmap.createBitmap(bmp, cx, cy, cropSize.coerceAtMost(bmp.width), cropSize.coerceAtMost(bmp.height))
                        }.getOrNull()
                        if (crop != null) {
                            Text("局部放大", color = SunColors.TextTertiary, fontSize = 10.sp)
                            Spacer(Modifier.height(6.dp))
                            Image(
                                crop.asImageBitmap(),
                                null,
                                contentScale = ContentScale.None,
                                modifier = Modifier
                                    .size(105.dp)
                                    .border(1.dp, SunColors.Hairline)
                            )
                        }
                    }
                }
            }
        }
    }
}



private fun cmykOf(r: Int, g: Int, b: Int): String {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val k = 1f - maxOf(rf, gf, bf)
    if (k >= 1f) return "0%, 0%, 0%, 100%"
    val c = (1f - rf - k) / (1f - k)
    val m = (1f - gf - k) / (1f - k)
    val y = (1f - bf - k) / (1f - k)
    return "${(c * 100).toInt()}%, ${(m * 100).toInt()}%, ${(y * 100).toInt()}%, ${(k * 100).toInt()}%"
}
