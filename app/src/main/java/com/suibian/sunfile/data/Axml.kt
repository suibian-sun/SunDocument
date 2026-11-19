package com.suibian.sunfile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 二进制 AXML（编译进 APK 的 AndroidManifest.xml 等）→ 文本 XML。
 *
 * 只做读取与格式化，不负责重新编译；资源 ID 保留 `@0x7f0a0001` 形式，
 * 与 MT 免费版的呈现一致。
 */
object Axml {

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_START_NS = 0x0100
    private const val CHUNK_END_NS = 0x0101
    private const val CHUNK_START = 0x0102
    private const val CHUNK_END = 0x0103
    private const val CHUNK_CDATA = 0x0104

    class AxmlException(message: String) : Exception(message)

    fun decode(bytes: ByteArray): String {
        if (bytes.size < 8) throw AxmlException("文件过小")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.getShort(0).toInt() and 0xFFFF
        if (magic != 0x0003) throw AxmlException("不是二进制 XML")
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        val sb = StringBuilder(4096)
        val strings = ArrayList<String>()
        var resIds: IntArray = IntArray(0)
        val nsMap = HashMap<Int, String>()
        val stack = ArrayList<String>()

        var pos = headerSize
        while (pos + 8 <= bytes.size) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val chunkHeader = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > bytes.size) break
            when (type) {
                CHUNK_STRING_POOL -> strings.addAll(readStringPool(buf, pos))
                CHUNK_RESOURCE_MAP -> {
                    val count = (chunkSize - chunkHeader) / 4
                    resIds = IntArray(count) { buf.getInt(pos + chunkHeader + it * 4) }
                }
                CHUNK_START_NS -> {
                    val prefix = str(strings, buf.getInt(pos + 16))
                    val uriIdx = buf.getInt(pos + 20)
                    nsMap[uriIdx] = prefix
                    if (prefix.isNotEmpty()) {
                        sb.append("xmlns:").append(prefix)
                            .append("=\"").append(esc(str(strings, uriIdx))).append("\"\n")
                    }
                }
                CHUNK_START -> {
                    val name = str(strings, buf.getInt(pos + 20))
                    val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                    val attrSize = buf.getShort(pos + 26).toInt() and 0xFFFF
                    val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                    sb.append("<").append(name)
                    // 属性区起点 = chunk 起点 + 16（node 头）+ attributeStart
                    var attrPos = pos + 16 + attrStart
                    for (i in 0 until attrCount) {
                        if (attrPos + 20 > pos + chunkSize) break
                        val ns = buf.getInt(attrPos)
                        val attrName = str(strings, buf.getInt(attrPos + 4))
                        val raw = buf.getInt(attrPos + 8)
                        val dataType = buf.get(attrPos + 15).toInt() and 0xFF
                        val data = buf.getInt(attrPos + 16)
                        val prefix = if (ns >= 0) nsMap[ns] else null
                        sb.append("\n    ")
                        if (!prefix.isNullOrEmpty()) sb.append(prefix).append(':')
                        sb.append(attrName).append("=\"")
                        sb.append(esc(valueOf(buf, strings, raw, dataType, data, resIds)))
                        sb.append('"')
                        attrPos += if (attrSize > 0) attrSize else 20
                    }
                    sb.append(">")
                    stack.add(name)
                }
                CHUNK_END -> {
                    val name = str(strings, buf.getInt(pos + 20))
                    if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                    sb.append("</").append(name).append(">\n")
                }
                CHUNK_CDATA -> {
                    sb.append(esc(str(strings, buf.getInt(pos + 16))))
                }
                else -> Unit
            }
            pos += chunkSize
        }
        if (sb.isEmpty()) throw AxmlException("没有可解析的节点")
        return sb.toString()
    }

    private fun readStringPool(buf: ByteBuffer, chunkStart: Int): List<String> {
        val count = buf.getInt(chunkStart + 8)
        val flags = buf.getInt(chunkStart + 16)
        val stringsStart = buf.getInt(chunkStart + 20)
        val utf8 = (flags and 0x100) != 0
        val headerSize = buf.getShort(chunkStart + 2).toInt() and 0xFFFF
        if (count <= 0 || count > 200_000) return emptyList()
        val out = ArrayList<String>(count)
        val base = chunkStart + stringsStart
        for (i in 0 until count) {
            val off = buf.getInt(chunkStart + headerSize + i * 4)
            if (off < 0) {
                out.add("")
                continue
            }
            var p = base + off
            out.add(runCatching {
                if (utf8) {
                    var len = buf.get(p).toInt() and 0xFF
                    if (len and 0x80 != 0) {
                        len = ((len and 0x7F) shl 8) or (buf.get(p + 1).toInt() and 0xFF)
                        p += 2
                    } else p += 1
                    var byteLen = buf.get(p).toInt() and 0xFF
                    if (byteLen and 0x80 != 0) {
                        byteLen = ((byteLen and 0x7F) shl 8) or (buf.get(p + 1).toInt() and 0xFF)
                        p += 2
                    } else p += 1
                    val arr = ByteArray(byteLen)
                    for (k in arr.indices) arr[k] = buf.get(p + k)
                    String(arr, Charsets.UTF_8)
                } else {
                    var len = buf.getShort(p).toInt() and 0xFFFF
                    p += 2
                    if (len and 0x8000 != 0) {
                        len = ((len and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
                        p += 2
                    }
                    val chars = CharArray(len)
                    for (k in 0 until len) chars[k] = buf.getChar(p + k * 2)
                    String(chars)
                }
            }.getOrDefault(""))
        }
        return out
    }

    private fun str(pool: List<String>, index: Int): String =
        if (index in pool.indices) pool[index] else ""

    private fun valueOf(
        buf: ByteBuffer,
        pool: List<String>,
        raw: Int,
        type: Int,
        data: Int,
        resIds: IntArray
    ): String {
        return when (type) {
            0x03 -> str(pool, data)
            0x01 -> "@0x%08x".format(data)
            0x02 -> "?0x%08x".format(data)
            0x04 -> java.lang.Float.intBitsToFloat(data).toString()
            0x05 -> {
                val unit = data and 0xF
                val value = java.lang.Float.intBitsToFloat(data and 0xFFFFFF00.toInt())
                val suffix = when (unit) {
                    0 -> "px"; 1 -> "dp"; 2 -> "sp"; 3 -> "pt"; 4 -> "in"; 5 -> "mm"; else -> ""
                }
                "$value$suffix"
            }
            0x06 -> {
                val unit = data and 0xF
                val value = java.lang.Float.intBitsToFloat(data and 0xFFFFFF00.toInt()) * 100
                if (unit == 0) "$value%" else "${value * 100}%p"
            }
            0x10 -> data.toString()
            0x11 -> "0x%x".format(data)
            0x12 -> if (data != 0) "true" else "false"
            0x1c, 0x1d, 0x1e, 0x1f -> "#%08x".format(data)
            else -> if (raw >= 0) str(pool, raw) else "0x%08x".format(data)
        }
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
