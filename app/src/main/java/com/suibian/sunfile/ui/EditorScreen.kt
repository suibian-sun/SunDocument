package com.suibian.sunfile.ui

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.material.icons.outlined.Visibility
import android.widget.Toast
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.Revision
import com.suibian.sunfile.ui.components.CircularReveal
import com.suibian.sunfile.ui.components.DrawCheck
import com.suibian.sunfile.ui.editor.CodeColors
import com.suibian.sunfile.ui.editor.CompletionEngine
import com.suibian.sunfile.ui.editor.CompletionItem
import com.suibian.sunfile.ui.editor.CompletionKind
import com.suibian.sunfile.ui.editor.CompletionResult
import com.suibian.sunfile.ui.editor.Lang
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
import kotlin.math.roundToInt
import kotlin.math.sin

/** 首次打开后，滚动到底部会自动继续追加的字节上限；再往后改为手动点击加载 */
private const val AUTO_GROW_LIMIT = 1_200_000L

private fun lineIndexOf(text: String, offset: Int): Int {
    var line = 1
    var i = 0
    val end = offset.coerceIn(0, text.length)
    while (i < end) {
        if (text[i] == '\n') line++
        i++
    }
    return line
}

private fun lineEndOffset(text: String, line: Int): Int {
    val start = offsetOfLine(text, line)
    val idx = text.indexOf('\n', start)
    return if (idx < 0) text.length else idx
}

private fun commentPrefix(lang: Lang): String = when (lang) {
    Lang.PYTHON, Lang.SHELL, Lang.YAML, Lang.PROPERTIES, Lang.INI, Lang.RUBY, Lang.DOCKER, Lang.GRADLE -> "# "
    Lang.SQL, Lang.LUA -> "-- "
    Lang.HTML, Lang.XML, Lang.MARKDOWN, Lang.JSON, Lang.CSS -> ""
    else -> "// "
}

/** 行首加/去注释，返回新文本与新的选区 */
private fun toggleComment(text: String, fromLine: Int, toLine: Int, prefix: String): Pair<String, IntRange>? {
    if (prefix.isBlank()) return null
    val lines = text.split('\n').toMutableList()
    if (lines.isEmpty()) return null
    val a = (fromLine - 1).coerceIn(0, lines.lastIndex)
    val b = (toLine - 1).coerceIn(0, lines.lastIndex)
    val token = prefix.trimEnd()
    val targets = (a..b).filter { lines[it].isNotBlank() }
    if (targets.isEmpty()) return null
    val commented = targets.all { lines[it].trimStart().startsWith(token) }
    for (i in a..b) {
        if (lines[i].isBlank()) continue
        lines[i] = if (commented) {
            val idx = lines[i].indexOf(token)
            if (idx < 0) lines[i] else {
                val removed = lines[i].removeRange(idx, idx + token.length)
                if (idx == 0 && removed.startsWith(" ")) removed.substring(1) else removed
            }
        } else {
            val indent = lines[i].takeWhile { it == ' ' || it == '\t' }
            indent + prefix + lines[i].removePrefix(indent)
        }
    }
    val newText = lines.joinToString("\n")
    val start = offsetOfLine(newText, a + 1)
    val end = lineEndOffset(newText, b + 1)
    return newText to (start until maxOf(start + 1, end))
}

