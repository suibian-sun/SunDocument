package com.suibian.sunfile.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 旧版 Word（.doc）正文抽取。
 *
 * FIB 里 fcMin/fcMac 给出正文字节区间，简单文档直接读这一段；
 * 复杂文档（快速保存）正文被拆成多段并压在 1Table 的 piece table 里，
 * 这里按 CLX 的 PlcPcd 还原，段落顺序仍然正确。
 */
object LegacyWord {

    fun extract(file: File): List<String> {
        val stream = Cfb.readStream(file, "WordDocument") ?: return emptyList()
        if (stream.size < 0x200) return emptyList()
        val buf = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buf.getShort(0x0A).toInt() and 0xFFFF
        val tableStreamName = if (flags and 0x0200 != 0) "1Table" else "0Table"
        val table = Cfb.readStream(file, tableStreamName)

        val text = runCatching { pieces(stream, table, buf) }.getOrNull()
            ?: runCatching { plainRange(stream, buf) }.getOrNull()
            ?: return emptyList()

        return text.split('\r', '\n', '\u0007', '\u000B', '\u000C')
            .map { it.replace('\u0000', ' ').trim() }
            .filter { it.isNotEmpty() && it.any(Char::isLetterOrDigit) }
            .distinct()
    }

    /** 简单文档：fcMin..fcMac 直接是正文（压缩存 8 位，否则 UTF-16） */
    private fun plainRange(stream: ByteArray, buf: ByteBuffer): String {
        val fcMin = buf.getInt(0x18)
        val fcMac = buf.getInt(0x1C)
        if (fcMin < 0 || fcMac <= fcMin || fcMac > stream.size) return ""
        return decode(stream, fcMin, fcMac - fcMin, compressed = looksCompressed(stream, fcMin, fcMac))
    }

    /** 快速保存文档：CLX → PlcPcd，逐段取字节 */
    private fun pieces(stream: ByteArray, table: ByteArray?, buf: ByteBuffer): String {
        if (table == null) return ""
        val fcClx = buf.getInt(0x01A2)
        val lcbClx = buf.getInt(0x01A6)
        if (fcClx <= 0 || lcbClx <= 0 || fcClx + lcbClx > table.size) return ""
        var pos = fcClx
        val end = fcClx + lcbClx
        // 跳过 Prc 记录，定位到 0x02 的 Pcdt
        while (pos + 5 < end) {
            when (table[pos].toInt()) {
                0x01 -> {
                    val cb = le32(table, pos + 1)
                    if (cb < 0 || pos + 5 + cb > end) return ""
                    pos += 5 + cb
                }
                0x02 -> {
                    val lcb = le32(table, pos + 1)
                    val plc = pos + 5
                    return readPlcPcd(stream, table, plc, lcb)
                }
                else -> return ""
            }
        }
        return ""
    }

    private fun readPlcPcd(stream: ByteArray, table: ByteArray, plc: Int, lcb: Int): String {
        // PlcPcd = (n+1) 个 CP + n 个 8 字节 Pcd
        val n = (lcb - 4) / 12
        if (n <= 0) return ""
        val out = StringBuilder()
        for (i in 0 until n) {
            val pcd = plc + (n + 1) * 4 + i * 8
            if (pcd + 8 > table.size) break
            val fcRaw = le32(table, pcd + 2)
            val compressed = fcRaw and 0x40000000 != 0
            val fc = fcRaw and 0x3FFFFFFF
            val cpStart = le32(table, plc + i * 4)
            val cpEnd = le32(table, plc + (i + 1) * 4)
            val chars = (cpEnd - cpStart).coerceAtLeast(0)
            val byteStart = if (compressed) fc / 2 else fc
            val byteLen = if (compressed) chars else chars * 2
            if (byteStart < 0 || byteLen <= 0 || byteStart + byteLen > stream.size) continue
            out.append(decode(stream, byteStart, byteLen, compressed))
        }
        return out.toString()
    }

