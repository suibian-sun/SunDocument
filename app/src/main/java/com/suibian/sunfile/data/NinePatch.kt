package com.suibian.sunfile.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * .9.png 的补丁线读写。
 *
 * 9-patch 本质是普通 PNG 再加一个 npTc 块：上边一行黑点标横向拉伸区，
 * 左边一列标纵向拉伸区，右边一列与下边一行标内容留白。
 * 这里只重写 npTc，像素数据交给 Bitmap 重新编码。
 */
object NinePatch {

    data class Divs(val x: List<Int>, val y: List<Int>) {
        val isEmpty: Boolean get() = x.isEmpty() && y.isEmpty()
    }

    /** 读取已有的 npTc；没有补丁块返回 null */
    fun readDivs(bytes: ByteArray): Divs? {
        if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 'P'.code.toByte()) return null
        var p = 8
        while (p + 12 <= bytes.size) {
            val len = be32(bytes, p)
            if (len < 0 || p + 12 + len > bytes.size) return null
            val type = String(bytes, p + 4, 4, Charsets.US_ASCII)
            if (type == "npTc") {
                val data = p + 8
                val numX = be32(bytes, data)
                val numY = be32(bytes, data + 4)
                if (numX < 0 || numY < 0 || 8 + (numX + numY) * 4 > len) return null
                val x = (0 until numX).map { be32(bytes, data + 8 + it * 4) }
                val y = (0 until numY).map { be32(bytes, data + 8 + numX * 4 + it * 4) }
                return Divs(x, y)
            }
            p += 12 + len
        }
        return null
    }

    /** 把一行/一列补丁像素换算成区间边界（内容坐标，左闭右开） */
    fun divisions(mark: BooleanArray, contentLen: Int): List<Int> {
        val out = ArrayList<Int>(8)
        var c = 0
        while (c < contentLen) {
            if (c + 1 < mark.size && mark[c + 1]) {
                val start = c
                while (c < contentLen && c + 1 < mark.size && mark[c + 1]) c++
                out.add(start)
                out.add(c)
            } else {
                c++
            }
        }
        return out
    }

    /** 从位图四边读出补丁标记 */
    fun marksOf(bitmap: Bitmap): Marks {
        val w = bitmap.width
        val h = bitmap.height
        val top = BooleanArray(w)
        val left = BooleanArray(h)
        val right = BooleanArray(h)
        val bottom = BooleanArray(w)
        for (x in 0 until w) {
            top[x] = isMarked(bitmap.getPixel(x, 0))
            bottom[x] = isMarked(bitmap.getPixel(x, h - 1))
        }
        for (y in 0 until h) {
            left[y] = isMarked(bitmap.getPixel(0, y))
            right[y] = isMarked(bitmap.getPixel(w - 1, y))
        }
        return Marks(w, h, top, left, right, bottom)
    }

    private fun isMarked(color: Int): Boolean {
        val a = (color ushr 24) and 0xFF
        if (a == 0) return false
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        // AAPT 认纯黑；半透明或偏色的老图也当成标记，避免打开后丢线
        return r < 40 && g < 40 && b < 40
    }

    /** 重新编码：位图 → PNG → 在 IHDR 后插入 npTc */
    fun encode(bitmap: Bitmap, xDivs: List<Int>, yDivs: List<Int>): ByteArray? = runCatching {
        val raw = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, raw)
        val png = raw.toByteArray()
        val ihdrLen = be32(png, 8)
        val insert = 8 + 12 + ihdrLen
        if (insert > png.size) return null

        val dataSize = 8 + (xDivs.size + yDivs.size) * 4
        val data = ByteArray(dataSize)
        putBe32(data, 0, xDivs.size)
        putBe32(data, 4, yDivs.size)
        xDivs.forEachIndexed { i, v -> putBe32(data, 8 + i * 4, v) }
        yDivs.forEachIndexed { i, v -> putBe32(data, 8 + xDivs.size * 4 + i * 4, v) }

        val chunk = ByteArray(12 + dataSize)
        putBe32(chunk, 0, dataSize)
        "npTc".toByteArray(Charsets.US_ASCII).copyInto(chunk, 4)
        data.copyInto(chunk, 8)
        val crc = CRC32()
        crc.update(chunk, 4, 4 + dataSize)
        putBe32(chunk, 8 + dataSize, crc.value.toInt())

        val out = ByteArray(png.size + chunk.size)
        png.copyInto(out, 0, 0, insert)
        chunk.copyInto(out, insert)
        png.copyInto(out, insert + chunk.size, insert, png.size)
        out
    }.getOrNull()

    data class Marks(
        val width: Int,
        val height: Int,
        val top: BooleanArray,
        val left: BooleanArray,
        val right: BooleanArray,
        val bottom: BooleanArray
    ) {
        val contentWidth: Int get() = (width - 2).coerceAtLeast(1)
        val contentHeight: Int get() = (height - 2).coerceAtLeast(1)
        val xDivs: List<Int> get() = divisions(top, contentWidth)
        val yDivs: List<Int> get() = divisions(left, contentHeight)

        /** 内容留白：右列 / 下行的黑点范围 */
        fun padding(): IntArray {
            val l = firstMark(right)
            val t = firstMark(bottom)
            val r = lastMark(right)
            val b = lastMark(bottom)
            return intArrayOf(l, t, (width - 2) - r, (height - 2) - b)
        }

        private fun firstMark(a: BooleanArray): Int {
            for (i in 1 until a.size - 1) if (a[i]) return i - 1
            return 0
        }

        private fun lastMark(a: BooleanArray): Int {
            for (i in a.size - 2 downTo 1) if (a[i]) return i - 1
            return a.size - 2
        }
    }

    private fun be32(b: ByteArray, p: Int): Int {
        if (p < 0 || p + 4 > b.size) return 0
        return ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
    }

    private fun putBe32(b: ByteArray, p: Int, v: Int) {
        if (p < 0 || p + 4 > b.size) return
        b[p] = ((v ushr 24) and 0xFF).toByte()
        b[p + 1] = ((v ushr 16) and 0xFF).toByte()
        b[p + 2] = ((v ushr 8) and 0xFF).toByte()
        b[p + 3] = (v and 0xFF).toByte()
    }
}
