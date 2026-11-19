package com.suibian.sunfile.data

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * 十六进制编辑器的内存缓冲。只加载有限长度（默认 6 MB），
 * 超出部分保持只读，避免误点一下就把几百 MB 读进内存。
 * 所有修改都发生在内存里，由调用方决定何时写回。
 */
class HexBuffer private constructor(
    private var data: ByteArray,
    var length: Int
) {

    companion object {
        const val MAX_BYTES = 6 * 1024 * 1024

        fun of(bytes: ByteArray): HexBuffer = HexBuffer(bytes.copyOf(), bytes.size)

        fun load(file: File, max: Int = MAX_BYTES): Load {
            val total = file.length()
            val take = minOf(total, max.toLong()).toInt()
            val buf = ByteArray(take)
            var read = 0
            runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    while (read < take) {
                        val n = raf.read(buf, read, take - read)
                        if (n <= 0) break
                        read += n
                    }
                }
            }
            return Load(HexBuffer(buf, read), total > read, total)
        }
    }

    data class Load(val buffer: HexBuffer, val truncated: Boolean, val totalSize: Long)

    fun byteAt(pos: Int): Int = if (pos in 0 until length) data[pos].toInt() and 0xFF else -1

    fun setByte(pos: Int, value: Int) {
        if (pos in 0 until length) data[pos] = value.toByte()
    }

    fun setBytes(pos: Int, bytes: ByteArray) {
        if (pos < 0 || pos >= length) return
        val n = minOf(bytes.size, length - pos)
        System.arraycopy(bytes, 0, data, pos, n)
    }

    fun insert(pos: Int, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val at = pos.coerceIn(0, length)
        if (length + bytes.size > MAX_BYTES * 2) return false
        val next = ByteArray(length + bytes.size)
        System.arraycopy(data, 0, next, 0, at)
        System.arraycopy(bytes, 0, next, at, bytes.size)
        System.arraycopy(data, at, next, at + bytes.size, length - at)
        data = next
        length = next.size
        return true
    }

    fun delete(pos: Int, count: Int): Boolean {
        if (count <= 0 || pos < 0 || pos >= length) return false
        val n = minOf(count, length - pos)
        val next = ByteArray(length - n)
        System.arraycopy(data, 0, next, 0, pos)
        System.arraycopy(data, pos + n, next, pos, length - pos - n)
        data = next
        length = next.size
        return true
    }

    fun indexOf(needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || needle.size > length) return -1
        val first = needle[0]
        val last = length - needle.size
        var i = from.coerceAtLeast(0)
        while (i <= last) {
            if (data[i] == first) {
                var k = 1
                while (k < needle.size && data[i + k] == needle[k]) k++
                if (k == needle.size) return i
            }
            i++
        }
        return -1
    }

    fun indexOfLast(needle: ByteArray, from: Int = length): Int {
        if (needle.isEmpty() || needle.size > length) return -1
        var i = minOf(from, length - needle.size)
        while (i >= 0) {
            var k = 0
            while (k < needle.size && data[i + k] == needle[k]) k++
            if (k == needle.size) return i
            i--
        }
        return -1
    }

    fun replaceAll(needle: ByteArray, replacement: ByteArray): Int {
        if (needle.isEmpty()) return 0
        var hits = 0
        var at = indexOf(needle, 0)
        while (at >= 0 && hits < 100_000) {
            delete(at, needle.size)
            insert(at, replacement)
            hits++
            at = indexOf(needle, at + replacement.size)
        }
        return hits
    }

    fun toByteArray(): ByteArray = data.copyOf(length)

    fun slice(from: Int, count: Int): ByteArray {
        val s = from.coerceIn(0, length)
        val e = (s + count).coerceIn(s, length)
        return data.copyOfRange(s, e)
    }

    fun save(file: File): Boolean = runCatching {
        file.outputStream().use { it.write(data, 0, length) }
        true
    }.getOrDefault(false)
}

object HexText {

    private val HEX = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray, from: Int, count: Int, sep: String = " "): String {
        val end = minOf(bytes.size, from + count)
        if (from >= end) return ""
        val sb = StringBuilder((end - from) * 3)
        for (i in from until end) {
            val v = bytes[i].toInt() and 0xFF
            if (sb.isNotEmpty()) sb.append(sep)
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** 接受 "00 69 73"、"006973"、"0x00,0x69"、"\x00" 等写法 */
    fun decode(input: String): ByteArray? {
        val cleaned = input
            .replace("0x", " ", ignoreCase = true)
            .replace("\\x", " ", ignoreCase = true)
            .replace(Regex("[\\s,;:._-]"), "")
        if (cleaned.isEmpty()) return null
        if (cleaned.length % 2 != 0) return null
        if (!cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            out[i] = cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    fun ascii(bytes: ByteArray, from: Int, count: Int): String {
        val end = minOf(bytes.size, from + count)
        if (from >= end) return ""
        val sb = StringBuilder(end - from)
        for (i in from until end) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(if (v in 0x20..0x7E) v.toChar() else '.')
        }
        return sb.toString()
    }

    fun checksum(bytes: ByteArray, algorithm: String): String = runCatching {
        val md = MessageDigest.getInstance(algorithm)
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    fun crc32(bytes: ByteArray): String {
        val c = CRC32()
        c.update(bytes)
        return c.value.toString(16).padStart(8, '0').uppercase()
    }

    private val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun base64(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            sb.append(B64[b0 ushr 2])
            sb.append(B64[(b0 and 0x03) shl 4 or (if (b1 >= 0) b1 ushr 4 else 0)])
            sb.append(if (b1 >= 0) B64[(b1 and 0x0F) shl 2 or (if (b2 >= 0) b2 ushr 6 else 0)] else '=')
            sb.append(if (b2 >= 0) B64[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }
}
