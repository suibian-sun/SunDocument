package com.suibian.sunfile.data

import android.os.StatFs
import android.os.SystemClock
import java.io.File

/** 当前路径所在文件系统的容量信息 */
data class StorageInfo(val total: Long, val available: Long) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val ratio: Float get() = if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}

object StorageStats {

    /**
     * statfs 是系统调用，返回栏、侧边栏、顶栏副标题都会问一次。
     * 800ms 内复用同一个结果：同一帧里多少次读取都只打一次系统调用，
     * 又不会让容量数字明显滞后。
     */
    private const val TTL_MS = 800L
    private val cache = HashMap<String, Pair<Long, StorageInfo>>()

    fun of(path: String): StorageInfo? {
        val key = if (path.isEmpty()) "/" else path
        val now = SystemClock.elapsedRealtime()
        synchronized(cache) {
            cache[key]?.let { (stamp, info) ->
                if (now - stamp < TTL_MS) return info
            }
        }
        val info = runCatching {
            val stat = StatFs(key)
            StorageInfo(stat.totalBytes, stat.availableBytes)
        }.getOrNull() ?: return null
        synchronized(cache) {
            if (cache.size > 32) cache.clear()
            cache[key] = now to info
        }
        return info
    }

    /** 写入/删除大文件后主动失效，让下一次读取拿到新值 */
    fun invalidate(path: String? = null) {
        synchronized(cache) {
            if (path == null) cache.clear() else cache.remove(if (path.isEmpty()) "/" else path)
        }
    }

    /** 当前目录直属的文件夹 / 文件数量 */
    fun count(entries: List<FileEntry>): Pair<Int, Int> {
        var dirs = 0
        var files = 0
        for (e in entries) if (e.isDir) dirs++ else files++
        return dirs to files
    }

    fun pathDisplay(path: String): String = if (path.isEmpty()) "/" else path
}
