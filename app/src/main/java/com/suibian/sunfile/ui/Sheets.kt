package com.suibian.sunfile.ui

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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import java.io.File

sealed interface SheetKind {
    data object New : SheetKind
    data object Sort : SheetKind
    data object Path : SheetKind
    data class Entry(val entry: FileEntry) : SheetKind
}

sealed interface DialogKind {
    data class Rename(val file: File) : DialogKind
    data object NewFolder : DialogKind
    data object NewFile : DialogKind
    data class Delete(val paths: List<String>) : DialogKind
    data class Properties(val file: File) : DialogKind
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
            OptionCard(Icons.Default.CreateNewFolder, "文件夹", SunColors.Folder, Modifier.weight(1f), onFolder)
            OptionCard(Icons.Default.NoteAdd, "文件", SunColors.Accent, Modifier.weight(1f), onFile)
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
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenText: () -> Unit,
    onOpenExternal: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onCopyPath: () -> Unit,
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
                "${if (entry.isDir) "文件夹" else com.suibian.sunfile.util.Fmt.size(entry.size)} · ${com.suibian.sunfile.util.Fmt.time(entry.lastModified)}",
                color = SunColors.TextTertiary,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        if (!entry.isDir) {
            SheetRow(Icons.Default.OpenInNew, "用其他应用打开", onOpenExternal)
            SheetRow(Icons.Default.TextFields, "用文本编辑器打开", onOpenText)
        } else {
            SheetRow(Icons.Default.OpenInNew, "进入文件夹", onOpen)
        }
        SheetRow(Icons.Default.DriveFileRenameOutline, "重命名", onRename)
        SheetRow(Icons.Default.Info, "属性", onProperties)
        SheetRow(Icons.Default.ContentCopy, "复制路径", onCopyPath)
        SheetRow(Icons.Default.Delete, "删除", onDelete, danger = true)
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
fun PathSheet(current: String, onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    val quick = listOf(
        "/storage/emulated/0",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
        "/storage/emulated/0/Android/data",
        "/"
    )
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
                            Text(k, color = SunColors.TextTertiary, fontSize = 10.5.sp, modifier = Modifier.width(56.dp))
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
                    "Kotlin + Jetpack Compose 实现的单栏文件管理器，支持双窗口交换、内置文本编辑与历史版本回滚。",
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