@Composable
fun EditorOverlay(
    file: File,
    origin: Offset,
    initialLine: Int = 0,
    vm: EditorViewModel,
    prefs: AppPrefs,
    onEditorPrefs: (Int?, Boolean?, Boolean?, Boolean?) -> Unit,
    onOpenHex: (File) -> Unit,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    var visible by remember { mutableStateOf(false) }
    val palette = SunColors
    val codeColors = remember(palette) { CodeColors.of(palette) }

    // 文本由界面持有：输入时只重排这一份，不经过 ViewModel，避免整页重组
    var value by remember(file) { mutableStateOf(TextFieldValue("")) }
    var fontSize by remember(file) { mutableIntStateOf(prefs.editorFontSize) }
    val liveFontSize by rememberUpdatedState(fontSize)
    var zoomPreview by remember { mutableFloatStateOf(1f) }
    var wrap by remember(file) { mutableStateOf(prefs.editorWrap) }
    var lineNumbers by remember(file) { mutableStateOf(prefs.editorLineNumbers) }
    var totalLines by remember(file) { mutableIntStateOf(1) }
    var caretLine by remember { mutableIntStateOf(1) }
    var caretColumn by remember { mutableIntStateOf(1) }
    var showFind by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var saveAsOpen by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var lineAnchor by remember { mutableIntStateOf(-1) }

    fun selectLines(line: Int) {
        val t = value.text
        val anchorLine = lineAnchor
        if (anchorLine > 0 && anchorLine != line) {
            val a = minOf(anchorLine, line)
            val b = maxOf(anchorLine, line)
            value = value.copy(selection = TextRange(offsetOfLine(t, a), lineEndOffset(t, b)))
            lineAnchor = -1
        } else {
            lineAnchor = line
            value = value.copy(selection = TextRange(offsetOfLine(t, line), lineEndOffset(t, line)))
        }
    }
    var burstKey by remember { mutableIntStateOf(0) }
    var completion by remember { mutableStateOf<CompletionResult?>(null) }
    var completionIndex by remember { mutableIntStateOf(0) }
    var encodingMenu by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        visible = true
        vm.open(file)
    }

    // 外部替换（打开、撤销、恢复历史）：只在内容确实不同的时候覆盖输入框
    LaunchedEffect(state.syncToken) {
        if (state.loading || state.file == null || state.syncToken <= 0) return@LaunchedEffect
        if (state.text == value.text) return@LaunchedEffect
        val target = if (state.syncToken == 1 && initialLine > 1) {
            offsetOfLine(state.text, initialLine)
        } else {
            value.selection.start.coerceIn(0, state.text.length)
        }
        value = TextFieldValue(state.text, TextRange(target))
        totalLines = state.lineCount
    }

    // 行数是重活，等打字停下来再在后台算，绝不放在每次输入上
    LaunchedEffect(value.text) {
        delay(180)
        val snapshot = value.text
        val lines = withContext(Dispatchers.Default) {
            var n = 1
            for (c in snapshot) if (c == '\n') n++
            n
        }
        totalLines = lines
        val caret = value.selection.start.coerceIn(0, snapshot.length)
        var l = 1
        var lineStart = 0
        for (i in 0 until caret) {
            if (snapshot[i] == '\n') { l++; lineStart = i + 1 }
        }
        caretLine = l
        caretColumn = caret - lineStart + 1
    }

    // 输入停手后再同步给 ViewModel，保存时以界面文本为准
    LaunchedEffect(value.text) {
        if (state.loading) return@LaunchedEffect
        delay(200)
        vm.updateText(value.text)
    }

    val matches = remember(value.text, findQuery) {
        if (findQuery.isEmpty()) emptyList() else buildList {
            var i = value.text.indexOf(findQuery, 0, ignoreCase = true)
            while (i >= 0 && size < 500) {
                add(i)
                i = value.text.indexOf(findQuery, i + 1, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(value.selection) {
        if (!value.selection.collapsed) completion = null
    }

    fun jumpTo(index: Int) {
        if (matches.isEmpty()) return
        val safe = ((index % matches.size) + matches.size) % matches.size
        findIndex = safe
        val start = matches[safe]
        value = value.copy(selection = TextRange(start, (start + findQuery.length).coerceAtMost(value.text.length)))
    }

    fun refreshSuggestions(tv: TextFieldValue) {
        if (!prefs.editorCompletion || !tv.selection.collapsed) {
            completion = null
            return
        }
        val next = CompletionEngine.suggest(state.lang, tv.text, tv.selection.start, 6)
        if (next?.typed != completion?.typed || next?.replaceEnd != completion?.replaceEnd) {
            completionIndex = 0
        }
        completion = next
    }

    fun applyCompletion(item: CompletionItem) {
        val c = completion ?: return
        val start = c.replaceStart.coerceIn(0, value.text.length)
        val end = c.replaceEnd.coerceIn(start, value.text.length)
        val newText = value.text.substring(0, start) + item.insert + value.text.substring(end)
        val caret = (start + item.insert.length - item.caretBack).coerceIn(0, newText.length)
        value = TextFieldValue(newText, TextRange(caret))
        completion = null
        vm.updateText(newText)
    }

    // 外接键盘：↑↓ 选择、Tab/Enter 接受、Esc 关闭
    fun onKeyEvent(e: KeyEvent): Boolean {
        val c = completion ?: return false
        return when (e.key) {
            Key.Tab, Key.Enter -> {
                if (c.items.isNotEmpty()) {
                    applyCompletion(c.items[completionIndex.coerceIn(0, c.items.lastIndex)])
                    true
                } else false
            }
            Key.DirectionDown -> {
                completionIndex = (completionIndex + 1) % c.items.size
                true
            }
            Key.DirectionUp -> {
                completionIndex = (completionIndex - 1 + c.items.size) % c.items.size
                true
            }
            Key.Escape -> {
                completion = null
                true
            }
            else -> false
        }
    }

    fun close() {
        if (value.text != state.baseline) {
            exitConfirm = true
        } else {
            scope.launch {
                visible = false
                delay(200)
                vm.reset()
                onClose()
            }
        }
    }

    BackHandler {
        when {
            showFind -> showFind = false
            completion != null -> completion = null
            menuOpen -> menuOpen = false
            else -> close()
        }
    }

    val lineHeight = (fontSize * 1.55f).sp
    val textColor = SunColors.TextPrimary
    val textStyle = remember(fontSize, textColor) {
        TextStyle(
            color = textColor,
            fontSize = fontSize.sp,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Monospace
        )
    }
    val highlight = remember(state.lang, codeColors) {
        Syntax.CodeHighlightTransformation(state.lang, codeColors)
    }

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
                        if (value.text != state.baseline) {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(SunColors.Warning))
                        }
                    }
                    Text(
                        (state.file ?: file).parent ?: "",
                        color = SunColors.TextTertiary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.lang == Lang.HTML || state.lang == Lang.MARKDOWN) {
                    EditorIcon(
                        icon = Icons.Outlined.Visibility,
                        label = if (state.lang == Lang.MARKDOWN) "预览 Markdown" else "预览网页"
                    ) { previewOpen = true }
                }
                EditorIcon(Icons.Default.Undo, "撤销", enabled = state.canUndo) { vm.undo() }
                EditorIcon(Icons.Default.Redo, "重做", enabled = state.canRedo) { vm.redo() }
                SaveButton(
                    saving = state.saving,
                    enabled = true,
                    burstKey = burstKey,
                    onSave = { vm.save(value.text) { ok -> if (ok) burstKey++ } }
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            "更多",
                            tint = SunColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
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
                        EditorMenu(if (wrap) "关闭自动换行" else "开启自动换行", Icons.Default.WrapText) {
                            menuOpen = false
                            wrap = !wrap
                            onEditorPrefs(null, wrap, null, null)
                        }
                        EditorMenu(if (lineNumbers) "隐藏行号" else "显示行号", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            lineNumbers = !lineNumbers
                            onEditorPrefs(null, null, lineNumbers, null)
                        }
                        EditorMenu("字号 +", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            fontSize = (fontSize + 1).coerceAtMost(28)
                            onEditorPrefs(fontSize, null, null, null)
                        }
                        EditorMenu("字号 −", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            fontSize = (fontSize - 1).coerceAtLeast(6)
                            onEditorPrefs(fontSize, null, null, null)
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
                        EditorMenu("换行符：${state.newline}", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            vm.setNewline(if (state.newline == "LF") "CRLF" else "LF")
                        }
                        EditorMenu("切换注释", Icons.Default.Code) {
                            menuOpen = false
                            val sel = value.selection
                            val from = lineIndexOf(value.text, sel.min)
                            val to = lineIndexOf(value.text, sel.max)
                            val result = toggleComment(value.text, from, to, commentPrefix(state.lang))
                            if (result == null) {
                                Toast.makeText(context, "当前类型没有行注释语法", Toast.LENGTH_SHORT).show()
                            } else {
                                value = TextFieldValue(result.first, TextRange(result.second.first, result.second.last))
                                vm.updateText(result.first)
                            }
                        }
                        EditorMenu("选中当前行", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            selectLines(caretLine)
                        }
                        EditorMenu("十六进制查看", Icons.Default.FormatLineSpacing) {
                            menuOpen = false
                            onOpenHex(state.file ?: file)
                        }
                    }
                }
            }

            // ---------------- 查找栏 ----------------
            AnimatedVisibility(
                visible = showFind,
                enter = fadeIn(tween(Motion.Fast)),
                exit = fadeOut(tween(Motion.Fast))
            ) {
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
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            "上一个",
                            tint = SunColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (matches.isNotEmpty()) jumpTo(findIndex + 1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            "下一个",
                            tint = SunColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showFind = false }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Close,
                            "关闭",
                            tint = SunColors.TextTertiary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // ---------------- 编辑区 ----------------
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(SunColors.Bg)
                    // 双指捏合：先按图层缩放预览，松手才提交字号，避免每帧整段重排
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var acc = 1f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size >= 2) {
                                    val z = event.calculateZoom()
                                    if (z != 1f && z > 0f) {
                                        acc = (acc * z).coerceIn(0.18f, 4.5f)
                                        zoomPreview = acc
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            val next = (liveFontSize * acc).roundToInt().coerceIn(6, 30)
                            zoomPreview = 1f
                            if (next != fontSize) {
                                fontSize = next
                                onEditorPrefs(next, null, null, null)
                            }
                        }
                    }
            ) {
                val density = LocalDensity.current
                val lineHpx = with(density) { lineHeight.toPx() }
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                val topPad = 8.dp
                val minFieldHeight = (maxHeight - topPad * 2).coerceAtLeast(40.dp)

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

                    else -> Row(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomPreview
                                scaleY = zoomPreview
                            }
                    ) {
                        // 行号：只画可见的那几十行，滚动时按偏移重绘
                        if (lineNumbers && !wrap) {
                            val topPadPx = with(density) { topPad.toPx() }
                            LineGutter(
                                scrollState = vScroll,
                                lineHeightPx = lineHpx,
                                topPaddingPx = topPadPx,
                                totalLines = totalLines,
                                activeLine = caretLine - 1,
                                onLongPressLine = { line ->
                                    if (line in 1..totalLines) selectLines(line)
                                },
                                modifier = Modifier
                                    .width(42.dp)
                                    .fillMaxHeight()
                                    .pointerInput(totalLines, lineHpx) {
                                        detectTapGestures(onLongPress = { pos ->
                                            if (lineHpx > 0f) {
                                                val line = ((pos.y + vScroll.value - topPadPx) / lineHpx).toInt() + 1
                                                if (line >= 1) selectLines(line)
                                            }
                                        })
                                    }
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { nv ->
                                val smart = Syntax.smartEdit(nv, value)
                                value = smart
                                refreshSuggestions(smart)
                            },
                            textStyle = textStyle,
                            cursorBrush = SolidColor(SunColors.Accent),
                            visualTransformation = highlight,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .heightIn(min = minFieldHeight)
                                .verticalScroll(vScroll)
                                .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
                                .onFocusChanged { if (!it.isFocused) completion = null }
                                .onPreviewKeyEvent { onKeyEvent(it) }
                                .padding(horizontal = 10.dp, vertical = topPad)
                        )
                    }
                }

                // 补全候选：浮在编辑区底部，不改变文本区高度，避免重复排版
                val comp = completion
                androidx.compose.animation.AnimatedVisibility(
                    visible = comp != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    enter = fadeIn(tween(Motion.Fast)) +
                        androidx.compose.animation.scaleIn(
                            tween(Motion.Base, easing = Motion.EaseOut),
                            initialScale = 0.97f
                        ),
                    exit = fadeOut(tween(Motion.Fast))
                ) {
                    if (comp != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(SunShapes.Medium)
                                .background(SunColors.Surface4)
                        ) {
                            comp.items.forEachIndexed { i, item ->
                                CompletionRow(
                                    item = item,
                                    active = i == completionIndex,
                                    onClick = { applyCompletion(item) }
                                )
                            }
                            CompletionDoc(comp.items.getOrNull(completionIndex.coerceIn(0, comp.items.lastIndex)))
                        }
                    }
                }

                // 滚到接近底部就继续追加下一段
                LaunchedEffect(state.hasMore, state.loadingMore) {
                    if (!state.hasMore) return@LaunchedEffect
                    snapshotFlow { vScroll.value to vScroll.maxValue }
                        .collect { (v, max) ->
                            val live = vm.state.value
                            if (max > 0 && v >= max - lineHpx * 6 && live.hasMore &&
                                live.loadedBytes < AUTO_GROW_LIMIT && !live.loadingMore
                            ) {
                                vm.loadMore { chunk ->
                                    value = value.copy(
                                        text = value.text + chunk,
                                        selection = value.selection
                                    )
                                }
                            }
                        }
                }
            }

            // ---------------- 继续载入（超过自动追加上限后才出现） ----------------
            if (state.hasMore && !state.loading &&
                (state.loadingMore || state.loadedBytes >= AUTO_GROW_LIMIT)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(SunColors.Surface1)
                        .clickable(enabled = !state.loadingMore) {
                            vm.loadMore { chunk ->
                                value = value.copy(text = value.text + chunk, selection = value.selection)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(
                            Modifier.size(10.dp),
                            color = SunColors.Accent,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("载入中…", color = SunColors.TextTertiary, fontSize = 9.5.sp)
                    } else {
                        Text(
                            "载入下一段 · 剩 ${Fmt.size(state.totalBytes - state.loadedBytes)}",
                            color = SunColors.Accent,
                            fontSize = 9.5.sp
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
                    "$caretLine:$caretColumn   $totalLines 行   ${value.text.length} 字符",
                    color = SunColors.TextTertiary,
                    style = NumberStyle
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.text != state.baseline) {
                        Text("未保存", color = SunColors.Warning, fontSize = 9.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Box {
                        Text(
                            state.encoding,
                            color = SunColors.TextTertiary,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(SunShapes.Small)
                                .clickable { encodingMenu = true }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        DropdownMenu(
                            expanded = encodingMenu,
                            onDismissRequest = { encodingMenu = false },
                            modifier = Modifier.background(SunColors.Surface4, SunShapes.Medium)
                        ) {
                            FileRepo.ENCODINGS.forEach { name ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            name,
                                            color = if (name == state.encoding) SunColors.Accent else SunColors.TextPrimary,
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        encodingMenu = false
                                        vm.setEncoding(name)
                                    },
                                    modifier = Modifier.heightIn(min = 38.dp)
                                )
                            }
                        }
                    }
                }
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
                    val next = value.text.replace(find, replace)
                    if (next != value.text) {
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
            label = "行号（1 - $totalLines）",
            initial = "",
            confirmText = "跳转",
            onDismiss = { gotoOpen = false },
            onConfirm = { text ->
                gotoOpen = false
                val line = text.toIntOrNull()?.coerceIn(1, totalLines) ?: return@InputDialog
                val offset = offsetOfLine(value.text, line)
                value = value.copy(selection = TextRange(offset))
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
                vm.saveAs(name, value.text) { ok -> if (ok) burstKey++ }
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
                    vm.save(value.text) { ok ->
                        if (ok) {
                            exitConfirm = false
                            scope.launch {
                                visible = false
                                delay(200)
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
                        delay(200)
                        vm.reset()
                        onClose()
                    }
                }) { Text("直接退出", color = SunColors.Danger, fontSize = 13.sp) }
            }
        )
    }

    if (previewOpen) {
        PreviewOverlay(
            file = state.file ?: file,
            content = value.text,
            markdown = state.lang == Lang.MARKDOWN,
            onClose = { previewOpen = false }
        )
    }
}

private fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 1) return 0
    var current = 1
    var i = 0
    while (i < text.length) {
        if (text[i] == '\n') {
            current++
            if (current == line) return i + 1
        }
        i++
    }
    return text.length
}

