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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
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
import com.suibian.sunfile.data.FileKind
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.OpenMode
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.data.StorageBook
import com.suibian.sunfile.data.NetworkMount
import com.suibian.sunfile.data.LocalMount
import com.suibian.sunfile.data.DEFAULT_GROUP
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.components.FolderDotsIcon
import com.suibian.sunfile.ui.components.HamburgerIcon
import com.suibian.sunfile.ui.components.MaterialFileIcon
import com.suibian.sunfile.ui.components.RollingNumber
import com.suibian.sunfile.ui.components.ShimmerList
import com.suibian.sunfile.ui.components.FileTypeBadge
import com.suibian.sunfile.ui.components.iconOf
import com.suibian.sunfile.ui.components.pressScale
import com.suibian.sunfile.ui.components.tintOf
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.Mt
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
    data object Terminal : Page
    data object Changelog : Page
    data object Trash : Page
    data object Saf : Page
    data object SysInfo : Page
    data object Plugins : Page
    data object Mcp : Page
    data object Inspect : Page
    data object MtPlugins : Page
    data object Apps : Page
    data object Remote : Page
    data object Signing : Page
    data object SmaliRef : Page
    data object Passwords : Page
    data object Stream : Page
    data object Payload : Page
    data object ColorPick : Page
    data object Music : Page
}

/** 内置全屏查看器 */
private sealed interface Viewer {
    data class Hex(val file: File) : Viewer
    data class Image(val file: File, val playlist: List<File>) : Viewer
    data class Archive(val file: File, val prefix: String = "") : Viewer
    data class Audio(val file: File, val playlist: List<File>) : Viewer
    data class Video(val file: File, val playlist: List<File>) : Viewer
    data class Doc(val file: File) : Viewer
    data class Pdf(val file: File) : Viewer
    data class Compare(val left: File, val right: File) : Viewer
    data class Reverse(val file: File, val tab: Int = 0) : Viewer
    data class Arsc(val source: ArscSource) : Viewer
    data class AxmlEdit(val file: File, val entry: String = "AndroidManifest.xml") : Viewer
    data class Smali(val file: File) : Viewer
    data class NinePatch(val file: File) : Viewer
    data class Web(val file: File) : Viewer
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
    // 在组合上下文中读一次，供下方非 @Composable 的点击回调使用
    val darkNow = SunColors.dark

    // 列表密度与时间格式：设置一改这里立刻反映到每一行
    SideEffect {
        BottomBarStyle.extra = if (prefs.bottomBarExtra) prefs.bottomBarPadding.dp else 0.dp
        com.suibian.sunfile.data.Shell.suCommand = prefs.customSuCommand.ifBlank { "su" }
        com.suibian.sunfile.ui.components.Thumbs.enabled = prefs.externalThumbs
        ListStyleHolder.style = ListStyle(
            rowHeight = when (prefs.fileListSize) {
                -1 -> Mt.RowHeight - 8.dp
                1 -> Mt.RowHeight + 12.dp
                else -> Mt.RowHeight
            },
            nameLines = prefs.fileNameMaxLines,
            dateFormat = prefs.dateFormat,
            timeByCreated = prefs.timeByCreated
        )
    }

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
    var contextMenu by remember { mutableStateOf<Pair<FileEntry, Offset>?>(null) }
    var archiveVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var trashTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var safPath by remember { mutableStateOf("primary:Android/data") }
    var pluginOutput by remember { mutableStateOf<String?>(null) }
    var zipEdit by remember { mutableStateOf<ZipEdit?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var storeDialog by remember { mutableStateOf<StorageDialog?>(null) }
    var networkTarget by remember { mutableStateOf<NetworkMount?>(null) }
    var toolPicker by remember { mutableStateOf(false) }

    // 本地存储不再手输路径，直接跳系统目录选择器，拿 SAF 授权
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
                ?: "外部目录"
            val path = safTreeToPath(uri) ?: uri.toString()
            val ok = StorageBook.addLocal(name, path, uri.toString())
            Toast.makeText(
                context,
                if (ok) "已添加 $name" else "该目录已在列表中",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // 从压缩包内打开文件时记住来源，关掉查看器/编辑器后回到原来的压缩包位置
    var archiveReturn by remember { mutableStateOf<ArchiveReturn?>(null) }
    var archivePrefix by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        StorageBook.ensure(context)
        vm.init()
        // 上次已经授过 Shizuku 的话，这里静默恢复，不必让用户再点一次
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (com.suibian.sunfile.data.ShizukuBridge.usable()) {
                com.suibian.sunfile.data.Shell.requestShizuku()
            }
        }
    }

