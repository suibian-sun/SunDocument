package com.suibian.sunfile

import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.data.SortSpec
import com.suibian.sunfile.update.GithubApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRepoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setUp() {
        root = tmp.newFolder("root")
    }

    private fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `uniqueTarget renames on conflict`() {
        val a = File(root, "note.txt")
        write(a, "1")
        val t1 = FileRepo.uniqueTarget(root, "note.txt")
        assertEquals("note (1).txt", t1.name)
        write(t1, "2")
        val t2 = FileRepo.uniqueTarget(root, "note.txt")
        assertEquals("note (2).txt", t2.name)
    }

    @Test
    fun `copy directory keeps structure and reports total`() {
        val src = File(root, "src")
        write(File(src, "a.txt"), "hello")
        write(File(src, "sub/b.txt"), "world!!")
        val dest = tmp.newFolder("dest")

        var lastTotal = -1L
        FileRepo.copy(src, dest) { _, done, total ->
            lastTotal = total
            assertTrue(done <= total)
        }

        assertEquals(12L, lastTotal)
        assertEquals("hello", File(File(dest, "src"), "a.txt").readText())
        assertEquals("world!!", File(File(dest, "src/sub"), "b.txt").readText())
    }

    @Test
    fun `copy twice produces renamed folder`() {
        val src = File(root, "dir")
        write(File(src, "x.txt"), "1")
        val dest = tmp.newFolder("dest")
        FileRepo.copy(src, dest) { _, _, _ -> }
        FileRepo.copy(src, dest) { _, _, _ -> }
        assertTrue(File(dest, "dir").exists())
        assertTrue(File(dest, "dir (1)").exists())
    }

    @Test
    fun `move removes source`() {
        val file = File(root, "move.txt")
        write(file, "data")
        val dest = tmp.newFolder("dest")
        FileRepo.move(file, dest) { _, _, _ -> }
        assertFalse(file.exists())
        assertTrue(File(dest, "move.txt").exists())
    }

    @Test
    fun `delete removes nested tree`() {
        val dir = File(root, "tree")
        write(File(dir, "1/2/3.txt"), "x")
        FileRepo.delete(dir)
        assertFalse(dir.exists())
    }

    @Test
    fun `list sorts folders first and hides hidden files`() {
        write(File(root, "b.txt"), "12")
        write(File(root, "a.txt"), "123")
        write(File(root, ".hidden"), "1")
        File(root, "zfolder").mkdirs()

        val visible = FileRepo.list(root, showHidden = false, sort = SortSpec(SortMode.NAME, true))
        assertEquals(listOf("zfolder", "a.txt", "b.txt"), visible.map { it.name })

        val all = FileRepo.list(root, showHidden = true, sort = SortSpec(SortMode.SIZE, false))
        assertEquals("zfolder", all.first().name)
        assertEquals(".hidden", all.last().name)

        val desc = FileRepo.list(root, showHidden = false, sort = SortSpec(SortMode.NAME, false))
        assertEquals(listOf("zfolder", "b.txt", "a.txt"), desc.map { it.name })
    }

    @Test
    fun `isInside detects nested path`() {
        val parent = File(root, "p")
        val child = File(parent, "c/d")
        parent.mkdirs()
        child.mkdirs()
        assertTrue(FileRepo.isInside(parent, child))
        assertFalse(FileRepo.isInside(child, parent))
    }

    @Test
    fun `dirSize and child count`() {
        write(File(root, "d/1.txt"), "12345")
        write(File(root, "d/s/2.txt"), "123")
        assertEquals(8L, FileRepo.dirSize(File(root, "d")))
        assertEquals(1 to 2, FileRepo.countChildren(File(root, "d")))
    }


    @Test
    fun `search finds nested matches and respects limit`() {
        write(File(root, "a/report.txt"), "x")
        write(File(root, "a/b/report-final.txt"), "x")
        write(File(root, "a/b/other.txt"), "x")
        var progressCalls = 0
        val hits = FileRepo.search(root, "report", maxResults = 10, onProgress = { _, _ -> progressCalls++ }, cancelled = { false })
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.name.contains("report") })
        assertTrue(progressCalls > 0)

        val limited = FileRepo.search(root, "txt", maxResults = 1, onProgress = { _, _ -> }, cancelled = { false })
        assertEquals(1, limited.size)
    }

    @Test
    fun `looksLikeText distinguishes text and binary`() {
        val txt = File(root, "note.md")
        write(txt, "# title")
        assertTrue(FileRepo.looksLikeText(txt))

        val bin = File(root, "photo.png")
        bin.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0))
        assertFalse(FileRepo.looksLikeText(bin))

        val noExt = File(root, "README")
        write(noExt, "plain text")
        assertTrue(FileRepo.looksLikeText(noExt))
    }

    @Test
    fun `write and read text roundtrip`() {
        val f = File(root, "sub/dir/file.txt")
        FileRepo.writeText(f, "你好\nworld")
        assertEquals("你好\nworld", FileRepo.readText(f))
    }

    @Test
    fun `sort by size and date`() {
        val small = File(root, "small.txt").apply { writeText("1") }
        val big = File(root, "big.txt").apply { writeText("1234567890") }
        small.setLastModified(1_000_000L)
        big.setLastModified(2_000_000L)

        val bySizeDesc = FileRepo.list(root, false, SortSpec(SortMode.SIZE, false)).map { it.name }
        assertEquals(listOf("big.txt", "small.txt"), bySizeDesc)

        val byDateAsc = FileRepo.list(root, false, SortSpec(SortMode.DATE, true)).map { it.name }
        assertEquals(listOf("small.txt", "big.txt"), byDateAsc)
    }

    @Test
    fun `version comparison`() {
        assertTrue(GithubApi.isNewer("1.0.1", "1.0.0"))
        assertTrue(GithubApi.isNewer("v2.0.0", "1.9.9"))
        assertTrue(GithubApi.isNewer("1.0.0.1", "1.0.0"))
        assertFalse(GithubApi.isNewer("1.0.0", "1.0.0"))
        assertFalse(GithubApi.isNewer("1.0.0+ci.9", "1.0.0"))
        assertFalse(GithubApi.isNewer("0.9", "1.0.0"))
    }
}
