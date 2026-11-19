package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suibian.sunfile.BuildConfig
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.components.HamburgerIcon
import com.suibian.sunfile.ui.components.RollingNumber
import com.suibian.sunfile.ui.components.ShimmerList
import com.suibian.sunfile.ui.components.pressScale
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.update.UpdateState
import com.suibian.sunfile.update.UpdateViewModel
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.EditorViewModel
import com.suibian.sunfile.vm.FileManagerViewModel
import com.suibian.sunfile.vm.PaneState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

sealed interface Page {
    data object Files : Page
    data object Search : Page
    data object Settings : Page
}

@Composable
fun MainScreen(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    vm: FileManagerViewModel = viewModel(),
    updateVm: UpdateViewModel = viewModel(),
    editorVm: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val panes by vm.panes.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val prefs by vm.prefsStore.prefs.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val searchState by vm.search.collectAsStateWithLifecycle()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val updateTick by vm.updateTick.collectAsStateWithLifecycle()

    val pane = panes[active]
    val snackbarHost = remember { SnackbarHostState() }

    var page by remember { mutableStateOf<Page>(Page.Files) }
    var overflowOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var editorTarget by remember { mutableStateOf<File?>(null) }
    var editorOrigin by remember { mutableStateOf(Offset.Zero) }
    var openFailure by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) { vm.init() }

    LaunchedEffect(updateTick) {
        if (updateTick > 0) updateVm.check(silent = false, useAccelerator = prefs.useAccelerator)
    }

    LaunchedEffect(Unit) {
        if (prefs.autoCheckUpdate) {
            delay(1600)
            updateVm.check(silent = true, useAccelerator = prefs.useAccelerator)
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        editorVm.toast.collect { snackbarHost.showSnackbar(it) }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = drawerState.isClosed && page != Page.Files) { page = Page.Files }
    BackHandler(enabled = drawerState.isClosed && page == Page.Files && pane.selectionMode) {
        vm.clearSelection()
    }

    fun openEntry(entry: FileEntry, origin: Offset) {
        if (entry.isDir) {
            vm.navigate(entry.path)
            return
        }
        if (FileRepo.looksLikeText(entry.file)) {
            editorOrigin = origin
            editorTarget = entry.file
        } else if (!openExternally(context, entry.file)) {
            openFailure = entry.file
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            DrawerContent(
                currentPath = pane.path,
                version = BuildConfig.VERSION_NAME,
                storageUsed = storage?.used ?: 0L,
                storageTotal = storage?.total ?: 0L,
                onNavigate = { path ->
                    vm.navigate(path)
                    page = Page.Files
                    scope.launch { drawerState.close() }
                },
                onSettings = { page = Page.Settings; scope.launch { drawerState.close() } },
                onCheckUpdate = {
                    updateVm.check(silent = false, useAccelerator = prefs.useAccelerator)
                    scope.launch { drawerState.close() }
                },
                onAbout = {
                    dialog = DialogKind.About
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize().background(SunColors.Bg)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (targetState != Page.Files) {
                        (slideInHorizontally(tween(Motion.Slow, easing = Motion.EaseOut)) { it / 8 } + fadeIn(tween(Motion.Base))) togetherWith
                            fadeOut(tween(Motion.Fast))
                    } else {
                        fadeIn(tween(Motion.Base)) togetherWith
                            (slideOutHorizontally(tween(Motion.Slow, easing = Motion.EaseIn)) { it / 8 } + fadeOut(tween(Motion.Base)))
                    }
                },
                label = "page"
            ) { target ->
                when (target) {
                    Page.Files -> FilesPage(
                        pane = pane,
                        panes = panes,
                        active = active,
                        hasAccess = hasAccess,
                        storageUsed = storage?.used ?: 0L,
                        storageTotal = storage?.total ?: 0L,
                        clipboardCount = clipboard.paths.size,
                        onRequestAccess = onRequestAccess,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        overflowOpen = overflowOpen,
                        showHidden = prefs.showHidden,
                        drawerOpen = drawerState.isOpen,
                        onOverflowChange = { overflowOpen = it },
                        onRefresh = { vm.refresh() },
                        onSearch = { vm.openSearch(); page = Page.Search },
                        onSort = { sheet = SheetKind.Sort },
                        onSelectAll = { vm.selectAll() },
                        onToggleHidden = { vm.toggleHidden() },
                        onSwap = { vm.swapPanes() },
                        onSettings = { page = Page.Settings },
                        onCheckUpdate = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                        onPaste = { vm.paste() },
                        onProperties = {
                            pane.selected.firstOrNull()?.let { dialog = DialogKind.Properties(File(it)) }
                        },
                        onPathClick = { sheet = SheetKind.Path },
                        onOpenEntry = { entry, origin -> openEntry(entry, origin) },
                        onLongPress = { entry -> vm.toggleSelect(entry) },
                        onEntryMore = { sheet = SheetKind.Entry(it) },
                        onBack = { vm.goBack() },
                        onForward = { vm.goForward() },
                        onUp = { vm.goUp() },
                        onNew = { sheet = SheetKind.New },
                        onSelectionCopy = { vm.setClipboard(false) },
                        onSelectionCut = { vm.setClipboard(true) },
                        onSelectionDelete = { dialog = DialogKind.Delete(pane.selected.toList()) },
                        onSelectionRename = {
                            pane.selected.firstOrNull()?.let { dialog = DialogKind.Rename(File(it)) }
                        },
                        onClearSelection = { vm.clearSelection() }
                    )

                    Page.Search -> SearchPage(
                        state = searchState,
                        onBack = { page = Page.Files },
                        onQueryChange = { vm.updateQuery(it) },
                        onSearch = { vm.startSearch() },
                        onCancel = { vm.cancelSearch() },
                        onResult = { entry ->
                            page = Page.Files
                            vm.revealPath(entry)
                        }
                    )

                    Page.Settings -> SettingsPage(
                        prefs = prefs,
                        showHidden = prefs.showHidden,
                        onBack = { page = Page.Files },
                        onToggleHidden = { vm.toggleHidden() },
                        onSort = { sheet = SheetKind.Sort },
                        onToggleAutoCheck = { vm.toggleAutoCheck() },
                        onToggleAccelerator = { vm.toggleAccelerator() },
                        onCheckUpdate = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                        onAbout = { dialog = DialogKind.About }
                    )
                }
            }

            // 进度浮层
            busy?.let { op ->
                BusyBar(op = op, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp))
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp, start = 12.dp, end = 12.dp)
            )
        }
    }

    // ---------------- 底部面板 ----------------

    when (val s = sheet) {
        is SheetKind.New -> NewSheet(
            onDismiss = { sheet = null },
            onFolder = { sheet = null; dialog = DialogKind.NewFolder },
            onFile = { sheet = null; dialog = DialogKind.NewFile }
        )

        is SheetKind.Sort -> SortSheet(
            current = prefs.sort.mode,
            ascending = prefs.sort.ascending,
            onPick = { mode -> sheet = null; vm.setSort(mode) },
            onDismiss = { sheet = null }
        )

        is SheetKind.Entry -> EntrySheet(
            entry = s.entry,
            onDismiss = { sheet = null },
            onOpen = {
                sheet = null
                openEntry(s.entry, Offset.Zero)
            },
            onOpenText = {
                sheet = null
                editorOrigin = Offset.Zero
                editorTarget = s.entry.file
            },
            onOpenExternal = {
                sheet = null
                if (!openExternally(context, s.entry.file)) openFailure = s.entry.file
            },
            onRename = { sheet = null; dialog = DialogKind.Rename(s.entry.file) },
            onProperties = { sheet = null; dialog = DialogKind.Properties(s.entry.file) },
            onCopyPath = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("path", s.entry.path))
                Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                sheet = null
            },
            onDelete = { sheet = null; dialog = DialogKind.Delete(listOf(s.entry.path)) }
        )

        is SheetKind.Path -> PathSheet(
            current = pane.path,
            onDismiss = { sheet = null },
            onGo = { path -> sheet = null; vm.navigate(path) }
        )

        null -> Unit
    }

    // ---------------- 对话框 ----------------

    when (val d = dialog) {
        is DialogKind.Rename -> InputDialog(
            title = "重命名",
            label = "名称",
            initial = d.file.name,
            confirmText = "保存",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.rename(d.file.absolutePath, name)
            }
        )

        is DialogKind.NewFolder -> InputDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            initial = "",
            confirmText = "创建",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.create(true, name)
            }
        )

        is DialogKind.NewFile -> InputDialog(
            title = "新建文件",
            label = "文件名（含扩展名）",
            initial = "",
            confirmText = "创建",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                vm.create(false, name)
            }
        )

        is DialogKind.Delete -> ConfirmDialog(
            title = "删除 ${d.paths.size} 项",
            message = d.paths.take(4).joinToString("\n") { File(it).name } +
                if (d.paths.size > 4) "\n…" else "",
            confirmText = "删除",
            danger = true,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                vm.deletePaths(d.paths)
            }
        )

        is DialogKind.Properties -> PropertiesDialog(
            file = d.file,
            loader = { vm.propertiesOf(it) },
            onDismiss = { dialog = null }
        )

        DialogKind.About -> AboutDialog(
            version = BuildConfig.VERSION_NAME,
            onDismiss = { dialog = null },
            onCheckUpdate = {
                dialog = null
                updateVm.check(silent = false, useAccelerator = prefs.useAccelerator)
            }
        )

        null -> Unit
    }

    // 无法用系统应用打开时的兜底
    openFailure?.let { file ->
        ConfirmDialog(
            title = "没有可用的打开方式",
            message = "系统里没有能打开 ${file.name} 的应用，可以用内置文本编辑器查看。",
            confirmText = "用文本打开",
            onDismiss = { openFailure = null },
            onConfirm = {
                openFailure = null
                editorOrigin = Offset.Zero
                editorTarget = file
            }
        )
    }

    // 更新弹窗
    if (updateState !is UpdateState.Idle) {
        val st = updateState
        val silentChecking = st is UpdateState.Checking && st.silent
        if (!silentChecking) {
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

    // 编辑器浮层（圆形扩散）
    editorTarget?.let { file ->
        EditorOverlay(
            file = file,
            origin = editorOrigin,
            vm = editorVm,
            onClose = {
                editorTarget = null
                vm.refresh()
            }
        )
    }
}

// ---------------- 文件页 ----------------

@Composable
private fun FilesPage(
    pane: PaneState,
    panes: List<PaneState>,
    active: Int,
    hasAccess: Boolean,
    storageUsed: Long,
    storageTotal: Long,
    clipboardCount: Int,
    onRequestAccess: () -> Unit,
    onOpenDrawer: () -> Unit,
    overflowOpen: Boolean,
    showHidden: Boolean,
    drawerOpen: Boolean,
    onOverflowChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleHidden: () -> Unit,
    onSwap: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPaste: () -> Unit,
    onProperties: () -> Unit,
    onPathClick: () -> Unit,
    onOpenEntry: (FileEntry, Offset) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onEntryMore: (FileEntry) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onNew: () -> Unit,
    onSelectionCopy: () -> Unit,
    onSelectionCut: () -> Unit,
    onSelectionDelete: () -> Unit,
    onSelectionRename: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(
            pane = pane,
            paneIndex = active,
            storageUsed = storageUsed,
            storageTotal = storageTotal,
            overflowOpen = overflowOpen,
            showHidden = showHidden,
            drawerOpen = drawerOpen,
            onOverflowChange = onOverflowChange,
            onOpenDrawer = onOpenDrawer,
            onPathClick = onPathClick,
            onRefresh = onRefresh,
            onSearch = onSearch,
            onSort = onSort,
            onSelectAll = onSelectAll,
            onToggleHidden = onToggleHidden,
            onSwap = onSwap,
            onSettings = onSettings,
            onCheckUpdate = onCheckUpdate,
            onPaste = onPaste,
            onProperties = onProperties,
            clipboardCount = clipboardCount
        )

        if (!hasAccess) {
            GrantBanner(onRequestAccess)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            FileList(
                pane = pane,
                showSkeleton = (pane.loading || pane.path.isEmpty()) && pane.entries.isEmpty(),
                onOpen = onOpenEntry,
                onLongPress = onLongPress,
                onMore = onEntryMore,
                onRetry = onRefresh
            )
        }

        BottomBar(
            selectionMode = pane.selectionMode,
            selectedCount = pane.selected.size,
            canBack = pane.canBack,
            canForward = pane.canForward,
            canUp = pane.canUp,
            onBack = onBack,
            onForward = onForward,
            onNew = onNew,
            onSwap = onSwap,
            onUp = onUp,
            onCopy = onSelectionCopy,
            onCut = onSelectionCut,
            onDelete = onSelectionDelete,
            onRename = onSelectionRename,
            onCancel = onClearSelection
        )
    }
}

