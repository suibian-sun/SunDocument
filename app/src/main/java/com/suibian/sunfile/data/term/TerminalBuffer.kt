package com.suibian.sunfile.data.term

/**
 * 终端屏幕模型：固定 cols×rows 的字符网格 + 回滚缓冲 + 一套 VT100/xterm 解析器。
 *
 * 为什么不用「一行一行 append 的 TextView」：那样 `top`、`clear`、`vim`、进度条
 * 这些依赖光标定位的输出会全部错位。真正的终端必须维护光标与网格，这也是
 * ZeroTermux / Termux 的底座。
 *
 * 解析器覆盖常用子集：SGR 颜色（16 / 256 / truecolor）、光标移动与定位、
 * 擦除、插入删除行列、滚动区域、自动换行、备用屏（?1049）、光标显隐。
 */

const val COLOR_DEFAULT = -1

const val ATTR_BOLD = 1
const val ATTR_DIM = 2
const val ATTR_ITALIC = 4
const val ATTR_UNDERLINE = 8
const val ATTR_INVERSE = 16
const val ATTR_STRIKE = 32

/** 一行字符 + 每格前景/背景/样式 */
class TermLine(var cols: Int) {
    var text: Array<String?> = arrayOfNulls(cols)
    var fg: IntArray = IntArray(cols) { COLOR_DEFAULT }
    var bg: IntArray = IntArray(cols) { COLOR_DEFAULT }
    var attr: ByteArray = ByteArray(cols)

    fun resize(newCols: Int) {
        if (newCols == cols) return
        val t = arrayOfNulls<String>(newCols)
        val f = IntArray(newCols) { COLOR_DEFAULT }
        val b = IntArray(newCols) { COLOR_DEFAULT }
        val a = ByteArray(newCols)
        val n = minOf(cols, newCols)
        System.arraycopy(text, 0, t, 0, n)
        System.arraycopy(fg, 0, f, 0, n)
        System.arraycopy(bg, 0, b, 0, n)
        System.arraycopy(attr, 0, a, 0, n)
        text = t; fg = f; bg = b; attr = a; cols = newCols
    }

    fun clear(from: Int = 0, to: Int = cols) {
        for (i in from until minOf(to, cols)) {
            text[i] = null
            fg[i] = COLOR_DEFAULT
            bg[i] = COLOR_DEFAULT
            attr[i] = 0
        }
    }

    fun copyFrom(o: TermLine) {
        val n = minOf(cols, o.cols)
        clear()
        System.arraycopy(o.text, 0, text, 0, n)
        System.arraycopy(o.fg, 0, fg, 0, n)
        System.arraycopy(o.bg, 0, bg, 0, n)
        System.arraycopy(o.attr, 0, attr, 0, n)
    }

    fun isEmpty(): Boolean {
        for (i in 0 until cols) if (text[i] != null) return false
        return true
    }

    fun textString(): String {
        val sb = StringBuilder(cols)
        for (i in 0 until cols) sb.append(text[i] ?: " ")
        return sb.toString().trimEnd()
    }
}

class TerminalBuffer(cols0: Int = 80, rows0: Int = 24) {

    @Volatile var cols: Int = cols0.coerceAtLeast(2)
        private set

    @Volatile var rows: Int = rows0.coerceAtLeast(2)
        private set

    private val lock = Any()
    private var grid: Array<TermLine> = Array(rows) { TermLine(cols) }
    private val scrollback = ArrayDeque<TermLine>()
    private val maxScrollback = 5000

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    /** 用户向上翻了几行；0 表示贴在底部 */
    @Volatile var viewOffset = 0
        private set

    @Volatile var revision = 0
        private set

    var title: String = ""
        private set

    // ---- 解析状态 ----
    private var state = 0 // 0 ground, 1 esc, 2 csi, 3 osc
    private val params = IntArray(16)
    private var paramCount = 0
    private var paramCur = -1
    private var privateMark = 0.toChar()
    private val oscBuf = StringBuilder()

    // ---- 当前 SGR ----
    private var curFg = COLOR_DEFAULT
    private var curBg = COLOR_DEFAULT
    private var curAttr = 0

    private var autoWrap = true
    private var wrapPending = false
    private var scrollTop = 0
    private var scrollBot = rows - 1
    private var appCursorKeys = false
    val applicationCursorKeys: Boolean
        get() = appCursorKeys

