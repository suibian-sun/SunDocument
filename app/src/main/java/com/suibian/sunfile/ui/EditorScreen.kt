package com.suibian.sunfile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.data.Revision
import com.suibian.sunfile.ui.components.CircularReveal
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.editor.Syntax
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun EditorOverlay(
    file: File,
    origin: Offset,
    vm: EditorViewModel,
    prefs: AppPrefs,
    onEditorPrefs: (Int?, Boolean?, Boolean?) -> Unit,
    onOpenHex: (File) -> Unit,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var value by remember(file) { mutableStateOf(TextFieldValue("")) }
    var fontSize by remember(file) { mutableIntStateOf(prefs.editorFontSize) }
    var wrap by remember(file) { mutableStateOf(prefs.editorWrap) }
    var lineNumbers by remember(file) { mutableStateOf(prefs.editorLineNumbers) }
    var showFind by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var saveAsOpen by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var burstKey by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var prefixStart by remember { mutableIntStateOf(0) }

    LaunchedEffect(file) {
        visible = true
        vm.open(file)
    }

    LaunchedEffect(state.syncToken) {
        if (!state.loading && state.file != null && state.syncToken > 0) {
            value = TextFieldValue(state.text, TextRange(0))
        }
    }

    val matches = remember(state.text, findQuery) {
        if (findQuery.isEmpty()) emptyList() else buildList {
            var i = state.text.indexOf(findQuery, 0, ignoreCase = true)
            while (i >= 0 && size < 2000) {
                add(i)
                i = state.text.indexOf(findQuery, i + 1, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(value.selection) {
        if (!value.selection.collapsed) suggestions = emptyList()
    }

    fun jumpTo(index: Int) {
        if (matches.isEmpty()) return
        val safe = ((index % matches.size) + matches.size) % matches.size
        findIndex = safe
        val start = matches[safe]
        value = value.copy(selection = TextRange(start, (start + findQuery.length).coerceAtMost(state.text.length)))
    }

    fun refreshSuggestions(tv: TextFieldValue) {
        if (!tv.selection.collapsed || tv.selection.start <= 0) {
            suggestions = emptyList()
            return
        }
        val caret = tv.selection.start
        var s = caret
        while (s > 0 && (tv.text[s - 1].isLetterOrDigit() || tv.text[s - 1] == '_')) s--
        val prefix = tv.text.substring(s, caret)
        prefixStart = s
        suggestions = if (prefix.length >= 2) Syntax.completions(state.lang, prefix, tv.text) else emptyList()
    }

    fun applyCompletion(item: String) {
        val caret = value.selection.start.coerceIn(0, value.text.length)
        val prefix = value.text.substring(prefixStart.coerceIn(0, caret), caret)
        val insert = Syntax.applySnippet(item, prefix)
        val newText = value.text.substring(0, prefixStart) + insert + value.text.substring(caret)
        val newValue = TextFieldValue(newText, TextRange(prefixStart + insert.length))
        value = newValue
        vm.updateText(newText)
        suggestions = emptyList()
    }

    fun close() {
        if (state.dirty && !state.readOnly) {
            exitConfirm = true
        } else {
            scope.launch {
                visible = false
                delay(220)
                vm.reset()
                onClose()
            }
        }
    }

    BackHandler {
        when {
            showFind -> showFind = false
            menuOpen -> menuOpen = false
            else -> close()
        }
    }

    val lineHeight = (fontSize * 1.55f).sp
    val textStyle = TextStyle(
        color = SunColors.TextPrimary,
        fontSize = fontSize.sp,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace
    )

    CircularReveal(origin = origin, visible = visible, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SunColors.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ---------------- 顶栏 ----------------
            Row(
                Modifier.fillMaxWidth().height(52.dp).background(SunColors.Surface1).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { close() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "返回",
                        tint = SunColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.file?.name ?: file.name,
                            color = SunColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.dirty) {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(SunColors.Warning))
                        }
                    }
                    Text(
                        compactPath((state.file ?: file).parent ?: "") +
                            "  ·  ${state.lang.label}" + if (state.readOnly) " · 只读" else "",
                        color = SunColors.TextTertiary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                EditorIcon(Icons.Default.Undo, "撤销", enabled = state.canUndo) { vm.undo() }
                EditorIcon(Icons.Default.Redo, "重做", enabled = state.canRedo) { vm.redo() }
                SaveButton(
                    saving = state.saving,
                    enabled = !state.readOnly,
                    burstKey = burstKey,
                    onSave = { vm.save { ok -> if (ok) burstKey++ } }
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.MoreVert, "更多", tint = SunColors.TextSecondary, modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(SunColors.Surface4, SunShapes.Medium)
                    ) {
                        EditorMenu("查找", Icons.Default.FindReplace) {
                            menuOpen = false
                            showFind = true
                        }
                        EditorMenu("替换…", Icons.Default.FindReplace) {
                            menuOpen = false
                            replaceOpen = true
                        }
                        EditorMenu("跳转到行", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            gotoOpen = true
                        }
                        EditorMenu(
                            if (wrap) "关闭自动换行" else "开启自动换行",
                            Icons.Default.WrapText
                        ) {
                            menuOpen = false
                            wrap = !wrap
                            onEditorPrefs(null, wrap, null)
                        }
                        EditorMenu(
                            if (lineNumbers) "隐藏行号" else "显示行号",
                            Icons.Default.FormatLineSpacing
                        ) {
                            menuOpen = false
                            lineNumbers = !lineNumbers
                            onEditorPrefs(null, null, lineNumbers)
                        }
                        EditorMenu("字号 +", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            fontSize = (fontSize + 1).coerceAtMost(22)
                            onEditorPrefs(fontSize, null, null)
                        }
                        EditorMenu("字号 −", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            fontSize = (fontSize - 1).coerceAtLeast(10)
                            onEditorPrefs(fontSize, null, null)
                        }
                        EditorMenu("历史版本", Icons.Default.History) {
                            menuOpen = false
                            showHistory = true
                        }
                        EditorMenu("另存为…", Icons.Default.Save) {
                            menuOpen = false
                            saveAsOpen = true
                        }
                        EditorMenu("重新载入", Icons.Default.History) {
                            menuOpen = false
                            vm.reload()
                        }
                        EditorMenu("十六进制查看", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            onOpenHex(state.file ?: file)
                        }
                    }
                }
            }

            // ---------------- 查找栏 ----------------
            AnimatedVisibility(visible = showFind, enter = fadeIn(tween(Motion.Fast)), exit = fadeOut(tween(Motion.Fast))) {
                Row(
                    Modifier.fillMaxWidth().background(SunColors.Surface2).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SunTextField(
                        value = findQuery,
                        onValueChange = {
                            findQuery = it
                            findIndex = 0
                        },
                        label = "查找",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Search,
                        onDone = { if (matches.isNotEmpty()) jumpTo(0) }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (matches.isEmpty()) "0/0" else "${findIndex + 1}/${matches.size}",
                        color = SunColors.TextTertiary,
                        style = NumberStyle
                    )
                    IconButton(
                        onClick = { if (matches.isNotEmpty()) jumpTo(findIndex - 1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "上一个", tint = SunColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (matches.isNotEmpty()) jumpTo(findIndex + 1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "下一个", tint = SunColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showFind = false }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = SunColors.TextTertiary, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // ---------------- 大文件提示 ----------------
            if (state.readOnly) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2313))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, null, tint = SunColors.Warning, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "大文件 ${Fmt.size(state.totalBytes)}，已载入前 ${Fmt.size(state.text.toByteArray().size.toLong())}（只读）",
                        color = SunColors.Warning,
                        fontSize = 9.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "十六进制",
                        color = SunColors.TextSecondary,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable { onOpenHex(state.file ?: file) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                    Text(
                        "另存为",
                        color = SunColors.Accent,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .clip(SunShapes.Small)
                            .clickable { saveAsOpen = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ---------------- 编辑区 ----------------
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(SunColors.Bg)) {
                val density = LocalDensity.current
                val lineHpx = with(density) { lineHeight.toPx() }
                val viewportPx = with(density) { maxHeight.toPx() }
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()

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

                    wrap -> BasicTextField(
                        value = value,
                        onValueChange = { nv ->
                            val smart = if (state.readOnly) nv else Syntax.smartEdit(nv, value)
                            value = smart
                            vm.updateText(smart.text)
                            refreshSuggestions(smart)
                        },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(SunColors.Accent),
                        visualTransformation = remember(state.lang) { Syntax.CodeHighlightTransformation(state.lang) },
                        readOnly = state.readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    else -> Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
                        if (lineNumbers && state.lineCount <= 3_000) {
                            val activeLine = currentLine(value)
                            Column(
                                Modifier
                                    .width(44.dp)
                                    .background(SunColors.Surface1.copy(alpha = 0.55f))
                                    .padding(top = 8.dp, bottom = 8.dp)
                            ) {
                                repeat(state.lineCount) { i ->
                                    Text(
                                        (i + 1).toString(),
                                        color = if (i == activeLine) SunColors.Accent else SunColors.TextTertiary.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        lineHeight = lineHeight,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                        Box(Modifier.horizontalScroll(hScroll)) {
                            BasicTextField(
                                value = value,
                                onValueChange = { nv ->
                                    val smart = if (state.readOnly) nv else Syntax.smartEdit(nv, value)
                                    value = smart
                                    vm.updateText(smart.text)
                                    refreshSuggestions(smart)
                                },
                                textStyle = textStyle,
                                cursorBrush = SolidColor(SunColors.Accent),
                                visualTransformation = remember(state.lang) { Syntax.CodeHighlightTransformation(state.lang) },
                                readOnly = state.readOnly,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Default
                                ),
                                // 超长单行文本限制最大宽度，避免水平滚动容器申请超大尺寸
                                modifier = Modifier
                                    .widthIn(max = 8000.dp)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // 光标跟随滚动
                LaunchedEffect(value.selection.start, wrap, state.syncToken) {
                    if (!wrap && state.lineCount <= 3_000 && state.text.length < 400_000) {
                        val line = currentLine(value)
                        val y = line * lineHpx
                        if (y < vScroll.value + lineHpx * 2 || y > vScroll.value + viewportPx - lineHpx * 3) {
                            vScroll.scrollTo((y - viewportPx / 2f).toInt().coerceAtLeast(0))
                        }
                    }
                }
            }

            // ---------------- 补全候选 ----------------
            AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(SunColors.Surface2)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    suggestions.forEach { item ->
                        Text(
                            item,
                            color = SunColors.TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(SunShapes.Small)
                                .background(SunColors.Surface4)
                                .clickable { applyCompletion(item) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ---------------- 状态栏 ----------------
            Row(
                Modifier.fillMaxWidth().height(24.dp).background(SunColors.Surface1).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "行 ${currentLine(value) + 1}, 列 ${currentColumn(value)}  ·  ${state.lineCount} 行  ·  ${state.text.length} 字符",
                    color = SunColors.TextTertiary,
                    style = NumberStyle
                )
                Text(
                    state.encoding + if (state.dirty) "  ·  未保存" else "  ·  已保存",
                    color = if (state.dirty) SunColors.Warning else SunColors.TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
    }

    // ---------------- 弹层 ----------------

    if (showHistory) {
        HistorySheet(
            revisions = vm.revisions(),
            revisionCount = state.revisionCount,
            onPreview = { rev -> vm.revisionContent(rev) },
            onRestore = { rev ->
                vm.restoreToEditor(rev)
                showHistory = false
            },
            onClear = {
                vm.clearHistory()
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    if (replaceOpen) {
        ReplaceDialog(
            initialFind = findQuery,
            onDismiss = { replaceOpen = false },
            onReplaceAll = { find, replace ->
                replaceOpen = false
                if (find.isNotEmpty()) {
                    val next = state.text.replace(find, replace)
                    if (next != state.text) {
                        vm.replaceText(next)
                        findQuery = find
                        showFind = true
                    }
                }
            }
        )
    }

    if (gotoOpen) {
        InputDialog(
            title = "跳转到行",
            label = "行号（1 - ${state.lineCount}）",
            initial = "",
            confirmText = "跳转",
            onDismiss = { gotoOpen = false },
            onConfirm = { text ->
                gotoOpen = false
                val line = text.toIntOrNull()?.coerceIn(1, state.lineCount) ?: return@InputDialog
                var index = 0
                var current = 1
                while (current < line && index < state.text.length) {
                    if (state.text[index] == '\n') current++
                    index++
                }
                value = value.copy(selection = TextRange(index.coerceAtMost(state.text.length)))
            }
        )
    }

    if (saveAsOpen) {
        InputDialog(
            title = "另存为",
            label = "文件名",
            initial = (state.file ?: file).name,
            confirmText = "保存",
            onDismiss = { saveAsOpen = false },
            onConfirm = { name ->
                saveAsOpen = false
                vm.saveAs(name) { ok -> if (ok) burstKey++ }
            }
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
                                delay(220)
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
                        delay(220)
                        vm.reset()
                        onClose()
                    }
                }) { Text("直接退出", color = SunColors.Danger, fontSize = 13.sp) }
            }
        )
    }
}

private fun currentLine(value: TextFieldValue): Int {
    val caret = value.selection.start.coerceIn(0, value.text.length)
    var count = 0
    var i = 0
    while (i < caret) {
        if (value.text[i] == '\n') count++
        i++
    }
    return count
}

private fun currentColumn(value: TextFieldValue): Int {
    val caret = value.selection.start.coerceIn(0, value.text.length)
    val idx = value.text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
    return if (idx < 0) caret + 1 else caret - idx
}

@Composable
private fun EditorIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            label,
            tint = if (enabled) SunColors.TextSecondary else SunColors.TextTertiary.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun EditorMenu(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = SunColors.TextPrimary, fontSize = 12.5.sp) },
        leadingIcon = { Icon(icon, null, tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 42.dp)
    )
}

/** 保存按钮：保存成功时收缩成圆 → 画对勾 → 粒子迸发 */
@Composable
private fun SaveButton(saving: Boolean, enabled: Boolean, burstKey: Int, onSave: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(burstKey) {
        if (burstKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
        }
    }
    val success = burstKey > 0 && progress.value < 1f

    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !saving && enabled) { onSave() },
        contentAlignment = Alignment.Center
    ) {
        when {
            saving -> CircularProgressIndicator(Modifier.size(16.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            success -> {
                DrawCheck(checked = true, modifier = Modifier.size(19.dp), background = SunColors.Success)
                Burst(progress = progress.value, color = SunColors.Success, modifier = Modifier.size(40.dp))
            }
            else -> Icon(
                Icons.Default.Save,
                "保存",
                tint = if (enabled) SunColors.Accent else SunColors.TextTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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
        AlertDialog(
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


@Composable
private fun ReplaceDialog(
    initialFind: String,
    onDismiss: () -> Unit,
    onReplaceAll: (String, String) -> Unit
) {
    var find by remember { mutableStateOf(initialFind) }
    var replace by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("查找并替换", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                SunTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = "查找内容",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SunTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = "替换为（留空即删除）",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onReplaceAll(find, replace) }) {
                Text("全部替换", color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}