@Composable
private fun TopBar(
    pane: PaneState,
    paneIndex: Int,
    storageUsed: Long,
    storageTotal: Long,
    overflowOpen: Boolean,
    showHidden: Boolean,
    drawerOpen: Boolean,
    onOverflowChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onPathClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleHidden: () -> Unit,
    onSwap: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPaste: () -> Unit,
    onProperties: () -> Unit,
    clipboardCount: Int
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(SunColors.Surface1)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer,
            interactionSource = interaction,
            modifier = Modifier.size(44.dp).pressScale(interaction, 0.88f)
        ) {
            HamburgerIcon(open = drawerOpen, tint = SunColors.TextSecondary, modifier = Modifier.size(21.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .clip(SunShapes.Small)
                .clickable { onPathClick() }
                .padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(if (paneIndex == 0) SunColors.AccentSoft else Color(0x33D9A93F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (paneIndex == 0) "A" else "B",
                        color = if (paneIndex == 0) SunColors.Accent else SunColors.Folder,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = compactPath(pane.path),
                    color = SunColors.TextPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(pane.dirCount, NumberStyle, SunColors.TextTertiary)
                Text(" 个文件夹", style = MaterialTheme.typography.labelSmall, color = SunColors.TextTertiary)
                Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = SunColors.TextTertiary)
                RollingNumber(pane.fileCount, NumberStyle, SunColors.TextTertiary)
                Text(" 个文件", style = MaterialTheme.typography.labelSmall, color = SunColors.TextTertiary)
                Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = SunColors.TextTertiary)
                Text(
                    "${Fmt.size(storageUsed)} / ${Fmt.size(storageTotal)}",
                    style = NumberStyle,
                    color = SunColors.TextTertiary,
                    maxLines = 1
                )
            }
        }

        Box {
            IconButton(
                onClick = { onOverflowChange(true) },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "工具栏",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { onOverflowChange(false) },
                modifier = Modifier.background(SunColors.Surface4, SunShapes.Medium),
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)
            ) {
                ToolMenuItem(Icons.Default.Refresh, "刷新", "重新读取当前目录") {
                    onOverflowChange(false); onRefresh()
                }
                ToolMenuItem(Icons.Default.Search, "搜索", "在当前目录下递归搜索") {
                    onOverflowChange(false); onSearch()
                }
                ToolMenuItem(Icons.Default.Sort, "排序", "名称 / 大小 / 时间 / 类型") {
                    onOverflowChange(false); onSort()
                }
                ToolMenuItem(Icons.Default.SelectAll, "全选", "${pane.entries.size} 项") {
                    onOverflowChange(false); onSelectAll()
                }
                ToolMenuItem(
                    if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (showHidden) "隐藏隐藏文件" else "显示隐藏文件",
                    "以 . 开头的文件"
                ) {
                    onOverflowChange(false); onToggleHidden()
                }
                ToolMenuItem(Icons.Default.SwapHoriz, "交换窗口", "A ⇄ B") {
                    onOverflowChange(false); onSwap()
                }
                if (clipboardCount > 0) {
                    ToolMenuItem(Icons.Default.ContentPaste, "粘贴", "剪贴板 $clipboardCount 项") {
                        onOverflowChange(false); onPaste()
                    }
                }
                if (pane.selected.size == 1) {
                    ToolMenuItem(Icons.Default.Info, "属性", "查看详情") {
                        onOverflowChange(false); onProperties()
                    }
                }
                ToolMenuItem(Icons.Default.Settings, "设置", null) {
                    onOverflowChange(false); onSettings()
                }
                ToolMenuItem(Icons.Default.SystemUpdate, "检查更新", "当前 v${BuildConfig.VERSION_NAME}") {
                    onOverflowChange(false); onCheckUpdate()
                }
            }
        }
    }
}

