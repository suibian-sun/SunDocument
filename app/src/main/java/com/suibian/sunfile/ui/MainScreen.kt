package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
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
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.OpenMode
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.components.FolderDotsIcon
import com.suibian.sunfile.ui.components.HamburgerIcon
import com.suibian.sunfile.ui.components.RollingNumber
import com.suibian.sunfile.ui.components.ShimmerList
import com.suibian.sunfile.ui.components.iconOf
import com.suibian.sunfile.ui.components.pressScale
import com.suibian.sunfile.ui.components.tintOf
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

/** 内置全屏查看器 */
private sealed interface Viewer {
    data class Hex(val file: File) : Viewer
    data class Image(val file: File) : Viewer
    data class Archive(val file: File) : Viewer
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
    var editorLine by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var editorOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewer by remember { mutableStateOf<Viewer?>(null) }
    var openWithTarget by remember { mutableStateOf<File?>(null) }
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

    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2000)
            exitArmed = false
        }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = drawerState.isClosed && page != Page.Files) { page = Page.Files }
    BackHandler(enabled = drawerState.isClosed && page == Page.Files && pane.selectionMode) {
        vm.clearSelection()
    }
    // 聚焦栏内按返回：先回上一级，只有在根目录才退出
    BackHandler(enabled = drawerState.isClosed && page == Page.Files && !pane.selectionMode) {
        if (pane.canUp) {
            vm.goUp()
        } else if (exitArmed) {
            (context as? android.app.Activity)?.finish()
        } else {
            exitArmed = true
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWith(file: File, mode: OpenMode, origin: Offset = Offset.Zero) {
        when (mode) {
            OpenMode.TEXT -> {
                editorOrigin = origin
                editorTarget = file
            }
            OpenMode.HEX -> viewer = Viewer.Hex(file)
            OpenMode.IMAGE -> viewer = Viewer.Image(file)
            OpenMode.ARCHIVE -> viewer = Viewer.Archive(file)
            OpenMode.EXTERNAL -> if (!openExternally(context, file)) openFailure = file
        }
    }

    fun openFile(file: File, origin: Offset) {
        val ext = FileKinds.extensionOf(file)
        val remembered = prefs.openWith[ext]
        val mode = remembered ?: FileKinds.defaultMode(file)
        if (mode == null) {
            openWithTarget = file
        } else {
            openWith(file, mode, origin)
        }
    }

    fun copyPath(path: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", path))
        Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            DrawerContent(
                currentPath = pane.path,
                version = BuildConfig.VERSION_NAME,
                recent = prefs.recent,
                storageUsed = storage?.used ?: 0L,
                storageTotal = storage?.total ?: 0L,
                bookmarks = prefs.bookmarks,
                onNavigate = { path ->
                    vm.navigate(path)
                    page = Page.Files
                    scope.launch { drawerState.close() }
                },
                onBookmarks = {
                    sheet = SheetKind.Bookmarks
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
                        panes = panes,
                        active = active,
                        hasAccess = hasAccess,
                        storageUsed = storage?.used ?: 0L,
                        storageTotal = storage?.total ?: 0L,
                        clipboardCount = clipboard.paths.size,
                        bookmarks = prefs.bookmarks,
                        showHidden = prefs.showHidden,
                        overflowOpen = overflowOpen,
                        drawerOpen = drawerState.isOpen,
                        onRequestAccess = onRequestAccess,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOverflowChange = { overflowOpen = it },
                        onActivate = { vm.setActive(it) },
                        onPathClick = { sheet = SheetKind.Path },
                        onOpenEntry = { index, entry, origin ->
                            vm.setActive(index)
                            if (entry.isDir) {
                                vm.navigate(entry.path)
                            } else {
                                openFile(entry.file, origin)
                            }
                        },
                        onLongPress = { index, entry ->
                            vm.setActive(index)
                            vm.toggleSelect(entry)
                        },
                        onEntryMore = { sheet = SheetKind.Entry(it) },
                        onUp = { index -> vm.goUp(index) },
                        onRefresh = { index -> vm.refreshPane(index) },
                        onToggleBookmark = { path -> vm.toggleBookmark(path) },
                        onBack = { vm.goBack() },
                        onForward = { vm.goForward() },
                        onNew = { sheet = SheetKind.New },
                        onSwap = { vm.swapPanes() },
                        onSelectionCopy = { vm.setClipboard(false) },
                        onSelectionCut = { vm.setClipboard(true) },
                        onSelectionDelete = { dialog = DialogKind.Delete(pane.selected.toList()) },
                        onSelectionRename = {
                            pane.selected.singleOrNull()?.let { dialog = DialogKind.Rename(File(it)) }
                        },
                        onClearSelection = { vm.clearSelection() },
                        // 顶栏工具
                        onRefreshActive = { vm.refresh() },
                        onSearch = { vm.openSearch(); page = Page.Search },
                        onSort = { sheet = SheetKind.Sort },
                        onSelectAll = { vm.selectAll() },
                        onInvertSelection = { vm.invertSelection() },
                        onToggleHidden = { vm.toggleHidden() },
                        onSettings = { page = Page.Settings },
                        onCheckUpdate = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                        onPaste = { vm.paste() },
                        onProperties = {
                            pane.selected.singleOrNull()?.let { dialog = DialogKind.Properties(File(it)) }
                        },
                        onCompressSelection = { dialog = DialogKind.Compress(pane.selected.toList()) },
                        onBatchRename = { dialog = DialogKind.BatchRename(pane.selected.toList()) },
                        onChecksum = {
                            pane.selected.singleOrNull()?.let { dialog = DialogKind.Checksum(File(it)) }
                        },
                        onBookmarks = { sheet = SheetKind.Bookmarks },
                        onApkInfo = {
                            pane.selected.singleOrNull()?.let { sheet = SheetKind.Apk(File(it)) }
                        },
                        onChmod = {
                            pane.selected.singleOrNull()?.let {
                                dialog = DialogKind.Chmod(File(it), File(it).let { f -> runCatching { com.suibian.sunfile.data.Shell.modeOf(f.absolutePath) }.getOrNull() })
                            }
                        }
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
                        },
                        onModeChange = { vm.setSearchMode(it) },
                        onContentResult = { hit ->
                            page = Page.Files
                            editorLine = hit.line
                            editorOrigin = Offset.Zero
                            editorTarget = hit.file
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
                        onEditorPrefs = { size, wrap, lines, comp -> vm.setEditorPrefs(size, wrap, lines, comp) },
                        onThemeChange = { vm.prefsStore.setThemeMode(it) },
                        onClearOpenWith = { vm.prefsStore.clearOpenWith() },
                        onClearRecent = { vm.prefsStore.clearRecent() },
                        onRequestRoot = {
                            scope.launch {
                                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.suibian.sunfile.data.Shell.requestRoot()
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) "已获得 Root，可访问全部目录" else "未获得 Root，系统目录仍受限",
                                    Toast.LENGTH_SHORT
                                ).show()
                                vm.refreshAll()
                            }
                        },
                        onAbout = { dialog = DialogKind.About }
                    )
                }
            }

            busy?.let { op ->
                BusyBar(op = op, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 74.dp, start = 12.dp, end = 12.dp)
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
            bookmarked = prefs.bookmarks.contains(if (s.entry.isDir) s.entry.path else (s.entry.file.parent ?: s.entry.path)),
            onDismiss = { sheet = null },
            onOpen = {
                sheet = null
                if (s.entry.isDir) vm.navigate(s.entry.path) else openFile(s.entry.file, Offset.Zero)
            },
            onOpenWith = {
                sheet = null
                openWithTarget = s.entry.file
            },
            onRename = { sheet = null; dialog = DialogKind.Rename(s.entry.file) },
            onProperties = { sheet = null; dialog = DialogKind.Properties(s.entry.file) },
            onCopyPath = {
                copyPath(s.entry.path)
                sheet = null
            },
            onShare = {
                sheet = null
                if (!shareFile(context, s.entry.file)) {
                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                }
            },
            onCompress = { sheet = null; dialog = DialogKind.Compress(listOf(s.entry.path)) },
            onBookmark = {
                vm.toggleBookmark(if (s.entry.isDir) s.entry.path else (s.entry.file.parent ?: s.entry.path))
                sheet = null
            },
            onDelete = { sheet = null; dialog = DialogKind.Delete(listOf(s.entry.path)) }
        )

        is SheetKind.Path -> PathSheet(
            current = pane.path,
            bookmarks = prefs.bookmarks,
            onDismiss = { sheet = null },
            onGo = { path -> sheet = null; vm.navigate(path) }
        )

        is SheetKind.Bookmarks -> BookmarkSheet(
            bookmarks = prefs.bookmarks,
            current = pane.path,
            onDismiss = { sheet = null },
            onGo = { path -> sheet = null; vm.navigate(path) },
            onRemove = { path -> vm.prefsStore.removeBookmark(path) }
        )

        is SheetKind.Apk -> ApkSheet(file = s.file, onDismiss = { sheet = null })

        is SheetKind.OpenWith -> OpenWithSheet(
            file = s.file,
            remembered = prefs.openWith[FileKinds.extensionOf(s.file)],
            onDismiss = { sheet = null },
            onPick = { mode ->
                sheet = null
                openWith(s.file, mode)
            },
            onPickAndRemember = { mode ->
                vm.prefsStore.setOpenWith(FileKinds.extensionOf(s.file), mode)
                sheet = null
                openWith(s.file, mode)
            }
        )

        null -> Unit
    }

    // 未知类型默认弹出打开方式
    openWithTarget?.let { file ->
        OpenWithSheet(
            file = file,
            remembered = prefs.openWith[FileKinds.extensionOf(file)],
            onDismiss = { openWithTarget = null },
            onPick = { mode ->
                openWithTarget = null
                openWith(file, mode)
            },
            onPickAndRemember = { mode ->
                vm.prefsStore.setOpenWith(FileKinds.extensionOf(file), mode)
                openWithTarget = null
                openWith(file, mode)
            }
        )
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
                vm.create(false, name) { file ->
                    if (file != null && FileRepo.looksLikeText(file)) {
                        editorOrigin = Offset.Zero
                        editorTarget = file
                    }
                }
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

        is DialogKind.Compress -> CompressDialog(
            paths = d.paths,
            onConfirm = { name ->
                dialog = null
                vm.compress(d.paths, name)
            },
            onDismiss = { dialog = null }
        )

        is DialogKind.BatchRename -> BatchRenameDialog(
            paths = d.paths,
            onConfirm = { find, replace ->
                dialog = null
                vm.renameBatch(d.paths, find, replace)
            },
            onDismiss = { dialog = null }
        )

        is DialogKind.Chmod -> ChmodDialog(
            file = d.file,
            current = d.current,
            onConfirm = { mode ->
                dialog = null
                vm.chmod(d.file.absolutePath, mode)
            },
            onDismiss = { dialog = null }
        )

        is DialogKind.Checksum -> ChecksumDialog(
            file = d.file,
            loader = { f, algo -> vm.checksumOf(f, algo) },
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
        if (openWithTarget == null) {
            OpenWithSheet(
                file = file,
                remembered = prefs.openWith[FileKinds.extensionOf(file)],
                onDismiss = { openFailure = null },
                onPick = { mode ->
                    openFailure = null
                    openWith(file, mode)
                },
                onPickAndRemember = { mode ->
                    vm.prefsStore.setOpenWith(FileKinds.extensionOf(file), mode)
                    openFailure = null
                    openWith(file, mode)
                }
            )
        }
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

    // 编辑器
    editorTarget?.let { file ->
        EditorOverlay(
            file = file,
            origin = editorOrigin,
            initialLine = editorLine,
            vm = editorVm,
            prefs = prefs,
            onEditorPrefs = { size, wrap, lines, comp -> vm.setEditorPrefs(size, wrap, lines, comp) },
            onOpenHex = { f ->
                editorVm.reset()
                editorTarget = null
                viewer = Viewer.Hex(f)
            },
            onClose = {
                editorTarget = null
                editorLine = 0
                vm.refreshAll()
            }
        )
    }

    // 查看器
    when (val v = viewer) {
        is Viewer.Hex -> HexViewerOverlay(v.file) { viewer = null }
        is Viewer.Image -> ImageViewerOverlay(v.file) { viewer = null }
        is Viewer.Archive -> ArchiveOverlay(
            file = v.file,
            onClose = { viewer = null },
            onExtractAll = {
                val dest = panes[active].currentDir
                viewer = null
                vm.extractArchive(v.file, dest)
            }
        )
        null -> Unit
    }
}

// ---------------- 文件页 ----------------

@Composable
private fun FilesPage(
    panes: List<PaneState>,
    active: Int,
    hasAccess: Boolean,
    storageUsed: Long,
    storageTotal: Long,
    clipboardCount: Int,
    bookmarks: List<String>,
    showHidden: Boolean,
    overflowOpen: Boolean,
    drawerOpen: Boolean,
    onRequestAccess: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOverflowChange: (Boolean) -> Unit,
    onActivate: (Int) -> Unit,
    onPathClick: () -> Unit,
    onOpenEntry: (Int, FileEntry, Offset) -> Unit,
    onLongPress: (Int, FileEntry) -> Unit,
    onEntryMore: (FileEntry) -> Unit,
    onUp: (Int) -> Unit,
    onRefresh: (Int) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSwap: () -> Unit,
    onSelectionCopy: () -> Unit,
    onSelectionCut: () -> Unit,
    onSelectionDelete: () -> Unit,
    onSelectionRename: () -> Unit,
    onClearSelection: () -> Unit,
    onRefreshActive: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onToggleHidden: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPaste: () -> Unit,
    onProperties: () -> Unit,
    onCompressSelection: () -> Unit,
    onBatchRename: () -> Unit,
    onChecksum: () -> Unit,
    onBookmarks: () -> Unit,
    onApkInfo: () -> Unit,
    onChmod: () -> Unit
) {
    val pane = panes[active]
    val scrollMemory = remember { mutableStateMapOf<String, Int>() }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(
            pane = pane,
            storageUsed = storageUsed,
            storageTotal = storageTotal,
            overflowOpen = overflowOpen,
            showHidden = showHidden,
            drawerOpen = drawerOpen,
            clipboardCount = clipboardCount,
            bookmarks = bookmarks,
            onOverflowChange = onOverflowChange,
            onOpenDrawer = onOpenDrawer,
            onPathClick = onPathClick,
            onRefresh = onRefreshActive,
            onSearch = onSearch,
            onSort = onSort,
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onToggleHidden = onToggleHidden,
            onSwap = onSwap,
            onSettings = onSettings,
            onCheckUpdate = onCheckUpdate,
            onPaste = onPaste,
            onProperties = onProperties,
            onCompress = onCompressSelection,
            onBatchRename = onBatchRename,
            onChecksum = onChecksum,
            onBookmarks = onBookmarks,
            onApkInfo = onApkInfo,
            onChmod = onChmod,
            onToggleBookmark = onToggleBookmark
        )

        if (!hasAccess) GrantBanner(onRequestAccess)

        Row(Modifier.weight(1f).fillMaxWidth()) {
            PaneView(
                index = 0,
                pane = panes[0],
                active = active == 0,
                scrollMemory = scrollMemory,
                modifier = Modifier.weight(1f),
                onActivate = { onActivate(0) },
                onOpen = { entry, origin -> onOpenEntry(0, entry, origin) },
                onLongPress = { onLongPress(0, it) },
                onMore = onEntryMore,
                onUp = { onUp(0) },
                onRefresh = { onRefresh(0) }
            )
            PaneDivider(active = active == 0)
            PaneView(
                index = 1,
                pane = panes[1],
                active = active == 1,
                scrollMemory = scrollMemory,
                modifier = Modifier.weight(1f),
                onActivate = { onActivate(1) },
                onOpen = { entry, origin -> onOpenEntry(1, entry, origin) },
                onLongPress = { onLongPress(1, it) },
                onMore = onEntryMore,
                onUp = { onUp(1) },
                onRefresh = { onRefresh(1) }
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
            onUp = { onUp(active) },
            onCopy = onSelectionCopy,
            onCut = onSelectionCut,
            onDelete = onSelectionDelete,
            onRename = onSelectionRename,
            onCancel = onClearSelection
        )
    }
}

// ---------------- 顶栏 ----------------

@Composable
private fun TopBar(
    pane: PaneState,
    storageUsed: Long,
    storageTotal: Long,
    overflowOpen: Boolean,
    showHidden: Boolean,
    drawerOpen: Boolean,
    clipboardCount: Int,
    bookmarks: List<String>,
    onOverflowChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onPathClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onToggleHidden: () -> Unit,
    onSwap: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPaste: () -> Unit,
    onProperties: () -> Unit,
    onCompress: () -> Unit,
    onBatchRename: () -> Unit,
    onChecksum: () -> Unit,
    onBookmarks: () -> Unit,
    onApkInfo: () -> Unit,
    onChmod: () -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(SunColors.Surface1)
            .padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer,
            interactionSource = interaction,
            modifier = Modifier.size(42.dp).pressScale(interaction, 0.88f)
        ) {
            HamburgerIcon(open = drawerOpen, tint = SunColors.TextSecondary, modifier = Modifier.size(20.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .clip(SunShapes.Small)
                .clickable { onPathClick() }
                .padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            AnimatedContent(
                targetState = pane.path,
                transitionSpec = {
                    (fadeIn(tween(Motion.Base)) + slideInVertically(tween(Motion.Base)) { it / 3 }) togetherWith
                        fadeOut(tween(Motion.Fast))
                },
                label = "path"
            ) { path ->
                Text(
                    path.ifEmpty { "/" },
                    color = SunColors.TextPrimary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Folder,
                    null,
                    tint = SunColors.Folder.copy(alpha = 0.75f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(3.dp))
                RollingNumber(pane.dirCount, NumberStyle, SunColors.TextTertiary)
                Spacer(Modifier.width(9.dp))
                Icon(
                    Icons.Outlined.Description,
                    null,
                    tint = SunColors.File.copy(alpha = 0.75f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(3.dp))
                RollingNumber(pane.fileCount, NumberStyle, SunColors.TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text(
                    Fmt.sizePair(storageUsed, storageTotal),
                    style = NumberStyle,
                    color = SunColors.TextTertiary,
                    maxLines = 1
                )
            }
        }

        Box {
            IconButton(onClick = { onOverflowChange(true) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
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
                ToolMenuItem(Icons.Outlined.Refresh, "刷新", null) {
                    onOverflowChange(false); onRefresh()
                }
                ToolMenuItem(Icons.Outlined.Search, "搜索", null) {
                    onOverflowChange(false); onSearch()
                }
                ToolMenuItem(Icons.Outlined.Sort, "排序", "${pane.entries.size}") {
                    onOverflowChange(false); onSort()
                }
                ToolMenuItem(Icons.Outlined.SelectAll, "全选", null) {
                    onOverflowChange(false); onSelectAll()
                }
                ToolMenuItem(Icons.Outlined.SelectAll, "反选", null) {
                    onOverflowChange(false); onInvertSelection()
                }
                ToolMenuItem(
                    if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (showHidden) "隐藏隐藏文件" else "显示隐藏文件",
                    "以 . 开头"
                ) {
                    onOverflowChange(false); onToggleHidden()
                }
                ToolMenuItem(Icons.Outlined.SwapHoriz, "交换窗口", null) {
                    onOverflowChange(false); onSwap()
                }
                if (clipboardCount > 0) {
                    ToolMenuItem(Icons.Outlined.ContentPaste, "粘贴", "剪贴板 $clipboardCount 项") {
                        onOverflowChange(false); onPaste()
                    }
                }
                ToolMenuItem(
                    if (bookmarks.contains(pane.path)) Icons.Outlined.Star else Icons.Outlined.BookmarkBorder,
                    if (bookmarks.contains(pane.path)) "取消收藏当前目录" else "收藏当前目录",
                    null
                ) {
                    onOverflowChange(false); onToggleBookmark(pane.path)
                }
                ToolMenuItem(Icons.Outlined.BookmarkBorder, "收藏夹", null) {
                    onOverflowChange(false); onBookmarks()
                }
                if (pane.selected.size == 1) {
                    val only = pane.selected.firstOrNull()?.let { File(it) }
                    if (only != null && only.extension.equals("apk", true)) {
                        ToolMenuItem(Icons.Outlined.Terminal, "安装包信息", null) {
                            onOverflowChange(false); onApkInfo()
                        }
                    }
                    ToolMenuItem(Icons.Outlined.Info, "属性", null) {
                        onOverflowChange(false); onProperties()
                    }
                    ToolMenuItem(Icons.Outlined.Fingerprint, "校验和", null) {
                        onOverflowChange(false); onChecksum()
                    }
                    if (com.suibian.sunfile.data.Shell.rootAvailable) {
                        ToolMenuItem(Icons.Outlined.Terminal, "修改权限", "chmod") {
                            onOverflowChange(false); onChmod()
                        }
                    }
                }
                if (pane.selected.isNotEmpty()) {
                    ToolMenuItem(Icons.Outlined.Archive, "压缩为 ZIP", "${pane.selected.size}") {
                        onOverflowChange(false); onCompress()
                    }
                }
                if (pane.selected.size > 1) {
                    ToolMenuItem(Icons.Outlined.DriveFileRenameOutline, "批量重命名", "${pane.selected.size}") {
                        onOverflowChange(false); onBatchRename()
                    }
                }
                ToolMenuItem(Icons.Outlined.Settings, "设置", null) {
                    onOverflowChange(false); onSettings()
                }
                ToolMenuItem(Icons.Outlined.SystemUpdate, "检查更新", "v${BuildConfig.VERSION_NAME}") {
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
            .background(SunColors.Warning.copy(alpha = if (SunColors.dark) 0.12f else 0.10f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "存储权限未开启",
            color = SunColors.Warning,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "开启",
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

// ---------------- 单栏 ----------------

@Composable
private fun PaneDivider(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) SunColors.Accent.copy(alpha = 0.55f) else SunColors.Hairline,
        animationSpec = tween(Motion.Base),
        label = "divider"
    )
    Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}

@Composable
private fun PaneView(
    index: Int,
    pane: PaneState,
    active: Boolean,
    scrollMemory: MutableMap<String, Int>,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onOpen: (FileEntry, Offset) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onMore: (FileEntry) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (active) SunColors.Surface1.copy(alpha = if (SunColors.dark) 0.55f else 0.75f) else Color.Transparent,
        animationSpec = tween(Motion.Base),
        label = "paneBg"
    )
    val accentAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(Motion.Base),
        label = "paneAccent"
    )

    Column(
        modifier
            .fillMaxHeight()
            .background(bg)
            // 手指进入或按下即聚焦该栏，不消费事件，子项点击照常
            .pointerInput(index) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        when (e.type) {
                            PointerEventType.Enter,
                            PointerEventType.Move,
                            PointerEventType.Press -> onActivate()
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        // 激活指示线：聚焦栏顶部一道细光，代替文字标签
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(SunColors.Accent.copy(alpha = 0.75f * accentAlpha))
        )

        // 固定的「..」上级目录行
        UpRow(enabled = pane.canUp, onClick = { onActivate(); if (pane.canUp) onUp() })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PaneList(
                pane = pane,
                scrollMemory = scrollMemory,
                onOpen = { entry, origin -> onActivate(); onOpen(entry, origin) },
                onLongPress = { onActivate(); onLongPress(it) },
                onMore = { onActivate(); onMore(it) },
                onRetry = { onActivate(); onRefresh() },
                onUp = { onActivate(); onUp() }
            )
        }
    }
}

@Composable
private fun UpRow(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(SunColors.Surface2.copy(alpha = 0.5f))
            .pressScale(interaction, 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderDotsIcon(
            tint = if (enabled) SunColors.Folder else SunColors.Folder.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "..",
            color = if (enabled) SunColors.TextSecondary else SunColors.TextTertiary.copy(alpha = 0.5f),
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(6.dp))
        Text("上一级", color = SunColors.TextTertiary.copy(alpha = 0.6f), fontSize = 9.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneList(
    pane: PaneState,
    scrollMemory: MutableMap<String, Int>,
    onOpen: (FileEntry, Offset) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onMore: (FileEntry) -> Unit,
    onRetry: () -> Unit,
    onUp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 每个路径独立记住滚动位置：从子目录返回、从编辑器返回都停在原处
    val listState = remember(pane.path) { androidx.compose.foundation.lazy.LazyListState() }
    var restored by remember(pane.path) { mutableStateOf(false) }
    LaunchedEffect(pane.path, pane.entries.size) {
        if (!restored && pane.entries.isNotEmpty()) {
            val saved = scrollMemory[pane.path]
            if (saved != null && saved in pane.entries.indices) {
                listState.scrollToItem(saved)
            }
            restored = true
        }
    }
    LaunchedEffect(listState, pane.path) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (pane.entries.isNotEmpty()) scrollMemory[pane.path] = idx
        }
    }

    AnimatedContent(
        targetState = pane.path,
        transitionSpec = {
            val dir = if (pane.navDirection >= 0) 1 else -1
            (fadeIn(tween(Motion.Base)) +
                slideInHorizontally(tween(Motion.Slow, easing = Motion.EaseOut)) { w -> dir * w / 6 }) togetherWith
                (fadeOut(tween(Motion.Fast)) +
                    slideOutHorizontally(tween(Motion.Base, easing = Motion.EaseIn)) { w -> -dir * w / 10 })
        },
        label = "paneList"
    ) { targetPath ->
        androidx.compose.runtime.key(targetPath) {
        when {
            (pane.loading || pane.path.isEmpty()) && pane.entries.isEmpty() -> ShimmerList(rows = 7)

            pane.notice != null && pane.entries.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Folder, null, tint = SunColors.Surface4, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(8.dp))
                Text("无法读取", color = SunColors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    pane.notice ?: "",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "返回上级",
                        color = SunColors.TextSecondary,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable { onUp() }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                    if (!com.suibian.sunfile.data.Shell.rootAvailable) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "获取 Root",
                            color = SunColors.TextPrimary,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(SunColors.AccentSoft)
                                .clickable {
                                    scope.launch {
                                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.suibian.sunfile.data.Shell.requestRoot()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (ok) "已获得 Root" else "未获得 Root",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (ok) onRetry()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            pane.error != null && pane.entries.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("打开失败", color = SunColors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Text(
                    pane.error ?: "",
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "重试",
                    color = SunColors.Accent,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onRetry() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            pane.entries.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Folder, null, tint = SunColors.Surface4, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("空", color = SunColors.TextTertiary, fontSize = 11.sp)
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pane.entries, key = { it.path }) { entry ->
                    FileRow(
                        modifier = Modifier.animateItemPlacement(),
                        entry = entry,
                        selected = pane.selected.contains(entry.path),
                        selectionMode = pane.selectionMode,
                        onClick = { origin ->
                            if (pane.selectionMode) onLongPress(entry) else onOpen(entry, origin)
                        },
                        onLongClick = { onLongPress(entry) },
                        onMore = { onMore(entry) }
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    modifier: Modifier = Modifier,
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: (Offset) -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val bg by animateColorAsState(
        targetValue = if (selected) SunColors.AccentSoft else Color.Transparent,
        animationSpec = tween(Motion.Fast),
        label = "rowBg"
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(bg)
            .onGloballyPositioned { coords ->
                val pos = coords.localToRoot(Offset.Zero)
                rowOrigin = Offset(pos.x + 40f, pos.y + coords.size.height / 2f)
            }
            .pressScale(interaction, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = { onClick(rowOrigin) }
            )
            .padding(start = 9.dp, end = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconOf(entry.file, entry.isDir),
            contentDescription = null,
            tint = tintOf(entry.file, entry.isDir).let { if (entry.canRead) it else it.copy(alpha = 0.4f) },
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = when {
                    selected -> SunColors.Accent
                    !entry.canRead -> SunColors.TextTertiary
                    else -> SunColors.TextPrimary
                },
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                if (entry.isDir) {
                    Fmt.timeShort(entry.lastModified)
                } else {
                    Fmt.size(entry.size) + " · " + Fmt.timeShort(entry.lastModified)
                },
                color = SunColors.TextTertiary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionMode) {
            DrawCheck(
                checked = selected,
                modifier = Modifier.size(18.dp).padding(end = 2.dp),
                background = SunColors.Accent
            )
            Spacer(Modifier.width(5.dp))
        } else {
            IconButton(onClick = onMore, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "更多",
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
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
    Column(Modifier.fillMaxWidth().background(SunColors.Surface1).animateContentSize()) {
        AnimatedVisibility(visible = selectionMode) {
            Row(
                Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 $selectedCount 项", color = SunColors.Accent, fontSize = 9.5.sp)
            }
        }
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                (fadeIn(tween(Motion.Base)) + slideInVertically(tween(Motion.Base)) { it / 3 }) togetherWith
                    fadeOut(tween(Motion.Fast))
            },
            label = "bottomBar"
        ) { selecting ->
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (!selecting) {
                    BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "后退", canBack, onBack)
                    BarIcon(Icons.Outlined.ArrowForward, "前进", canForward, onForward)
                    BarIcon(Icons.Outlined.Add, "新建", true, onNew)
                    BarIcon(Icons.Outlined.SwapHoriz, "交换窗口", true, onSwap)
                    BarIcon(Icons.Outlined.ArrowUpward, "上一级", canUp, onUp)
                } else {
                    BarIcon(Icons.Outlined.ContentCopy, "复制", true, onCopy)
                    BarIcon(Icons.Outlined.ContentCut, "剪切", true, onCut)
                    BarIcon(Icons.Outlined.DriveFileRenameOutline, "重命名", selectedCount == 1, onRename)
                    BarIcon(Icons.Outlined.Delete, "删除", true, onDelete, danger = true)
                    BarIcon(Icons.Outlined.Close, "取消", true, onCancel)
                }
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
        !enabled -> SunColors.TextTertiary.copy(alpha = 0.35f)
        danger -> SunColors.Danger
        else -> SunColors.TextSecondary
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressScale(interaction, 0.86f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
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
    bookmarks: List<String>,
    recent: List<String>,
    onNavigate: (String) -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAbout: () -> Unit
) {
    val shortcuts = listOf(
        Triple("内部存储", "/storage/emulated/0", Icons.Outlined.Storage),
        Triple("下载", "/storage/emulated/0/Download", Icons.Outlined.Download),
        Triple("文档", "/storage/emulated/0/Documents", Icons.Outlined.Description),
        Triple("图片", "/storage/emulated/0/Pictures", Icons.Outlined.Image),
        Triple("音乐", "/storage/emulated/0/Music", Icons.Outlined.MusicNote),
        Triple("电影", "/storage/emulated/0/Movies", Icons.Outlined.VideoLibrary),
        Triple("根目录", "/", Icons.Outlined.Folder)
    )
    val existing = shortcuts.filter { File(it.second).isDirectory }
    ModalDrawerSheet(
        drawerContainerColor = SunColors.Surface3,
        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.width(300.dp)
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Folder,
                        null,
                        tint = SunColors.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("SunDocument", color = SunColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("v$version · 双栏文件管理", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
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
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 5.dp)
            )
            existing.forEach { (label, path, icon) ->
                DrawerItem(
                    icon = icon,
                    label = label,
                    selected = currentPath == path,
                    onClick = { onNavigate(path) }
                )
            }

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "最近访问",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                )
                recent.filter { it != currentPath }.take(4).forEach { path ->
                    DrawerItem(
                        icon = Icons.Outlined.History,
                        label = path.substringAfterLast('/').ifEmpty { "/" },
                        selected = false,
                        onClick = { onNavigate(path) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))
            Spacer(Modifier.height(5.dp))

            DrawerItem(
                Icons.Outlined.BookmarkBorder,
                if (bookmarks.isEmpty()) "收藏夹" else "收藏夹（${bookmarks.size}）",
                false,
                onBookmarks
            )
            DrawerItem(Icons.Outlined.Settings, "设置", false, onSettings)
            DrawerItem(Icons.Outlined.SystemUpdate, "检查更新", false, onCheckUpdate)
            DrawerItem(Icons.Outlined.Info, "关于", false, onAbout)

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
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    if (path.length <= 34) return path
    val tail = path.takeLast(30)
    val cut = tail.indexOf('/')
    return "…" + if (cut >= 0) tail.substring(cut) else tail
}

internal fun openExternally(context: Context, file: File): Boolean {
    val mime = FileRepo.mimeOf(file)
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

internal fun shareFile(context: Context, file: File): Boolean {
    return runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = FileRepo.mimeOf(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
        true
    }.getOrDefault(false)
}
