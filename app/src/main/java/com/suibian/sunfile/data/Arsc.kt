package com.suibian.sunfile.data

import java.util.Locale

/**
 * resources.arsc 的读取与改写。
 *
 * 只做「看得懂 + 改得动」两件事：把编译后的资源表还原成
 * 类型 / 条目 / 限定符 / 值 四层列表，并支持改字符串与改整数。
 * 二进制布局按 AOSP ResourceTypes.h，遇到不认识的块直接跳过。
 */
object Arsc {

    private const val T_STRING_POOL = 0x0001
    private const val T_TABLE = 0x0002
    private const val T_PACKAGE = 0x0200
    private const val T_TYPE = 0x0201
    private const val T_TYPESPEC = 0x0202

    const val TYPE_NULL = 0x00
    const val TYPE_REFERENCE = 0x01
    const val TYPE_ATTRIBUTE = 0x02
    const val TYPE_STRING = 0x03
    const val TYPE_FLOAT = 0x04
    const val TYPE_DIMENSION = 0x05
    const val TYPE_FRACTION = 0x06
    const val TYPE_INT_DEC = 0x10
    const val TYPE_INT_HEX = 0x11
    const val TYPE_INT_BOOLEAN = 0x12
    const val TYPE_INT_COLOR_ARGB8 = 0x1c

    /** 一个资源配置值 */
    data class Entry(
        val kind: String,
        val name: String,
        val resId: Int,
        val config: String,
        val configKey: String,
        val valueType: Int,
        val data: Int,
        val text: String,
        /** 字符串值在全局字符串池中的下标，-1 表示不是字符串 */
        val stringIndex: Int,
        /** 值中 4 字节 data 的绝对偏移，可直接就地改写 */
        val dataOffset: Int,
        val complex: Boolean = false,
        val complexCount: Int = 0
    )

    data class Table(
        val entries: List<Entry> = emptyList(),
        val strings: List<String> = emptyList(),
        val packages: List<String> = emptyList(),
        val error: String? = null
    ) {
        /** 同一类型、同一限定符下按名称取条目，用于查看引用指向哪里 */
        fun nameOf(resId: Int): String? = byId[resId]
        val byId: Map<Int, String> by lazy { entries.associate { it.resId to "${it.kind}/${it.name}" } }
    }

    // ---------------- 读取 ----------------

    fun parse(bytes: ByteArray): Table {
        val buf = Buf(bytes)
        if (buf.n < 12 || buf.u16(0) != T_TABLE) return Table(error = "不是 resources.arsc")
        val headerSize = buf.u16(2)
        val total = buf.u32(4).coerceIn(0, buf.n)
        val pool = parsePool(buf, headerSize) ?: return Table(error = "字符串池损坏")
        val strings = pool.strings

        val entries = ArrayList<Entry>(4096)
        val packages = ArrayList<String>()
        var p = headerSize + pool.size
        while (p + 8 <= total) {
            val type = buf.u16(p)
            val size = buf.u32(p + 4)
            if (size < 8 || p + size > total) break
            if (type == T_PACKAGE) runCatching { parsePackage(buf, p, size, strings, entries, packages) }
            p += size
        }

        // 先建 resId → 类型/名称 的表，再把 @0x7f… 换成可读名字
        val named = entries.filter { it.configKey.isEmpty() }.associate { it.resId to "${it.kind}/${it.name}" }
        val readable = entries.map { e ->
            when (e.valueType) {
                TYPE_REFERENCE -> e.copy(text = "@" + (named[e.data] ?: hexId(e.data)))
                TYPE_ATTRIBUTE -> e.copy(text = "?" + (named[e.data] ?: hexId(e.data)))
                else -> e
            }
        }
        return Table(readable, strings, packages)
    }

    fun parse(file: java.io.File): Table = runCatching { parse(file.readBytes()) }
        .getOrElse { Table(error = it.message ?: "读取失败") }

