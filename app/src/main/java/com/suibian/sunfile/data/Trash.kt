package com.suibian.sunfile.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TrashItem(
    val id: String,
    val name: String,
    val origPath: String,
    val deletedAt: Long,
    val size: Long,
    val isDir: Boolean
)

/**
 * 回收站：删除先搬进这里，30 天后自动清除。
 *
 * 文件放在应用外部目录（与内置存储同一卷），rename 即可秒移；
 * 跨卷（例如 SD 卡）时退回「复制 + 删除」。索引是一行一条 JSON，
 * 免得每次删除都重写整个数组。
 */
object Trash {

    const val KEEP_DAYS = 30

    fun root(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "trash").apply { if (!exists()) mkdirs() }
    }

    private fun indexFile(context: Context) = File(root(context), "index.json")

    private fun readAll(context: Context): MutableList<TrashItem> {
        val f = indexFile(context)
        if (!f.exists()) return mutableListOf()
        val out = mutableListOf<TrashItem>()
        runCatching {
            f.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val o = JSONObject(line)
                out.add(
                    TrashItem(
                        id = o.getString("id"),
                        name = o.optString("name"),
                        origPath = o.optString("orig"),
                        deletedAt = o.optLong("at"),
                        size = o.optLong("size"),
                        isDir = o.optBoolean("dir")
                    )
                )
            }
        }
        return out
    }

    private fun writeAll(context: Context, items: List<TrashItem>) {
        val f = indexFile(context)
        val tmp = File(f.parentFile, "index.tmp")
        tmp.bufferedWriter().use { w ->
            items.forEach { i ->
                val o = JSONObject()
                    .put("id", i.id)
                    .put("name", i.name)
                    .put("orig", i.origPath)
                    .put("at", i.deletedAt)
                    .put("size", i.size)
                    .put("dir", i.isDir)
                w.write(o.toString())
                w.newLine()
            }
        }
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    fun list(context: Context): List<TrashItem> = readAll(context).sortedByDescending { it.deletedAt }

    fun count(context: Context): Int = readAll(context).size

    /** 搬进回收站；成功返回 null，失败返回原因 */
    fun move(context: Context, source: File): String? = runCatching {
        if (!source.exists()) return "文件不存在"
        val items = readAll(context)
        val id = System.currentTimeMillis().toString(36) + "-" + UUID.randomUUID().toString().take(6)
        val isDir = source.isDirectory
        val size = if (isDir) 0L else source.length()
        val target = File(root(context), id + "_" + source.name)
        val moved = source.renameTo(target) || run {
            if (isDir) copyDir(source, target) else copyFile(source, target)
            deleteRecursively(source).isSuccess
        }
        if (!moved) return "无法移动到回收站"
        items.add(
            TrashItem(
                id = id,
                name = source.name,
                origPath = source.absolutePath,
                deletedAt = System.currentTimeMillis(),
                size = size,
                isDir = isDir
            )
        )
        writeAll(context, items)
        null
    }.getOrElse { it.message ?: "移动到回收站失败" }

    /** 还原到原路径；原路径已有同名项时追加 (1) */
    fun restore(context: Context, item: TrashItem): Boolean = runCatching {
        val items = readAll(context)
        val stored = File(root(context), item.id + "_" + item.name)
        if (!stored.exists()) {
            writeAll(context, items.filterNot { it.id == item.id })
            return false
        }
        val dest = uniqueTarget(File(item.origPath))
        dest.parentFile?.mkdirs()
        val ok = stored.renameTo(dest) || run {
            if (item.isDir) copyDir(stored, dest) else copyFile(stored, dest)
            deleteRecursively(stored).isSuccess
        }
        if (ok) writeAll(context, items.filterNot { it.id == item.id })
        ok
    }.getOrDefault(false)

    fun purge(context: Context, item: TrashItem) {
        runCatching { deleteRecursively(File(root(context), item.id + "_" + item.name)) }
        writeAll(context, readAll(context).filterNot { it.id == item.id })
    }

    fun empty(context: Context) {
        runCatching { root(context).listFiles()?.forEach { deleteRecursively(it) } }
        writeAll(context, emptyList())
    }

    /** 30 天到期清理，应用启动时调用 */
    fun purgeExpired(context: Context, keepDays: Int = KEEP_DAYS) {
        val deadline = System.currentTimeMillis() - keepDays * 86_400_000L
        val items = readAll(context)
        val stale = items.filter { it.deletedAt in 1 until deadline }
        if (stale.isEmpty()) return
        stale.forEach { runCatching { deleteRecursively(File(root(context), it.id + "_" + it.name)) } }
        writeAll(context, items.filterNot { it.id in stale.map { s -> s.id } })
    }

    private fun uniqueTarget(target: File): File {
        if (!target.exists()) return target
        val base = target.nameWithoutExtension
        val ext = target.extension
        var i = 1
        while (true) {
            val name = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            val f = File(target.parentFile, name)
            if (!f.exists()) return f
            i++
        }
    }

    private fun copyFile(from: File, to: File): Boolean = runCatching {
        to.parentFile?.mkdirs()
        from.inputStream().use { input -> to.outputStream().use { input.copyTo(it, 128 * 1024) } }
        true
    }.getOrDefault(false)

    private fun copyDir(from: File, to: File): Boolean = runCatching {
        to.mkdirs()
        from.listFiles()?.forEach { child ->
            val dest = File(to, child.name)
            if (child.isDirectory) copyDir(child, dest) else copyFile(child, dest)
        }
        true
    }.getOrDefault(false)

    fun deleteRecursively(file: File): Result<Unit> = runCatching {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        if (!file.delete() && file.exists()) error("无法删除 ${file.name}")
    }
}
