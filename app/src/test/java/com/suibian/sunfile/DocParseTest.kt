package com.suibian.sunfile

import com.suibian.sunfile.data.DocParser
import com.suibian.sunfile.data.DocStyle
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.FileKind
import com.suibian.sunfile.data.LenientJson
import com.suibian.sunfile.data.Plugins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文档解析与插件清单的回归测试。
 *
 * 上一版把 Office 判成「用其他应用打开」，这里盯死两件事：
 * 按内容能认出 pptx，以及 manifest.json 允许注释和裸键。
 */
class DocParseTest {

    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"), "sun-doc-test").apply {
        deleteRecursively()
        mkdirs()
    }

    private fun pptx(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            put("[Content_Types].xml", "<Types/>")
            put("ppt/presentation.xml", "<p:presentation/>")
            put(
                "ppt/slides/slide1.xml",
                """
                <p:sld><p:cSld><p:spTree>
                  <p:sp><p:txBody>
                    <a:p><a:r><a:t>季度汇报</a:t></a:r></a:p>
                    <a:p><a:r><a:t>收入增长 18%</a:t></a:r></a:p>
                  </p:txBody></p:sp>
                </p:spTree></p:cSld></p:sld>
                """.trimIndent()
            )
            put(
                "ppt/slides/slide2.xml",
                """
                <p:sld><p:cSld><p:spTree>
                  <p:sp><p:txBody><a:p><a:r><a:t>下一步</a:t></a:r></a:p></p:txBody></p:sp>
                </p:spTree></p:cSld></p:sld>
                """.trimIndent()
            )
        }
    }

    @Test
    fun pptxIsReadableAndSplitsSlides() {
        val file = File(tempDir(), "report.pptx")
        pptx(file)

        assertTrue(FileKinds.isDocumentReadable(file))
        assertEquals(FileKind.OFFICE, FileKinds.kindOfName(file.name))

        val content = DocParser.parse(file)
        assertEquals("2 页", content.meta)
        val slides = content.blocks.filter { it.style == DocStyle.SLIDE }
        assertEquals(2, slides.size)
        assertEquals("季度汇报", slides[0].text)
        assertEquals(1, slides[0].level)
        assertEquals("下一步", slides[1].text)
        assertTrue(content.blocks.any { it.text.contains("18%") })
    }

    @Test
    fun renamedPptxStillOpensByContent() {
        // 扩展名骗人的情况：按 zip 内部结构判断，而不是看后缀
        val file = File(tempDir(), "其实是幻灯片.ppt")
        pptx(file)
        val content = DocParser.parse(file)
        assertEquals("2 页", content.meta)
    }

    @Test
    fun csvBecomesSheet() {
        val file = File(tempDir(), "a.csv")
        file.writeText("名称,数量\n苹果,3\n香蕉,5\n")
        val content = DocParser.parse(file)
        assertEquals(1, content.sheets.size)
        assertEquals(listOf("名称", "数量"), content.sheets[0].rows[0])
        assertEquals(listOf("香蕉", "5"), content.sheets[0].rows[2])
    }

    @Test
    fun manifestAcceptsCommentsAndBareKeys() {
        val json = """
            {
              // 插件 SDK 版本
              pluginSdkVersion: 2,
              pluginID: "cc.sun.demo",   /* 唯一标识 */
              name: '示例',
              interfaces: [],
              actions: [
                { id: "md5", label: "计算 MD5", script: "md5sum ${'$'}{files}", extensions: ["*"] },
              ]
            }
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val map = LenientJson(json).parse() as Map<String, Any?>
        assertEquals(2, map["pluginSdkVersion"])
        assertEquals("cc.sun.demo", map["pluginID"])
        assertEquals("示例", map["name"])
        val actions = map["actions"] as List<*>
        assertEquals(1, actions.size)
        assertNotNull((actions[0] as Map<*, *>)["script"])
    }

    @Test
    fun pluginActionFiltersByExtension() {
        val action = Plugins.Action(
            id = "py",
            label = "格式化",
            pluginId = "cc.sun.demo",
            pluginName = "示例",
            script = "true",
            extensions = listOf("py")
        )
        val dir = tempDir()
        assertTrue(action.accepts(File(dir, "a.py")))
        assertFalse(action.accepts(File(dir, "a.kt")))
    }
}
