package com.suibian.sunfile.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.suibian.sunfile.data.RemoteServer
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ==================== 已安装应用 ====================

private data class AppItem(
    val label: String,
    val pkg: String,
    val version: String,
    val size: Long,
    val system: Boolean,
    val icon: android.graphics.Bitmap?,
    val apkPath: String?
)

@Composable
fun AppsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AppItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val apps by produceState(initialValue = emptyList<AppItem>(), tick) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    val shown = remember(apps, query, showSystem) {
        apps.filter { a ->
            (showSystem || !a.system) &&
                (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }
    }

    ToolScaffold(
        title = "已安装应用",
        subtitle = "${shown.size} / ${apps.size} 个应用",
        onBack = onBack,
        actions = {
            BarIcon(Icons.Outlined.Refresh, "重新扫描") {
                loading = true
                tick++
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SunField(query, { query = it }, "搜索应用名或包名")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示系统应用", color = SunColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                SunSwitch(showSystem, { showSystem = it })
            }
        }
        Hairline()
        if (loading) {
            EmptyState("正在读取应用列表…")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(shown, key = { it.pkg }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { detail = app }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SunColors.Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = app.icon
                            if (bmp != null) {
                                Image(
                                    bmp.asImageBitmap(),
                                    null,
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Text(
                                    app.label.take(1),
                                    color = SunColors.TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                color = SunColors.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                app.pkg,
                                color = SunColors.TextTertiary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.size(app.size), color = SunColors.TextSecondary, style = NumberStyle)
                            if (app.system) {
                                Text("系统", color = SunColors.TextTertiary, fontSize = 8.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { app ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = { Text(app.label, color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column {
                    KeyValue("包名", app.pkg, mono = true)
                    KeyValue("版本", app.version)
                    KeyValue("大小", Fmt.size(app.size))
                    KeyValue("路径", app.apkPath ?: "不可读", mono = true)
                }
            },
            confirmButton = {
                Row {
                    Text(
                        "提取 APK",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val path = app.apkPath
                                if (path == null) {
                                    toast(context, "无法读取安装包")
                                } else {
                                    scope.launch {
                                        val dest = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val dir = File("/storage/emulated/0/Download/提取的应用")
                                                dir.mkdirs()
                                                val out = File(dir, "${app.label}-${app.version}.apk")
                                                File(path).copyTo(out, overwrite = true)
                                                out
                                            }.getOrNull()
                                        }
                                        toast(context, if (dest != null) "已提取到 ${dest.absolutePath}" else "提取失败")
                                    }
                                }
                                detail = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        "应用信息",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.pkg}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                detail = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        "打开",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val ok = runCatching {
                                    context.packageManager.getLaunchIntentForPackage(app.pkg)
                                }.getOrNull()
                                if (ok != null) {
                                    ok.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(ok)
                                } else {
                                    toast(context, "该应用没有启动入口")
                                }
                                detail = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                Text(
                    "关闭",
                    color = SunColors.TextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { detail = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }
}

private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 33) {
        PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
    } else null
    val infos = runCatching {
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(flags!!)
        else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_META_DATA)
    }.getOrDefault(emptyList())

    return infos.mapNotNull { pi ->
        val ai: ApplicationInfo = pi.applicationInfo ?: return@mapNotNull null
        val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pi.packageName)
        val size = runCatching { File(ai.sourceDir).length() }.getOrDefault(0L)
        val icon = runCatching {
            ai.loadIcon(pm).toBitmap(96, 96)
        }.getOrNull()
        AppItem(
            label = label,
            pkg = pi.packageName,
            version = pi.versionName ?: "-",
            size = size,
            system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            icon = icon,
            apkPath = ai.sourceDir
        )
    }.sortedBy { it.label.lowercase() }
}



// ==================== 远程管理 ====================

@Composable
fun RemotePage(onBack: () -> Unit, defaultRoot: String) {
    val context = LocalContext.current
    var rootPath by remember { mutableStateOf(defaultRoot) }
    var portText by remember { mutableStateOf("8080") }
    val running by RemoteServer.runningFlow.collectAsState()
    val logs by RemoteServer.logs.collectAsState()
    val addresses = remember(running) { RemoteServer.lanAddresses() }
    var copied by remember { mutableStateOf("") }

    ToolScaffold(
        title = "远程管理",
        subtitle = if (running) "运行中 · 端口 ${RemoteServer.port}" else "局域网 HTTP 文件服务",
        onBack = onBack
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "在同一 WiFi 的电脑或手机浏览器里打开下面地址，即可浏览、下载、上传文件。",
                        color = SunColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("共享目录", color = SunColors.TextTertiary, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    SunField(rootPath, { rootPath = it }, "/storage/emulated/0", mono = true)
                    Spacer(Modifier.height(10.dp))
                    Text("端口", color = SunColors.TextTertiary, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    SunField(portText, { portText = it.filter { c -> c.isDigit() } }, "8080")
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        if (running) "停止服务" else "启动服务",
                        modifier = Modifier.fillMaxWidth(),
                        color = if (running) SunColors.Danger else SunColors.Accent
                    ) {
                        if (running) {
                            RemoteServer.stop()
                        } else {
                            val dir = File(rootPath)
                            if (!dir.isDirectory) {
                                toast(context, "目录不存在")
                            } else {
                                val r = RemoteServer.start(dir, portText.toIntOrNull() ?: 8080)
                                toast(context, r.getOrDefault("启动失败"))
                            }
                        }
                    }
                }
            }
            if (running) {
                item {
                    SectionCaption("访问地址")
                    if (addresses.isEmpty()) {
                        EmptyState("没有检测到局域网地址", "请先连接 WiFi 或开启热点")
                    } else {
                        addresses.forEach { ip ->
                            val url = "http://$ip:${RemoteServer.port}/"
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                                        copied = url
                                        toast(context, "已复制")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(url, color = SunColors.Accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        "点击复制",
                                        color = SunColors.TextTertiary,
                                        fontSize = 9.sp
                                    )
                                }
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    null,
                                    tint = SunColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionCaption("运行日志")
                if (logs.isEmpty()) {
                    EmptyState("暂无日志")
                } else {
                    logs.take(40).forEach { line ->
                        Text(
                            line,
                            color = SunColors.TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 音乐播放器 ====================

/** 极简后台播放器：应用内共享一个 MediaPlayer 实例 */
object AudioEngine {
    private var player: MediaPlayer? = null
    val current = mutableStateOf<File?>(null)
    val playing = mutableStateOf(false)
    val position = mutableStateOf(0)
    val duration = mutableStateOf(0)

    fun play(file: File, playlist: List<File>) {
        stop()
        val p = MediaPlayer()
        runCatching {
            p.setDataSource(file.absolutePath)
            p.setOnPreparedListener {
                it.start()
                playing.value = true
                duration.value = it.duration
            }
            p.setOnCompletionListener {
                playing.value = false
                val idx = playlist.indexOfFirst { f -> f.absolutePath == current.value?.absolutePath }
                val next = playlist.getOrNull(idx + 1) ?: playlist.firstOrNull()
                if (next != null) play(next, playlist)
            }
            p.prepareAsync()
        }
        player = p
        current.value = file
        position.value = 0
        duration.value = 0
    }

    fun toggle() {
        val p = player ?: return
        runCatching {
            if (p.isPlaying) {
                p.pause(); playing.value = false
            } else {
                p.start(); playing.value = true
            }
        }
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
        playing.value = false
    }

    /** 把播放器的真实进度同步到状态，进度条才会走 */
    fun refreshPosition() {
        val p = player ?: return
        runCatching {
            position.value = p.currentPosition
            val d = p.duration
            if (d > 0) duration.value = d
        }
    }

    fun seekTo(ms: Int) {
        runCatching { player?.seekTo(ms) }
        position.value = ms
    }
}

@Composable
fun MusicPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val tracks by produceState(initialValue = emptyList<File>()) {
        value = withContext(Dispatchers.IO) { scanAudio() }
    }
    val currentFile by AudioEngine.current
    val isPlaying by AudioEngine.playing
    LaunchedEffect(isPlaying, currentFile) {
        while (isPlaying) {
            AudioEngine.refreshPosition()
            delay(400)
        }
    }

    val shown = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter { it.name.contains(query, true) || it.parentFile?.name?.contains(query, true) == true }
    }

    ToolScaffold(
        title = "音乐播放器",
        subtitle = "${tracks.size} 首本地音频",
        onBack = onBack,
        actions = {
            if (currentFile != null) {
                BarIcon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "播放/暂停") {
                    AudioEngine.toggle()
                }
                BarIcon(Icons.Outlined.Stop, "停止") { AudioEngine.stop() }
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SunField(query, { query = it }, "搜索曲名或文件夹")
        }
        Hairline()
        if (tracks.isEmpty()) {
            EmptyState("没有找到音频", "把音乐放到 /storage/emulated/0/Music 再回来")
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(shown, key = { it.absolutePath }) { f ->
                    val active = currentFile?.absolutePath == f.absolutePath
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (active) SunColors.AccentSoft else Color.Transparent)
                            .clickable { AudioEngine.play(f, shown) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (active && isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            null,
                            tint = if (active) SunColors.Accent else SunColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.nameWithoutExtension,
                                color = if (active) SunColors.Accent else SunColors.TextPrimary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                f.parentFile?.name ?: "",
                                color = SunColors.TextTertiary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        Text(Fmt.size(f.length()), color = SunColors.TextTertiary, style = NumberStyle)
                    }
                }
            }
        }
        currentFile?.let { f ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .topBarBackground()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    f.nameWithoutExtension,
                    color = SunColors.OnTopBar,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        val d = AudioEngine.duration.value
                        if (d > 0) AudioEngine.position.value.toFloat() / d else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = SunColors.Accent,
                    trackColor = SunColors.Surface3
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        fmtTime(AudioEngine.position.value),
                        color = SunColors.OnTopBarMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        fmtTime(AudioEngine.duration.value),
                        color = SunColors.OnTopBarMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun fmtTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun scanAudio(): List<File> {
    val roots = listOf(File("/storage/emulated/0/Music"), File("/storage/emulated/0/Download"))
    val out = ArrayList<File>()
    fun walk(dir: File, depth: Int) {
        if (depth > 4) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (f.isDirectory) walk(f, depth + 1)
            else if (f.extension.lowercase() in setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")) out.add(f)
        }
    }
    roots.forEach { walk(it, 0) }
    return out.sortedBy { it.name.lowercase() }
}