    private fun hexId(id: Int) = "0x" + id.toUInt().toString(16).padStart(8, '0')

    private fun parsePackage(
        buf: Buf,
        start: Int,
        size: Int,
        strings: List<String>,
        out: MutableList<Entry>,
        packages: MutableList<String>
    ) {
        val headerSize = buf.u16(start + 2)
        val pkgId = buf.u32(start + 8)
        val name = utf16z(buf, start + 12, 128)
        if (name.isNotEmpty()) packages.add(name)
        val typeStrings = buf.u32(start + 268)
        val keyStrings = buf.u32(start + 276)
        val typeIdOffset = if (headerSize >= 288) buf.u32(start + 284) else 0
        val types = parsePool(buf, start + typeStrings)?.strings ?: emptyList()
        val keys = parsePool(buf, start + keyStrings)?.strings ?: emptyList()

        val end = start + size
        var p = start + headerSize
        while (p + 8 <= end) {
            val type = buf.u16(p)
            val csize = buf.u32(p + 4)
            if (csize < 8 || p + csize > end) break
            if (type == T_TYPE) {
                runCatching { parseType(buf, p, csize, pkgId, typeIdOffset, types, keys, strings, out) }
            }
            p += csize
        }
    }

    private fun parseType(
        buf: Buf,
        start: Int,
        size: Int,
        pkgId: Int,
        typeIdOffset: Int,
        types: List<String>,
        keys: List<String>,
        strings: List<String>,
        out: MutableList<Entry>
    ) {
        val id = buf.u8(start + 8)
        val flags = buf.u8(start + 9)
        val count = buf.u32(start + 12)
        val entriesStart = buf.u32(start + 16)
        val configStart = start + 20
        val configSize = buf.u32(configStart).takeIf { it in 4..256 } ?: 0x40
        val config = configText(buf, configStart, configSize)
        val offsetsBase = configStart + configSize
        val typeName = types.getOrNull(id - 1) ?: "type$id"
        val sparse = flags and 0x01 != 0
        val offset16 = flags and 0x02 != 0
        val end = start + size

        var i = 0
        while (i < count) {
            val index: Int
            val off: Int
            if (sparse) {
                index = buf.u16(offsetsBase + i * 4)
                off = buf.u16(offsetsBase + i * 4 + 2) * 4
            } else if (offset16) {
                val o = buf.u16(offsetsBase + i * 2)
                index = i
                off = if (o == 0xFFFF) -1 else o * 4
            } else {
                index = i
                val o = buf.u32(offsetsBase + i * 4)
                off = if (o == 0xFFFFFFFF.toInt() || o == -1) -1 else o
            }
            i++
            if (off < 0) continue
            val e = start + entriesStart + off
            if (e + 8 > end) continue
            val entrySize = buf.u16(e)
            val entryFlags = buf.u16(e + 2)
            val keyIdx = buf.u32(e + 4)
            val key = keys.getOrNull(keyIdx) ?: ("#key$keyIdx")
            val resId = (pkgId shl 24) or ((id + typeIdOffset) shl 16) or index
            if (entryFlags and 0x0001 != 0) {
                // 样式 / 数组：值是一串 key-value，不展开编辑，只给个规模
                val mapCount = buf.u32(e + 12)
                out.add(
                    Entry(
                        typeName, key, resId, config, configKey(buf, configStart, configSize),
                        0x100, 0, "复合值 · $mapCount 项", -1, -1, true, mapCount
                    )
                )
            } else {
                val v = e + entrySize
                if (v + 8 > end) continue
                val vt = buf.u8(v + 3)
                val data = buf.u32(v + 4)
                val idx = if (vt == TYPE_STRING && data in strings.indices) data else -1
                out.add(
                    Entry(
                        typeName, key, resId, config, configKey(buf, configStart, configSize),
                        vt, data, valueText(vt, data, strings), idx, v + 4
                    )
                )
            }
        }
    }