@Composable
private fun ToolMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, color = SunColors.TextPrimary, fontSize = 13.sp)
                if (subtitle != null) {
                    Text(subtitle, color = SunColors.TextTertiary, fontSize = 9.5.sp)
                }
            }
        },
        leadingIcon = {
            Icon(icon, null, tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp))
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp)
    )
}

@Composable
private fun GrantBanner(onRequestAccess: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF241D10))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "未获得全部文件访问权限，部分目录不可读写",
            color = SunColors.Warning,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "去授权",
            color = SunColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(SunShapes.Small)
                .clickable { onRequestAccess() }
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

// ---------------- 文件列表 ----------------

@Composable
private fun FileList(
    pane: PaneState,
    showSkeleton: Boolean,
    onOpen: (FileEntry, Offset) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onMore: (FileEntry) -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(pane.token) { if (pane.entries.isNotEmpty()) listState.scrollToItem(0) }

    when {
        showSkeleton -> ShimmerList()

        pane.error != null && pane.entries.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("打不开这个目录", color = SunColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                pane.error,
                color = SunColors.TextTertiary,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 300.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "重试",
                color = SunColors.Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { onRetry() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        pane.entries.isEmpty() -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Folder, null, tint = SunColors.Surface4, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text("这个文件夹是空的", color = SunColors.TextTertiary, fontSize = 11.5.sp)
        }

        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 10.dp)
        ) {
            items(pane.entries, key = { it.path }) { entry ->
                FileRow(
                    entry = entry,
                    selected = pane.selected.contains(entry.path),
                    selectionMode = pane.selectionMode,
                    onClick = { origin -> onOpen(entry, origin) },
                    onLongClick = { onLongPress(entry) },
                    onMore = { onMore(entry) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: (Offset) -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val bg = when {
        selected -> SunColors.AccentSoft
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bg)
            .onGloballyPositioned { coords ->
                val pos = coords.localToRoot(Offset.Zero)
                rowOrigin = Offset(pos.x + 60f, pos.y + coords.size.height / 2f)
            }
            .pressScale(interaction, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = { onClick(rowOrigin) }
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg(entry)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconOf(entry),
                contentDescription = null,
                tint = iconTint(entry),
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = if (selected) SunColors.Accent else SunColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.isDir) "文件夹" else Fmt.size(entry.size),
                    style = NumberStyle,
                    color = SunColors.TextTertiary
                )
                Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = SunColors.TextTertiary)
                Text(
                    Fmt.time(entry.lastModified),
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp
                )
            }
        }
        if (selectionMode) {
            DrawCheck(
                checked = selected,
                modifier = Modifier.size(21.dp).padding(end = 0.dp),
                background = SunColors.Accent
            )
            Spacer(Modifier.width(10.dp))
        } else {
            IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "更多",
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun iconOf(entry: FileEntry): ImageVector {
    if (entry.isDir) return Icons.Default.Folder
    return when (entry.name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> Icons.Default.Image
        "mp3", "flac", "wav", "ogg", "m4a", "aac" -> Icons.Default.Audiotrack
        "mp4", "mkv", "avi", "mov", "webm", "3gp" -> Icons.Default.Movie
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "apk", "xapk", "apks" -> Icons.Default.Android
        "pdf" -> Icons.Default.PictureAsPdf
        "txt", "md", "log", "json", "xml", "csv" -> Icons.Default.Description
        "kt", "java", "js", "ts", "py", "c", "cpp", "html", "css", "sh", "smali", "gradle" -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }
}

private fun iconTint(entry: FileEntry): Color = when {
    entry.isDir -> SunColors.Folder
    entry.name.substringAfterLast('.', "").lowercase() in setOf("apk", "xapk") -> SunColors.Success
    else -> SunColors.TextSecondary
}

private fun iconBg(entry: FileEntry): Color = when {
    entry.isDir -> Color(0x1FD9A93F)
    entry.name.substringAfterLast('.', "").lowercase() in setOf("apk", "xapk") -> Color(0x1F3FB950)
    else -> Color(0x14FFFFFF)
}

// ---------------- 底栏 ----------------

@Composable
private fun BottomBar(
    selectionMode: Boolean,
    selectedCount: Int,
    canBack: Boolean,
    canForward: Boolean,
    canUp: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSwap: () -> Unit,
    onUp: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(SunColors.Surface1)) {
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 $selectedCount 项", color = SunColors.Accent, fontSize = 10.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (!selectionMode) {
                BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "后退", canBack, onBack)
                BarIcon(Icons.AutoMirrored.Filled.ArrowForward, "前进", canForward, onForward)
                BarIcon(Icons.Default.Add, "新建", true, onNew)
                BarIcon(Icons.Default.SwapHoriz, "交换窗口", true, onSwap)
                BarIcon(Icons.Default.ArrowUpward, "上一级", canUp, onUp)
            } else {
                BarIcon(Icons.Default.ContentCopy, "复制", true, onCopy)
                BarIcon(Icons.Default.ContentCut, "剪切", true, onCut)
                BarIcon(Icons.Default.DriveFileRenameOutline, "重命名", selectedCount == 1, onRename)
                BarIcon(Icons.Default.Delete, "删除", true, onDelete, danger = true)
                BarIcon(Icons.Default.Close, "取消", true, onCancel)
            }
        }
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> SunColors.TextTertiary.copy(alpha = 0.4f)
        danger -> SunColors.Danger
        else -> SunColors.TextSecondary
    }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .pressScale(interaction, 0.88f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
    }
}