/**
 * 行号槽：用原生 Canvas 直接绘制可见的几十个数字，
 * 不生成任何 Composable，滚动再快也不掉帧。
 */
@Composable
private fun LineGutter(
    scrollState: androidx.compose.foundation.ScrollState,
    lineHeightPx: Float,
    topPaddingPx: Float,
    totalLines: Int,
    activeLine: Int,
    onLongPressLine: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val labelPx = with(density) { 9.sp.toPx() }
    val normalColor = SunColors.TextTertiary.copy(alpha = 0.55f).toArgb()
    val activeColor = SunColors.Accent.toArgb()
    Canvas(modifier) {
        if (lineHeightPx <= 0f) return@Canvas
        // 在绘制阶段读取滚动值：不触发重组，只重绘
        val scroll = scrollState.value
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = labelPx
            textAlign = Paint.Align.RIGHT
        }
        val first = ((scroll - topPaddingPx) / lineHeightPx).toInt().coerceAtLeast(0)
        val visible = (size.height / lineHeightPx).toInt() + 2
        val last = min(totalLines - 1, first + visible)
        val x = size.width - 7.dp.toPx()
        for (i in first..last) {
            if (i < 0) continue
            val y = topPaddingPx + i * lineHeightPx - scroll + lineHeightPx * 0.78f
            if (y < -lineHeightPx || y > size.height + lineHeightPx) continue
            paint.color = if (i == activeLine) activeColor else normalColor
            drawContext.canvas.nativeCanvas.drawText((i + 1).toString(), x, y, paint)
        }
    }
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
                    Text("$revisionCount 个版本", color = SunColors.TextTertiary, fontSize = 9.5.sp)
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
                                    Fmt.size(rev.size),
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

