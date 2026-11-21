package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Shell
import com.suibian.sunfile.data.ShellEngine
import com.suibian.sunfile.data.term.ATTR_BOLD
import com.suibian.sunfile.data.term.ATTR_DIM
import com.suibian.sunfile.data.term.ATTR_INVERSE
import com.suibian.sunfile.data.term.ATTR_ITALIC
import com.suibian.sunfile.data.term.ATTR_STRIKE
import com.suibian.sunfile.data.term.ATTR_UNDERLINE
import com.suibian.sunfile.data.term.COLOR_DEFAULT
import com.suibian.sunfile.data.term.TerminalSession
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes

/**
 * 终端会话的宿主。
 *
 * 会话放在页面外，退出终端回到文件列表再进来，历史与运行中的进程都还在，
 * 这一点与 Termux / ZeroTermux 的行为一致。
 */
private object TermSessions {
    private val _list = mutableStateListOf<TerminalSession>()
    val list: List<TerminalSession> get() = _list
    var active by mutableIntStateOf(0)
    private var seq = 1

    fun add(context: Context, cols: Int = 80, rows: Int = 24): TerminalSession {
        val s = TerminalSession(seq++, context.applicationContext, cols, rows)
        s.start()
        _list.add(s)
        active = _list.lastIndex
        return s
    }

    fun close(index: Int) {
        val s = _list.getOrNull(index) ?: return
        s.close()
        _list.removeAt(index)
        if (_list.isEmpty()) {
            active = 0
        } else {
            active = active.coerceIn(0, _list.lastIndex)
        }
    }

    fun closeAll() {
        _list.forEach { it.close() }
        _list.clear()
        active = 0
    }
}

// Termux 的默认配色，刻意不跟随应用主题：终端黑底是行业惯例，也更省电
private val TermBg = Color(0xFF0B0B0C)
private val TermFg = Color(0xFFE7E7E9)

