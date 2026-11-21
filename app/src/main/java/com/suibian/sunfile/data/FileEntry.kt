package com.suibian.sunfile.data

import java.io.File

/** 列表中的一行（文件或目录） */
data class FileEntry(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    /** inode 变更时间，作为「创建/变更」这一列的数据源；shell 枚举时为 0 */
    val created: Long = 0L,
    val isHidden: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    val childCount: Int = 0,
    /** 权限串（如 rw-r--r--），仅在通过 shell 枚举时可用 */
    val mode: String? = null
) {
    val path: String get() = file.absolutePath
}

enum class SortMode(val label: String) {
    NAME("名称"),
    SIZE("大小"),
    DATE("时间"),
    TYPE("类型")
}

data class SortSpec(val mode: SortMode = SortMode.NAME, val ascending: Boolean = true) {
    fun nextFor(mode: SortMode): SortSpec =
        if (this.mode == mode) SortSpec(mode, !ascending) else SortSpec(mode, true)
}

/** 剪贴板：待复制/移动的路径集合 */
data class ClipItem(val paths: List<String>, val cut: Boolean) {
    val isEmpty: Boolean get() = paths.isEmpty()
}