/** 补全候选行：类型徽标 + 命中字符高亮 + 右侧类型 */
@Composable
private fun CompletionRow(item: CompletionItem, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) SunColors.AccentSoft else Color.Transparent
    val badge = when (item.kind) {
        CompletionKind.KEYWORD -> SunColors.Accent
        CompletionKind.SNIPPET -> SunColors.Success
        CompletionKind.SYMBOL -> SunColors.Folder
        CompletionKind.MEMBER -> SunColors.Accent
        CompletionKind.TYPE -> SunColors.Warning
        CompletionKind.WORD -> SunColors.TextTertiary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(badge.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.kind.tag,
                color = badge,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                append(item.label)
                item.hits.forEach { h ->
                    if (h in item.label.indices) {
                        addStyle(SpanStyle(color = SunColors.Accent, fontWeight = FontWeight.SemiBold), h, h + 1)
                    }
                }
            },
            color = SunColors.TextPrimary,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(item.detail, color = SunColors.TextTertiary, fontSize = 8.5.sp, maxLines = 1)
    }
}

/** 选中项的说明卡：一句话讲清它是什么、干什么用 */
@Composable
private fun CompletionDoc(item: CompletionItem?) {
    if (item == null || item.documentation.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(SunColors.Surface3)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            item.kind.label + " · " + item.detail,
            color = SunColors.TextTertiary,
            fontSize = 8.5.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.documentation,
            color = SunColors.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
