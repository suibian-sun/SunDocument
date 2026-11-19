package com.suibian.sunfile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private const val PATTERN = "yyyy-MM-dd HH:mm"

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
        return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i])
        else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun time(millis: Long): String =
        if (millis <= 0) "-" else SimpleDateFormat(PATTERN, Locale.getDefault()).format(Date(millis))

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
