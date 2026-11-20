package com.suibian.sunfile.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * zip 内的增、删、改名。
 *
 * 统一走「重写」：读原包、逐条写给临时文件、最后原子替换。
 * 对常见的几十 MB 包耗时在百毫秒级，换来的是不会把包写坏。
 * 新建压缩包由 [Archives.create] 负责（支持 7z / 密码 / 压缩级别）。
 */
object ArchiveEdit {

    fun zipDelete(zip: File, names: Set<String>): Boolean =
        rewriteZip(zip, drop = names, rename = emptyMap(), replaced = emptyMap(), additions = emptyList())

    fun zipRename(zip: File, oldName: String, newName: String): Boolean {
        if (newName.isBlank() || newName == oldName) return false
        return rewriteZip(zip, drop = emptySet(), rename = mapOf(oldName to newName), replaced = emptyMap(), additions = emptyList())
    }

    fun zipPut(zip: File, entryName: String, bytes: ByteArray): Boolean =
        rewriteZip(zip, drop = emptySet(), rename = emptyMap(), replaced = mapOf(entryName to bytes), additions = emptyList())

    fun zipAdd(zip: File, sources: List<File>): Int {
        val destPath = runCatching { zip.canonicalPath }.getOrDefault(zip.absolutePath)
        val entries = ArrayList<Pair<String, File>>()
        for (src in sources) collect(src, src.name, entries, destPath, 0)
        val additions = entries.map { (name, file) -> name to file.readBytes() }
        return if (additions.isEmpty()) 0 else {
            val ok = rewriteZip(zip, drop = emptySet(), rename = emptyMap(), replaced = emptyMap(), additions = additions)
            if (ok) additions.size else 0
        }
    }

    private fun collect(
        file: File,
        entryName: String,
        out: MutableList<Pair<String, File>>,
        skipPath: String,
        depth: Int
    ) {
        if (depth > 32) return
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        if (path == skipPath) return
        if (file.isDirectory) {
            file.listFiles()?.sortedBy { it.name }?.forEach { child ->
                collect(child, "$entryName/${child.name}", out, skipPath, depth + 1)
            }
        } else {
            out.add(entryName to file)
        }
    }

    private fun rewriteZip(
        zip: File,
        drop: Set<String>,
        rename: Map<String, String>,
        replaced: Map<String, ByteArray>,
        additions: List<Pair<String, ByteArray>>
    ): Boolean {
        val parent = zip.parentFile ?: return false
        val temp = File(parent, "${zip.name}.tmp${System.nanoTime() % 1000}")
        var ok = true
        runCatching {
            ZipFile(zip).use { zf ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(temp), 64 * 1024)).use { out ->
                    val used = HashSet<String>()
                    val droppedPrefixes = drop.filter { it.endsWith("/") }
                    for (entry in zf.entries()) {
                        val name = entry.name
                        if (name in drop) continue
                        if (droppedPrefixes.any { name.startsWith(it) && name != it }) continue
                        val target = rename[name] ?: name
                        if (!used.add(target)) continue
                        val newEntry = ZipEntry(target).apply { time = entry.time }
                        out.putNextEntry(newEntry)
                        if (!entry.isDirectory) {
                            val swap = replaced[target]
                            if (swap != null) {
                                out.write(swap)
                            } else {
                                zf.getInputStream(entry).use { copy(it, out) }
                            }
                        }
                        out.closeEntry()
                    }
                    for ((name, bytes) in replaced) {
                        if (used.contains(name)) continue
                        out.putNextEntry(ZipEntry(name))
                        out.write(bytes)
                        out.closeEntry()
                        used.add(name)
                    }
                    for ((name, bytes) in additions) {
                        if (!used.add(name)) continue
                        out.putNextEntry(ZipEntry(name))
                        out.write(bytes)
                        out.closeEntry()
                    }
                }
            }
            ok = temp.length() > 0 && temp.renameTo(zip)
        }.onFailure { ok = false }
        if (!ok) temp.delete()
        return ok
    }

    private fun copy(input: InputStream, out: java.io.OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
    }
}
