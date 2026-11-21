package com.suibian.sunfile

import com.suibian.sunfile.data.term.ATTR_BOLD
import com.suibian.sunfile.data.term.COLOR_DEFAULT
import com.suibian.sunfile.data.term.TerminalBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 终端网格与 ANSI 解析的回归测试：光标定位、颜色、清屏、宽字符、回滚 */
class TerminalBufferTest {

    private fun line(b: TerminalBuffer, row: Int): String {
        val lines = b.visibleLines()
        return lines[row].textString()
    }

    @Test
    fun plainTextWrapsAtColumns() {
        val b = TerminalBuffer(10, 3)
        b.feed("hello")
        assertEquals("hello", line(b, 0))
        b.feed("world!!")
        // 10 列：helloworld / !!
        assertEquals("helloworld", line(b, 0))
        assertEquals("!!", line(b, 1))
    }

    @Test
    fun carriageReturnAndNewline() {
        val b = TerminalBuffer(20, 4)
        b.feed("abc\r\ndef")
        assertEquals("abc", line(b, 0))
        assertEquals("def", line(b, 1))
    }

    @Test
    fun sgrColorsAndAttributes() {
        val b = TerminalBuffer(20, 3)
        b.feed("\u001b[31;1mRED\u001b[0m ok")
        val lines = b.visibleLines()
        val red = lines[0]
        assertEquals("R", red.text[0])
        assertEquals(0xCD0000, red.fg[0])
        assertTrue((red.attr[0].toInt() and ATTR_BOLD) != 0)
        // reset 之后的空格回到默认色
        assertEquals(COLOR_DEFAULT, red.fg[4])
    }

    @Test
    fun trueColor() {
        val b = TerminalBuffer(20, 2)
        b.feed("\u001b[38;2;18;52;86mX\u001b[0m")
        assertEquals((18 shl 16) or (52 shl 8) or 86, b.visibleLines()[0].fg[0])
    }

    @Test
    fun cursorPositionAndErase() {
        val b = TerminalBuffer(20, 4)
        b.feed("\u001b[2;3Habc")
        assertEquals("  abc", line(b, 1))
        b.feed("\u001b[2J")
        assertEquals("", line(b, 1))
    }

    @Test
    fun eraseToEndOfLine() {
        val b = TerminalBuffer(20, 2)
        b.feed("abcdef\u001b[1;3H\u001b[K")
        assertEquals("ab", line(b, 0))
    }

    @Test
    fun wideCharactersOccupyTwoCells() {
        val b = TerminalBuffer(10, 2)
        b.feed("中文")
        val l = b.visibleLines()[0]
        assertEquals("中", l.text[0])
        assertEquals("", l.text[1])
        assertEquals("文", l.text[2])
        assertEquals("", l.text[3])
        // 光标在 4 列
        assertEquals(4, b.cursorCol)
    }

    @Test
    fun scrollbackKeepsHistory() {
        val b = TerminalBuffer(20, 2)
        b.feed("one\r\ntwo\r\nthree\r\nfour")
        // 可见 2 行，前两行进回滚
        assertEquals("three", line(b, 0))
        assertEquals("four", line(b, 1))
        b.scrollBy(-3)
        assertEquals("one", line(b, 0))
    }

    @Test
    fun alternateScreenRestoresMain() {
        val b = TerminalBuffer(20, 3)
        b.feed("main")
        b.feed("\u001b[?1049h")
        assertEquals("", line(b, 0))
        b.feed("alt")
        assertEquals("alt", line(b, 0))
        b.feed("\u001b[?1049l")
        assertEquals("main", line(b, 0))
    }

    @Test
    fun backspaceAndTab() {
        val b = TerminalBuffer(20, 2)
        b.feed("abc\bX")
        assertEquals("abX", line(b, 0))
        val b2 = TerminalBuffer(20, 2)
        b2.feed("a\tb")
        assertEquals("a", b2.visibleLines()[0].text[0])
        assertEquals("b", b2.visibleLines()[0].text[8])
    }

    @Test
    fun utf8SplitAcrossFeeds() {
        val b = TerminalBuffer(10, 2)
        val bytes = "中".toByteArray(Charsets.UTF_8)
        b.feed(bytes, 1)
        assertEquals("", line(b, 0))
        b.feed(bytes.copyOfRange(1, bytes.size), bytes.size - 1)
        assertEquals("中", line(b, 0))
    }

    @Test
    fun resizeKeepsContent() {
        val b = TerminalBuffer(10, 2)
        b.feed("abc")
        b.resize(20, 4)
        assertEquals("abc", line(b, 0))
        assertEquals(20, b.cols)
        assertEquals(4, b.rows)
    }

    @Test
    fun clearScreenAlsoClearsScrollback() {
        val b = TerminalBuffer(10, 2)
        b.feed("a\r\nb\r\nc\r\n")
        b.feed("\u001b[3J")
        b.scrollBy(-100)
        assertEquals(0, b.viewOffset)
        assertEquals("c", line(b, 0))
    }
}