// ---------------- 进度浮层 ----------------

@Composable
private fun BusyBar(op: com.suibian.sunfile.vm.OpState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = SunColors.Surface4,
        shape = SunShapes.Medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(op.title, color = SunColors.TextPrimary, fontSize = 11.5.sp)
                Text("${(op.progress * 100).toInt()}%", color = SunColors.Accent, style = NumberStyle)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { op.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = SunColors.Accent,
                trackColor = SunColors.Surface2
            )
            if (op.currentFile.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    op.currentFile,
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------- 侧边栏 ----------------

@Composable
private fun DrawerContent(
    currentPath: String,
    version: String,
    storageUsed: Long,
    storageTotal: Long,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAbout: () -> Unit
) {
    val shortcuts = listOf(
        Triple("内部存储", "/storage/emulated/0", Icons.Default.Storage),
        Triple("下载", "/storage/emulated/0/Download", Icons.Default.Download),
        Triple("文档", "/storage/emulated/0/Documents", Icons.Default.Description),
        Triple("图片", "/storage/emulated/0/Pictures", Icons.Default.Image),
        Triple("音乐", "/storage/emulated/0/Music", Icons.Default.Audiotrack),
        Triple("电影", "/storage/emulated/0/Movies", Icons.Default.Movie),
        Triple("根目录", "/", Icons.Default.Folder)
    )
    val existing = shortcuts.filter { File(it.second).isDirectory }
    ModalDrawerSheet(
        drawerContainerColor = SunColors.Surface3,
        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.width(302.dp)
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SunColors.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, null, tint = SunColors.Accent, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("SunDocument", color = SunColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("v$version · 双栏文件管理", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${Fmt.size(storageUsed)} 已用 / ${Fmt.size(storageTotal)}",
                    color = SunColors.TextSecondary,
                    style = NumberStyle
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (storageTotal > 0) storageUsed.toFloat() / storageTotal else 0f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = SunColors.Accent,
                    trackColor = SunColors.Surface1
                )
            }

            Text(
                "快速访问",
                color = SunColors.TextTertiary,
                fontSize = 9.5.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp)
            )
            existing.forEach { (label, path, icon) ->
                DrawerItem(
                    icon = icon,
                    label = label,
                    selected = currentPath == path,
                    onClick = { onNavigate(path) }
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))
            Spacer(Modifier.height(6.dp))

            DrawerItem(Icons.Default.Settings, "设置", false, onSettings)
            DrawerItem(Icons.Default.SystemUpdate, "检查更新", false, onCheckUpdate)
            DrawerItem(Icons.Default.Info, "关于", false, onAbout)

            Spacer(Modifier.weight(1f))
            Text(
                "github.com/suibian-sun/SunDocument",
                color = SunColors.TextTertiary,
                fontSize = 9.sp,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(SunShapes.Small)
            .background(if (selected) SunColors.AccentSoft else Color.Transparent)
            .pressScale(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) SunColors.Accent else SunColors.TextSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) SunColors.Accent else SunColors.TextPrimary,
            fontSize = 12.5.sp
        )
    }
}

// ---------------- 工具函数 ----------------

/** 长路径中段省略，保留可读的尾部目录名 */
internal fun compactPath(path: String): String {
    if (path.isEmpty()) return "/"
    if (path.length <= 40) return path
    val tail = path.takeLast(34)
    val cut = tail.indexOf('/')
    return "…" + if (cut >= 0) tail.substring(cut) else tail
}

internal fun openExternally(context: Context, file: File): Boolean {
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    return runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