@Composable
fun TerminalPage(
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // 自定义 su 命令与引擎只影响「提示语」，终端本身永远走本地 sh
    val engine = Shell.engine

    val sessions: SnapshotStateList<TerminalSession> = TermSessions.list as SnapshotStateList<TerminalSession>
    val activeIndex = TermSessions.active
    val session = sessions.getOrNull(activeIndex)

    var tick by remember { mutableIntStateOf(0) }
    var fontSize by remember { mutableIntStateOf(12) }
    var showMenu by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var kbVisible by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }

    val measurer = rememberTextMeasurer(cacheSize = 1024)
    val density = LocalDensity.current
    val textStyle = remember(fontSize) {
        TextStyle(
            color = TermFg,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (fontSize * 1.24f).sp
        )
    }
    val cellW = remember(fontSize, measurer) {
        measurer.measure("M", textStyle).size.width.toFloat().coerceAtLeast(1f)
    }
    val cellH = with(density) { (fontSize * 1.24f).sp.toPx() }

    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewW, viewH, cellW, cellH, activeIndex) {
        if (viewW <= 0 || viewH <= 0) return@LaunchedEffect
        val cols = (viewW / cellW).toInt().coerceIn(20, 400)
        val rows = (viewH / cellH).toInt().coerceIn(4, 300)
        // 首帧先量屏幕再起进程：pty 的窗口尺寸从第一条命令起就是对的
        if (sessions.isEmpty()) {
            TermSessions.add(context, cols, rows)
        } else {
            sessions.getOrNull(activeIndex)?.resize(cols, rows)
        }
        tick++
    }

    // 输出不是每字节都触发重组：40ms 一次，滚动与重绘都够顺
    LaunchedEffect(activeIndex) {
        while (true) {
            kotlinx.coroutines.delay(40)
            session?.let { tick = it.buffer.revision }
        }
    }

    fun send(text: String) {
        session?.write(text)
        session?.buffer?.scrollToBottom()
    }

    fun sendKey(code: String) {
        val out = when {
            ctrlArmed && code.length == 1 -> {
                val c = code[0].uppercaseChar()
                if (c in 'A'..'Z') ((c.code - 64).toChar()).toString() else code
            }
            altArmed -> "\u001b$code"
            else -> code
        }
        ctrlArmed = false
        altArmed = false
        send(out)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TermBg)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ---------------- 顶栏 ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .topBarBackground()
                .statusBarsPadding()
                .height(Mt.BarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "终端",
                    color = SunColors.OnTopBar,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (sessions.size > 1) "会话 ${activeIndex + 1}/${sessions.size} · ${engine.label}" else engine.label,
                    color = SunColors.OnTopBarMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                "${fontSize}sp",
                color = SunColors.OnTopBarMuted,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { fontSize = (fontSize + 1).coerceAtMost(22) }
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            )
            IconButton(onClick = { TermSessions.add(context) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, "新建会话", tint = SunColors.OnTopBar, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.MoreVert, "更多", tint = SunColors.OnTopBar, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        MenuAction("复制屏幕") {
                            showMenu = false
                            session?.let { copyToClipboard(context, it.buffer.screenText()) }
                        }
                        MenuAction("复制全部") {
                            showMenu = false
                            session?.let { copyToClipboard(context, it.buffer.allText()) }
                        }
                        MenuAction("粘贴") {
                            showMenu = false
                            pasteFromClipboard(context)?.let { send(it) }
                        }
                        MenuAction("清屏") {
                            showMenu = false
                            session?.buffer?.clearAll()
                            tick++
                        }
                        MenuAction("发送 Ctrl+C") {
                            showMenu = false
                            send("\u0003")
                        }
                        MenuAction("发送 Ctrl+D") {
                            showMenu = false
                            send("\u0004")
                        }
                        MenuAction("字号 +") { showMenu = false; fontSize = (fontSize + 1).coerceAtMost(22) }
                        MenuAction("字号 −") { showMenu = false; fontSize = (fontSize - 1).coerceAtLeast(8) }
                        MenuAction("重新连接") {
                            showMenu = false
                            if (session != null && !session.running) {
                                session.start(); tick++
                            }
                        }
                        MenuAction("关闭当前会话") {
                            showMenu = false
                            TermSessions.close(activeIndex)
                        }
                    if (sessions.size > 1) {
                        MenuAction("切换会话") {
                            showMenu = false
                            showSessions = true
                        }
                    }
                }
            }
        }

        // ---------------- 终端画面 ----------------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { s -> viewW = s.width; viewH = s.height }
        ) {
            if (session == null) {
                Text(
                    "正在启动…",
                    color = SunColors.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                TerminalCanvas(
                    session = session,
                    measurer = measurer,
                    textStyle = textStyle,
                    cellW = cellW,
                    cellH = cellH,
                    tick = tick,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(session) {
                            detectTapGestures(
                                onTap = {
                                    focus.requestFocus()
                                    keyboard?.show()
                                    kbVisible = true
                                },
                                onLongPress = {
                                    copyToClipboard(context, session.buffer.screenText())
                                    toast(context, "已复制当前屏幕")
                                }
                            )
                        }
                        .pointerInput(session) {
                            var acc = 0f
                            detectDragGestures { change, drag ->
                                acc += drag.y
                                val step = (acc / cellH).toInt()
                                if (step != 0) {
                                    session.buffer.scrollBy(-step)
                                    acc -= step * cellH
                                    tick++
                                }
                                change.consume()
                            }
                        }
                )
            }
        }

        // ---------------- 扩展键行 ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF151517))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyCap("ESC") { sendKey("\u001b") }
            KeyCap("CTRL", armed = ctrlArmed) { ctrlArmed = !ctrlArmed; altArmed = false }
            KeyCap("ALT", armed = altArmed) { altArmed = !altArmed; ctrlArmed = false }
            KeyCap("TAB") { sendKey("\t") }
            KeyCap("↑") { sendKey("\u001b[A") }
            KeyCap("↓") { sendKey("\u001b[B") }
            KeyCap("→") { sendKey("\u001b[C") }
            KeyCap("←") { sendKey("\u001b[D") }
            KeyCap("HOME") { sendKey("\u001b[H") }
            KeyCap("END") { sendKey("\u001b[F") }
            KeyCap("PGUP") { sendKey("\u001b[5~") }
            KeyCap("PGDN") { sendKey("\u001b[6~") }
            KeyCap("-") { sendKey("-") }
            KeyCap("/") { sendKey("/") }
            KeyCap("|") { sendKey("|") }
            KeyCap("~") { sendKey("~") }
            KeyCap("↩") { sendKey("\r") }
        }

        // ---------------- 输入区 ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101012))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "❯",
                color = Color(0xFF7AD17A),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (!kbVisible) {
                    Text(
                        "点屏幕开始输入",
                        color = Color(0xFF6A6A70),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focus.requestFocus()
                                keyboard?.show()
                                kbVisible = true
                            }
                            .padding(vertical = 8.dp)
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { v ->
                        // 输入法组词期间不发送，等候选词确定后再送，避免把拼音打进 shell
                        if (v.composition == null) {
                            val text = v.text
                            if (text.isNotEmpty()) {
                                send(text)
                                field = TextFieldValue("")
                            } else {
                                field = v
                            }
                        } else {
                            field = v
                        }
                        kbVisible = true
                    },
                    textStyle = TextStyle(
                        color = TermFg,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.Enter -> { send("\r"); true }
                                Key.Backspace -> { send("\u007f"); true }
                                Key.Escape -> { send("\u001b"); true }
                                Key.Tab -> { send("\t"); true }
                                Key.DirectionUp -> { send("\u001b[A"); true }
                                Key.DirectionDown -> { send("\u001b[B"); true }
                                Key.DirectionLeft -> { send("\u001b[D"); true }
                                Key.DirectionRight -> { send("\u001b[C"); true }
                                else -> {
                                    val cp = e.utf16CodePoint
                                    if (e.isCtrlPressed && cp in 'a'.code..'z'.code) {
                                        send(((cp - 'a'.code + 1).toChar()).toString())
                                        true
                                    } else false
                                }
                            }
                        }
                )
            }
        }
    }

    // 会话切换菜单
    if (showSessions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSessions = false },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = { Text("会话", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Column {
                    sessions.forEachIndexed { i, s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(SunShapes.Small)
                                .background(if (i == activeIndex) SunColors.AccentSoft else Color.Transparent)
                                .clickable { TermSessions.active = i; showSessions = false }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}",
                                color = SunColors.TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                s.title(),
                                color = SunColors.TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (s.running) "运行中" else "已退出",
                                color = if (s.running) SunColors.Success else SunColors.TextTertiary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    "新建",
                    color = SunColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { TermSessions.add(context); showSessions = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            },
            dismissButton = {
                Text(
                    "关闭",
                    color = SunColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSessions = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            // 页面退出只收键盘，会话保留（和 Termux 一致）
            runCatching { keyboard?.hide() }
        }
    }
}

// ---------------- 画布 ----------------

@Composable
private fun TerminalCanvas(
    session: TerminalSession,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    cellW: Float,
    cellH: Float,
    tick: Int,
    modifier: Modifier
) {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(530)
            blink = !blink
        }
    }
    val buffer = session.buffer
    val cols = buffer.cols
    val rows = buffer.rows

    Canvas(modifier) {
        // 在绘制阶段读取 tick，输出变化时 Compose 会重画这一层
        val redrawKey = tick
        if (redrawKey < 0) return@Canvas
        val lines = buffer.visibleLines()
        val cursorRow = buffer.cursorViewRow()
        val cursorCol = buffer.cursorCol
        val cursorOn = buffer.cursorVisible && blink && cursorRow >= 0

        for (r in 0 until minOf(rows, lines.size)) {
            val line = lines[r]
            val lineCols = minOf(cols, line.cols)
            var c = 0
            while (c < lineCols) {
                val fg0 = line.fg[c]
                val bg0 = line.bg[c]
                val at0 = line.attr[c].toInt()
                var end = c
                val sb = StringBuilder()
                while (end < lineCols && line.fg[end] == fg0 && line.bg[end] == bg0 && line.attr[end].toInt() == at0) {
                    val s = line.text[end]
                    if (s != "") sb.append(s ?: " ")
                    end++
                }
                drawCellRun(
                    measurer = measurer,
                    text = sb.toString(),
                    x = c * cellW,
                    y = r * cellH,
                    w = (end - c) * cellW,
                    h = cellH,
                    fg = fg0,
                    bg = bg0,
                    attr = at0,
                    style = textStyle,
                    inverseCursor = cursorOn && r == cursorRow && c <= cursorCol && cursorCol < end
                )
                c = end
            }
        }

        if (cursorOn && cursorCol in 0 until cols) {
            val x = cursorCol * cellW
            val y = cursorRow * cellH
            drawRect(
                color = TermFg.copy(alpha = 0.55f),
                topLeft = Offset(x, y),
                size = Size(cellW, cellH)
            )
        }

        // 滚动条
        if (buffer.viewOffset > 0) {
            val frac = buffer.viewOffset.toFloat() / (buffer.viewOffset + rows)
            drawRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(size.width - 3.dp.toPx(), 0f),
                size = Size(3.dp.toPx(), size.height * (1f - frac).coerceIn(0.08f, 1f))
            )
        }
    }
}

