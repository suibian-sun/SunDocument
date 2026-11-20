package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Archives
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.OpenMode
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.util.Fmt
import java.io.File

sealed interface SheetKind {
    data object New : SheetKind
    data object Sort : SheetKind
    data object Path : SheetKind
    data object Bookmarks : SheetKind
    data class Entry(val entry: FileEntry) : SheetKind
    data class OpenWith(val file: File) : SheetKind
    data class Apk(val file: File) : SheetKind
    data object OpenWithManager : SheetKind
}

sealed interface DialogKind {
    data class Rename(val file: File) : DialogKind
    data object NewFolder : DialogKind
    data object NewFile : DialogKind
    data class Delete(val paths: List<String>) : DialogKind
    data class Properties(val file: File) : DialogKind
    data class Compress(val paths: List<String>) : DialogKind
    data class BatchRename(val paths: List<String>) : DialogKind
    data class Checksum(val file: File) : DialogKind
    data class Chmod(val file: File, val current: String?) : DialogKind
    data class ZipRename(val zip: File, val entry: String) : DialogKind
    data class Filter(val current: String) : DialogKind
    data class Goto(val current: String) : DialogKind
    data object About : DialogKind
}

/** 底部面板通用外壳：先停半屏，可继续上滑 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SunColors.Surface4,
        scrimColor = Color(0x99000000),
        shape = SunShapes.Sheet,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(SunColors.Hairline))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp)) {
            content()
        }
    }
}

@Composable
fun NewSheet(onDismiss: () -> Unit, onFolder: () -> Unit, onFile: () -> Unit) {
    Sheet(onDismiss) {
        Text(
            "新建",
            color = SunColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 14.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OptionCard(Icons.Outlined.CreateNewFolder, "文件夹", SunColors.Folder, Modifier.weight(1f), onFolder)
            OptionCard(Icons.Outlined.Description, "文件", SunColors.Accent, Modifier.weight(1f), onFile)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(92.dp)
            .clip(SunShapes.Medium)
            .background(SunColors.Surface3)
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = SunColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
fun SortSheet(
    current: SortMode,
    ascending: Boolean,
    onPick: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    Sheet(onDismiss) {
        Text(
            "排序方式",
            color = SunColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )
        SortMode.entries.forEach { mode ->
            val active = mode == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(mode) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    mode.label,
                    color = if (active) SunColors.Accent else SunColors.TextPrimary,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (active) {
                    Text(
                        if (ascending) "升序 ↑" else "降序 ↓",
                        color = SunColors.TextTertiary,
                        fontSize = 10.5.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Check, null, tint = SunColors.Accent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            "再次点击同一项可切换升序 / 降序",
            color = SunColors.TextTertiary,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun EntrySheet(
    entry: FileEntry,
    bookmarked: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onCopyPath: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onBookmark: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit = {},
    onOpenAsText: () -> Unit = {}
) {
    val kind = FileKinds.kindOf(entry.file)
    val isApk = entry.file.extension.equals("apk", true)
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                entry.name,
                color = SunColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (entry.isDir) "文件夹" else Fmt.size(entry.size)} · ${Fmt.time(entry.lastModified)}",
                color = SunColors.TextTertiary,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        SheetRow(
            if (entry.isDir) Icons.Outlined.FolderOpen else Icons.Outlined.OpenInNew,
            if (entry.isDir) "进入文件夹" else "打开",
            onOpen
        )
        if (!entry.isDir) {
            SheetRow(Icons.Outlined.OpenInNew, "打开方式…", onOpenWith)
        }
        if (isApk) {
            SheetRow(Icons.Outlined.Terminal, "安装", onInstall)
        }
        if (!entry.isDir && kind != com.suibian.sunfile.data.FileKind.TEXT) {
            SheetRow(Icons.Outlined.TextFields, "用文本打开", onOpenAsText)
        }
        SheetRow(Icons.Outlined.DriveFileRenameOutline, "重命名", onRename)
        SheetRow(Icons.Outlined.Info, "属性", onProperties)
        SheetRow(Icons.Outlined.ContentCopy, "复制路径", onCopyPath)
        if (!entry.isDir) {
            SheetRow(Icons.Outlined.Share, "分享", onShare)
            SheetRow(Icons.Outlined.Archive, "压缩为 ZIP", onCompress)
        }
        SheetRow(
            Icons.Outlined.BookmarkBorder,
            if (bookmarked) "取消收藏" else "收藏此路径",
            onBookmark
        )
        SheetRow(Icons.Outlined.Delete, "删除", onDelete, danger = true)
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (danger) SunColors.Danger else SunColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) SunColors.Danger else SunColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
fun PathSheet(
    current: String,
    bookmarks: List<String>,
    onDismiss: () -> Unit,
    onGo: (String) -> Unit
) {
    var value by remember { mutableStateOf(current) }
    val quick = (listOf(
        "/storage/emulated/0",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
        "/storage/emulated/0/Android/data",
        "/"
    ) + bookmarks).distinct()
    Sheet(onDismiss) {
        Text(
            "跳转路径",
            color = SunColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )
        SunTextField(
            value = value,
            onValueChange = { value = it },
            label = "绝对路径",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            mono = true,
            imeAction = ImeAction.Go,
            onDone = { if (value.isNotBlank()) onGo(value.trim()) }
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            quick.forEach { p ->
                Text(
                    p,
                    color = SunColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGo(p) }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { if (value.isNotBlank()) onGo(value.trim()) }) {
                Text("跳转", color = SunColors.Accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BookmarkSheet(
    bookmarks: List<String>,
    current: String,
    onDismiss: () -> Unit,
    onGo: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Sheet(onDismiss) {
        Text(
            "收藏路径",
            color = SunColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
        )
        if (bookmarks.isEmpty()) {
            Text(
                "还没有收藏。在文件菜单中选择「收藏此路径」即可添加。",
                color = SunColors.TextTertiary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp)
            )
        } else {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                bookmarks.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onGo(p) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            null,
                            tint = if (p == current) SunColors.Accent else SunColors.Folder,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            p,
                            color = if (p == current) SunColors.Accent else SunColors.TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "移除",
                            color = SunColors.Danger,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .clickable { onRemove(p) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 打开方式选择。
 *
 * 结构照 MT：居中直角卡片、三列彩色方块、底部左「类型」右「关闭」。
 * 方块比列表行更容易在拇指区一次点中，也把「这是什么」交给图标本身回答。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenWithSheet(
    file: File,
    remembered: OpenMode?,
    onDismiss: () -> Unit,
    onPick: (OpenMode) -> Unit,
    onPickAndRemember: (OpenMode) -> Unit,
    onSetDefault: (OpenMode) -> Unit = {},
    onManage: () -> Unit = {}
) {
    var remember by remember { mutableStateOf(false) }
    val rawExt = FileKinds.extensionOf(file)
    val kind = FileKinds.kindOf(file)
    val modes = buildList {
        add(OpenMode.TEXT)
        add(OpenMode.HEX)
        when (kind) {
            com.suibian.sunfile.data.FileKind.IMAGE -> add(OpenMode.IMAGE)
            com.suibian.sunfile.data.FileKind.AUDIO -> add(OpenMode.AUDIO)
            com.suibian.sunfile.data.FileKind.VIDEO -> add(OpenMode.VIDEO)
            com.suibian.sunfile.data.FileKind.OFFICE ->
                if (FileKinds.isDocumentReadable(file)) add(OpenMode.DOCUMENT)
            com.suibian.sunfile.data.FileKind.APK -> {
                add(OpenMode.INFO)
                add(OpenMode.AXML)
                add(OpenMode.DEX)
            }
            else -> Unit
        }
        if (FileKinds.isZipReadable(file)) add(OpenMode.ARCHIVE)
        add(OpenMode.EXTERNAL)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .clip(SunShapes.Dialog)
                .background(SunColors.Surface4)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "打开方式…",
                    color = SunColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "管理",
                    color = SunColors.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onManage() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            Text(
                file.name + if (remembered != null) "  ·  默认 ${remembered.label}" else "",
                color = SunColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp)
            )
            Spacer(Modifier.height(10.dp))

            modes.chunked(3).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    line.forEach { mode ->
                        OpenModeTile(
                            mode = mode,
                            isDefault = remembered == mode,
                            modifier = Modifier.weight(1f),
                            onClick = { if (remember) onPickAndRemember(mode) else onPick(mode) },
                            onLongClick = { onSetDefault(mode) }
                        )
                    }
                    repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { remember = !remember }
                    .padding(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = remember,
                    onCheckedChange = { remember = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SunColors.Accent,
                        uncheckedColor = SunColors.TextTertiary
                    )
                )
                Text(
                    if (rawExt.isEmpty()) "总是用此方式打开无扩展名文件" else "总是用此方式打开 .$rawExt",
                    color = SunColors.TextSecondary,
                    fontSize = 11.5.sp
                )
            }

            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "类型",
                    color = SunColors.Accent,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onManage() }
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    color = SunColors.Accent,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onDismiss() }
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OpenModeTile(
    mode: OpenMode,
    isDefault: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier
            .clip(SunShapes.Small)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(SunShapes.Tile)
                .background(modeColor(mode)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modeIcon(mode),
                null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            mode.label,
            color = if (isDefault) SunColors.Accent else SunColors.TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (isDefault) "默认" else modeHint(mode).substringBefore(" / "),
            color = SunColors.TextTertiary,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/** MT 的每个功能方块都有自己的颜色，这里按用途分组取色 */