    private fun decode(data: ByteArray, from: Int, len: Int, compressed: Boolean): String =
        if (compressed) String(data, from, len, Charsets.ISO_8859_1)
        else String(data, from, len, Charsets.UTF_16LE)

    private fun looksCompressed(data: ByteArray, from: Int, to: Int): Boolean {
        var zeros = 0
        val step = ((to - from) / 400).coerceAtLeast(1)
        var i = from + 1
        while (i < to && i < data.size) {
            if (data[i].toInt() == 0) zeros++
            i += step
        }
        return zeros < (to - from) / 400 / 4
    }

    private fun le32(d: ByteArray, i: Int): Int =
        if (i + 3 >= d.size) 0
        else (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) or
            ((d[i + 2].toInt() and 0xFF) shl 16) or ((d[i + 3].toInt() and 0xFF) shl 24)
}

/**
 * 旧版 Excel（.xls）读取：BIFF8 的 Workbook 流。
 *
 * 只做三件事：读出工作表名、共享字符串表（SST）、每个单元格的文本或数字。
 * 公式单元格取缓存值，样式一概忽略——预览只需要看到内容。
 */
object LegacyExcel {

    private const val MAX_ROWS = 4000
    private const val MAX_COLS = 80

    fun extract(file: File): List<SheetTable> {
        val stream = Cfb.readStream(file, "Workbook")
            ?: Cfb.readStream(file, "Book")
            ?: return emptyList()
        val sst = ArrayList<String>()
        val sheets = ArrayList<SheetTable>()
        var currentName = "Sheet1"
        var rows = HashMap<Int, HashMap<Int, String>>()
        var maxRow = 0

        fun flush() {
            if (rows.isEmpty()) return
            val out = ArrayList<List<String>>()
            for (r in 0..maxRow) {
                val row = rows[r] ?: continue
                if (row.isEmpty()) continue
                val width = (row.keys.maxOrNull() ?: 0) + 1
                out.add((0 until width).map { row[it].orEmpty() })
                if (out.size >= MAX_ROWS) break
            }
            if (out.isNotEmpty()) sheets.add(SheetTable(currentName, out))
            rows = HashMap()
            maxRow = 0
        }

        var pos = 0
        while (pos + 4 <= stream.size) {
            val type = le16(stream, pos)
            val len = le16(stream, pos + 2)
            if (type == 0x0A && sheets.isNotEmpty()) {
                // EOF：一张表结束
                flush()
            }
            if (pos + 4 + len > stream.size) break
            val body = pos + 4
            when (type) {
                0x0085 -> currentName = readSheetName(stream, body, len, sst).ifBlank { "Sheet${sheets.size + 1}" }
                0x00FC -> readSst(stream, body, len, sst)
                0x00FD -> {
                    val r = le16(stream, body)
                    val c = le16(stream, body + 2)
                    val idx = le32(stream, body + 6)
                    put(rows, r, c, sst.getOrElse(idx) { "" }, MAX_COLS, MAX_ROWS)
                    if (r > maxRow) maxRow = r
                }
                0x0204 -> {
                    val r = le16(stream, body)
                    val c = le16(stream, body + 2)
                    val text = readUnicodeString(stream, body + 6, len - 6)?.first.orEmpty()
                    put(rows, r, c, text, MAX_COLS, MAX_ROWS)
                    if (r > maxRow) maxRow = r
                }
                0x0203 -> {
                    val r = le16(stream, body)
                    val c = le16(stream, body + 2)
                    val v = Double.fromBits(le64(stream, body + 6))
                    put(rows, r, c, plainNumber(v), MAX_COLS, MAX_ROWS)
                    if (r > maxRow) maxRow = r
                }
                0x027E -> {
                    val r = le16(stream, body)
                    val c = le16(stream, body + 2)
                    put(rows, r, c, plainNumber(rk(le32(stream, body + 6))), MAX_COLS, MAX_ROWS)
                    if (r > maxRow) maxRow = r
                }
                0x00BD -> {
                    val r = le16(stream, body)
                    val first = le16(stream, body + 2)
                    val count = (len - 6) / 6
                    for (i in 0 until count) {
                        val c = first + i
                        val v = rk(le32(stream, body + 4 + i * 6 + 2))
                        put(rows, r, c, plainNumber(v), MAX_COLS, MAX_ROWS)
                    }
                    if (r > maxRow) maxRow = r
                }
            }
            pos += 4 + len
        }
        flush()
        return sheets
    }

