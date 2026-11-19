package com.suibian.sunfile

import com.suibian.sunfile.data.DocParser
import com.suibian.sunfile.data.DocStyle
import com.suibian.sunfile.data.FileRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 新格式支持：Office 文本提取、tar 解包、编码切换 */
class FormatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(file: File, entries: Map<String, String>) {
        ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                entries.forEach { (name, body) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(body.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            file.writeBytes(bos.toByteArray())
        }
    }

    @Test
    fun `docx paragraphs and headings are extracted`() {
        val f = File(tmp.newFolder(), "a.docx")
        val xml = """
            <w:document xmlns:w="x"><w:body>
              <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>标题一</w:t></w:r></w:p>
              <w:p><w:r><w:t xml:space="preserve">第一段</w:t></w:r></w:p>
              <w:p><w:r><w:t>第二段 &amp; 符号</w:t></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        zip(f, mapOf("word/document.xml" to xml))

        val doc = DocParser.parse(f)
        assertEquals(3, doc.blocks.size)
        assertEquals(DocStyle.HEADING, doc.blocks[0].style)
        assertEquals("标题一", doc.blocks[0].text)
        assertEquals("第二段 & 符号", doc.blocks[2].text)
    }

    @Test
    fun `xlsx shared strings become a table`() {
        val f = File(tmp.newFolder(), "b.xlsx")
        val shared = """
            <sst><si><t>姓名</t></si><si><t>年龄</t></si><si><t>张三</t></si></sst>
        """.trimIndent()
        val sheet = """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
              <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>28</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        zip(
            f,
            mapOf(
                "xl/sharedStrings.xml" to shared,
                "xl/workbook.xml" to """<workbook><sheets><sheet name="成绩" sheetId="1"/></sheets></workbook>""",
                "xl/worksheets/sheet1.xml" to sheet
            )
        )

        val doc = DocParser.parse(f)
        assertEquals(1, doc.sheets.size)
        assertEquals("成绩", doc.sheets[0].name)
        assertEquals(listOf("姓名", "年龄"), doc.sheets[0].rows[0])
        assertEquals(listOf("张三", "28"), doc.sheets[0].rows[1])
    }

    @Test
    fun `tar archive entries are listed and readable`() {
        val f = File(tmp.newFolder(), "c.tar")
        f.writeBytes(tarOf(mapOf("hello.txt" to "你好".toByteArray(Charsets.UTF_8))))

        val entries = FileRepo.archiveEntries(f)
        assertEquals(1, entries.size)
        assertEquals("hello.txt", entries[0].name)
        assertEquals("你好", String(FileRepo.readArchiveEntry(f, "hello.txt"), Charsets.UTF_8))
    }

    @Test
    fun `gb18030 text can be read with explicit encoding`() {
        val f = File(tmp.newFolder(), "gbk.txt")
        val text = "中文编码测试"
        f.writeBytes(text.toByteArray(charset("GB18030")))

        val auto = FileRepo.readTextSmart(f)
        // 自动识别会优先 UTF-8，失败后按 GB18030 兜底
        assertEquals(text, auto.text)

        val forced = FileRepo.readWindow(f, 0L, encoding = "GB18030")
        assertEquals(text, forced.text)
        assertEquals("GB18030", forced.encoding)
    }

    @Test
    fun `utf16 text is editable through windows`() {
        val f = File(tmp.newFolder(), "u16.txt")
        val text = "第一行\n第二行\n"
        f.writeBytes(text.toByteArray(Charsets.UTF_16LE))
        val w = FileRepo.readWindow(f, 0L, encoding = "UTF-16LE")
        assertTrue(w.text.trim().contains("第一行"))
        assertTrue(w.text.contains("第二行"))
    }

    private fun tarOf(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        files.forEach { (name, data) ->
            val header = ByteArray(512)
            name.toByteArray(Charsets.UTF_8).copyInto(header, 0)
            "%011o".format(0x1A4).toByteArray().copyInto(header, 100)   // mode 0644
            "%011o".format(0).toByteArray().copyInto(header, 108)       // uid
            "%011o".format(0).toByteArray().copyInto(header, 116)       // gid
            "%011o".format(data.size).toByteArray().copyInto(header, 124)
            "%011o".format(0).toByteArray().copyInto(header, 136)       // mtime
            header[156] = '0'.code.toByte()
            "ustar".toByteArray().copyInto(header, 257)
            header[263] = '0'.code.toByte()
            out.write(header)
            out.write(data)
            val pad = (512 - data.size % 512) % 512
            if (pad > 0) out.write(ByteArray(pad))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }
}
