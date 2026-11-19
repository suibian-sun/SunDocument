package com.suibian.sunfile.data

import android.os.StatFs
import java.io.File

/** 当前路径所在文件系统的容量信息 */
data class StorageInfo(val total: Long, val available: Long) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val ratio: Float get() = if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}

object StorageStats {
    fun of(path: String): StorageInfo? = runCatching {
        val stat = StatFs(if (path.isEmpty()) "/" else path)
        StorageInfo(stat.totalBytes, stat.availableBytes)
    }.getOrNull()

    /** 当前目录直属的文件夹 / 文件数量 */
    fun count(entries: List<FileEntry>): Pair<Int, Int> {
        var dirs = 0
        var files = 0
        for (e in entries) if (e.isDir) dirs++ else files++
        return dirs to files
    }

    fun pathDisplay(path: String): String = if (path.isEmpty()) "/" else path
}
