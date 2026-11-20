package com.suibian.sunfile

import com.suibian.sunfile.data.ArchiveEdit
import com.suibian.sunfile.data.Archives
import com.suibian.sunfile.data.Axml
import com.suibian.sunfile.data.DexIndex
import com.suibian.sunfile.data.Diff
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.HexBuffer
import com.suibian.sunfile.data.HexText
import com.suibian.sunfile.data.Query
import com.suibian.sunfile.data.RenameExpr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolsTest {

    // ---------------- 过滤表达式 ----------------

    private fun match(expr: String, name: String): Boolean =
        Query.matches(Query.parse(expr), name)

    @Test
    fun filterPlainKeyword() {
        assertTrue(match("zip", "backup.ZIP"))
        assertFalse(match("zip", "photo.png"))
        assertTrue(match("", "anything"))
    }

    @Test
    fun filterNegation() {
        assertTrue(match("!zip", "photo.png"))
        assertFalse(match("!zip", "backup.zip"))
    }

    @Test
    fun filterRegexAndNegatedRegex() {
        assertTrue(match("/^log_\\d+", "log_2024.txt"))
        assertFalse(match("/^log_\\d+", "old_log_1.txt"))
        assertTrue(match("!/\\.png$", "a.jpg"))
        assertFalse(match("!/\\.png$", "a.png"))
    }

    @Test
    fun filterBrokenRegexFallsBack() {
        // 正则写错不应崩，也不应把列表全部过滤掉
        assertTrue(match("/(", "abc("))
    }

    // ---------------- 批量重命名表达式 ----------------

    private fun item(name: String, time: Long = 1_700_000_000_000L) =
        RenameExpr.Item(name, time)

    @Test
    fun renameIdentityAndSuffix() {
        assertEquals("abc.txt", RenameExpr.render("{P}{S}", item("abc.txt"), 0))
        assertEquals("abc.txt.bak", RenameExpr.render("{P}{S}.bak", item("abc.txt"), 0))
        assertEquals("abc.bak", RenameExpr.render("{P}.bak", item("abc.txt"), 0))
    }

    @Test
    fun renameNumbering() {
        val names = listOf("a.jpg", "b.jpg", "c.jpg")
        val out = RenameExpr.preview("图片{1}", names.map { item(it) })
        assertEquals(listOf("图片1", "图片2", "图片3"), out)
    }

    @Test
    fun renameZeroPadded() {
        val out = RenameExpr.preview("图{z1}", listOf(item("a"), item("b"), item("c")))
        assertEquals(listOf("图01", "图02", "图03"), out)
        // 起始值本身是两位数时，宽度跟着起始值走
        val out2 = RenameExpr.preview("{z10}", listOf(item("a"), item("b")))
        assertEquals(listOf("10", "11"), out2)
    }

    @Test
    fun renameTimeAndUnknownToken() {
        val out = RenameExpr.render("{T:yyyy}", item("a.txt"), 0)
        assertEquals("2023", out)
        // 未知标记原样保留，方便用户自己发现写错了
        assertEquals("{Q}", RenameExpr.render("{Q}", item("a"), 0))
    }

    @Test
    fun renameSanitize() {
        assertEquals("abc", RenameExpr.sanitize("a/b:c*?"))
        assertEquals("", RenameExpr.sanitize("///"))
    }

    // ---------------- 文本对比 ----------------

    @Test
    fun diffIdentical() {
        val rows = Diff.lines(listOf("a", "b"), listOf("a", "b"))
        assertTrue(rows.all { it.op == Diff.Op.SAME })
        assertEquals(2, rows.size)
    }

    @Test
    fun diffAddAndDelete() {
        val rows = Diff.lines(listOf("a", "b", "c"), listOf("a", "c", "d"))
        val (add, del, same) = Diff.stats(rows)
        assertEquals(1, add)
        assertEquals(1, del)
        assertEquals(2, same)
        assertEquals("d", rows.last().text)
    }

    @Test
    fun diffLargeInputDoesNotHang() {
        val a = (0 until 3000).map { "line $it" }
        val b = (0 until 3000).map { "line ${it + 1}" }
        val rows = Diff.lines(a, b)
        assertTrue(rows.isNotEmpty())
    }

    @Test
    fun treeReportCounts() {
        val left = mapOf(
            "a.txt" to Diff.Node("a.txt", 10, false),
            "dir" to Diff.Node("dir", 0, true),
            "only-left" to Diff.Node("only-left", 1, false)
        )
        val right = mapOf(
            "a.txt" to Diff.Node("a.txt", 20, false),
            "dir" to Diff.Node("dir", 0, true),
            "only-right" to Diff.Node("only-right", 1, false)
        )
        val report = Diff.treeReport("/l", "/r", left, right)
        assertTrue(report.contains("仅左侧"))
        assertTrue(report.contains("仅右侧"))
        assertTrue(report.contains("大小不同"))
    }

    // ---------------- 十六进制 ----------------

    @Test
    fun hexDecodeVariants() {
        val expect = byteArrayOf(0x00, 0x69, 0x73, 0xFF.toByte())
        assertTrue(expect.contentEquals(HexText.decode("00 69 73 FF")!!))
        assertTrue(expect.contentEquals(HexText.decode("006973ff")!!))
        assertTrue(expect.contentEquals(HexText.decode("0x00,0x69,0x73,0xFF")!!))
        assertNull(HexText.decode("0 69"))
        assertNull(HexText.decode("zz"))
    }

    @Test
    fun hexEncodeAndAscii() {
        val bytes = byteArrayOf(0x41, 0x42, 0x00, 0x43)
        assertEquals("41 42 00 43", HexText.encode(bytes, 0, bytes.size))
        assertEquals("AB.C", HexText.ascii(bytes, 0, bytes.size))
    }

    @Test
    fun hexEditOperations() {
        val b = HexBuffer.of(byteArrayOf(1, 2, 3, 4))
        b.setByte(1, 0xFF)
        assertEquals(0xFF, b.byteAt(1))
        b.insert(2, byteArrayOf(9, 9))
        assertEquals(6, b.length)
        assertEquals(9, b.byteAt(2))
        b.delete(2, 2)
        assertEquals(4, b.length)
        assertEquals(3, b.byteAt(2))
    }

    @Test
    fun hexSearchAndReplace() {
        val b = HexBuffer.of("hello hello".toByteArray())
        assertEquals(0, b.indexOf("hello".toByteArray()))
        assertEquals(6, b.indexOf("hello".toByteArray(), 1))
        val n = b.replaceAll("hello".toByteArray(), "hi".toByteArray())
        assertEquals(2, n)
        assertEquals("hi hi", String(b.toByteArray()))
    }

    @Test
    fun hexDigests() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HexText.checksum(ByteArray(0), "MD5"))
        assertEquals("CBF43926", HexText.crc32("123456789".toByteArray()))
        assertEquals("TWFu", HexText.base64("Man".toByteArray()))
        assertEquals("TWE=", HexText.base64("Ma".toByteArray()))
        assertEquals("TQ==", HexText.base64("M".toByteArray()))
    }

    // ---------------- 二进制格式的健壮性 ----------------

    @Test
    fun axmlRejectsGarbage() {
        val ok = runCatching { Axml.decode("not a binary xml".toByteArray()) }.isFailure
        assertTrue(ok)
    }

    @Test
    fun dexRejectsGarbage() {
        val ok = runCatching { DexIndex.read(ByteArray(200)) }.isFailure
        assertTrue(ok)
    }

    // ---------------- 压缩包写入 ----------------

    private fun tempDir(): File {
        val dir = kotlin.io.path.createTempDirectory("sunfile").toFile()
        dir.deleteOnExit()
        return dir
    }

    @Test
    fun zipCreateRenamePutDelete() {
        val dir = tempDir()
        val a = File(dir, "a.txt").apply { writeText("hello") }
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "b.txt").writeText("world")
        val zip = File(dir, "out.zip")

        val count = Archives.create(listOf(a, sub), zip, Archives.Format.ZIP)
        assertTrue(count >= 3)
        val names = FileRepo.archiveEntries(zip).map { it.name }
        assertTrue(names.contains("a.txt"))
        assertTrue(names.contains("sub/b.txt"))

        assertTrue(ArchiveEdit.zipRename(zip, "a.txt", "c.txt"))
        assertTrue(FileRepo.archiveEntries(zip).any { it.name == "c.txt" })

        assertTrue(ArchiveEdit.zipPut(zip, "c.txt", "bye".toByteArray()))
        assertEquals("bye", String(FileRepo.readZipEntry(zip, "c.txt")))

        assertTrue(ArchiveEdit.zipDelete(zip, setOf("c.txt")))
        assertFalse(FileRepo.archiveEntries(zip).any { it.name == "c.txt" })
    }

    @Test
    fun tarCreateAndRead() {
        val dir = tempDir()
        val a = File(dir, "note.txt").apply { writeText("tar content") }
        val tar = File(dir, "out.tar")
        Archives.create(listOf(a), tar, Archives.Format.TAR)
        val names = FileRepo.archiveEntries(tar).map { it.name }
        assertTrue(names.contains("note.txt"))
        assertTrue(String(FileRepo.readArchiveEntry(tar, "note.txt")).contains("tar content"))
    }

    @Test
    fun sevenZCreateAndRead() {
        val dir = tempDir()
        val a = File(dir, "note.txt").apply { writeText("7z content") }
        val sub = File(dir, "sub").apply { mkdirs() }
        File(sub, "b.txt").writeText("nested")
        val out = File(dir, "out.7z")
        val count = Archives.create(listOf(a, sub), out, Archives.Format.SEVEN_Z)
        assertTrue(count >= 3)
        assertTrue(out.length() > 0)
        val names = Archives.entries(out).map { it.name }
        assertTrue(names.contains("note.txt"))
        assertTrue(String(Archives.readEntry(out, "note.txt")).contains("7z content"))
        assertTrue(names.contains("sub/b.txt"))
        val target = Archives.extractEntry(out, "note.txt", File(dir, "x"))
        assertEquals("7z content", target.readText())
    }

    @Test
    fun encryptedZipNeedsPassword() {
        val dir = tempDir()
        val a = File(dir, "secret.txt").apply { writeText("top secret") }
        val out = File(dir, "enc.zip")
        Archives.create(listOf(a), out, Archives.Format.ZIP, level = 6, password = "1234")
        val names = Archives.entries(out, "1234").map { it.name }
        assertTrue(names.contains("secret.txt"))
        assertEquals("top secret", String(Archives.readEntry(out, "secret.txt", password = "1234")))
        assertFalse(runCatching { Archives.readEntry(out, "secret.txt", password = "bad") }.isSuccess)
    }

    @Test
    fun tarGzCreateAndRead() {
        val dir = tempDir()
        val a = File(dir, "x.txt").apply { writeText("gzip content") }
        val tgz = File(dir, "out.tar.gz")
        Archives.create(listOf(a), tgz, Archives.Format.TAR_GZ)
        assertTrue(String(FileRepo.readArchiveEntry(tgz, "x.txt")).contains("gzip content"))
    }
}
