package com.suibian.sunfile.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class Revision(
    val id: Long,
    val time: Long,
    val size: Long,
    val snapshot: File
)

/**
 * 文件修改历史：每次保存前把旧内容留档，按文件路径分目录保存，
 * 单个文件最多保留 30 个版本，超出后丢弃最旧的。
 */
class HistoryStore(context: Context) {

    private val root = File(context.filesDir, "history")
    private val limit = 30

    init {
        if (!root.exists()) root.mkdirs()
    }

    private fun key(path: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }.take(24)
    }

    private fun dirFor(target: File, create: Boolean = true): File {
        val dir = File(root, key(target.absolutePath))
        if (!dir.exists()) {
            if (!create) return dir
            dir.mkdirs()
            File(dir, "_path").writeText(target.absolutePath)
        }
        return dir
    }

    fun revisions(target: File): List<Revision> {
        val dir = dirFor(target, create = false)
        if (!dir.exists()) return emptyList()
        val list = dir.listFiles { f -> f.isFile && f.name.endsWith(".rev") } ?: return emptyList()
        return list.mapNotNull { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
            Revision(id, id, f.length(), f)
        }.sortedByDescending { it.time }
    }

    fun latest(target: File): Revision? = revisions(target).firstOrNull()

    /** 把内容存成一个版本，返回该版本 */
    fun snapshot(target: File, content: String): Revision? = runCatching {
        val dir = dirFor(target)
        var stamp = System.currentTimeMillis()
        // 同一毫秒内多次保存时顺延，避免覆盖
        while (File(dir, "$stamp.rev").exists()) stamp++
        val file = File(dir, "$stamp.rev")
        file.writeText(content)
        trim(dir)
        Revision(stamp, stamp, file.length(), file)
    }.getOrNull()

    private fun trim(dir: File) {
        val all = dir.listFiles { f -> f.isFile && f.name.endsWith(".rev") } ?: return
        if (all.size <= limit) return
        all.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .take(all.size - limit)
            .forEach { it.delete() }
    }

    fun read(rev: Revision): String = runCatching { rev.snapshot.readText() }.getOrDefault("")

    fun clear(target: File) {
        dirFor(target).listFiles()?.forEach { if (it.name.endsWith(".rev")) it.delete() }
    }

    fun clearAll() {
        root.listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    fun totalSize(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun totalCount(): Int = root.walkTopDown().count { it.isFile && it.name.endsWith(".rev") }
}
