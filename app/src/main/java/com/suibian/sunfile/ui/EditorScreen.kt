package com.suibian.sunfile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.Revision
import com.suibian.sunfile.ui.components.CircularReveal
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.EditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EditorOverlay(
    file: File,
    origin: Offset,
    vm: EditorViewModel,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var text by remember(file) { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var burstKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(file) {
        visible = true
        vm.open(file)
    }

    LaunchedEffect(state.loading, state.file) {
        if (!state.loading && state.file != null) text = state.text
    }

    fun close() {
        if (state.dirty) {
            exitConfirm = true
        } else {
            scope.launch {
                visible = false
                delay(300)
                vm.reset()
                onClose()
            }
        }
    }

    BackHandler { close() }

    CircularReveal(origin = origin, visible = visible, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SunColors.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { close() }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = SunColors.TextSecondary, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            file.name,
                            color = SunColors.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.dirty) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(SunColors.Warning))
                        }
                    }
                    Text(
                        compactPath(file.parent ?: ""),
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showHistory = true }, modifier = Modifier.size(40.dp)) {
                    Box {
                        Icon(Icons.Default.History, "历史版本", tint = SunColors.TextSecondary, modifier = Modifier.size(19.dp))
                        if (state.revisionCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SunColors.Accent)
                            )
                        }
                    }
                }
                SaveButton(
                    saving = state.saving,
                    burstKey = burstKey,
                    onSave = { vm.save { ok -> if (ok) burstKey++ } }
                )
            }

            // 编辑区
            Box(Modifier.weight(1f).fillMaxWidth().background(SunColors.Bg)) {
                when {
                    state.loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                        color = SunColors.Accent,
                        strokeWidth = 2.dp
                    )

                    state.error != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("无法打开文件", color = SunColors.TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(state.error ?: "", color = SunColors.TextTertiary, fontSize = 10.5.sp)
                    }

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                vm.updateText(it)
                            },
                            textStyle = TextStyle(
                                color = SunColors.TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(SunColors.Accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp)
                        )
                    }
                }
            }

            // 状态栏
            Row(
                Modifier.fillMaxWidth().height(28.dp).background(SunColors.Surface1).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "行 ${state.lineCount}  ·  ${state.text.length} 字符",
                    color = SunColors.TextTertiary,
                    style = NumberStyle
                )
                Text(
                    if (state.dirty) "未保存" else "已保存",
                    color = if (state.dirty) SunColors.Warning else SunColors.TextTertiary,
                    fontSize = 9.5.sp
                )
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            revisions = vm.revisions(),
            revisionCount = state.revisionCount,
            onPreview = { rev -> vm.revisionContent(rev) },
            onRestore = { rev ->
                vm.restoreToEditor(rev)
                text = vm.state.value.text
                showHistory = false
            },
            onClear = {
                vm.clearHistory()
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = { Text("还有未保存的修改", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = { Text("退出后本次修改会丢失，是否先保存？", color = SunColors.TextSecondary, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.save { ok ->
                        if (ok) {
                            exitConfirm = false
                            scope.launch {
                                visible = false
                                delay(300)
                                vm.reset()
                                onClose()
                            }
                        }
                    }
                }) { Text("保存并退出", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = {
                    exitConfirm = false
                    scope.launch {
                        visible = false
                        delay(300)
                        vm.reset()
                        onClose()
                    }
                }) { Text("直接退出", color = SunColors.Danger, fontSize = 13.sp) }
            }
        )
    }
}

/** 保存按钮：保存成功时收缩成圆 → 画对勾 → 粒子迸发 */
@Composable
private fun SaveButton(saving: Boolean, burstKey: Int, onSave: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(burstKey) {
        if (burstKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
        }
    }
    val success = burstKey > 0 && progress.value < 1f

    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !saving) { onSave() },
        contentAlignment = Alignment.Center
    ) {
        when {
            saving -> CircularProgressIndicator(Modifier.size(17.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            success -> {
                DrawCheck(checked = true, modifier = Modifier.size(21.dp), background = SunColors.Success)
                Burst(progress = progress.value, color = SunColors.Success, modifier = Modifier.size(44.dp))
            }
            else -> Icon(Icons.Default.Save, "保存", tint = SunColors.Accent, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Burst(progress: Float, color: Color, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension * 0.5f
        repeat(8) { i ->
            val angle = (Math.PI / 4 * i).toFloat()
            val r = maxR * (0.35f + progress * 0.65f)
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha * 0.9f),
                radius = 1.6.dp.toPx() * (1f - progress * 0.5f),
                center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    revisions: List<Revision>,
    revisionCount: Int,
    onPreview: (Revision) -> String,
    onRestore: (Revision) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var preview by remember { mutableStateOf<Pair<Revision, String>?>(null) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Sheet
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("修改历史", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("$revisionCount 个版本 · 每次保存前自动留档", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                }
                if (revisions.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("清空", color = SunColors.Danger, fontSize = 12.sp)
                    }
                }
            }
            if (revisions.isEmpty()) {
                Text(
                    "还没有历史版本，首次保存后会生成",
                    color = SunColors.TextTertiary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    revisions.forEach { rev ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { preview = rev to onPreview(rev) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.time(rev.time), color = SunColors.TextPrimary, fontSize = 12.sp)
                                Text(
                                    "${Fmt.size(rev.size)} · 点击预览",
                                    color = SunColors.TextTertiary,
                                    fontSize = 9.5.sp,
                                    style = NumberStyle
                                )
                            }
                            TextButton(onClick = { onRestore(rev) }) {
                                Text("恢复", color = SunColors.Accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { (rev, content) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = {
                Text("版本预览 · ${Fmt.time(rev.time)}", color = SunColors.TextPrimary, fontSize = 14.sp)
            },
            text = {
                Text(
                    content.take(4000),
                    color = SunColors.TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRestore(rev)
                    preview = null
                }) { Text("恢复此版本", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) {
                    Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }
}