    private var savedRow = 0
    private var savedCol = 0
    private var savedFg = COLOR_DEFAULT
    private var savedBg = COLOR_DEFAULT
    private var savedAttr = 0

    private var altSavedGrid: Array<TermLine>? = null
    private var altSavedCursor = 0 to 0

    // ---- UTF-8 增量解码：上一次没凑齐的多字节尾巴 ----
    private var carry = EMPTY

    val cursorRowView: Int get() = cursorRow
    val cursorColView: Int get() = cursorCol

    // ===================== 对外 =====================

    fun feed(bytes: ByteArray, len: Int = bytes.size) {
        if (len <= 0) return
        synchronized(lock) {
            val fresh = if (len == bytes.size) bytes else bytes.copyOf(len)
            val data = if (carry.isEmpty()) fresh else carry + fresh
            val consumed = parse(data)
            carry = if (consumed < data.size) data.copyOfRange(consumed, data.size) else EMPTY
            revision++
        }
    }

    fun feed(text: String) {
        val b = text.toByteArray(Charsets.UTF_8)
        feed(b, b.size)
    }

    fun scrollBy(delta: Int) {
        synchronized(lock) {
            val max = scrollback.size
            viewOffset = (viewOffset - delta).coerceIn(0, max)
            revision++
        }
    }

    fun scrollToBottom() {
        if (viewOffset != 0) {
            viewOffset = 0
            revision++
        }
    }

    fun scrollToTop() {
        viewOffset = scrollback.size
        revision++
    }

    /** 可见区域的行（已按 viewOffset 偏移），长度为 rows */
    fun visibleLines(): List<TermLine> {
        synchronized(lock) {
            val end = scrollback.size + rows - viewOffset
            val start = end - rows
            val out = ArrayList<TermLine>(rows)
            for (i in start until end) {
                val l = lineAt(i)
                // 回滚里的行可能还是旧宽度（resize 后没整批重排），用时再对齐
                if (l.cols != cols) l.resize(cols)
                out.add(l)
            }
            return out
        }
    }

    /** 光标在可见区域中的行；滚出屏幕时返回 -1 */
    fun cursorViewRow(): Int {
        synchronized(lock) {
            if (viewOffset != 0) return -1
            return cursorRow
        }
    }