    private fun put(
        rows: HashMap<Int, HashMap<Int, String>>,
        r: Int,
        c: Int,
        text: String,
        maxCols: Int,
        maxRows: Int
    ) {
        if (r < 0 || r >= maxRows || c < 0 || c >= maxCols) return
        val v = text.trim()
        if (v.isEmpty()) return
        rows.getOrPut(r) { HashMap() }[c] = v
    }

    /** BOUNDSHEET：名字在记录末尾，用 SST 之外的独立编码 */
    private fun readSheetName(stream: ByteArray, body: Int, len: Int, sst: List<String>): String {
        if (len < 8) return ""
        val cch = stream[body + 6].toInt() and 0xFF
        val flags = stream[body + 7].toInt() and 0xFF
        val start = body + 8
        return if (flags and 0x01 != 0 && start + cch * 2 <= stream.size) {
            String(stream, start, cch * 2, Charsets.UTF_16LE)
        } else if (start + cch <= stream.size) {
            String(stream, start, cch, Charsets.ISO_8859_1)
        } else ""
    }

    /** SST：总条数 + 唯一条数，之后是 XLUnicodeRichExtendedString 序列 */
    private fun readSst(stream: ByteArray, body: Int, len: Int, out: MutableList<String>) {
        if (len < 8) return
        var pos = body + 8
        val end = body + len
        var guard = 0
        while (pos + 3 <= end && guard < 60_000) {
            val (value, next) = readUnicodeString(stream, pos, end - pos) ?: break
            out.add(value)
            pos = next
            guard++
        }
    }

    /** 返回 (文本, 下一个位置)；跨表引用的 Continue 记录这里不做合并 */
    private fun readUnicodeString(stream: ByteArray, pos: Int, available: Int): Pair<String, Int>? {
        if (available < 3 || pos + 3 > stream.size) return null
        val cch = le16(stream, pos)
        val flags = stream[pos + 2].toInt() and 0xFF
        val high = flags and 0x01 != 0
        val ext = flags and 0x04 != 0
        val rich = flags and 0x08 != 0
        var p = pos + 3
        var runs = 0
        var extLen = 0
        if (rich && p + 2 <= stream.size) {
            runs = le16(stream, p)
            p += 2
        }
        if (ext && p + 4 <= stream.size) {
            extLen = le32(stream, p)
            p += 4
        }
        val bytes = if (high) cch * 2 else cch
        if (p + bytes > stream.size || cch < 0) return null
        val value = if (high) String(stream, p, bytes, Charsets.UTF_16LE)
        else String(stream, p, bytes, Charsets.ISO_8859_1)
        p += bytes + runs * 4 + extLen
        return value to p
    }

    private fun rk(v: Int): Double {
        val isInt = v and 0x02 != 0
        val div100 = v and 0x01 != 0
        val raw = v and 0xFFFFFFFC.toInt()
        val d = if (isInt) (raw shr 2).toDouble() else Double.fromBits((raw.toLong() shl 32))
        return if (div100) d / 100.0 else d
    }

    private fun plainNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

    private fun le16(d: ByteArray, i: Int): Int =
        if (i + 1 >= d.size) 0 else (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8)

    private fun le32(d: ByteArray, i: Int): Int =
        if (i + 3 >= d.size) 0
        else (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) or
            ((d[i + 2].toInt() and 0xFF) shl 16) or ((d[i + 3].toInt() and 0xFF) shl 24)

    private fun le64(d: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or (if (i + k < d.size) (d[i + k].toLong() and 0xFF) else 0L)
        return v
    }
}
