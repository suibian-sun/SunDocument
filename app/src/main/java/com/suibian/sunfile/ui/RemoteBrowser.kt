package com.suibian.sunfile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.data.NetworkMount
import com.suibian.sunfile.data.PrefsStore
import com.suibian.sunfile.data.remote.RemoteEntry
import com.suibian.sunfile.data.remote.RemoteFs
import com.suibian.sunfile.data.remote.RemoteFsFactory
import com.suibian.sunfile.data.remote.parentOf
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 远程存储浏览页。
 *
 * 六种协议都走 [RemoteFs]，这里只做一件事：把目录变成可点的列表。
 * 缩略图受设置里「网络缩略图 / 仅 Wi-Fi / 单图上限 / 超时」四项控制，
 * 不满足条件就只显示图标，不会为了好看把流量烧光。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteBrowserPage(
    mount: NetworkMount,
    onBack: () -> Unit,
    onOpenLocalPath: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = PrefsStore.get(context).prefs.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()

    var fs by remember { mutableStateOf<RemoteFs?>(null) }
    var connecting by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    val busy = remember { MutableStateFlow<String?>(null) }
    val progress = remember { MutableStateFlow(-1f) }
    val busyText by busy.collectAsStateWithLifecycle()
    val progressValue by progress.collectAsStateWithLifecycle()

    // 动作对话框
    var target by remember { mutableStateOf<RemoteEntry?>(null) }
    var showAction by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    val currentPath by rememberUpdatedState(path)
    val currentFs by rememberUpdatedState(fs)

    fun report(e: Throwable) {
        error = e.message ?: "操作失败"
    }

    LaunchedEffect(mount.uri, reloadTick) {
        connecting = true
        error = null
        val r = withContext(Dispatchers.IO) {
            runCatching { RemoteFsFactory.open(mount, prefs.networkTimeoutSec) }
        }
        r.onSuccess {
            runCatching { fs?.close() }
            fs = it
            path = it.rootPath
            connecting = false
        }.onFailure {
            fs = null
            connecting = false
            error = it.message ?: "连接失败"
        }
    }

    LaunchedEffect(path, fs) {
        val f = fs ?: return@LaunchedEffect
        loading = true
        val r = withContext(Dispatchers.IO) { runCatching { f.list(path) } }
        r.onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取目录失败"; entries = emptyList() }
        loading = false
    }

    fun reload() { reloadTick++ }

    fun openRemote(entry: RemoteEntry) {
        if (busy.value != null) return
        scope.launch {
            busy.value = "正在下载 ${entry.name}"
            progress.value = 0f
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "remote").apply { mkdirs() }
                    val out = uniqueFile(dir, entry.name)
                    currentFs!!.openInput(entry.path).use { input ->
                        out.outputStream().use { o -> copyWithProgress(input, o, entry.size, progress) }
                    }
                    out
                }
            }
            busy.value = null
            progress.value = -1f
            r.onSuccess { file ->
                if (!openExternally(context, file)) toast(context, "没有能打开该文件的应用")
            }.onFailure { report(it) }
        }
    }

    fun download(entry: RemoteEntry) {
        if (busy.value != null) return
        scope.launch {
            busy.value = "正在下载 ${entry.name}"
            progress.value = 0f
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val base = File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ),
                        "SunDocument/${mount.name}"
                    ).apply { mkdirs() }
                    val out = uniqueFile(base, entry.name)
                    currentFs!!.openInput(entry.path).use { input ->
                        out.outputStream().use { o -> copyWithProgress(input, o, entry.size, progress) }
                    }
                    out
                }
            }
            busy.value = null
            progress.value = -1f
            r.onSuccess { toast(context, "已保存到 ${it.absolutePath}") }.onFailure { report(it) }
        }
    }

    fun uploadAll(uris: List<Uri>) {
        val f = fs ?: return
        if (uris.isEmpty() || busy.value != null) return
        scope.launch {
            busy.value = "正在上传 ${uris.size} 个文件"
            progress.value = 0f
            var okCount = 0
            val errors = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for ((i, uri) in uris.withIndex()) {
                    val name = queryName(context, uri) ?: "upload_${i + 1}.bin"
                    busy.value = "正在上传 $name（${i + 1}/${uris.size}）"
                    val r = runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("无法读取所选文件")
                        val total = runCatching {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                        }.getOrDefault(-1L)
                        input.use { src ->
                            f.openOutput("${currentPath.trimEnd('/')}/$name").use { dst ->
                                copyWithProgress(src, dst, total, progress)
                            }
                        }
                    }
                    if (r.isSuccess) okCount++ else errors.add(r.exceptionOrNull()?.message ?: name)
                }
            }
            busy.value = null
            progress.value = -1f
            if (okCount > 0) {
                toast(context, "已上传 $okCount/${uris.size}")
                reload()
            }
            if (errors.isNotEmpty()) error = errors.first()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uploadAll(uris) }

    // ---------------- 界面 ----------------

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.TopBar)
                .height(Mt.BarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    mount.name,
                    color = SunColors.OnTopBar,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (connecting) "连接中…" else path,
                    color = SunColors.OnTopBarMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                enabled = fs != null && busyText == null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Outlined.Upload, "上传", tint = SunColors.OnTopBar, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = { reload() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Refresh, "刷新", tint = SunColors.OnTopBar, modifier = Modifier.size(19.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.MoreVert, "更多", tint = SunColors.OnTopBar, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("新建文件夹", color = SunColors.TextPrimary, fontSize = 12.5.sp) },
                        onClick = { menu = false; showNewFolder = true }
                    )
                    DropdownMenuItem(
                        text = { Text("回到根目录", color = SunColors.TextPrimary, fontSize = 12.5.sp) },
                        onClick = { menu = false; path = fs?.rootPath ?: "/" }
                    )
                    DropdownMenuItem(
                        text = { Text("重新连接", color = SunColors.TextPrimary, fontSize = 12.5.sp) },
                        onClick = { menu = false; runCatching { fs?.close() }; fs = null; reload() }
                    )
                    DropdownMenuItem(
                        text = { Text("连接信息", color = SunColors.TextPrimary, fontSize = 12.5.sp) },
                        onClick = { menu = false; showInfo = true }
                    )
                }
            }
        }

        busyText?.let { msg ->
            Column(Modifier.fillMaxWidth().background(SunColors.Surface2).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(msg, color = SunColors.TextSecondary, fontSize = 10.5.sp, maxLines = 1)
                if (progressValue >= 0f) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = SunColors.Accent,
                        trackColor = SunColors.Surface3
                    )
                }
            }
        }

        error?.let { msg ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SunColors.Danger.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(msg, color = SunColors.Danger, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                Text(
                    "重试",
                    color = SunColors.Danger,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { reload() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (connecting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = SunColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(mount.kind.label, color = SunColors.TextTertiary, fontSize = 10.5.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                val root = fs?.rootPath ?: "/"
                if (path != "/" && path != root) {
                    item {
                        RemoteRow(
                            icon = {
                                Icon(
                                    Icons.Outlined.Folder,
                                    null,
                                    tint = SunColors.TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            name = "..",
                            subtitle = "上一级",
                            onClick = { path = parentOf(path) }
                        )
                    }
                }
                items(entries, key = { it.path }) { e ->
                    RemoteRow(
                        icon = { entryIcon(e) },
                        name = e.name,
                        subtitle = if (e.isDir) "文件夹" else humanSize(e.size),
                        thumb = if (shouldThumb(e, prefs, context)) e else null,
                        fs = fs,
                        onClick = { if (e.isDir) path = e.path else openRemote(e) },
                        onLongClick = {
                            target = e
                            renameDraft = e.name
                            renaming = false
                            showAction = true
                        }
                    )
                }
                if (!loading && entries.isEmpty()) {
                    item {
                        Text(
                            "空目录",
                            color = SunColors.TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(30.dp)
                        )
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    // ---------------- 对话框 ----------------

    if (showNewFolder) {
        TextPromptDialog(
            title = "新建文件夹",
            initial = "",
            hint = "文件夹名称",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                val f = fs
                if (f != null) {
                    scope.launch {
                        busy.value = "正在创建 $name"
                        val r = withContext(Dispatchers.IO) { runCatching { f.mkdir("$path/$name") } }
                        busy.value = null
                        r.onSuccess { reload() }.onFailure { report(it) }
                    }
                }
            }
        )
    }

    val e = target
    if (showAction && e != null) {
        RemoteActionSheet(
            entry = e,
            renaming = renaming,
            draft = renameDraft,
            onDraft = { renameDraft = it },
            onStartRename = { renaming = true },
            onRename = { newName ->
                showAction = false
                val f = fs
                if (f != null) {
                    scope.launch {
                        busy.value = "正在重命名"
                        val r = withContext(Dispatchers.IO) {
                            runCatching { f.rename(e.path, "${parentOf(e.path).trimEnd('/')}/$newName") }
                        }
                        busy.value = null
                        target = null
                        r.onSuccess { reload() }.onFailure { report(it) }
                    }
                }
            },
            onDownload = {
                showAction = false
                if (!e.isDir) download(e)
            },
            onDelete = {
                showAction = false
                showDelete = true
            },
            onDismiss = { showAction = false; target = null }
        )
    }

    if (showDelete && e != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false; target = null },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = { Text("删除", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    if (e.isDir) "删除文件夹「${e.name}」及其中全部内容？" else "删除「${e.name}」？",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Text(
                    "删除",
                    color = SunColors.Danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable {
                            showDelete = false
                            val f = fs
                            if (f != null) {
                                scope.launch {
                                    busy.value = "正在删除 ${e.name}"
                                    val r = withContext(Dispatchers.IO) {
                                        runCatching { f.delete(e.path, e.isDir) }
                                    }
                                    busy.value = null
                                    target = null
                                    r.onSuccess { reload() }.onFailure { report(it) }
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    color = SunColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { showDelete = false; target = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = { Text(mount.name, color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column {
                    Text(mount.uri, color = SunColors.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${mount.kind.label} · 分组 ${mount.group}\n" +
                            "超时 ${prefs.networkTimeoutSec}s · 缩略图${if (prefs.networkThumbs) "开" else "关"}" +
                            " · 单图上限 ${prefs.networkImageLimitKb}KB",
                        color = SunColors.TextTertiary,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Text(
                    "关闭",
                    color = SunColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { showInfo = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }
}

// ---------------- 列表行 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteRow(
    icon: @Composable () -> Unit,
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    thumb: RemoteEntry? = null,
    fs: RemoteFs? = null
) {
    var image by remember(thumb?.path) { mutableStateOf<ImageBitmap?>(null) }
    if (thumb != null && fs != null) {
        LaunchedEffect(thumb.path) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    fs.openInput(thumb.path).use { input ->
                        val cap = minOf(thumb.size, 1024L * 1024L).toInt().coerceAtLeast(1)
                        val buf = ByteArray(cap)
                        var off = 0
                        while (off < cap) {
                            val n = input.read(buf, off, cap - off)
                            if (n < 0) break
                            off += n
                        }
                        android.graphics.BitmapFactory.decodeByteArray(buf, 0, off)
                    }
                }.getOrNull()
            }
            image = bmp?.asImageBitmap()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SunColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            val img = image
            if (img != null) {
                androidx.compose.foundation.Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                icon()
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = SunColors.TextPrimary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = SunColors.TextTertiary, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun entryIcon(e: RemoteEntry) {
    when {
        e.isDir -> Icon(Icons.Outlined.Folder, null, tint = SunColors.Folder, modifier = Modifier.size(20.dp))
        isImageName(e.name) -> Icon(Icons.Outlined.Image, null, tint = SunColors.Accent, modifier = Modifier.size(19.dp))
        else -> Icon(Icons.Outlined.Description, null, tint = SunColors.TextSecondary, modifier = Modifier.size(19.dp))
    }
}

// ---------------- 对话框组件 ----------------

@Composable
private fun TextPromptDialog(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface2,
        shape = RoundedCornerShape(14.dp),
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 15.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint, color = SunColors.TextTertiary, fontSize = 12.sp) },
                singleLine = true,
                textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.sp)
            )
        },
        confirmButton = {
            Text(
                "确定",
                color = if (text.isBlank()) SunColors.TextTertiary else SunColors.Accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable(enabled = text.isNotBlank()) { onConfirm(text.trim()) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = SunColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    )
}

@Composable
private fun RemoteActionSheet(
    entry: RemoteEntry,
    renaming: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onStartRename: () -> Unit,
    onRename: (String) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface2,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(entry.name, color = SunColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                Text(
                    if (entry.isDir) "文件夹" else humanSize(entry.size),
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                if (renaming) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraft,
                        singleLine = true,
                        textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "保存",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable(enabled = draft.isNotBlank() && draft != entry.name) {
                                onRename(draft.trim())
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                } else {
                    ActionLine("重命名", SunColors.TextPrimary, onStartRename)
                    if (!entry.isDir) ActionLine("下载到 Download", SunColors.TextPrimary, onDownload)
                    ActionLine("删除", SunColors.Danger, onDelete)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                "关闭",
                color = SunColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    )
}

@Composable
private fun ActionLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label == "删除") {
            Icon(Icons.Outlined.Delete, null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(label, color = color, fontSize = 13.sp)
    }
}

// ---------------- 工具 ----------------

private fun copyWithProgress(
    input: java.io.InputStream,
    output: java.io.OutputStream,
    total: Long,
    progress: MutableStateFlow<Float>
) {
    val buf = ByteArray(64 * 1024)
    var done = 0L
    var lastPct = -1
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        output.write(buf, 0, n)
        done += n
        if (total > 0) {
            val pct = ((done * 100) / total).toInt()
            if (pct != lastPct) {
                lastPct = pct
                progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
            }
        }
    }
    output.flush()
}

private fun shouldThumb(e: RemoteEntry, prefs: AppPrefs, context: Context): Boolean {
    if (!prefs.networkThumbs || e.isDir) return false
    if (!isImageName(e.name)) return false
    if (e.size <= 0 || e.size > prefs.networkImageLimitKb.toLong() * 1024L) return false
    if (prefs.networkWifiOnly && !onWifi(context)) return false
    return true
}

private fun onWifi(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}.getOrDefault(false)

private fun isImageName(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif")
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

private fun uniqueFile(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "")
    var i = 1
    while (f.exists()) {
        f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
        i++
    }
    return f
}

private fun queryName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
}.getOrNull() ?: uri.lastPathSegment
