package com.suibian.sunfile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private const val PATTERN = "yyyy-MM-dd HH:mm"

    // SimpleDateFormat 非线程安全，用 ThreadLocal 复用，避免列表滚动时反复构造
    private val longTime = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat(PATTERN, Locale.getDefault())
    }
    private val shortTime = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }

    /** 文件大小：统一保留两位小数，符合"精确到 0.01"的要求 */
    fun size(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var v = bytes.toDouble() / 1024.0
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.2f %s", v, units[i])
    }

    /** 保留两位小数的存储占用，如 "36.24 GB / 128.00 GB" */
    fun sizePair(used: Long, total: Long): String = "${size(used)} / ${size(total)}"

    fun time(millis: Long): String =
        if (millis <= 0) "-" else longTime.get()!!.format(Date(millis))

    fun timeShort(millis: Long): String =
        if (millis <= 0) "-" else shortTime.get()!!.format(Date(millis))

    fun speed(bytesPerSec: Long): String = size(bytesPerSec) + "/s"

    fun duration(seconds: Long): String {
        if (seconds <= 0) return "--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun permissions(f: java.io.File): String {
        val sb = StringBuilder()
        sb.append(if (f.isDirectory) "d" else "-")
        sb.append(if (f.canRead()) "r" else "-")
        sb.append(if (f.canWrite()) "w" else "-")
        sb.append(if (f.canExecute()) "x" else "-")
        return sb.toString()
    }

    /** 把 3 位权限串转成 rwx 形式 */
    fun rwx(mode: Int): String {
        val chars = charArrayOf('r', 'w', 'x')
        val sb = StringBuilder()
        for (i in 2 downTo 0) {
            for (j in 0..2) {
                val bit = (mode shr (i * 3 + (2 - j))) and 1
                sb.append(if (bit == 1) chars[j] else '-')
            }
        }
        return sb.toString()
    }
}