    private fun valueText(type: Int, data: Int, strings: List<String>): String = when (type) {
        TYPE_NULL -> if (data == 0) "空" else "空(${data})"
        TYPE_REFERENCE -> "@" + hexId(data)
        TYPE_ATTRIBUTE -> "?" + hexId(data)
        TYPE_STRING -> strings.getOrNull(data) ?: "<字符串 #$data 缺失>"
        TYPE_FLOAT -> {
            val f = Float.fromBits(data)
            if (f == f.toLong().toFloat()) f.toLong().toString() else String.format(Locale.US, "%s", f.toString())
        }
        TYPE_DIMENSION -> dimension(data, "dp")
        TYPE_FRACTION -> dimension(data, "%")
        TYPE_INT_DEC -> data.toString()
        TYPE_INT_HEX -> "0x" + data.toUInt().toString(16)
        TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
        TYPE_INT_COLOR_ARGB8, 0x1d, 0x1e, 0x1f ->
            "#" + data.toUInt().toString(16).padStart(8, '0').uppercase(Locale.US)
        else -> "0x" + data.toUInt().toString(16)
    }

    private fun dimension(data: Int, fallback: String): String {
        val unit = when (data and 0xF) {
            0 -> "px"
            1 -> "dp"
            2 -> "sp"
            3 -> "pt"
            4 -> "in"
            5 -> "mm"
            else -> fallback
        }
        val radix = (data shr 4) and 0x3
        val raw = data shr 6
        val v = when (radix) {
            0 -> raw.toFloat()
            1 -> raw / 128f
            2 -> raw / 32768f
            else -> raw.toFloat()
        }
        return if (v == v.toLong().toFloat()) "$v$unit" else String.format(Locale.US, "%.3f%s", v, unit)
    }

    private fun configText(buf: Buf, p: Int, size: Int): String {
        if (size < 12) return "默认"
        val parts = ArrayList<String>(4)
        val lang = bytesAscii(buf, p + 8, 2)
        val region = bytesAscii(buf, p + 10, 2)
        if (lang.isNotEmpty() && lang != "any") parts.add(if (region.isEmpty() || region == "any") lang else "$lang-r$region")
        if (size >= 16) {
            when (buf.u8(p + 12)) {
                1 -> parts.add("竖屏")
                2 -> parts.add("横屏")
            }
            when (buf.u16(p + 14)) {
                120 -> parts.add("ldpi")
                160 -> parts.add("mdpi")
                213 -> parts.add("tvdpi")
                240 -> parts.add("hdpi")
                320 -> parts.add("xhdpi")
                480 -> parts.add("xxhdpi")
                640 -> parts.add("xxxhdpi")
                0xFFFE -> parts.add("anydpi")
                0xFFFF -> parts.add("nodpi")
                0 -> Unit
                else -> parts.add("${buf.u16(p + 14)}dpi")
            }
        }
        if (size >= 28) {
            val sdk = buf.u16(p + 24)
            if (sdk > 0) parts.add("v$sdk")
        }
        return if (parts.isEmpty()) "默认" else parts.joinToString("-")
    }

    /** 限定符指纹：默认配置返回空串，其余取配置体前 20 字节的十六进制 */
    private fun configKey(buf: Buf, p: Int, size: Int): String {
        if (size <= 4) return ""
        val sb = StringBuilder()
        var any = false
        for (i in 4 until minOf(size, 24)) {
            val v = buf.u8(p + i)
            if (v != 0) any = true
            sb.append(v.toString(16).padStart(2, '0'))
        }
        return if (any) sb.toString() else ""
    }

