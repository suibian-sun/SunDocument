package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            fontSize = 15.sp,
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
            fontSize = 15.sp,
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
    onDelete: () -> Unit
) {
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
            fontSize = 15.sp,
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
            fontSize = 15.sp,
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

/** 打开方式选择：仿 MT 管理器的「打开方式」弹窗 */
@Composable
fun OpenWithSheet(
    file: File,
    remembered: OpenMode?,
    onDismiss: () -> Unit,
    onPick: (OpenMode) -> Unit,
    onPickAndRemember: (OpenMode) -> Unit
) {
    var remember by remember { mutableStateOf(false) }
    val rawExt = FileKinds.extensionOf(file)
    val ext = rawExt.ifEmpty { "无扩展名" }
    val extLabel = if (rawExt.isEmpty()) "" else ".$rawExt"
    val modes = buildList {
        add(OpenMode.TEXT)
        add(OpenMode.HEX)
        if (FileKinds.kindOf(file) == com.suibian.sunfile.data.FileKind.IMAGE) add(OpenMode.IMAGE)
        if (FileKinds.isZipReadable(file)) add(OpenMode.ARCHIVE)
        add(OpenMode.EXTERNAL)
    }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "打开方式",
                color = SunColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${file.name} · $extLabel" .trimEnd(' ', '·') +
                    if (remembered != null) " · 当前默认：${remembered.label}" else "",
                color = SunColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        modes.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (remember) onPickAndRemember(mode) else onPick(mode)
                    }
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modeIcon(mode),
                    null,
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, color = SunColors.TextPrimary, fontSize = 13.sp)
                    Text(modeHint(mode), color = SunColors.TextTertiary, fontSize = 9.sp)
                }
                if (remembered == mode) {
                    Icon(Icons.Default.Check, null, tint = SunColors.Accent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { remember = !remember }
                .padding(horizontal = 20.dp, vertical = 10.dp),
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
    }
}

private fun modeIcon(mode: OpenMode) = when (mode) {
    OpenMode.TEXT -> Icons.Outlined.TextFields
    OpenMode.HEX -> Icons.Outlined.Terminal
    OpenMode.IMAGE -> Icons.Outlined.Image
    OpenMode.ARCHIVE -> Icons.Outlined.Archive
    OpenMode.EXTERNAL -> Icons.Outlined.OpenInNew
}

private fun modeHint(mode: OpenMode) = when (mode) {
    OpenMode.TEXT -> "语法高亮、补全，可直接编辑保存"
    OpenMode.HEX -> "按字节查看任意大小的文件"
    OpenMode.IMAGE -> "内置缩放查看大图"
    OpenMode.ARCHIVE -> "浏览 zip/apk 内部文件并解压"
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
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
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
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
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
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
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
        title = { Text("属性", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
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
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val default = when (paths.size) {
        1 -> File(paths.first()).name.substringBeforeLast('.').ifEmpty { "archive" } + ".zip"
        else -> "archive.zip"
    }
    var name by remember { mutableStateOf(default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("压缩为 ZIP", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("共 ${paths.size} 项", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                SunTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "压缩包名称",
                    modifier = Modifier.fillMaxWidth(),
                    mono = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text("开始压缩", color = SunColors.Accent, fontSize = 13.sp)
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
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    val sample = paths.take(3).map { File(it).name.replace(find, replace) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("批量重命名 ${paths.size} 项", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                SunTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = "查找",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SunTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = "替换为（留空即删除）",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("预览", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                sample.forEach { s ->
                    Text(
                        s,
                        color = SunColors.TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (find.isNotEmpty()) onConfirm(find, replace) }) {
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
        title = { Text("校验和", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
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
                    fontSize = 15.sp,
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
        title = { Text("修改权限", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
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
