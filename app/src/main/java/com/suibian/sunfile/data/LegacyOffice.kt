package com.suibian.sunfile.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 老式 Office（.ppt / .doc / .xls）是 OLE 复合文档，不是 zip。
 *
 * 这里只做一件事：从复合文档里按名字取出一个流，供上层抽文字。
 * 够用即可——预览不需要样式，只需要内容。
 */
object Cfb {

    private const val MAGIC = -0x340d631b // D0 CF 11 E0 A1 B1 1A E1 的小端 long

    data class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    fun readStream(file: File, streamName: String): ByteArray? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(512)
            raf.readFully(head)
            val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            if (buf.getInt(0) != MAGIC) return null
            val sectorShift = buf.getShort(30).toInt() and 0xFFFF
            val miniShift = buf.getShort(32).toInt() and 0xFFFF
            val sectorSize = 1 shl sectorShift
            val miniSize = 1 shl miniShift
            val numFat = buf.getInt(44)
            val dirStart = buf.getInt(48)
            val miniCutoff = buf.getInt(56)
            val miniFatStart = buf.getInt(60)
            val difatStart = buf.getInt(68)
            val numDifat = buf.getInt(72)

            // DIFAT：头部 109 项 + 后续链
            val fatSectors = ArrayList<Int>()
            for (i in 0 until 109) {
                val s = buf.getInt(76 + i * 4)
                if (s >= 0) fatSectors.add(s)
            }
            var difat = difatStart
            var guard = 0
            while (difat >= 0 && guard < numDifat + 8) {
                val sec = readSector(raf, difat, sectorSize)
                val sb = ByteBuffer.wrap(sec).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sectorSize / 4 - 1) {
                    val s = sb.getInt(i * 4)
                    if (s >= 0) fatSectors.add(s)
                }
                difat = sb.getInt(sectorSize - 4)
                guard++
            }

            // FAT
            val fat = ArrayList<Int>()
            fatSectors.take(numFat.coerceAtLeast(0)).forEach { s ->
                val sec = readSector(raf, s, sectorSize)
                val sb = ByteBuffer.wrap(sec).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sectorSize / 4) fat.add(sb.getInt(i * 4))
            }

            // 目录
            val dirBytes = readChain(raf, dirStart, fat, sectorSize)
            val entries = ArrayList<Entry>()
            var off = 0
            while (off + 128 <= dirBytes.size) {
                val eb = ByteBuffer.wrap(dirBytes, off, 128).order(ByteOrder.LITTLE_ENDIAN)
                val nameLen = eb.getShort(64).toInt() and 0xFFFF
                val type = eb.get(66).toInt()
                val start = eb.getInt(116)
                val size = eb.getLong(120)
                if (nameLen in 2..64) {
                    val nameBytes = ByteArray(nameLen - 2)
                    eb.position(0)
                    eb.get(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_16LE)
                    entries.add(Entry(name, type, start, size))
                }
                off += 128
            }

            val target = entries.firstOrNull { it.name.equals(streamName, ignoreCase = true) && it.type == 2 }
                ?: return null

            if (target.size < miniCutoff) {
                // 小流：从 root 的 mini stream 里按 miniFAT 拼
                val root = entries.firstOrNull { it.type == 5 } ?: return null
                val miniStream = readChain(raf, root.start, fat, sectorSize)
                val miniFatBytes = readChain(raf, miniFatStart, fat, sectorSize)
                val miniFat = ArrayList<Int>()
                val mb = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until miniFatBytes.size / 4) miniFat.add(mb.getInt(i * 4))
                val out = java.io.ByteArrayOutputStream()
                var s = target.start
                var n = 0
                while (s >= 0 && n < 10000) {
                    val from = s * miniSize
                    if (from + miniSize > miniStream.size) break
                    out.write(miniStream, from, miniSize)
                    s = miniFat.getOrElse(s) { -2 }
                    n++
                }
                out.toByteArray().copyOf(target.size.toInt().coerceAtLeast(0))
            } else {
                readChain(raf, target.start, fat, sectorSize).copyOf(target.size.toInt().coerceAtMost(Int.MAX_VALUE))
            }
        }
    }.getOrNull()

    private fun readSector(raf: RandomAccessFile, index: Int, sectorSize: Int): ByteArray {
        val out = ByteArray(sectorSize)
        raf.seek(512L + index.toLong() * sectorSize)
        raf.readFully(out)
        return out
    }

    private fun readChain(raf: RandomAccessFile, start: Int, fat: List<Int>, sectorSize: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && guard < 200_000) {
            out.write(readSector(raf, s, sectorSize))
            s = fat.getOrElse(s) { -2 }
            guard++
        }
        return out.toByteArray()
    }
}

/**
 * 旧版 PowerPoint（.ppt/.pps）文字抽取。
 *
 * 二进制 PPT 的正文在 TextCharsAtom(0x0FA0, UTF-16) / TextBytesAtom(0x0FA8, 单字节)
 * 记录里，容器记录 recVer=0xF 需要递归进去。顺序按记录出现顺序，够做文字预览。
 */
object LegacyPpt {

    fun extract(file: File): List<String> {
        val stream = Cfb.readStream(file, "PowerPoint Document") ?: return emptyList()
        val out = LinkedHashSet<String>()
        walk(stream, 0, stream.size.coerceAtMost(64 * 1024 * 1024), out, 0)
        return out.toList()
    }

    private fun walk(data: ByteArray, from: Int, to: Int, out: LinkedHashSet<String>, depth: Int) {
        if (depth > 24) return
        var pos = from
        while (pos + 8 <= to) {
            val verInst = u16(data, pos)
            val type = u16(data, pos + 2)
            val len = u32(data, pos + 4)
            if (len < 0 || pos + 8 + len > to) break
            val body = pos + 8
            when {
                (verInst and 0xF) == 0xF -> walk(data, body, body + len, out, depth + 1)
                type == 0x0FA0 -> {
                    val text = String(data, body, len, Charsets.UTF_16LE).trim()
                    if (text.isNotEmpty() && text.any { it.isLetterOrDigit() }) out.add(text)
                }
                type == 0x0FA8 -> {
                    val text = String(data, body, len, Charsets.ISO_8859_1).trim()
                    if (text.isNotEmpty() && text.any { it.isLetterOrDigit() }) out.add(text)
                }
            }
            pos = body + len
        }
    }

    private fun u16(d: ByteArray, i: Int): Int = (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, i: Int): Int =
        (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) or
            ((d[i + 2].toInt() and 0xFF) shl 16) or ((d[i + 3].toInt() and 0xFF) shl 24)
}
