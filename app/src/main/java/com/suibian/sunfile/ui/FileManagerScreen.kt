package com.suibian.sunfile.ui

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suibian.sunfile.BuildConfig
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.update.UpdateViewModel
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.FileManagerViewModel
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun FileManagerScreen(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    vm: FileManagerViewModel = viewModel(),
    updateVm: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val panes by vm.panes.collectAsStateWithLifecycle()
    val prefs by vm.prefsStore.prefs.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val updateTick by vm.updateTick.collectAsStateWithLifecycle()
    val updateState by updateVm.state.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }

    var activePane by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var newFolderPane by remember { mutableStateOf<Int?>(null) }
    var newFilePane by remember { mutableStateOf<Int?>(null) }
    var pathPane by remember { mutableStateOf<Int?>(null) }
    var propsFile by remember { mutableStateOf<File?>(null) }
    var deletePaths by remember { mutableStateOf<List<String>?>(null) }
    var actionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var actionEntryPane by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    LaunchedEffect(hasAccess) {
        if (!vm.isInitialized) vm.init(hasAccess) else if (hasAccess) vm.refreshAll()
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    LaunchedEffect(updateTick) {
        if (updateTick > 0) updateVm.check(silent = false, useAccelerator = prefs.useAccelerator)
    }

    LaunchedEffect(Unit) {
        if (prefs.autoCheckUpdate) {
            delay(1500)
            updateVm.check(silent = true, useAccelerator = prefs.useAccelerator)
        }
    }

    val active = panes.getOrNull(activePane) ?: panes[0]
    val selectedCount = active.selected.size
    val singleSelected: File? = if (selectedCount == 1) File(active.selected.first()) else null

    Box(Modifier.fillMaxSize().background(SunColors.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(SunColors.Panel)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SunFile", color = SunColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    " 双栏文件管理器 v${BuildConfig.VERSION_NAME}",
                    color = SunColors.TextDim,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.refreshAll() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, "刷新", tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.SystemUpdate, "检查更新", tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, "菜单", tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (prefs.showHidden) "隐藏隐藏文件" else "显示隐藏文件", fontSize = 13.sp) },
                        leadingIcon = {
                            if (prefs.showHidden) Icon(Icons.Default.Check, null, tint = SunColors.Accent, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.size(16.dp))
                        },
                        onClick = { menuOpen = false; vm.toggleHidden() }
                    )
                    DropdownMenuItem(
                        text = { Text("排序方式：${prefs.sort.mode.label}${if (prefs.sort.ascending) " ↑" else " ↓"}", fontSize = 13.sp) },
                        onClick = { menuOpen = false; sortOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (prefs.useAccelerator) "更新加速：已开启" else "更新加速：已关闭", fontSize = 13.sp) },
                        leadingIcon = {
                            if (prefs.useAccelerator) Icon(Icons.Default.Check, null, tint = SunColors.Accent, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.size(16.dp))
                        },
                        onClick = { menuOpen = false; vm.toggleAccelerator() }
                    )
                    DropdownMenuItem(
                        text = { Text("启动时检查更新", fontSize = 13.sp) },
                        leadingIcon = {
                            if (prefs.autoCheckUpdate) Icon(Icons.Default.Check, null, tint = SunColors.Accent, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.size(16.dp))
                        },
                        onClick = { menuOpen = false; vm.toggleAutoCheck() }
                    )
                    DropdownMenuItem(
                        text = { Text("关于 SunFile", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = SunColors.TextSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            menuOpen = false
                            Toast.makeText(context, "SunFile ${BuildConfig.VERSION_NAME} · 开源双栏文件管理器", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 权限提示条
            if (!hasAccess) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3A2A12))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "未获得「所有文件访问」权限，部分目录不可读写",
                        color = Color(0xFFE3B341),
                        fontSize = 10.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "去授权",
                        color = SunColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRequestAccess() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 双栏主体
            Row(Modifier.weight(1f).fillMaxWidth()) {
                PanePanel(
                    index = 0,
                    state = panes[0],
                    onUp = { activePane = 0; vm.navigateUp(0) },
                    onRefresh = { activePane = 0; vm.refresh(0) },
                    onPathClick = { activePane = 0; pathPane = 0 },
                    onEntryClick = { entry -> activePane = 0; onEntryClick(vm, 0, entry, context) },
                    onEntryLongClick = { entry -> activePane = 0; vm.toggleSelect(0, entry) },
                    onEntryMenu = { entry -> activePane = 0; actionEntryPane = 0; actionEntry = entry },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(SunColors.Divider))
                PanePanel(
                    index = 1,
                    state = panes[1],
                    onUp = { activePane = 1; vm.navigateUp(1) },
                    onRefresh = { activePane = 1; vm.refresh(1) },
                    onPathClick = { activePane = 1; pathPane = 1 },
                    onEntryClick = { entry -> activePane = 1; onEntryClick(vm, 1, entry, context) },
                    onEntryLongClick = { entry -> activePane = 1; vm.toggleSelect(1, entry) },
                    onEntryMenu = { entry -> activePane = 1; actionEntryPane = 1; actionEntry = entry },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            // 底部操作栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(SunColors.Panel)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(Icons.Default.ContentCopy, "复制", enabled = selectedCount > 0) {
                    vm.setClipboard(activePane, false)
                }
                ActionButton(Icons.Default.ContentCut, "剪切", enabled = selectedCount > 0) {
                    vm.setClipboard(activePane, true)
                }
                ActionButton(Icons.Default.ContentPaste, "粘贴", enabled = !clipboard.isEmpty) {
                    vm.paste(activePane)
                }
                ActionButton(Icons.Default.Delete, "删除", enabled = selectedCount > 0, danger = true) {
                    deletePaths = active.selected.toList()
                }
                ActionButton(Icons.Default.CreateNewFolder, "新建夹", enabled = active.path.isNotEmpty()) {
                    newFolderPane = activePane
                }
                ActionButton(Icons.Default.NoteAdd, "新建文件", enabled = active.path.isNotEmpty()) {
                    newFilePane = activePane
                }
                ActionButton(Icons.Default.DriveFileRenameOutline, "重命名", enabled = singleSelected != null) {
                    renameTarget = singleSelected
                }
                ActionButton(Icons.Default.Info, "属性", enabled = singleSelected != null) {
                    propsFile = singleSelected
                }
                ActionButton(Icons.Default.SwapHoriz, "移到对栏", enabled = selectedCount > 0) {
                    vm.transfer(activePane, 1 - activePane, cut = true)
                }
                ActionButton(
                    Icons.Default.SelectAll,
                    if (active.selectionMode) "取消" else "全选",
                    enabled = active.entries.isNotEmpty()
                ) {
                    if (active.selectionMode) vm.clearSelection(activePane) else vm.selectAll(activePane)
                }
                ActionButton(Icons.Default.Refresh, "刷新", enabled = true) {
                    vm.refresh(activePane)
                }

            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp, start = 12.dp, end = 12.dp)
        )

        busy?.let { b ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 74.dp)
                    .background(SunColors.PanelAlt, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(b.title, color = SunColors.TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    Text("${(b.progress * 100).toInt()}%", color = SunColors.Accent, fontSize = 11.5.sp)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = SunColors.Accent,
                    trackColor = SunColors.Divider
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (b.currentFile.isNotEmpty()) b.currentFile else "正在统计…",
                    color = SunColors.TextDim,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // ------- 各类对话框 -------

    renameTarget?.let { f ->
        InputDialog(
            title = "重命名",
            initial = f.name,
            onConfirm = { name ->
                val target = f
                renameTarget = null
                vm.rename(activePane, target.absolutePath, name)
            },
            onDismiss = { renameTarget = null }
        )
    }

    newFolderPane?.let { pane ->
        InputDialog(
            title = "新建文件夹",
            initial = "",
            hint = "文件夹名称",
            onConfirm = { name ->
                newFolderPane = null
                vm.createFolder(pane, name)
            },
            onDismiss = { newFolderPane = null }
        )
    }

    newFilePane?.let { pane ->
        InputDialog(
            title = "新建文件",
            initial = "",
            hint = "例如 note.txt",
            onConfirm = { name ->
                newFilePane = null
                vm.createFile(pane, name)
            },
            onDismiss = { newFilePane = null }
        )
    }

    pathPane?.let { pane ->
        PathDialog(
            current = panes[pane].path,
            onGo = { path ->
                pathPane = null
                vm.navigate(pane, path)
            },
            onDismiss = { pathPane = null }
        )
    }

    propsFile?.let { f ->
        PropertiesDialog(
            file = f,
            loader = { target -> vm.propertiesOf(target) },
            onDismiss = { propsFile = null }
        )
    }

    deletePaths?.let { paths ->
        ConfirmDialog(
            title = "删除确认",
            message = "将永久删除 ${paths.size} 项：\n${paths.take(3).joinToString("\n") { File(it).name }}" +
                if (paths.size > 3) "\n…" else "",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                deletePaths = null
                vm.deleteSelected(activePane)
            },
            onDismiss = { deletePaths = null }
        )
    }

    sortOpen.let {
        if (it) {
            AlertDialogSort(
                current = prefs.sort.mode,
                ascending = prefs.sort.ascending,
                onPick = { mode -> sortOpen = false; vm.setSort(mode) },
                onDismiss = { sortOpen = false }
            )
        }
    }

    actionEntry?.let { entry ->
        ConfirmActionDialog(
            entry = entry,
            onOpen = {
                actionEntry = null
                onEntryClick(vm, actionEntryPane, entry, context)
            },
            onRename = {
                actionEntry = null
                renameTarget = entry.file
            },
            onProps = {
                actionEntry = null
                propsFile = entry.file
            },
            onDelete = {
                actionEntry = null
                vm.clearSelection(actionEntryPane)
                vm.toggleSelect(actionEntryPane, entry)
                deletePaths = listOf(entry.path)
            },
            onCopyPath = {
                actionEntry = null
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("path", entry.path))
                Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { actionEntry = null }
        )
    }

    if (updateState !is com.suibian.sunfile.update.UpdateState.Idle) {
        val st = updateState
        val isSilentChecking = st is com.suibian.sunfile.update.UpdateState.Checking && st.silent
        if (!isSilentChecking) {
            UpdateDialog(
                state = st,
                currentVersion = BuildConfig.VERSION_NAME,
                onDownload = { info -> updateVm.download(info, prefs.useAccelerator) },
                onInstall = { apk -> updateVm.install(apk) },
                onOpenPermission = { updateVm.openPermissionSettings() },
                onRetry = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                onDismiss = { updateVm.dismiss() }
            )
        }
    }
}

private fun onEntryClick(vm: FileManagerViewModel, pane: Int, entry: FileEntry, context: Context) {
    val state = vm.pane(pane)
    if (state.selectionMode) {
        vm.toggleSelect(pane, entry)
        return
    }
    if (entry.isDir) {
        vm.navigate(pane, entry.path)
    } else {
        openFile(context, entry.file)
    }
}

private fun openFile(context: Context, file: File) {
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> SunColors.TextDim
        danger -> SunColors.Danger
        else -> SunColors.TextSecondary
    }
    Column(
        Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun AlertDialogSort(
    current: SortMode,
    ascending: Boolean,
    onPick: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Panel,
        shape = RoundedCornerShape(14.dp),
        title = { Text("排序方式", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(mode) }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = if (mode == current) SunColors.Accent else Color.Transparent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            mode.label,
                            color = if (mode == current) SunColors.Accent else SunColors.TextPrimary,
                            fontSize = 13.sp
                        )
                        if (mode == current) {
                            Spacer(Modifier.weight(1f))
                            Text(if (ascending) "升序" else "降序", color = SunColors.TextDim, fontSize = 10.sp)
                        }
                    }
                }
                Text(
                    "重复点击同一项可切换升序 / 降序",
                    color = SunColors.TextDim,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("关闭", color = SunColors.Accent, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun ConfirmActionDialog(
    entry: FileEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onProps: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Panel,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                entry.name,
                color = SunColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    "${if (entry.isDir) "文件夹" else Fmt.size(entry.size)} · ${Fmt.time(entry.lastModified)}",
                    color = SunColors.TextDim,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "打开" to onOpen,
                    "重命名" to onRename,
                    "属性" to onProps,
                    "复制路径" to onCopyPath,
                    "删除" to onDelete
                ).forEach { (label, action) ->
                    Text(
                        label,
                        color = if (label == "删除") SunColors.Danger else SunColors.TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { action() }
                            .padding(vertical = 11.dp, horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}
