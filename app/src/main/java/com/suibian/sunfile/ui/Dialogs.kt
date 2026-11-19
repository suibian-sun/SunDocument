package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.update.ReleaseInfo
import com.suibian.sunfile.update.UpdateState
import com.suibian.sunfile.util.Fmt
import java.io.File

private val dialogShape = RoundedCornerShape(14.dp)

@Composable
fun InputDialog(
    title: String,
    initial: String,
    confirmText: String = "确定",
    hint: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Panel,
        titleContentColor = SunColors.TextPrimary,
        textContentColor = SunColors.TextSecondary,
        shape = dialogShape,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { if (hint.isNotEmpty()) Text(hint, fontSize = 12.sp, color = SunColors.TextDim) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SunColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm(value.trim()) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SunColors.Accent,
                        unfocusedBorderColor = SunColors.Divider,
                        cursorColor = SunColors.Accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) {
                Text(confirmText, color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

@Composable
fun PathDialog(
    current: String,
    onGo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(current) }
    val shortcuts = listOf(
        "内部存储" to "/storage/emulated/0",
        "下载" to "/storage/emulated/0/Download",
        "根目录" to "/"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Panel,
        titleContentColor = SunColors.TextPrimary,
        shape = dialogShape,
        title = { Text("跳转到路径", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = SunColors.TextPrimary,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SunColors.Accent,
                        unfocusedBorderColor = SunColors.Divider,
                        cursorColor = SunColors.Accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                shortcuts.forEach { (label, path) ->
                    TextButton(onClick = { onGo(path) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)) {
                        Text("› $label   $path", color = SunColors.TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onGo(value.trim()) }) {
                Text("跳转", color = SunColors.Accent, fontSize = 13.sp)
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
        containerColor = SunColors.Panel,
        shape = dialogShape,
        title = { Text(title, fontSize = 15.sp, color = SunColors.TextPrimary, fontWeight = FontWeight.Medium) },
        text = { Text(message, fontSize = 12.5.sp, color = SunColors.TextSecondary) },
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
    val props by produceState<Map<String, String>>(initialValue = emptyMap(), file) {
        value = loader(file)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Panel,
        shape = dialogShape,
        title = { Text("属性", fontSize = 15.sp, color = SunColors.TextPrimary, fontWeight = FontWeight.Medium) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (props.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在统计…", color = SunColors.TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    props.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                k, color = SunColors.TextDim, fontSize = 11.sp,
                                modifier = Modifier.width(58.dp)
                            )
                            Text(
                                v, color = SunColors.TextPrimary, fontSize = 11.sp,
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
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onDownload: (ReleaseInfo) -> Unit,
    onInstall: (File) -> Unit,
    onOpenPermission: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateState.Idle) return
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        containerColor = SunColors.Panel,
        shape = dialogShape,
        title = {
            Text(
                text = when (state) {
                    is UpdateState.Checking -> "检查更新"
                    is UpdateState.UpToDate -> "检查更新"
                    is UpdateState.Available -> "发现新版本 v${state.info.version}"
                    is UpdateState.Downloading -> "正在下载更新"
                    is UpdateState.Ready -> "下载完成"
                    is UpdateState.Error -> "更新失败"
                    else -> "检查更新"
                },
                fontSize = 15.sp,
                color = SunColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when (state) {
                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在连接 GitHub…", color = SunColors.TextSecondary, fontSize = 12.sp)
                        }
                    }

                    is UpdateState.UpToDate -> Text(
                        "当前版本 v${state.current} 已是最新版本。",
                        color = SunColors.TextSecondary,
                        fontSize = 12.5.sp
                    )

                    is UpdateState.Available -> {
                        Text(
                            "当前 v$currentVersion  →  最新 v${state.info.version}",
                            color = SunColors.TextPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            "安装包 ${Fmt.size(state.info.size)} · ${state.info.publishedAt.take(10)}",
                            color = SunColors.TextDim,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("更新说明", color = SunColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.info.notes.take(1500),
                            color = SunColors.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SunColors.Bg, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            "线路 ${state.attempt}/${state.routeCount}：${state.route}",
                            color = SunColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(SunColors.Bg, RoundedCornerShape(3.dp)),
                            color = SunColors.Accent,
                            trackColor = SunColors.Divider
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${(state.progress * 100).toInt()}%  ${Fmt.size(state.downloaded)} / ${Fmt.size(state.total)}",
                                color = SunColors.TextPrimary,
                                fontSize = 11.sp
                            )
                            Text(
                                if (state.speed > 0) Fmt.speed(state.speed) else "连接中…",
                                color = SunColors.TextDim,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "从 GitHub Release 拉取，加速线路失败会自动切换下一条。",
                            color = SunColors.TextDim,
                            fontSize = 10.sp
                        )
                    }

                    is UpdateState.Ready -> {
                        Text(
                            if (state.needPermission) "安装包已下载，但需要先允许安装未知来源应用。"
                            else "安装包已下载，正在调起系统安装程序…",
                            color = SunColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "文件：${state.apk.name}\n大小：${Fmt.size(state.apk.length())}\n版本：v${state.info.version}",
                            color = SunColors.TextDim,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    is UpdateState.Error -> Text(
                        state.message,
                        color = SunColors.Danger,
                        fontSize = 12.sp
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onDownload(state.info) }) {
                    Text("立即更新", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Ready -> TextButton(
                    onClick = {
                        if (state.needPermission) onOpenPermission() else onInstall(state.apk)
                    }
                ) {
                    Text(if (state.needPermission) "去授权" else "立即安装", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Error -> TextButton(onClick = onRetry) {
                    Text("重试", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Downloading -> TextButton(onClick = {}, enabled = false) {
                    Text("下载中…", color = SunColors.TextDim, fontSize = 13.sp)
                }

                else -> TextButton(onClick = onDismiss) {
                    Text("好的", color = SunColors.Accent, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            if (state is UpdateState.Downloading) {
                Text("后台下载中", color = SunColors.TextDim, fontSize = 11.sp)
            } else if (state is UpdateState.Ready && state.needPermission) {
                TextButton(onClick = { onInstall(state.apk) }) {
                    Text("重新安装", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
fun ColorDot(color: Color, size: Int = 8) {
    Spacer(Modifier.size(size.dp).background(color, RoundedCornerShape(50)))
}
