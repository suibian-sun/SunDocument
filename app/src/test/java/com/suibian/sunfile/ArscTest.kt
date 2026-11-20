package com.suibian.sunfile

import com.suibian.sunfile.data.Arsc
import com.suibian.sunfile.data.NinePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 资源表与点九图的二进制处理。
 *
 * 这里手工拼一个最小 resources.arsc：一个包、一个 string 类型、一个条目。
 * 能解析出来、能改字符串、改完还能再解析回同样的结构，就算通过。
 */
class ArscTest {

    private fun le16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun le32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    /** UTF-8 字符串池 */
    private fun pool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        strings.forEach { s ->
            offsets.add(data.size())
            val bytes = s.toByteArray(Charsets.UTF_8)
            if (s.length > 0x7F) {
                data.write(0x80 or (s.length shr 8))
                data.write(s.length and 0xFF)
            } else {
                data.write(s.length)
            }
            if (bytes.size > 0x7F) {
                data.write(0x80 or (bytes.size shr 8))
                data.write(bytes.size and 0xFF)
            } else {
                data.write(bytes.size)
            }
            data.write(bytes)
            data.write(0)
        }
        val content = data.toByteArray()
        val headerSize = 0x1C
        val stringsStart = headerSize + strings.size * 4
        var total = stringsStart + content.size
        total = (total + 3) and 3.inv()
        val out = ByteArrayOutputStream()
        le16(out, 0x0001)
        le16(out, headerSize)
        le32(out, total)
        le32(out, strings.size)
        le32(out, 0)
        le32(out, 0x100)
        le32(out, stringsStart)
        le32(out, 0)
        offsets.forEach { le32(out, it) }
        out.write(content)
        while (out.size() < total) out.write(0)
        return out.toByteArray()
    }

    private fun typeChunk(typeNameIndex: Int, keyIndex: Int, stringValueIndex: Int): ByteArray {
        val configSize = 0x40
        val headerSize = 20 + configSize
        val entriesStart = headerSize + 4
        val out = ByteArrayOutputStream()
        le16(out, 0x0201)
        le16(out, headerSize)
        le32(out, entriesStart + 16)
        out.write(1)          // id
        out.write(0)          // flags
        le16(out, 0)          // reserved
        le32(out, 1)          // entryCount
        le32(out, entriesStart)
        le32(out, configSize)
        while (out.size() < 20 + configSize) out.write(0)
        le32(out, 0)          // 第一个条目的偏移
        // ResTable_entry
        le16(out, 8)
        le16(out, 0)
        le32(out, keyIndex)
        // Res_value
        le16(out, 8)
        out.write(0)
        out.write(3)          // TYPE_STRING
        le32(out, stringValueIndex)
        return out.toByteArray()
    }

    private fun sampleTable(stringValueIndex: Int = 1): ByteArray {
        val global = pool(listOf("first", "world"))
        val typePool = pool(listOf("string"))
        val keyPool = pool(listOf("greeting"))
        val type = typeChunk(0, 0, stringValueIndex)
        val pkgHeader = 288
        val pkgSize = pkgHeader + typePool.size + keyPool.size + type.size
        val pkg = ByteArrayOutputStream()
        le16(pkg, 0x0200)
        le16(pkg, pkgHeader)
        le32(pkg, pkgSize)
        le32(pkg, 0x7f) // package id
        "app".forEach { c -> le16(pkg, c.code) }
        while (pkg.size() < pkgHeader) pkg.write(0)
        // 覆写 name 之后的三个偏移
        val bytes = pkg.toByteArray()
        putLe32(bytes, 268, pkgHeader)
        putLe32(bytes, 272, 1)
        putLe32(bytes, 276, pkgHeader + typePool.size)
        putLe32(bytes, 280, 1)
        val all = ByteArrayOutputStream()
        all.write(bytes)
        all.write(typePool)
        all.write(keyPool)
        all.write(type)

        val total = 12 + global.size + all.size()
        val out = ByteArrayOutputStream()
        le16(out, 0x0002)
        le16(out, 12)
        le32(out, total)
        le32(out, 1)
        out.write(global)
        out.write(all.toByteArray())
        return out.toByteArray()
    }

    private fun putLe32(b: ByteArray, p: Int, v: Int) {
        b[p] = (v and 0xFF).toByte()
        b[p + 1] = ((v shr 8) and 0xFF).toByte()
        b[p + 2] = ((v shr 16) and 0xFF).toByte()
        b[p + 3] = ((v shr 24) and 0xFF).toByte()
    }

    @Test
    fun parsesMinimalTable() {
        val table = Arsc.parse(sampleTable())
        assertEquals(null, table.error)
        assertEquals(1, table.entries.size)
        val e = table.entries.first()
        assertEquals("string", e.kind)
        assertEquals("greeting", e.name)
        assertEquals("world", e.text)
        assertEquals(0x7f010000, e.resId)
        assertEquals("默认", e.config)
        assertEquals("first", table.strings[0])
    }

    @Test
    fun replacesStringAndKeepsIndices() {
        val bytes = sampleTable()
        val next = Arsc.replaceString(bytes, 1, "there")
        assertNotNull(next)
        val table = Arsc.parse(next!!)
        assertEquals("there", table.entries.first().text)
        // 其它字符串的下标不能动，否则资源值会指错
        assertEquals("first", table.strings[0])
        assertEquals(2, table.strings.size)
    }

    @Test
    fun longerStringStillParses() {
        val next = Arsc.replaceString(sampleTable(), 1, "一个很长的中文字符串，用来验证长度前缀与偏移重建")
        assertNotNull(next)
        val table = Arsc.parse(next!!)
        assertTrue(table.strings[1].startsWith("一个很长"))
    }

    @Test
    fun divisionsComeFromMarkedPixels() {
        // 内容宽 4，标记落在像素 2、3（即内容坐标 1、2）
        val mark = booleanArrayOf(false, false, true, true, false, false)
        assertEquals(listOf(1, 3), NinePatch.divisions(mark, 4))
    }

    @Test
    fun divisionsHandleTwoRuns() {
        val mark = booleanArrayOf(false, true, true, false, true, false)
        assertEquals(listOf(0, 2, 3, 4), NinePatch.divisions(mark, 4))
    }
}