    /** 整个缓冲（回滚 + 屏幕）的纯文本，用于复制 */
    fun allText(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            for (l in scrollback) sb.append(l.textString()).append('\n')
            for (l in grid) sb.append(l.textString()).append('\n')
            return sb.toString().trimEnd('\n')
        }
    }

    fun screenText(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            for (l in visibleLines()) sb.append(l.textString()).append('\n')
            return sb.toString().trimEnd('\n')
        }
    }

    fun clearAll() {
        synchronized(lock) {
            grid.forEach { it.clear() }
            scrollback.clear()
            cursorRow = 0; cursorCol = 0; viewOffset = 0
            revision++
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceAtLeast(2)
        val r = newRows.coerceAtLeast(2)
        if (c == cols && r == rows) return
        synchronized(lock) {
            if (r < rows) {
                // 变矮：顶部多出来的行进回滚
                for (i in 0 until rows - r) scrollback.addLast(grid[i])
                val ng = Array(r) { TermLine(c) }
                for (i in 0 until r) ng[i].copyFrom(grid[i + (rows - r)])
                grid = ng
            } else if (r > rows) {
                val ng = Array(r) { TermLine(c) }
                for (i in 0 until rows) ng[i].copyFrom(grid[i])
                grid = ng
            }
            grid.forEach { it.resize(c) }
            cols = c; rows = r
            scrollTop = 0
            scrollBot = rows - 1
            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            revision++
        }
    }

    // ===================== 内部 =====================

    private fun lineAt(i: Int): TermLine {
        val sb = scrollback.size
        return if (i < sb) {
            scrollback.elementAt(i)
        } else {
            val idx = i - sb
            if (idx in grid.indices) grid[idx] else TermLine(cols)
        }
    }

    private fun blankLine(): TermLine = TermLine(cols)

    /** 解析一段完整字节，返回实际消费到的下标（尾部不完整的 UTF-8 留给下一次） */
    private fun parse(data: ByteArray): Int {
        var i = 0
        val n = data.size
        while (i < n) {
            val b0 = data[i].toInt() and 0xFF
            val need = when {
                b0 < 0x80 -> 1
                b0 in 0xC2..0xDF -> 2
                b0 in 0xE0..0xEF -> 3
                b0 in 0xF0..0xF4 -> 4
                else -> 1
            }
            if (i + need > n) break
            val cp: Int = when (need) {
                1 -> if (b0 < 0x80) b0 else 0xFFFD
                2 -> ((b0 and 0x1F) shl 6) or (data[i + 1].toInt() and 0x3F)
                3 -> ((b0 and 0x0F) shl 12) or ((data[i + 1].toInt() and 0x3F) shl 6) or
                    (data[i + 2].toInt() and 0x3F)
                else -> ((b0 and 0x07) shl 18) or ((data[i + 1].toInt() and 0x3F) shl 12) or
                    ((data[i + 2].toInt() and 0x3F) shl 6) or (data[i + 3].toInt() and 0x3F)
            }
            i += need
            handleCodePoint(cp)
        }
        return i
    }

    private fun handleCodePoint(cp: Int) {
        when (state) {
            3 -> { // OSC：吃到 BEL 或 ESC \
                if (cp == 0x07) { finishOsc() }
                else if (cp == 0x1B) { state = 4 }
                else if (oscBuf.length < 512) oscBuf.appendCodePoint(cp)
                return
            }
            4 -> { // OSC 里的 ESC，看到 \ 结束，否则回 OSC
                if (cp == '\\'.code) finishOsc() else {
                    if (oscBuf.length < 512) oscBuf.appendCodePoint(cp)
                    state = 3
                }
                return
            }
        }
        if (cp == 0x1B) {
            state = 1
            return
        }
        when (state) {
            1 -> {
                when (cp.toChar()) {
                    '[' -> { state = 2; resetParams() }
                    ']' -> { state = 3; oscBuf.setLength(0) }
                    '7' -> saveCursor()
                    '8' -> restoreCursor()
                    'D' -> index()
                    'E' -> { carriageReturn(); index() }
                    'M' -> reverseIndex()
                    'c' -> resetAll()
                    '(', ')', '*', '+' -> state = 5 // 跳过字符集选择的一个字节
                    else -> state = 0
                }
                return
            }
            5 -> { state = 0; return }
            2 -> {
                handleCsi(cp)
                return
            }
        }
        // ground
        if (cp < 0x20) {
            control(cp)
        } else if (cp == 0x7F) {
            // DEL 忽略
        } else {
            putChar(cp)
        }
    }

    private fun control(cp: Int) {
        when (cp) {
            0x08 -> { if (cursorCol > 0) cursorCol--; wrapPending = false }
            0x09 -> { cursorCol = ((cursorCol / 8) + 1) * 8; if (cursorCol >= cols) cursorCol = cols - 1; wrapPending = false }
            0x0A, 0x0B, 0x0C -> index()
            0x0D -> carriageReturn()
            else -> Unit
        }
    }

    private fun putChar(cp: Int) {
        if (wrapPending) {
            if (autoWrap) {
                carriageReturn(); index()
            }
            wrapPending = false
        }
        val w = charWidth(cp)
        if (w == 0) return
        if (cursorCol + w > cols) {
            if (!autoWrap) return
            carriageReturn(); index()
        }
        val line = grid[cursorRow]
        // 覆盖宽字符的前半/后半时先清干净，避免留下半个字
        if (line.text[cursorCol] == "") {
            if (cursorCol > 0) {
                line.text[cursorCol - 1] = null
                line.fg[cursorCol - 1] = COLOR_DEFAULT
                line.bg[cursorCol - 1] = COLOR_DEFAULT
                line.attr[cursorCol - 1] = 0
            }
        }
        val s = String(Character.toChars(cp))
        line.text[cursorCol] = s
        line.fg[cursorCol] = curFg
        line.bg[cursorCol] = curBg
        line.attr[cursorCol] = curAttr.toByte()
        if (w == 2 && cursorCol + 1 < cols) {
            line.text[cursorCol + 1] = ""
            line.fg[cursorCol + 1] = curFg
            line.bg[cursorCol + 1] = curBg
            line.attr[cursorCol + 1] = curAttr.toByte()
        }
        if (cursorCol + w >= cols) {
            cursorCol = cols - 1
            wrapPending = autoWrap
        } else {
            cursorCol += w
        }
    }

    private fun carriageReturn() {
        cursorCol = 0
        wrapPending = false
    }

    private fun index() {
        if (cursorRow == scrollBot) scrollUp(1) else cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
        wrapPending = false
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1) else cursorRow = (cursorRow - 1).coerceAtLeast(0)
    }

    private fun scrollUp(n: Int) {
        val count = n.coerceAtMost(scrollBot - scrollTop + 1)
        repeat(count) {
            if (scrollTop == 0 && altSavedGrid == null) {
                // 必须复制一份：下面会把 grid[0] 的内容改成上一行的，
                // 直接存引用会让回滚里的历史跟着一起变。
                val snapshot = TermLine(cols)
                snapshot.copyFrom(grid[0])
                if (scrollback.size >= maxScrollback) scrollback.removeFirst()
                scrollback.addLast(snapshot)
            }
            for (r in scrollTop until scrollBot) {
                grid[r].copyFrom(grid[r + 1])
            }
            grid[scrollBot].clear()
        }
        if (viewOffset != 0) viewOffset = (viewOffset + count).coerceAtMost(scrollback.size)
    }

    private fun scrollDown(n: Int) {
        val count = n.coerceAtMost(scrollBot - scrollTop + 1)
        repeat(count) {
            for (r in scrollBot downTo scrollTop + 1) {
                grid[r].copyFrom(grid[r - 1])
            }
            grid[scrollTop].clear()
        }
    }

    private fun resetParams() {
        params.fill(0)
        paramCount = 0
        paramCur = -1
        privateMark = 0.toChar()
    }

    private fun handleCsi(cp: Int) {
        val c = cp.toChar()
        if (c in '0'..'9') {
            paramCur = (if (paramCur < 0) 0 else paramCur) * 10 + (c - '0')
            return
        }
        if (c == ';' || c == ':') {
            if (paramCount < params.size) params[paramCount++] = if (paramCur < 0) 0 else paramCur
            paramCur = -1
            return
        }
        if (c == '?' || c == '>' || c == '<' || c == '=') {
            privateMark = c
            return
        }
        if (c in ' '..'/') return
        // final
        if (paramCur >= 0 && paramCount < params.size) params[paramCount++] = paramCur
        paramCur = -1
        val p0 = if (paramCount > 0) params[0] else 0
        val n1 = if (p0 == 0) 1 else p0
        when (c) {
            'A' -> cursorRow = (cursorRow - n1).coerceAtLeast(scrollTop)
            'B' -> cursorRow = (cursorRow + n1).coerceAtMost(scrollBot)
            'C' -> cursorCol = (cursorCol + n1).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - n1).coerceAtLeast(0)
            'E' -> { cursorRow = (cursorRow + n1).coerceAtMost(scrollBot); cursorCol = 0 }
            'F' -> { cursorRow = (cursorRow - n1).coerceAtLeast(scrollTop); cursorCol = 0 }
            'G', '`' -> cursorCol = (n1 - 1).coerceIn(0, cols - 1)
            'd' -> cursorRow = (n1 - 1).coerceIn(0, rows - 1)
            'H', 'f' -> {
                val r = if (paramCount > 0) params[0] else 1
                val cc = if (paramCount > 1) params[1] else 1
                cursorRow = (if (r == 0) 1 else r).coerceIn(1, rows) - 1
                cursorCol = (if (cc == 0) 1 else cc).coerceIn(1, cols) - 1
            }
            'J' -> eraseDisplay(p0)
            'K' -> eraseLine(p0)
            'L' -> { scrollBot = rows - 1; insertLines(n1) }
            'M' -> deleteLines(n1)
            '@' -> insertChars(n1)
            'P' -> deleteChars(n1)
            'X' -> eraseChars(n1)
            'S' -> scrollUp(n1)
            'T' -> scrollDown(n1)
            'm' -> sgr()
            'r' -> {
                val top = if (paramCount > 0) params[0] else 1
                val bot = if (paramCount > 1) params[1] else rows
                scrollTop = (if (top == 0) 1 else top).coerceIn(1, rows) - 1
                scrollBot = (if (bot == 0) rows else bot).coerceIn(scrollTop + 1, rows) - 1
                cursorRow = scrollTop
                cursorCol = 0
            }
            'h', 'l' -> setMode(p0, c == 'h')
            's' -> saveCursor()
            'u' -> restoreCursor()
            'n' -> {
                // DSR 只处理 6n（光标位置），由会话层回写
                if (p0 == 6) pendingResponse = "\u001b[${cursorRow + 1};${cursorCol + 1}R"
            }
            else -> Unit
        }
        state = 0
    }

    /** 需要回写给 shell 的应答（光标位置报告等） */
    @Volatile var pendingResponse: String? = null

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                grid[cursorRow].clear(cursorCol, cols)
                for (r in cursorRow + 1 until rows) grid[r].clear()
            }
            1 -> {
                for (r in 0 until cursorRow) grid[r].clear()
                grid[cursorRow].clear(0, cursorCol + 1)
            }
            2 -> grid.forEach { it.clear() }
            3 -> scrollback.clear()
        }
    }

    private fun eraseLine(mode: Int) {
        val l = grid[cursorRow]
        when (mode) {
            0 -> l.clear(cursorCol, cols)
            1 -> l.clear(0, cursorCol + 1)
            2 -> l.clear()
        }
    }

    private fun insertLines(n: Int) {
        val count = n.coerceAtMost(scrollBot - cursorRow + 1)
        for (r in scrollBot downTo cursorRow + count) {
            grid[r].copyFrom(grid[r - count])
        }
        for (r in cursorRow until cursorRow + count) grid[r].clear()
    }

    private fun deleteLines(n: Int) {
        val count = n.coerceAtMost(scrollBot - cursorRow + 1)
        for (r in cursorRow..scrollBot - count) {
            grid[r].copyFrom(grid[r + count])
        }
        for (r in scrollBot - count + 1..scrollBot) grid[r].clear()
    }

    private fun insertChars(n: Int) {
        val line = grid[cursorRow]
        val count = n.coerceAtMost(cols - cursorCol)
        for (i in cols - 1 downTo cursorCol + count) {
            line.text[i] = line.text[i - count]
            line.fg[i] = line.fg[i - count]
            line.bg[i] = line.bg[i - count]
            line.attr[i] = line.attr[i - count]
        }
        line.clear(cursorCol, cursorCol + count)
    }

    private fun deleteChars(n: Int) {
        val line = grid[cursorRow]
        val count = n.coerceAtMost(cols - cursorCol)
        for (i in cursorCol until cols - count) {
            line.text[i] = line.text[i + count]
            line.fg[i] = line.fg[i + count]
            line.bg[i] = line.bg[i + count]
            line.attr[i] = line.attr[i + count]
        }
        line.clear(cols - count, cols)
    }

    private fun eraseChars(n: Int) {
        grid[cursorRow].clear(cursorCol, (cursorCol + n).coerceAtMost(cols))
    }

    private fun setMode(mode: Int, on: Boolean) {
        if (privateMark != '?') return
        when (mode) {
            1 -> appCursorKeys = on
            7 -> autoWrap = on
            25 -> cursorVisible = on
            47, 1047, 1049 -> {
                if (on) enterAlt() else leaveAlt()
            }
        }
    }

    private fun enterAlt() {
        if (altSavedGrid != null) return
        altSavedGrid = grid
        altSavedCursor = cursorRow to cursorCol
        grid = Array(rows) { TermLine(cols) }
        cursorRow = 0; cursorCol = 0
    }

    private fun leaveAlt() {
        val saved = altSavedGrid ?: return
        grid = saved
        altSavedGrid = null
        cursorRow = altSavedCursor.first.coerceIn(0, rows - 1)
        cursorCol = altSavedCursor.second.coerceIn(0, cols - 1)
    }

    private fun saveCursor() {
        savedRow = cursorRow; savedCol = cursorCol
        savedFg = curFg; savedBg = curBg; savedAttr = curAttr
    }

    private fun restoreCursor() {
        cursorRow = savedRow.coerceIn(0, rows - 1)
        cursorCol = savedCol.coerceIn(0, cols - 1)
        curFg = savedFg; curBg = savedBg; curAttr = savedAttr
    }

    private fun resetAll() {
        grid.forEach { it.clear() }
        scrollback.clear()
        cursorRow = 0; cursorCol = 0
        curFg = COLOR_DEFAULT; curBg = COLOR_DEFAULT; curAttr = 0
        scrollTop = 0; scrollBot = rows - 1
        autoWrap = true; cursorVisible = true
        altSavedGrid = null
    }

    private fun finishOsc() {
        val s = oscBuf.toString()
        val idx = s.indexOf(';')
        if (idx > 0) {
            val code = s.substring(0, idx)
            if (code == "0" || code == "2") title = s.substring(idx + 1).take(80)
        }
        oscBuf.setLength(0)
        state = 0
    }

    // ---------------- SGR ----------------

    private fun sgr() {
        if (paramCount == 0) {
            curFg = COLOR_DEFAULT; curBg = COLOR_DEFAULT; curAttr = 0
            return
        }
        var i = 0
        while (i < paramCount) {
            when (val p = params[i]) {
                0 -> { curFg = COLOR_DEFAULT; curBg = COLOR_DEFAULT; curAttr = 0 }
                1 -> curAttr = curAttr or ATTR_BOLD
                2 -> curAttr = curAttr or ATTR_DIM
                3 -> curAttr = curAttr or ATTR_ITALIC
                4 -> curAttr = curAttr or ATTR_UNDERLINE
                7 -> curAttr = curAttr or ATTR_INVERSE
                9 -> curAttr = curAttr or ATTR_STRIKE
                21, 22 -> curAttr = curAttr and ATTR_BOLD.inv() and ATTR_DIM.inv()
                23 -> curAttr = curAttr and ATTR_ITALIC.inv()
                24 -> curAttr = curAttr and ATTR_UNDERLINE.inv()
                27 -> curAttr = curAttr and ATTR_INVERSE.inv()
                29 -> curAttr = curAttr and ATTR_STRIKE.inv()
                in 30..37 -> curFg = ANSI_16[p - 30]
                39 -> curFg = COLOR_DEFAULT
                in 40..47 -> curBg = ANSI_16[p - 40]
                49 -> curBg = COLOR_DEFAULT
                in 90..97 -> curFg = ANSI_16[p - 90 + 8]
                in 100..107 -> curBg = ANSI_16[p - 100 + 8]
                38, 48 -> {
                    val isFg = p == 38
                    when {
                        i + 2 < paramCount && params[i + 1] == 5 -> {
                            val c = color256(params[i + 2])
                            if (isFg) curFg = c else curBg = c
                            i += 2
                        }
                        i + 4 < paramCount && params[i + 1] == 2 -> {
                            val c = (params[i + 2] shl 16) or (params[i + 3] shl 8) or params[i + 4]
                            if (isFg) curFg = c else curBg = c
                            i += 4
                        }
                        else -> Unit
                    }
                }
            }
            i++
        }
    }

    companion object {
        val ANSI_16 = intArrayOf(
            0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
            0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF
        )

        fun color256(n: Int): Int {
            if (n < 16) return ANSI_16[n.coerceIn(0, 15)]
            if (n < 232) {
                val i = n - 16
                val r = i / 36
                val g = (i % 36) / 6
                val b = i % 6
                fun v(x: Int) = if (x == 0) 0 else 55 + x * 40
                return (v(r) shl 16) or (v(g) shl 8) or v(b)
            }
            val gray = 8 + (n - 232) * 10
            return (gray shl 16) or (gray shl 8) or gray
        }

        /** 简易 wcwidth：东亚宽字符与 emoji 占两格 */
        fun charWidth(cp: Int): Int = when {
            cp == 0 -> 0
            cp < 0x20 -> 0
            cp in 0x0300..0x036F -> 0
            cp in 0x1100..0x115F -> 2
            cp in 0x2E80..0x303E -> 2
            cp in 0x3041..0x33FF -> 2
            cp in 0x3400..0x4DBF -> 2
            cp in 0x4E00..0x9FFF -> 2
            cp in 0xA000..0xA4CF -> 2
            cp in 0xAC00..0xD7A3 -> 2
            cp in 0xF900..0xFAFF -> 2
            cp in 0xFE30..0xFE6F -> 2
            cp in 0xFF00..0xFF60 -> 2
            cp in 0xFFE0..0xFFE6 -> 2
            cp in 0x1F300..0x1F64F -> 2
            cp in 0x1F900..0x1F9FF -> 2
            cp in 0x20000..0x3FFFD -> 2
            else -> 1
        }
    }
}

private val EMPTY = ByteArray(0)