private fun DrawScope.drawCellRun(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fg: Int,
    bg: Int,
    attr: Int,
    style: TextStyle,
    inverseCursor: Boolean
) {
    val inv = (attr and ATTR_INVERSE) != 0
    var fgColor = if (fg == COLOR_DEFAULT) TermFg else Color(fg)
    var bgColor = if (bg == COLOR_DEFAULT) Color.Transparent else Color(bg)
    if (inv) {
        val t = fgColor
        fgColor = if (bg == COLOR_DEFAULT) TermBg else bgColor
        bgColor = if (fg == COLOR_DEFAULT) TermFg else t
    }
    if (bgColor != Color.Transparent) {
        drawRect(bgColor, Offset(x, y), Size(w, h))
    }
    if (text.isEmpty()) return
    val st = style.copy(
        color = fgColor,
        fontWeight = if ((attr and ATTR_BOLD) != 0) FontWeight.Bold else FontWeight.Normal,
        textDecoration = when {
            (attr and ATTR_UNDERLINE) != 0 && (attr and ATTR_STRIKE) != 0 ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            (attr and ATTR_UNDERLINE) != 0 -> TextDecoration.Underline
            (attr and ATTR_STRIKE) != 0 -> TextDecoration.LineThrough
            else -> null
        }
    )
    drawText(
        textMeasurer = measurer,
        text = text,
        topLeft = Offset(x, y),
        style = st,
        softWrap = false,
        maxLines = 1
    )
}

// ---------------- 小部件 ----------------

@Composable
private fun KeyCap(label: String, armed: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (armed) Color.White else Color(0xFFC9C9CE),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (armed) Color(0xFF3B6EA8) else Color(0xFF232326))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = SunColors.TextPrimary, fontSize = 12.5.sp) },
        onClick = onClick
    )
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
}

private fun pasteFromClipboard(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()