    private fun bytesAscii(buf: Buf, p: Int, len: Int): String {
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val c = buf.u8(p + i)
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    private fun utf16z(buf: Buf, p: Int, maxChars: Int): String {
        val sb = StringBuilder()
        for (i in 0 until maxChars) {
            val c = buf.u16(p + i * 2)
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    // ---------------- 改写 ----------------

    /** 改全局字符串池中的一条（保序重建，其它字符串的下标不变） */
    fun replaceString(bytes: ByteArray, index: Int, value: String): ByteArray? = runCatching {
        val buf = Buf(bytes)
        val headerSize = buf.u16(2)
        val pool = parsePool(buf, headerSize) ?: return null
        if (index !in pool.strings.indices) return null
        val list = pool.strings.toMutableList()
        list[index] = value
        val chunk = buildPool(buf, pool, list)
        val out = ByteArray(bytes.size - pool.size + chunk.size)
        System.arraycopy(bytes, 0, out, 0, pool.start)
        System.arraycopy(chunk, 0, out, pool.start, chunk.size)
        val tail = bytes.size - (pool.start + pool.size)
        System.arraycopy(bytes, pool.start + pool.size, out, pool.start + chunk.size, tail)
        // 表头里的总大小跟着变
        if (buf.u32(4) == bytes.size) putU32(out, 4, out.size)
        out
    }.getOrNull()

    /** 改 4 字节的值：整数、布尔、颜色、引用都走这里 */
    fun replaceValue(bytes: ByteArray, offset: Int, data: Int): ByteArray? = runCatching {
        if (offset < 0 || offset + 4 > bytes.size) return null
        val out = bytes.copyOf()
        putU32(out, offset, data)
        out
    }.getOrNull()

    // ---------------- 二进制细节 ----------------

    internal class Pool(
        val start: Int,
        val size: Int,
        val utf8: Boolean,
        val styleCount: Int,
        val stylesStart: Int,
        val strings: List<String>
    )

    private fun parsePool(buf: Buf, start: Int): Pool? {
        if (start < 0 || start + 28 > buf.n || buf.u16(start) != T_STRING_POOL) return null
        val headerSize = buf.u16(start + 2)
        val size = buf.u32(start + 4)
        if (size <= 0 || start + size > buf.n) return null
        val count = buf.u32(start + 8)
        val styles = buf.u32(start + 12)
        val flags = buf.u32(start + 16)
        val stringsStart = buf.u32(start + 20)
        val stylesStart = buf.u32(start + 24)
        if (count < 0 || count > 2_000_000) return null
        val utf8 = flags and 0x100 != 0
        val base = start + headerSize
        val list = ArrayList<String>(minOf(count, 4096))
        for (i in 0 until count) {
            val offPos = base + i * 4
            if (offPos + 4 > start + size) break
            val off = buf.u32(offPos)
            if (off == -1) {
                list.add("")
                continue
            }
            val p = start + stringsStart + off
            list.add(if (p in start until start + size) decodeString(buf, p, utf8) else "")
        }
        return Pool(start, size, utf8, styles, stylesStart, list)
    }

    private fun decodeString(buf: Buf, p: Int, utf8: Boolean): String {
        if (utf8) {
            var q = p
            val c1 = buf.u8(q)
            if (c1 and 0x80 != 0) q += 2 else q += 1
            val b0 = buf.u8(q)
            val byteLen: Int
            if (b0 and 0x80 != 0) {
                byteLen = ((b0 and 0x7F) shl 8) or buf.u8(q + 1)
                q += 2
            } else {
                byteLen = b0
                q += 1
            }
            if (byteLen < 0 || q + byteLen > buf.n) return ""
            return String(buf.b, q, byteLen, Charsets.UTF_8)
        }
        var q = p
        val l0 = buf.u16(q)
        val len: Int
        if (l0 and 0x8000 != 0) {
            len = ((l0 and 0x7FFF) shl 16) or buf.u16(q + 2)
            q += 4
        } else {
            len = l0
            q += 2
        }
        if (len < 0 || q + len * 2 > buf.n) return ""
        return String(buf.b, q, len * 2, Charsets.UTF_16LE)
    }

    private fun buildPool(buf: Buf, pool: Pool, strings: List<String>): ByteArray {
        val encoded = strings.map { if (pool.utf8) encodeUtf8(it) else encodeUtf16(it) }
        val dataSize = encoded.sumOf { it.size }
        val count = strings.size
        val styleCount = pool.styleCount
        val headerSize = 0x1C
        val stringsStart = headerSize + count * 4 + styleCount * 4
        val styleLen = if (styleCount > 0 && pool.stylesStart > 0) pool.size - pool.stylesStart else 0
        var total = stringsStart + dataSize + styleLen
        total = (total + 3) and 3.inv()
        val out = ByteArray(total)

        putU16(out, 0, T_STRING_POOL)
        putU16(out, 2, headerSize)
        putU32(out, 4, total)
        putU32(out, 8, count)
        putU32(out, 12, styleCount)
        putU32(out, 16, if (pool.utf8) 0x100 else 0)
        putU32(out, 20, stringsStart)
        putU32(out, 24, if (styleCount > 0) stringsStart + dataSize else 0)

        var cursor = stringsStart
        encoded.forEachIndexed { i, bytes ->
            putU32(out, headerSize + i * 4, cursor - stringsStart)
            System.arraycopy(bytes, 0, out, cursor, bytes.size)
            cursor += bytes.size
        }
        if (styleCount > 0 && styleLen > 0) {
            val styleOffBase = headerSize + count * 4
            for (i in 0 until styleCount) {
                putU32(out, styleOffBase + i * 4, buf.u32(pool.start + headerSize + count * 4 + i * 4))
            }
            System.arraycopy(buf.b, pool.start + pool.stylesStart, out, stringsStart + dataSize, styleLen)
        }
        return out
    }

    private fun encodeUtf8(s: String): ByteArray {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val chars = s.length
        val head = ArrayList<Byte>(4)
        if (chars > 0x7F) {
            head.add((0x80 or (chars shr 8)).toByte())
            head.add((chars and 0xFF).toByte())
        } else {
            head.add(chars.toByte())
        }
        if (bytes.size > 0x7F) {
            head.add((0x80 or (bytes.size shr 8)).toByte())
            head.add((bytes.size and 0xFF).toByte())
        } else {
            head.add(bytes.size.toByte())
        }
        val out = ByteArray(head.size + bytes.size + 1)
        head.forEachIndexed { i, b -> out[i] = b }
        System.arraycopy(bytes, 0, out, head.size, bytes.size)
        return out
    }

    private fun encodeUtf16(s: String): ByteArray {
        val chars = s.length
        val head = if (chars > 0x7FFF) 4 else 2
        val out = ByteArray(head + chars * 2 + 2)
        if (head == 4) {
            putU16(out, 0, 0x8000 or (chars shr 16))
            putU16(out, 2, chars and 0xFFFF)
        } else {
            putU16(out, 0, chars)
        }
        val b = s.toByteArray(Charsets.UTF_16LE)
        System.arraycopy(b, 0, out, head, b.size)
        return out
    }

    private fun putU16(b: ByteArray, p: Int, v: Int) {
        if (p < 0 || p + 2 > b.size) return
        b[p] = (v and 0xFF).toByte()
        b[p + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putU32(b: ByteArray, p: Int, v: Int) {
        if (p < 0 || p + 4 > b.size) return
        b[p] = (v and 0xFF).toByte()
        b[p + 1] = ((v shr 8) and 0xFF).toByte()
        b[p + 2] = ((v shr 16) and 0xFF).toByte()
        b[p + 3] = ((v shr 24) and 0xFF).toByte()
    }

    internal class Buf(val b: ByteArray) {
        val n = b.size
        fun u8(p: Int): Int = if (p in 0 until n) b[p].toInt() and 0xFF else 0
        fun u16(p: Int): Int = u8(p) or (u8(p + 1) shl 8)
        fun u32(p: Int): Int = u8(p) or (u8(p + 1) shl 8) or (u8(p + 2) shl 16) or (u8(p + 3) shl 24)
    }
}