private fun modeColor(mode: OpenMode): Color = when (mode) {
    OpenMode.TEXT -> Color(0xFF2F6FD0)
    OpenMode.HEX -> Color(0xFF5B8A91)
    OpenMode.IMAGE -> Color(0xFF2AA79B)
    OpenMode.AUDIO -> Color(0xFFDE474E)
    OpenMode.VIDEO -> Color(0xFF7A5BD3)
    OpenMode.DOCUMENT -> Color(0xFF3B72C4)
    OpenMode.ARCHIVE -> Color(0xFF8B5E3C)
    OpenMode.AXML -> Color(0xFF2E8B8B)
    OpenMode.DEX -> Color(0xFF4F8F8A)
    OpenMode.INFO -> Color(0xFF3FA45B)
    OpenMode.EXTERNAL -> Color(0xFF566573)
}

/** 默认打开方式管理：列出已记住的类型，可逐条删除或全部清除 */
@Composable
fun OpenWithManagerSheet(
    mapping: Map<String, OpenMode>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Sheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("默认打开方式", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (mapping.isEmpty()) "还没有为任何类型指定默认方式" else "${mapping.size} 个类型已指定",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp
                )
            }
            if (mapping.isNotEmpty()) {
                Text(
                    "全部清除",
                    color = SunColors.Danger,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onClearAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        mapping.entries.sortedBy { it.key }.forEach { (ext, mode) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ".${ext.ifEmpty { "无扩展名" }}",
                    color = SunColors.TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(110.dp)
                )
                Text(mode.label, color = SunColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Outlined.Close,
                    null,
                    tint = SunColors.TextTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(ext) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun modeIcon(mode: OpenMode) = when (mode) {
    OpenMode.TEXT -> Icons.Outlined.TextFields
    OpenMode.HEX -> Icons.Outlined.Terminal
    OpenMode.IMAGE -> Icons.Outlined.Image
    OpenMode.AUDIO -> Icons.Outlined.MusicNote
    OpenMode.VIDEO -> Icons.Outlined.Movie
    OpenMode.DOCUMENT -> Icons.Outlined.Description
    OpenMode.ARCHIVE -> Icons.Outlined.Archive
    OpenMode.AXML -> Icons.Outlined.Code
    OpenMode.DEX -> Icons.Outlined.DataObject
    OpenMode.INFO -> Icons.Outlined.Android
    OpenMode.EXTERNAL -> Icons.Outlined.OpenInNew
}

private fun modeHint(mode: OpenMode) = when (mode) {
    OpenMode.TEXT -> "语法高亮、补全，可直接编辑保存"
    OpenMode.HEX -> "按字节查看任意大小的文件"
    OpenMode.IMAGE -> "内置缩放，可左右滑动切换"
    OpenMode.AUDIO -> "底部小窗播放，可切上一首下一首"
    OpenMode.VIDEO -> "全屏播放，左右滑动切换"
    OpenMode.DOCUMENT -> "直接读取 Word / Excel / PPT 内容"
    OpenMode.ARCHIVE -> "浏览 zip / tar 内部文件并解压"
    OpenMode.AXML -> "把二进制 XML 还原成可读文本"
    OpenMode.DEX -> "查看类、方法、字段与字符串"
    OpenMode.INFO -> "包名、签名、权限、组件一览"
    OpenMode.EXTERNAL -> "交给系统中已安装的应用"
}

// ---------------- 输入与提示 ----------------

/** 浮动标签输入框：聚焦时只加深阴影，不画黑框 */
@Composable
fun SunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val elevation by animateDpAsState(
        targetValue = if (focused) 10.dp else 0.dp,
        animationSpec = tween(Motion.Base),
        label = "fieldShadow"
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            color = SunColors.TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = interaction,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SunColors.Surface4,
            unfocusedContainerColor = SunColors.Surface3,
            focusedBorderColor = SunColors.Accent.copy(alpha = 0.75f),
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = SunColors.Accent,
            unfocusedLabelColor = SunColors.TextTertiary,
            cursorColor = SunColors.Accent,
            focusedTextColor = SunColors.TextPrimary,
            unfocusedTextColor = SunColors.TextPrimary
        ),
        shape = SunShapes.Small,
        modifier = modifier.shadow(elevation, SunShapes.Small, ambientColor = Color.Black, spotColor = Color.Black)
    )
}

@Composable
fun InputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String = "确定",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                SunTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                    onDone = { if (value.isNotBlank()) onConfirm(value.trim()) }
                )
                Text(
                    "名称不能包含 / 且不能为空",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank() && !value.contains("/")) onConfirm(value.trim()) }) {
                Text(confirmText, color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Text(
                message,
                color = SunColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = if (message.contains("/")) FontFamily.Monospace else FontFamily.Default
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) SunColors.Danger else SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun PropertiesDialog(
    file: File,
    loader: suspend (File) -> Map<String, String>,
    onDismiss: () -> Unit
) {
    val props by produceState(initialValue = emptyMap<String, String>(), file) {
        value = loader(file)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("属性", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (props.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在统计…", color = SunColors.TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    props.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(k, color = SunColors.TextTertiary, fontSize = 10.5.sp, modifier = Modifier.width(64.dp))
                            Text(
                                v,
                                color = SunColors.TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = if (k == "路径") FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = SunColors.Accent, fontSize = 13.sp) }
        }
    )
}

@Composable
fun CompressDialog(
    paths: List<String>,
    onConfirm: (String, Archives.Format, Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val stem = when (paths.size) {
        1 -> File(paths.first()).name.substringBeforeLast('.').ifEmpty { "archive" }
        else -> "archive"
    }
    var format by remember { mutableStateOf(Archives.Format.ZIP) }
    var name by remember { mutableStateOf(stem) }
    var level by remember { androidx.compose.runtime.mutableIntStateOf(6) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val levels = listOf(0 to "存储", 3 to "快速", 6 to "标准", 9 to "极限")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = {
            Text(
                "压缩 ${paths.size} 项",
                color = SunColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                // 格式
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Archives.Format.entries.forEach { f ->
                        val active = f == format
                        Text(
                            f.label,
                            color = if (active) SunColors.Accent else SunColors.TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(if (active) SunColors.AccentSoft else SunColors.Surface3)
                                .clickable { format = f }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SunTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称（自动补 .${format.ext}）",
                    modifier = Modifier.fillMaxWidth(),
                    mono = true
                )

                Spacer(Modifier.height(14.dp))
                Text("压缩级别", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEach { (v, label) ->
                        val active = v == level
                        Text(
                            label,
                            color = if (active) SunColors.Surface1 else SunColors.TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(if (active) SunColors.Accent else SunColors.Surface3)
                                .clickable { level = v }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (format == Archives.Format.ZIP) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("密码", color = SunColors.TextTertiary, fontSize = 10.sp)
                            Spacer(Modifier.height(5.dp))
                            SunTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "留空则不加密",
                                password = !showPassword,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            "显示密码",
                            tint = SunColors.TextTertiary,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { showPassword = !showPassword }
                                .padding(5.dp)
                        )
                    }
                    Text(
                        "AES-256 加密，7-Zip / MT 管理器均可直接解压",
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name.trim(), format, level, password.ifBlank { null })
                }
            }) {
                Text("开始", color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun BatchRenameDialog(
    paths: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expr by remember { mutableStateOf(com.suibian.sunfile.data.RenameExpr.DEFAULT_EXPR) }
    val items = remember(paths) {
        paths.map { p ->
            val f = File(p)
            com.suibian.sunfile.data.RenameExpr.Item(f.name, f.lastModified())
        }
    }
    val preview = remember(expr, paths) {
        runCatching { com.suibian.sunfile.data.RenameExpr.preview(expr, items) }.getOrDefault(emptyList())
    }
    val tokens = listOf("{P}", "{S}", "{1}", "{z1}", "{T:yyyyMMdd}", "{AP}")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = {
            Column {
                Text("批量重命名 ${paths.size} 项", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("表达式决定新文件名，{P} 原名、{S} 后缀、{1} 递增", color = SunColors.TextTertiary, fontSize = 9.5.sp)
            }
        },
        text = {
            Column {
                SunTextField(
                    value = expr,
                    onValueChange = { expr = it },
                    label = "重命名表达式",
                    modifier = Modifier.fillMaxWidth(),
                    mono = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tokens.forEach { t ->
                        Text(
                            t,
                            color = SunColors.TextSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .background(SunColors.Surface3)
                                .clickable { expr = expr + t }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("预览", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                preview.take(5).forEachIndexed { i, n ->
                    Text(
                        "${File(paths[i]).name}  →  $n",
                        color = if (n == File(paths[i]).name) SunColors.TextTertiary else SunColors.TextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview.size > 5) {
                    Text("… 其余 ${preview.size - 5} 项", color = SunColors.TextTertiary, fontSize = 9.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (expr.isNotBlank()) onConfirm(expr) }) {
                Text("重命名", color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun ChecksumDialog(
    file: File,
    loader: suspend (File, String) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val values by produceState(initialValue = emptyMap<String, String>(), file) {
        val map = LinkedHashMap<String, String>()
        map["SHA-256"] = loader(file, "SHA-256")
        map["MD5"] = loader(file, "MD5")
        value = map
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("校验和", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text(file.name, color = SunColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (values.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在计算…", color = SunColors.TextSecondary, fontSize = 11.sp)
                    }
                } else {
                    values.forEach { (k, v) ->
                        Text(k, color = SunColors.TextTertiary, fontSize = 9.5.sp, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            v,
                            color = SunColors.TextPrimary,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("checksum", values.values.joinToString("\n")))
                Toast.makeText(context, "已复制校验和", Toast.LENGTH_SHORT).show()
            }) { Text("复制", color = SunColors.Accent, fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit, onCheckUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("SunDocument", color = SunColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("版本 v$version", color = SunColors.TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Kotlin + Jetpack Compose 实现的双栏文件管理器：线性图标、内置编辑器（语法高亮 / 补全 / 历史版本）、压缩包浏览、十六进制与大图查看。",
                    color = SunColors.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "github.com/suibian-sun/SunDocument",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCheckUpdate) { Text("检查更新", color = SunColors.Accent, fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

// ---------------- APK 详情 ----------------

@Composable
fun ApkSheet(file: File, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val meta by androidx.compose.runtime.produceState<com.suibian.sunfile.util.ApkMeta?>(
        initialValue = null, file
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.suibian.sunfile.util.ApkInspector.read(context, file)
        }
    }
    Sheet(onDismiss) {
        val m = meta
        if (m == null) {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在解析安装包…", color = SunColors.TextSecondary, fontSize = 12.sp)
            }
            return@Sheet
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = m.icon
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SunColors.Surface2),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(Icons.Outlined.Terminal, null, tint = SunColors.TextTertiary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.label,
                    color = SunColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    m.packageName,
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            MetaCell("版本", (m.versionName ?: "—") + " (${m.versionCode})", Modifier.weight(1f))
            MetaCell("SDK", "min ${m.minSdk} · target ${m.targetSdk}", Modifier.weight(1f))
            MetaCell("大小", Fmt.size(file.length()), Modifier.weight(1f))
        }

        if (m.permissions.isNotEmpty()) {
            Text(
                "权限 ${m.permissions.size}",
                color = SunColors.TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
            )
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                m.permissions.take(120).forEach { p ->
                    Text(
                        p,
                        color = SunColors.TextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = SunColors.TextTertiary, fontSize = 8.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = SunColors.TextPrimary, fontSize = 10.5.sp, style = NumberStyle, maxLines = 1)
    }
}

// ---------------- 权限修改 ----------------

@Composable
fun ChmodDialog(
    file: File,
    current: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("644") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("修改权限", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text(
                    current?.let { "当前 $it" } ?: file.name,
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(10.dp))
                SunTextField(
                    value = mode,
                    onValueChange = { mode = it.filter { c -> c in '0'..'7' }.take(4) },
                    label = "八进制权限，如 644 / 755",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode) },
                enabled = mode.length in 3..4
            ) { Text("应用", color = SunColors.Accent, fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}