    // Shizuku 的授权框由它自己弹，结果在这里统一收口：
    // 授权成功后立刻把文件操作切到 Shizuku 引擎，并重读当前目录。
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { granted ->
            if (granted) {
                scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.suibian.sunfile.data.Shell.requestShizuku()
                    }
                    Toast.makeText(
                        context,
                        if (ok) "Shizuku 已接管，可读取根目录" else "Shizuku 通道不可用",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) vm.refreshAll()
                }
            }
        }
        com.suibian.sunfile.data.ShizukuBridge.addResultListener(listener)
        onDispose { com.suibian.sunfile.data.ShizukuBridge.removeResultListener(listener) }
    }

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
    BackHandler(enabled = drawerState.isClosed && page != Page.Files) {
        page = when (page) {
            Page.Changelog, Page.Plugins, Page.Trash, Page.Mcp, Page.Inspect, Page.MtPlugins -> Page.Settings
            else -> Page.Files
        }
    }
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

    // 同一目录下的同类文件，供查看器左右滑动切换
    fun playlistOf(file: File, kind: FileKind): List<File> {
        val list = panes[active].entries
            .filter { !it.isDir && FileKinds.kindOfName(it.name) == kind }
            .map { it.file }
        return if (list.any { it.absolutePath == file.absolutePath }) list else listOf(file)
    }

    /** 关闭当前查看器：有压缩包来源就先回到压缩包，否则退回文件列表 */
    fun closeViewer() {
        val back = archiveReturn
        archiveReturn = null
        viewer = if (back != null) Viewer.Archive(back.file, back.prefix) else null
    }

    fun openWith(file: File, mode: OpenMode, origin: Offset = Offset.Zero) {
        val ext = FileKinds.extensionOf(file)
        // 矢量图与网页：用 WebView 渲染，比当文本打开更接近真实效果
        if (ext in setOf("svg", "html", "htm", "xhtml") &&
            mode != OpenMode.TEXT && mode != OpenMode.HEX && mode != OpenMode.ARCHIVE
        ) {
            viewer = Viewer.Web(file)
            return
        }
        when (mode) {
            OpenMode.TEXT -> {
                editorOrigin = origin
                editorTarget = file
            }
            OpenMode.HEX -> viewer = Viewer.Hex(file)
            OpenMode.IMAGE -> viewer = Viewer.Image(file, playlistOf(file, FileKind.IMAGE))
            OpenMode.AUDIO -> viewer = Viewer.Audio(file, playlistOf(file, FileKind.AUDIO))
            OpenMode.VIDEO -> viewer = Viewer.Video(file, playlistOf(file, FileKind.VIDEO))
            OpenMode.DOCUMENT -> viewer = Viewer.Doc(file)
            OpenMode.PDF -> viewer = Viewer.Pdf(file)
            OpenMode.ARCHIVE -> viewer = Viewer.Archive(file)
            OpenMode.AXML -> viewer = Viewer.Reverse(file, 1)
            OpenMode.DEX -> viewer = Viewer.Reverse(file, 2)
            OpenMode.INFO -> viewer = Viewer.Reverse(file, 0)
            OpenMode.ARSC -> viewer = Viewer.Arsc(
                if (file.name.lowercase().endsWith(".apk")) ArscSource(file, "resources.arsc")
                else ArscSource(file)
            )
            OpenMode.NINEPATCH -> viewer = Viewer.NinePatch(file)
            OpenMode.AXML_EDIT -> viewer = Viewer.AxmlEdit(file)
            OpenMode.SMALI -> viewer = Viewer.Smali(file)
            OpenMode.EXTERNAL -> if (!openExternally(context, file)) openFailure = file
        }
    }

    fun openFile(file: File, origin: Offset) {
        val ext = FileKinds.extensionOf(file)
        if (file.name.lowercase().endsWith(".9.png")) {
            viewer = Viewer.NinePatch(file)
            return
        }
        // 网页 / Markdown 由设置决定：询问、直接预览、直接编辑
        if (ext in setOf("html", "htm", "xhtml", "md", "markdown")) {
            when (prefs.htmlMode) {
                "PREVIEW" -> {
                    viewer = Viewer.Web(file)
                    return
                }
                "EDIT" -> {
                    editorOrigin = origin
                    editorTarget = file
                    return
                }
            }
        }
        val remembered = prefs.openWith[ext]
        // 早期版本把 Office / 压缩包记成了「用其他应用打开」，这些旧记忆一律作废
        val stale = remembered == OpenMode.EXTERNAL &&
            (FileKinds.isDocumentReadable(file) || FileKinds.isZipReadable(file))
        val mode = (if (stale) null else remembered) ?: FileKinds.defaultMode(file)
        if (mode == null) {
            openWithTarget = file
        } else {
            openWith(file, mode, origin)
        }
    }

    /** 从文件列表/搜索结果打开：先清掉压缩包来源，避免关掉查看器后莫名回到压缩包 */
    fun openFromList(file: File, origin: Offset) {
        archiveReturn = null
        openFile(file, origin)
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
            val store by StorageBook.config.collectAsStateWithLifecycle()
            ModalDrawerSheet(
                drawerContainerColor = SunColors.TopBar,
                drawerShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                modifier = Modifier.width(318.dp)
            ) {
                SidebarContent(
                    currentPath = pane.path,
                    dark = darkNow,
                    themeMode = prefs.themeMode,
                    locals = store.locals,
                    networks = store.networks,
                    bookmarks = prefs.bookmarks,
                    showBookmarks = prefs.sidebarBookmarks,
                    tools = AppTool.resolve(prefs.sidebarTools),
                    onNavigate = { path ->
                        vm.navigate(path)
                        page = Page.Files
                        scope.launch { drawerState.close() }
                    },
                    onToggleTheme = {
                        vm.prefsStore.setThemeMode(
                            if (darkNow) com.suibian.sunfile.ui.theme.ThemeMode.LIGHT
                            else com.suibian.sunfile.ui.theme.ThemeMode.DARK
                        )
                    },
                    onFollowSystem = {
                        vm.prefsStore.setThemeMode(com.suibian.sunfile.ui.theme.ThemeMode.SYSTEM)
                    },
                    onAddLocal = { folderPicker.launch(null) },
                    onAddNetwork = { storeDialog = StorageDialog.AddNetwork },
                    onManageGroups = { storeDialog = StorageDialog.ManageGroups },
                    onManageTools = { toolPicker = true },
                    onSettings = { page = Page.Settings; scope.launch { drawerState.close() } },
                    onOpenTool = { tool ->
                        if (tool == AppTool.EDITOR) {
                            val tmp = File(context.cacheDir, "未命名-${System.currentTimeMillis() / 1000}.txt")
                            runCatching { tmp.createNewFile() }
                            editorOrigin = Offset.Zero
                            editorTarget = tmp
                            page = Page.Files
                        } else {
                            page = when (tool) {
                                AppTool.PLUGINS -> Page.Plugins
                                AppTool.TERMINAL -> Page.Terminal
                                AppTool.REMOTE -> Page.Remote
                                AppTool.APPS -> Page.Apps
                                AppTool.MCP -> Page.Mcp
                                AppTool.COLOR -> Page.ColorPick
                                AppTool.SIGN -> Page.Signing
                                AppTool.SMALI -> Page.SmaliRef
                                AppTool.PASSWORD -> Page.Passwords
                                AppTool.MUSIC -> Page.Music
                                AppTool.STREAM -> Page.Stream
                                AppTool.PAYLOAD -> Page.Payload
                                AppTool.SYSINFO -> Page.SysInfo
                                AppTool.TRASH -> Page.Trash
                                AppTool.EDITOR -> Page.Files
                            }
                        }
                        scope.launch { drawerState.close() }
                    },
                    onOpenNetwork = { net ->
                        networkTarget = net
                        scope.launch { drawerState.close() }
                    },
                    onRemoveLocal = { StorageBook.removeLocal(it) },
                    onRemoveNetwork = { StorageBook.removeNetwork(it.uri) },
                    onOpenBookmark = { path ->
                        vm.navigate(path)
                        page = Page.Files
                        scope.launch { drawerState.close() }
                    }
                )
            }
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
                                openFromList(entry.file, origin)
                            }
                        },
                        onLongPress = { index, entry, origin ->
                            vm.setActive(index)
                            contextMenu = entry to origin
                        },
                        onToggleSelect = { index, entry ->
                            vm.setActive(index)
                            vm.toggleSelect(entry, index)
                        },
                        onUp = { index -> vm.goUp(index) },
                        onRefresh = { index -> vm.refreshPane(index) },
                        onToggleBookmark = { path -> vm.toggleBookmark(path) },
                        onBack = { vm.goBack() },
                        onForward = { vm.goForward() },
                        onNew = { sheet = SheetKind.New },
                        onSwap = { vm.swapPanes() },
                        onSync = { vm.syncPanes() },
                        onSelectionCopy = { vm.setClipboard(false) },
                        onSelectionCut = { vm.setClipboard(true) },
                        onSelectionDelete = { dialog = DialogKind.Delete(pane.selected.toList()) },
                        onSelectionRename = {
                            pane.selected.singleOrNull()?.let { dialog = DialogKind.Rename(File(it)) }
                        },
                        onClearSelection = { vm.clearSelection() },
                        // 顶栏工具
                        onRefreshActive = { vm.refresh() },
                        onSearch = { vm.openSearch(); searchOpen = true },
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
                        },
                        onQuickSelect = { index, pos -> vm.selectAt(pos, index) },
                        onDragSelect = { index, a, b -> vm.selectRange(a, b, index) },
                        onFilter = { dialog = DialogKind.Filter(pane.filter) },
                        onClearFilter = { vm.clearFilter() },
                        onGoto = { dialog = DialogKind.Goto(pane.path) },
                        onSelectSimilar = { vm.selectSimilar() },
                        onShareSelection = {
                            val targets = pane.selected.map { File(it) }
                            if (targets.isEmpty()) {
                                Toast.makeText(context, "先选中文件", Toast.LENGTH_SHORT).show()
                            } else if (targets.size == 1) {
                                if (!shareFile(context, targets.first())) {
                                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                shareFiles(context, targets)
                            }
                        },
                        onCompareSelection = {
                            val targets = pane.selected.map { File(it) }
                            if (targets.size != 2) {
                                Toast.makeText(context, "请选中两个文件", Toast.LENGTH_SHORT).show()
                            } else {
                                viewer = Viewer.Compare(targets[0], targets[1])
                            }
                        }
                    )

                    Page.Search -> Unit

                    Page.Terminal -> TerminalPage(
                        onBack = { page = Page.Files },
                        onOpenPath = { path ->
                            vm.navigate(path)
                            page = Page.Files
                        }
                    )

                    Page.Changelog -> ChangelogPage(onBack = { page = Page.Settings })

                    Page.Saf -> SafPage(
                        path = safPath,
                        label = if (safPath.endsWith("obb")) "Android/obb" else "Android/data",
                        onBack = { page = Page.Files },
                        onOpen = { f -> openFromList(f, Offset.Zero) },
                        onExport = { node ->
                            val dest = panes[active].currentDir
                            scope.launch {
                                val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.suibian.sunfile.data.SafRepo.copyTo(context, node, dest)
                                }
                                Toast.makeText(
                                    context,
                                    if (out != null) "已导出到 ${dest.name}" else "导出失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                                vm.refreshAll()
                            }
                        }
                    )

                    Page.SysInfo -> SystemInfoPage(onBack = { page = Page.Files })

                    Page.Plugins -> PluginPage(onBack = { page = Page.Files }, onMtPlugins = { page = Page.MtPlugins })

                    Page.Mcp -> McpPage(onBack = { page = Page.Settings })

                    Page.Inspect -> InspectPage(onBack = { page = Page.Settings })

                    Page.MtPlugins -> MtPluginPage(onBack = { page = Page.Plugins })

                    Page.Apps -> AppsPage(onBack = { page = Page.Files })

                    Page.Remote -> RemotePage(onBack = { page = Page.Files }, defaultRoot = pane.path)

                    Page.Signing -> SigningPage(onBack = { page = Page.Files })

                    Page.SmaliRef -> SmaliRefPage(onBack = { page = Page.Files })

                    Page.Passwords -> PasswordPage(onBack = { page = Page.Files })

                    Page.Stream -> StreamPage(onBack = { page = Page.Files })

                    Page.Payload -> PayloadPage(onBack = { page = Page.Files })

                    Page.ColorPick -> ColorPickerPage(onBack = { page = Page.Files })

                    Page.Music -> MusicPage(onBack = { page = Page.Files })

                    Page.Trash -> {
                        val items = remember(trashTick) { vm.trashItems() }
                        TrashPage(
                            items = items,
                            onBack = { page = Page.Settings },
                            onRestore = { item -> vm.restoreTrash(item) { trashTick++ } },
                            onPurge = { item -> vm.purgeTrash(item) { trashTick++ } },
                            onEmpty = { vm.emptyTrash { trashTick++ } }
                        )
                    }

                    Page.Settings -> SettingsPage(
                        prefs = prefs,
                        apply = { block -> vm.updatePrefs(block) },
                        onBack = { page = Page.Files },
                        onRequestRoot = {
                            scope.launch {
                                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.suibian.sunfile.data.Shell.requestSu()
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) "已获得 Root" else "未获得 Root",
                                    Toast.LENGTH_SHORT
                                ).show()
                                vm.refreshAll()
                            }
                        },
                        onRequestShizuku = {
                            when {
                                !com.suibian.sunfile.data.ShizukuBridge.installed(context) ->
                                    Toast.makeText(context, "请先安装 Shizuku", Toast.LENGTH_SHORT).show()
                                !com.suibian.sunfile.data.ShizukuBridge.running() ->
                                    Toast.makeText(context, "请先在 Shizuku 里启动服务", Toast.LENGTH_SHORT).show()
                                !com.suibian.sunfile.data.ShizukuBridge.permitted() ->
                                    com.suibian.sunfile.data.ShizukuBridge.requestPermission()
                                else -> scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.suibian.sunfile.data.Shell.requestShizuku()
                                    }
                                    vm.refreshAll()
                                }
                            }
                        },
                        onInstallShizuku = {
                            val pkg = com.suibian.sunfile.data.ShizukuBridge.PACKAGE
                            val launched = runCatching {
                                val i = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (i != null) {
                                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    true
                                } else false
                            }.getOrDefault(false)
                            if (!launched) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        },
                        onCheckUpdate = { updateVm.check(silent = false, useAccelerator = prefs.useAccelerator) },
                        onChangelog = { page = Page.Changelog },
                        onAbout = { dialog = DialogKind.About },
                        onOpenSource = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/suibian-sun/SunDocument")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onThanks = { dialog = DialogKind.About },
                        onAppInfo = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onTrash = { page = Page.Trash },
                        dataDir = remember {
                            runCatching { com.suibian.sunfile.data.Mt2.root(context).absolutePath }
                                .getOrDefault("")
                        }
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

    // ---------------- 长按菜单 ----------------

    contextMenu?.let { (entry, anchorPos) ->
        val target = entry.file
        val paths = listOf(entry.path)
        val isApk = target.extension.equals("apk", true)
        val selectedFiles = remember(entry.path, pane.selectionMode, pane.selected.size) {
            if (pane.selectionMode && pane.selected.isNotEmpty()) {
                panes[active].entries.filter { pane.selected.contains(it.path) }.map { it.file }
            } else {
                listOf(target)
            }
        }
        val pluginActions = remember(entry.path, selectedFiles) {
            runCatching { com.suibian.sunfile.data.Plugins.actionsFor(context, selectedFiles) }.getOrDefault(emptyList())
        }
        EntryActionMenu(
            entry = entry,
            anchor = anchorPos,
            onDismiss = { contextMenu = null },
            actions = buildList {
                add(
                    EntryAction(
                        icon = if (entry.isDir) Icons.Outlined.FolderOpen else Icons.Outlined.OpenInNew,
                        label = if (entry.isDir) "进入文件夹" else "打开"
                    ) {
                        if (entry.isDir) vm.open(active, entry.path) else openFromList(target, anchorPos)
                    }
                )
                if (!entry.isDir) {
                    add(EntryAction(Icons.Outlined.OpenInNew, "打开方式…") { openWithTarget = target })
                }
                add(EntryAction(Icons.Outlined.ContentCopy, "复制到另一栏") { vm.copyToOtherPane(paths) })
                add(EntryAction(Icons.Outlined.ContentCut, "移动到另一栏") { vm.moveToOtherPane(paths) })
                add(EntryAction(Icons.Outlined.ContentCopy, "复制") {
                    vm.selectOnly(entry)
                    vm.setClipboard(false)
                })
                add(EntryAction(Icons.Outlined.ContentCut, "剪切") {
                    vm.selectOnly(entry)
                    vm.setClipboard(true)
                })
                add(EntryAction(Icons.Outlined.DriveFileRenameOutline, "重命名") {
                    dialog = DialogKind.Rename(target)
                })
                add(EntryAction(Icons.Outlined.Archive, "压缩为 ZIP") {
                    dialog = DialogKind.Compress(paths)
                })
                if (!entry.isDir && FileKinds.isZipReadable(target)) {
                    add(EntryAction(Icons.Outlined.Unarchive, "解压到当前目录") { vm.extractHere(target) })
                }
                if (isApk) {
                    add(EntryAction(Icons.Outlined.Android, "安装") {
                        if (!installApk(context, target)) {
                            Toast.makeText(context, "无法启动安装程序", Toast.LENGTH_SHORT).show()
                        }
                    })
                    add(EntryAction(Icons.Outlined.Info, "安装包信息") {
                        viewer = Viewer.Reverse(target, 0)
                    })
                    add(EntryAction(Icons.Outlined.Description, "反编译 AndroidManifest") {
                        viewer = Viewer.Reverse(target, 1)
                    })
                    add(EntryAction(Icons.Outlined.Terminal, "Dex 结构浏览") {
                        viewer = Viewer.Reverse(target, 2)
                    })
                }
                if (!entry.isDir) {
                    add(EntryAction(Icons.Outlined.Description, "用文本打开") {
                        openWith(target, OpenMode.TEXT)
                    })
                    add(EntryAction(Icons.Outlined.Share, "分享") {
                        if (!shareFile(context, target)) {
                            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                add(EntryAction(Icons.Outlined.Info, "属性") {
                    dialog = DialogKind.Properties(target)
                })
                add(EntryAction(Icons.Outlined.SelectAll, "多选") { vm.selectOnly(entry) })
                add(EntryAction(Icons.Outlined.ContentCopy, "复制路径") { copyPath(entry.path) })
                add(
                    EntryAction(
                        Icons.Outlined.BookmarkBorder,
                        if (prefs.bookmarks.contains(if (entry.isDir) entry.path else (target.parent ?: entry.path)))
                            "取消收藏" else "收藏当前目录"
                    ) {
                        vm.toggleBookmark(if (entry.isDir) entry.path else (target.parent ?: entry.path))
                    }
                )
                pluginActions.forEach { act ->
                    add(
                        EntryAction(
                            Icons.Outlined.Code,
                            act.label,
                            act.pluginName
                        ) {
                            scope.launch {
                                val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        com.suibian.sunfile.data.Plugins.run(
                                            context,
                                            act,
                                            selectedFiles,
                                            com.suibian.sunfile.data.Shell.rootAvailable
                                        )
                                    }.getOrElse { e -> "执行失败：${e.message}" }
                                }
                                pluginOutput = out?.ifBlank { "没有输出" } ?: "没有输出"
                            }
                        }
                    )
                }
                add(EntryAction(Icons.Outlined.Delete, "删除", danger = true) {
                    dialog = DialogKind.Delete(paths)
                })
            }
        )
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
                if (s.entry.isDir) vm.navigate(s.entry.path) else openFromList(s.entry.file, Offset.Zero)
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
            onDelete = { sheet = null; dialog = DialogKind.Delete(listOf(s.entry.path)) },
            onInstall = {
                sheet = null
                if (!installApk(context, s.entry.file)) {
                    Toast.makeText(context, "无法启动安装程序", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenAsText = { sheet = null; openWith(s.entry.file, OpenMode.TEXT) }
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
                vm.setOpenWith(FileKinds.extensionOf(s.file), mode)
                sheet = null
                openWith(s.file, mode)
            },
            onSetDefault = { mode ->
                vm.setOpenWith(FileKinds.extensionOf(s.file), mode)
                sheet = null
                Toast.makeText(context, "已设为默认：${mode.label}", Toast.LENGTH_SHORT).show()
            },
            onManage = { sheet = SheetKind.OpenWithManager }
        )

        is SheetKind.OpenWithManager -> OpenWithManagerSheet(
            mapping = prefs.openWith,
            onRemove = { ext -> vm.setOpenWith(ext, null) },
            onClearAll = { vm.clearAllOpenWith(); sheet = null },
            onDismiss = { sheet = null }
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
                vm.setOpenWith(FileKinds.extensionOf(file), mode)
                openWithTarget = null
                openWith(file, mode)
            },
            onSetDefault = { mode ->
                vm.setOpenWith(FileKinds.extensionOf(file), mode)
                openWithTarget = null
                Toast.makeText(context, "已设为默认：${mode.label}", Toast.LENGTH_SHORT).show()
            },
            onManage = {
                openWithTarget = null
                sheet = SheetKind.OpenWithManager
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

        is DialogKind.Delete -> DeleteDialog(
            count = d.paths.size,
            names = d.paths.take(4).joinToString("\n") { File(it).name } +
                if (d.paths.size > 4) "\n…" else "",
            onDismiss = { dialog = null },
            onConfirm = { toTrash ->
                dialog = null
                vm.deletePaths(d.paths, trash = toTrash)
            }
        )

        is DialogKind.Properties -> PropertiesDialog(
            file = d.file,
            loader = { vm.propertiesOf(it) },
            onDismiss = { dialog = null }
        )

        is DialogKind.Compress -> CompressDialog(
            paths = d.paths,
            onConfirm = { name, format, level, password ->
                dialog = null
                vm.compressAs(d.paths, name, format, level, password)
            },
            onDismiss = { dialog = null }
        )

        is DialogKind.BatchRename -> BatchRenameDialog(
            paths = d.paths,
            onConfirm = { expr ->
                dialog = null
                vm.renameWithExpr(d.paths, expr)
            },
            onDismiss = { dialog = null }
        )

        is DialogKind.ZipRename -> InputDialog(
            title = "重命名压缩包条目",
            label = "完整路径（改父目录即移动）",
            initial = d.entry,
            confirmText = "保存",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                val target = name.trim()
                if (target.isNotEmpty() && target != d.entry) {
                    vm.zipRenameEntry(d.zip, d.entry, target) { archiveVersion++ }
                }
            }
        )

        is DialogKind.Filter -> InputDialog(
            title = "过滤当前列表",
            label = "关键字 / 正则 / !否定",
            initial = d.current,
            confirmText = "应用",
            onDismiss = { dialog = null },
            onConfirm = { text ->
                dialog = null
                vm.setFilter(text)
            }
        )

        is DialogKind.Goto -> InputDialog(
            title = "跳转到路径",
            label = "绝对路径",
            initial = d.current,
            confirmText = "跳转",
            onDismiss = { dialog = null },
            onConfirm = { text ->
                dialog = null
                if (text.isNotBlank()) vm.navigate(text.trim())
            }
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

    // 插件执行结果
    pluginOutput?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pluginOutput = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = { Text("插件输出", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text,
                        color = SunColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("plugin", text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    pluginOutput = null
                }) { Text("复制", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pluginOutput = null }) {
                    Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
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
                    vm.setOpenWith(FileKinds.extensionOf(file), mode)
                    openFailure = null
                    openWith(file, mode)
                },
                onSetDefault = { mode ->
                    vm.setOpenWith(FileKinds.extensionOf(file), mode)
                    openFailure = null
                    Toast.makeText(context, "已设为默认：${mode.label}", Toast.LENGTH_SHORT).show()
                },
                onManage = {
                    openFailure = null
                    sheet = SheetKind.OpenWithManager
                }
            )
        }
    }

    // 侧边栏工具管理
    if (toolPicker) {
        ToolPickerDialog(
            selected = prefs.sidebarTools.ifEmpty { AppTool.DEFAULTS.map { it.id } },
            onDismiss = { toolPicker = false },
            onConfirm = { ids ->
                vm.prefsStore.setSidebarTools(ids)
                toolPicker = false
            }
        )
    }

    // 侧边栏：添加存储 / 分组管理
    storeDialog?.let { kind ->
        val store by StorageBook.config.collectAsStateWithLifecycle()
        StorageDialogHost(
            kind = kind,
            groups = store.groups,
            onDismiss = { storeDialog = null },
            onAddLocal = { name, path ->
                val ok = StorageBook.addLocal(name, path)
                Toast.makeText(context, if (ok) "已添加本地存储" else "路径已存在", Toast.LENGTH_SHORT).show()
                storeDialog = null
            },
            onAddNetwork = { name, uri, group ->
                val ok = StorageBook.addNetwork(name, uri, group)
                Toast.makeText(context, if (ok) "已添加网络存储" else "链接已存在", Toast.LENGTH_SHORT).show()
                storeDialog = null
            },
            onAddGroup = { name ->
                val ok = StorageBook.addGroup(name)
                Toast.makeText(context, if (ok) "已创建分组「$name」" else "分组已存在", Toast.LENGTH_SHORT).show()
                storeDialog = null
            },
            onRenameGroup = { from, to ->
                val ok = StorageBook.renameGroup(from, to)
                if (!ok) Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
            },
            onDeleteGroup = { StorageBook.removeGroup(it) }
        )
    }

    // 网络存储：只有名称和链接，点开就是链接本身
    networkTarget?.let { net ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { networkTarget = null },
            containerColor = SunColors.Surface2,
            shape = SunShapes.Medium,
            title = { Text(net.name, color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        net.uri,
                        color = SunColors.Accent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("分组：${net.group}", color = SunColors.TextTertiary, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Row {
                    Text(
                        "打开",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable {
                                val uri = net.uri
                                val ok = runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(normalizeLink(uri)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.isSuccess
                                Toast.makeText(
                                    context,
                                    if (ok) "已交给系统打开" else "没有能打开该链接的应用",
                                    Toast.LENGTH_SHORT
                                ).show()
                                networkTarget = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        "复制",
                        color = SunColors.Accent,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("network", net.uri))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                networkTarget = null
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
                        .clip(SunShapes.Small)
                        .clickable { networkTarget = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }

    // 搜索弹窗
    if (searchOpen) {
        SearchDialog(
            state = searchState,
            history = prefs.recentSearches,
            favorites = prefs.searchFavorites,
            showFavorites = prefs.inputFavorites,
            onDismiss = { searchOpen = false },
            onQueryChange = { vm.updateQuery(it) },
            onOptionsChange = { vm.setSearchOptions(it) },
            onSearch = { vm.startSearch() },
            onCancel = { vm.cancelSearch() },
            onResult = { entry ->
                searchOpen = false
                vm.revealPath(entry)
            },
            onHistoryPick = { vm.updateQuery(it) },
            onClearHistory = { vm.prefsStore.clearSearches() },
            onToggleFavorite = { vm.prefsStore.toggleSearchFavorite(it) },
            onRemoveFavorite = { vm.prefsStore.removeSearchFavorite(it) },
            onClearFavorites = { vm.prefsStore.clearSearchFavorites() }
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
                onDismiss = { updateVm.dismiss() },
                onHistory = {
                    updateVm.dismiss()
                    page = Page.Changelog
                }
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
                val pending = zipEdit
                zipEdit = null
                editorTarget = null
                editorLine = 0
                if (pending != null) {
                    scope.launch {
                        val now = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { hashOf(pending.temp) }.getOrDefault("")
                        }
                        if (now != pending.hash) {
                            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { pending.temp.readBytes() }.getOrNull()
                            }
                            if (bytes != null) {
                                vm.zipPutEntry(pending.zip, pending.entry, bytes) { archiveVersion++ }
                            }
                        }
                    }
                }
                vm.refreshAll()
                val back = archiveReturn
                archiveReturn = null
                if (back != null) viewer = Viewer.Archive(back.file, back.prefix)
            }
        )
    }

    // 查看器
    when (val v = viewer) {
        is Viewer.Hex -> HexEditorOverlay(v.file) { closeViewer() }
        is Viewer.Compare -> CompareOverlay(v.left, v.right) { closeViewer() }
        is Viewer.Reverse -> ReverseOverlay(
            file = v.file,
            startTab = v.tab,
            onEditResources = { source -> viewer = Viewer.Arsc(source) },
            onClose = { closeViewer() }
        )
        is Viewer.Arsc -> ArscOverlay(v.source) { closeViewer() }
        is Viewer.NinePatch -> NinePatchOverlay(v.file) { closeViewer() }
        is Viewer.AxmlEdit -> AxmlEditorOverlay(v.file, v.entry) { closeViewer() }
        is Viewer.Smali -> SmaliOverlay(
            apk = v.file,
            prefs = prefs,
            onEditorPrefs = { size, wrap, lines, comp -> vm.setEditorPrefs(size, wrap, lines, comp) },
            onClose = { closeViewer() }
        )
        is Viewer.Web -> WebPreviewOverlay(v.file) { closeViewer() }
        is Viewer.Image -> ImageViewerOverlay(v.file, v.playlist) { closeViewer() }
        is Viewer.Audio -> AudioSheetOverlay(v.file, v.playlist) { closeViewer() }
        is Viewer.Video -> VideoViewerOverlay(v.file, v.playlist) { closeViewer() }
        is Viewer.Pdf -> PdfViewerOverlay(v.file) { closeViewer() }
        is Viewer.Doc -> DocumentViewerOverlay(
            file = v.file,
            onClose = { closeViewer() },
            onOpenExternal = { f ->
                viewer = null
                if (!openExternally(context, f)) openWithTarget = f
            }
        )
        is Viewer.Archive -> ArchiveOverlay(
            file = v.file,
            destDir = panes[active].currentDir,
            opVersion = archiveVersion,
            initialPrefix = v.prefix,
            onPrefixChange = { archivePrefix = it },
            onClose = { closeViewer() },
            onExtractAll = { pw ->
                val dest = panes[active].currentDir
                archiveReturn = null
                viewer = null
                vm.extractArchive(v.file, dest, pw)
            },
            onOpenEntry = { temp, entryName ->
                archiveReturn = ArchiveReturn(v.file, archivePrefix)
                viewer = null
                val mode = prefs.openWith[FileKinds.extensionOf(temp)] ?: FileKinds.defaultMode(temp)
                if (mode == OpenMode.TEXT) {
                    scope.launch {
                        val before = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { hashOf(temp) }.getOrDefault("")
                        }
                        zipEdit = ZipEdit(v.file, entryName, temp, before)
                        editorOrigin = Offset.Zero
                        editorTarget = temp
                    }
                } else {
                    openFile(temp, Offset.Zero)
                }
            },
            onRenameRequest = { entry -> dialog = DialogKind.ZipRename(v.file, entry) },
            onDeleteEntry = { entry ->
                vm.zipDeleteEntries(v.file, listOf(entry)) { archiveVersion++ }
            },
            onAddFiles = {
                val paths = panes[active].selected.toList()
                if (paths.isEmpty()) {
                    Toast.makeText(context, "先在文件列表选中要添加的文件", Toast.LENGTH_SHORT).show()
                } else {
                    vm.zipAddFiles(v.file, paths) { archiveVersion++ }
                }
            }
        )
        null -> Unit
    }
}

/** 从压缩包打开的文件：编辑保存后据此写回条目 */
private data class ZipEdit(val zip: File, val entry: String, val temp: File, val hash: String)

/** 压缩包内的当前位置：文件 + 目录前缀 */
private data class ArchiveReturn(val file: File, val prefix: String)

private fun hashOf(file: File): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
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
    onLongPress: (Int, FileEntry, Offset) -> Unit,
    onToggleSelect: (Int, FileEntry) -> Unit,
    onQuickSelect: (Int, Int) -> Unit,
    onDragSelect: (Int, Int, Int) -> Unit,
    onUp: (Int) -> Unit,
    onRefresh: (Int) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSwap: () -> Unit,
    onSync: () -> Unit,
    onGoto: () -> Unit,
    onFilter: () -> Unit,
    onClearFilter: () -> Unit,
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
    onSelectSimilar: () -> Unit,
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
    onChmod: () -> Unit,
    onShareSelection: () -> Unit,
    onCompareSelection: () -> Unit
) {
    val pane = panes[active]
    val scrollMemory = remember { mutableStateMapOf<String, Int>() }
    Column(
        Modifier
            .fillMaxSize()
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

        // 顶栏下沿的柔和投影：工具栏压在内容之上，而不是和内容拼成一块
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(SunColors.TopBar.copy(alpha = 0.42f), Color.Transparent)
                    )
                )
        )

        // 过滤状态条：只在生效时出现，一行说清过滤条件与剩余数量
        AnimatedVisibility(visible = pane.filter.isNotBlank(), enter = fadeIn(tween(Motion.Fast)), exit = fadeOut(tween(Motion.Fast))) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(SunColors.AccentSoft)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "过滤  ${pane.filter}",
                    color = SunColors.Accent,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("${pane.entries.size} 项", color = SunColors.TextTertiary, fontSize = 10.sp)
                Icon(
                    Icons.Outlined.Close,
                    "清除过滤",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(15.dp)
                        .clickable { onClearFilter() }
                )
                Spacer(Modifier.width(6.dp))
            }
        }

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
                onLongPress = { entry, origin -> onLongPress(0, entry, origin) },
                onToggle = { onToggleSelect(0, it) },
                onQuickSelect = { pos -> onQuickSelect(0, pos) },
                onDragSelect = { a, b -> onDragSelect(0, a, b) },
                onUp = { onUp(0) },
                onRefresh = { onRefresh(0) }
            )
            PaneView(
                index = 1,
                pane = panes[1],
                active = active == 1,
                scrollMemory = scrollMemory,
                modifier = Modifier.weight(1f),
                onActivate = { onActivate(1) },
                onOpen = { entry, origin -> onOpenEntry(1, entry, origin) },
                onLongPress = { entry, origin -> onLongPress(1, entry, origin) },
                onToggle = { onToggleSelect(1, it) },
                onQuickSelect = { pos -> onQuickSelect(1, pos) },
                onDragSelect = { a, b -> onDragSelect(1, a, b) },
                onUp = { onUp(1) },
                onRefresh = { onRefresh(1) }
            )
        }

        // 底栏上沿同理：内容淡出到底栏，工具栏是浮在最上面的一层
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, SunColors.Surface1.copy(alpha = 0.45f))
                    )
                )
        )

        BottomBar(
            selectionMode = pane.selectionMode,
            selectedCount = pane.selected.size,
            canBack = pane.canBack,
            canForward = pane.canForward,
            canUp = pane.canUp,
            filterActive = pane.filter.isNotBlank(),
            onBack = onBack,
            onForward = onForward,
            onNew = onNew,
            onSync = onSync,
            onUp = { onUp(active) },
            onGoto = onGoto,
            onFilter = onFilter,
            onSwipeUp = onBookmarks,
            onCopy = onSelectionCopy,
            onCut = onSelectionCut,
            onDelete = onSelectionDelete,
            onRename = onSelectionRename,
            onSelectAll = onSelectAll,
            onInvert = onInvertSelection,
            onSimilar = onSelectSimilar,
            onCompress = onCompressSelection,
            onBatchRename = onBatchRename,
            onProperties = onProperties,
            onShare = onShareSelection,
            onCompare = onCompareSelection,
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
            .background(SunColors.TopBar)
            // 底色先铺满，再让状态栏高度算进栏内 —— 顶栏颜色一直延伸到屏幕最上沿
            .statusBarsPadding()
            .height(Mt.BarHeight)
            .padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer,
            interactionSource = interaction,
            modifier = Modifier.size(46.dp).pressScale(interaction, 0.88f)
        ) {
            HamburgerIcon(open = drawerOpen, tint = SunColors.OnTopBar, modifier = Modifier.size(22.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .clip(SunShapes.Small)
                .clickable { onPathClick() }
                .padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            AnimatedContent(
                targetState = pane.path,
                transitionSpec = {
                    (fadeIn(tween(Motion.Base)) + slideInVertically(tween(Motion.Base)) { it / 3 }) togetherWith
                        fadeOut(tween(Motion.Fast))
                },
                label = "path"
            ) { path ->
                val shown = path.ifEmpty { "/" }
                Text(
                    shown,
                    color = SunColors.OnTopBar,
                    fontSize = Mt.titleSize(shown.length),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(1.dp))
            // MT 的副标题就是一行纯文字，不挂图标，读起来更快
            Text(
                "${pane.dirCount} 文件夹 · ${pane.fileCount} 文件 · ${Fmt.sizeCompact(storageUsed)}/${Fmt.sizeCompact(storageTotal)}",
                color = SunColors.OnTopBarMuted,
                fontSize = Mt.BarMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { onOverflowChange(true) }, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "工具栏",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
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
                    null
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

// ---------------- 列表样式 ----------------

/** 列表外观设置：行高、文件名行数、时间格式。用 state 承载，改设置后列表立即重绘 */
data class ListStyle(
    val rowHeight: androidx.compose.ui.unit.Dp = Mt.RowHeight,
    val nameLines: Int = 1,
    val dateFormat: String = "yy-MM-dd HH:mm",
    val timeByCreated: Boolean = false
)

private object ListStyleHolder {
    var style by mutableStateOf(ListStyle())
}

/** 底栏下边距：为全面屏手势区留出的额外空间 */
private object BottomBarStyle {
    var extra by mutableStateOf(0.dp)
}

// ---------------- 单栏 ----------------

/**
 * 两栏之间只用一条与内容同色的细线分隔。
 * 聚焦状态改由栏底色与内容亮度表达，不再出现蓝色高亮线。
 */
@Composable
private fun PaneDivider() {
    // 两栏之间用 2dp 高对比线切开：暗色下近黑、亮色下深灰，边界一目了然
    Box(Modifier.width(2.dp).fillMaxHeight().background(SunColors.PaneDivider))
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
    onLongPress: (FileEntry, Offset) -> Unit,
    onToggle: (FileEntry) -> Unit,
    onQuickSelect: (Int) -> Unit,
    onDragSelect: (Int, Int) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit
) {
    // 没有分割线：聚焦的一栏抬到 Surface1，另一栏留在底色上或压到 Surface2，
    // 两栏之间靠明度差与边缘光分开，看起来像前后两个平面而不是一张纸上的两格。
    val dark = SunColors.dark
    val bg by animateColorAsState(
        targetValue = when {
            active -> SunColors.Surface1
            dark -> Color.Transparent
            else -> SunColors.Surface2
        },
        animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
        label = "paneBg"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.86f,
        animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
        label = "paneAlpha"
    )

    Column(
        modifier
            .fillMaxHeight()
            .background(bg)
            // 边缘高光 + 内侧微阴影：给聚焦栏做出「被托起来」的厚度
            .drawBehind {
                if (!active) return@drawBehind
                val edge = 3.dp.toPx()
                if (dark) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color(0x16FFFFFF), Color.Transparent), 0f, edge
                        )
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0x16FFFFFF)), size.width - edge, size.width
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0x12FFFFFF), Color.Transparent), 0f, 2.dp.toPx()
                        )
                    )
                } else {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color(0x0F000000), Color.Transparent), 0f, edge
                        )
                    )
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0x0F000000)), size.width - edge, size.width
                        )
                    )
                }
            }
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
        // 固定的「..」上级目录行
        UpRow(enabled = pane.canUp, onClick = { onActivate(); if (pane.canUp) onUp() })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PaneList(
                pane = pane,
                alpha = contentAlpha,
                scrollMemory = scrollMemory,
                onOpen = { entry, origin -> onActivate(); onOpen(entry, origin) },
                onLongPress = { entry, origin -> onActivate(); onLongPress(entry, origin) },
                onToggle = { entry -> onActivate(); onToggle(entry) },
                onQuickSelect = { pos -> onActivate(); onQuickSelect(pos) },
                onDragSelect = { a, b -> onActivate(); onDragSelect(a, b) },
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
            .height(46.dp)
            .pressScale(interaction, 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialFileIcon(
            name = "..",
            isDir = true,
            size = 28.dp,
            modifier = Modifier.alpha(if (enabled) 1f else 0.35f)
        )
        Spacer(Modifier.width(13.dp))
        Text(
            "..",
            color = if (enabled) SunColors.TextPrimary else SunColors.TextTertiary.copy(alpha = 0.5f),
            fontSize = Mt.Name
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneList(
    pane: PaneState,
    alpha: Float,
    scrollMemory: MutableMap<String, Int>,
    onOpen: (FileEntry, Offset) -> Unit,
    onLongPress: (FileEntry, Offset) -> Unit,
    onToggle: (FileEntry) -> Unit,
    onQuickSelect: (Int) -> Unit,
    onDragSelect: (Int, Int) -> Unit,
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
                        // 首选 Shizuku：不用 root，授权一次就能读根目录
                        val shizukuReady = com.suibian.sunfile.data.ShizukuBridge.usable()
                        Text(
                            if (shizukuReady) "用 Shizuku 读取" else "获取权限",
                            color = SunColors.TextPrimary,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(SunColors.AccentSoft)
                                .clickable {
                                    when {
                                        shizukuReady -> scope.launch {
                                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.suibian.sunfile.data.Shell.requestShizuku()
                                            }
                                            if (ok) onRetry()
                                        }
                                        // 装了但没授权：弹 Shizuku 的授权框，结果由上方统一回调接管
                                        com.suibian.sunfile.data.ShizukuBridge.running() ->
                                            com.suibian.sunfile.data.ShizukuBridge.requestPermission()
                                        else -> scope.launch {
                                            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.suibian.sunfile.data.Shell.requestSu()
                                            }
                                            Toast.makeText(
                                                context,
                                                if (ok) "已获得 Root" else "未获得 Root",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            if (ok) onRetry()
                                        }
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
                Text("打开失败", color = SunColors.TextSecondary, fontSize = 13.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    pane.error ?: "",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "重试",
                    color = SunColors.Accent,
                    fontSize = 12.sp,
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
                Icon(Icons.Outlined.Folder, null, tint = SunColors.Surface3, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text("空", color = SunColors.TextTertiary, fontSize = 12.5.sp)
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // 左右滑动进入多选：只在横向位移明显大于纵向时才接管，
                    // 不消费按下事件，因此轻点、长按、纵向滚动都不受影响
                    .pointerInput(pane.path, pane.entries.size) {
                        val threshold = 26.dp.toPx()
                        fun indexAt(y: Float): Int {
                            val items = listState.layoutInfo.visibleItemsInfo
                            if (items.isEmpty()) return -1
                            for (v in items) if (y >= v.offset && y < v.offset + v.size) return v.index
                            return if (y < items.first().offset) items.first().index else items.last().index
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dx = 0f
                            var dy = 0f
                            var active = false
                            var anchor = -1
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val c = e.changes.firstOrNull() ?: break
                                if (c.changedToUpIgnoreConsumed()) break
                                val d = c.positionChange()
                                dx += d.x
                                dy += d.y
                                if (!active && kotlin.math.abs(dx) > threshold &&
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f
                                ) {
                                    val pos = indexAt(down.position.y)
                                    if (pos >= 0) {
                                        active = true
                                        anchor = pos
                                        onQuickSelect(pos)
                                    }
                                }
                                if (active) {
                                    c.consume()
                                    val cur = indexAt(c.position.y)
                                    if (cur >= 0 && anchor >= 0) onDragSelect(anchor, cur)
                                }
                            }
                        }
                    }
            ) {
                items(pane.entries, key = { it.path }) { entry ->
                    FileRow(
                        modifier = Modifier.animateItemPlacement(),
                        entry = entry,
                        selected = pane.selected.contains(entry.path),
                        selectionMode = pane.selectionMode,
                        alpha = alpha,
                        onClick = { origin ->
                            if (pane.selectionMode) onToggle(entry) else onOpen(entry, origin)
                        },
                        onLongClick = { origin -> onLongPress(entry, origin) }
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
    alpha: Float,
    onClick: (Offset) -> Unit,
    onLongClick: (Offset) -> Unit
) {
    val style = ListStyleHolder.style
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
            .height(style.rowHeight)
            .background(bg)
            .onGloballyPositioned { coords ->
                val pos = coords.localToRoot(Offset.Zero)
                rowOrigin = Offset(pos.x + 40f, pos.y + coords.size.height / 2f)
            }
            .pressScale(interaction, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = { onLongClick(rowOrigin) },
                onClick = { onClick(rowOrigin) }
            )
            .padding(start = Mt.RowGutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeBadge(
            file = entry.file,
            isDir = entry.isDir,
            size = Mt.RowIcon,
            modifier = Modifier.alpha((if (entry.canRead) 1f else 0.45f) * alpha)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = when {
                    selected -> SunColors.Accent
                    !entry.canRead -> SunColors.TextTertiary
                    else -> SunColors.TextPrimary
                },
                fontSize = Mt.Name,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = style.nameLines,
                lineHeight = (Mt.Name.value + 3).sp,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val stamp = if (style.timeByCreated && entry.created > 0) entry.created else entry.lastModified
            Text(
                if (entry.isDir) {
                    Fmt.timeAs(stamp, style.dateFormat)
                } else {
                    // MT 的顺序：先时间后大小，扫一眼就能比出「什么时候、多大」
                    Fmt.timeAs(stamp, style.dateFormat) + " " + Fmt.sizeCompact(entry.size)
                },
                color = SunColors.TextTertiary,
                fontSize = Mt.Meta,
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
    filterActive: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
    onUp: () -> Unit,
    onGoto: () -> Unit,
    onFilter: () -> Unit,
    onSwipeUp: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onSimilar: () -> Unit,
    onCompress: () -> Unit,
    onBatchRename: () -> Unit,
    onProperties: () -> Unit,
    onShare: () -> Unit,
    onCompare: () -> Unit,
    onCancel: () -> Unit
) {
    var moreOpen by remember { mutableStateOf(false) }
    val hairline = SunColors.Hairline
    Column(
        Modifier
            .fillMaxWidth()
            .background(SunColors.Surface1)
            .drawBehind {
                val stroke = 0.6.dp.toPx()
                drawLine(
                    hairline,
                    Offset(0f, stroke / 2),
                    Offset(size.width, stroke / 2),
                    stroke
                )
            }
            .animateContentSize()
            .padding(bottom = BottomBarStyle.extra)
            // 从底栏上滑打开书签，与 MT 一致；不消费事件，图标点击照常
            .pointerInput(Unit) {
                val threshold = 22.dp.toPx()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dy = 0f
                    var fired = false
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull() ?: break
                        if (c.changedToUpIgnoreConsumed()) break
                        dy += c.positionChange().y
                        if (!fired && dy < -threshold) {
                            fired = true
                            onSwipeUp()
                        }
                    }
                }
            }
    ) {
        AnimatedVisibility(visible = selectionMode) {
            Row(
                Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 $selectedCount 项", color = SunColors.Accent, fontSize = 11.5.sp)
                Spacer(Modifier.weight(1f))
                MiniChip("全选", onSelectAll)
                MiniChip("反选", onInvert)
                MiniChip("类选", onSimilar)
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
                Modifier.fillMaxWidth().height(Mt.BottomHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (!selecting) {
                    BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "后退", canBack, onBack)
                    BarIcon(Icons.AutoMirrored.Outlined.ArrowForward, "前进", canForward, onForward)
                    BarIcon(Icons.Outlined.Add, "新建", true, onNew)
                    BarIcon(
                        Icons.Outlined.SwapHoriz,
                        "同步窗口（长按过滤）",
                        true,
                        onSync,
                        active = filterActive,
                        onLongClick = onFilter
                    )
                    BarIcon(Icons.Outlined.ArrowUpward, "上一级（长按跳转路径）", canUp, onUp, onLongClick = onGoto)
                } else {
                    BarIcon(Icons.Outlined.ContentCopy, "复制", true, onCopy)
                    BarIcon(Icons.Outlined.ContentCut, "剪切", true, onCut)
                    BarIcon(Icons.Outlined.DriveFileRenameOutline, "重命名", selectedCount == 1, onRename)
                    BarIcon(Icons.Outlined.Delete, "删除", true, onDelete, danger = true)
                    Box {
                        BarIcon(Icons.Outlined.MoreVert, "更多", true, { moreOpen = true })
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            MenuLine("压缩", Icons.Outlined.Archive) { moreOpen = false; onCompress() }
                            MenuLine("批量重命名", Icons.Outlined.DriveFileRenameOutline) { moreOpen = false; onBatchRename() }
                            MenuLine("分享", Icons.Outlined.Share) { moreOpen = false; onShare() }
                            if (selectedCount == 2) {
                                MenuLine("对比", Icons.Outlined.SwapHoriz) { moreOpen = false; onCompare() }
                            }
                            MenuLine("属性", Icons.Outlined.Info) { moreOpen = false; onProperties() }
                            MenuLine("取消选择", Icons.Outlined.Close) { moreOpen = false; onCancel() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuLine(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = SunColors.TextPrimary, fontSize = 12.5.sp) },
        leadingIcon = { Icon(icon, null, tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp)) },
        onClick = onClick
    )
}

@Composable
private fun MiniChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = SunColors.TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .padding(start = 7.dp)
            .clip(SunShapes.Small)
            .background(SunColors.Surface3)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> SunColors.TextTertiary.copy(alpha = 0.35f)
        danger -> SunColors.Danger
        active -> SunColors.Accent
        else -> SunColors.TextSecondary
    }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .pressScale(interaction, 0.86f)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
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

// ---------------- 工具函数 ----------------

/** content:// tree uri 的 documentId 形如 primary:DCIM，翻译成真实路径 */
private fun safTreeToPath(uri: android.net.Uri): String? {
    val id = runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: return null
    val parts = id.split(":", limit = 2)
    val rel = parts.getOrElse(1) { "" }
    val base = when (parts[0]) {
        "primary" -> "/storage/emulated/0"
        else -> "/storage/${parts[0]}"
    }
    return (if (rel.isBlank()) base else "$base/$rel").trimEnd('/').ifBlank { base }
}

/** smb / ftp / webdav 这些自定义 scheme 系统浏览器认不出，转成可打开的等价链接 */
private fun normalizeLink(uri: String): String {
    val lower = uri.lowercase()
    return when {
        lower.startsWith("webdav://") -> "http://" + uri.substringAfter("://")
        lower.startsWith("webdavs://") -> "https://" + uri.substringAfter("://")
        else -> uri
    }
}

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

/** 调用系统安装器安装 APK */
internal fun installApk(context: Context, file: File): Boolean {
    return runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

internal fun shareFiles(context: Context, files: List<File>): Boolean {
    if (files.isEmpty()) return false
    return runCatching {
        val uris = ArrayList<android.net.Uri>(files.size)
        for (f in files) {
            uris.add(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${files.size} 个文件"))
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
