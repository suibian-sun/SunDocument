package com.suibian.sunfile.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 压缩包的写入侧：创建 zip / tar / tar.gz，以及 zip 内的增、删、改名。
 *
 * zip 的增删改统一走「重写」：读原包、逐条写给临时文件、最后原子替换。
 * 对常见的几十 MB 包耗时在百毫秒级，换来的是不会把包写坏。
 */
object ArchiveEdit {

    enum class Format(val label: String, val ext: String) {
        ZIP("ZIP", "zip"),
        TAR("TAR", "tar"),
        TAR_GZ("TAR.GZ", "tar.gz")
    }

    fun formatOf(name: String): Format = when {
        name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true) -> Format.TAR_GZ
        name.endsWith(".tar", true) -> Format.TAR
        else -> Format.ZIP
    }

    // ---------------- 创建 ----------------

    fun create(
        sources: List<File>,
        dest: File,
        format: Format,
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Int {
        val destPath = runCatching { dest.canonicalPath }.getOrDefault(dest.absolutePath)
        val entries = ArrayList<Triple<String, File, Boolean>>()
        for (src in sources) {
            collect(src, src.name, entries, destPath, 0)
        }
        if (entries.isEmpty()) return 0
        val total = entries.size
        var done = 0
        when (format) {
            Format.ZIP -> ZipOutputStream(BufferedOutputStream(FileOutputStream(dest), 64 * 1024)).use { zos ->
                for ((name, file, isDir) in entries) {
                    val entryName = if (isDir) "$name/" else name
                    zos.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                    if (!isDir) file.inputStream().use { it.copyTo(zos, 64 * 1024) }
                    zos.closeEntry()
                    onProgress(name, ++done, total)
                }
            }
            Format.TAR, Format.TAR_GZ -> {
                val raw = BufferedOutputStream(FileOutputStream(dest), 64 * 1024)
                val out: java.io.OutputStream = if (format == Format.TAR_GZ) GZIPOutputStream(raw, 64 * 1024) else raw
                out.use { stream ->
                    for ((name, file, isDir) in entries) {
                        writeTarEntry(stream, if (isDir) "$name/" else name, file, isDir)
                        onProgress(name, ++done, total)
                    }
                    // 两个全零块作为结束标记
                    stream.write(ByteArray(1024))
                }
            }
        }
        return done
    }

    private fun collect(
        file: File,
        entryName: String,
        out: MutableList<Triple<String, File, Boolean>>,
        skipPath: String,
        depth: Int
    ) {
        if (depth > 32) return
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        if (path == skipPath) return
        if (file.isDirectory) {
            out.add(Triple(entryName, file, true))
            file.listFiles()?.sortedBy { it.name }?.forEach { child ->
                collect(child, "$entryName/${child.name}", out, skipPath, depth + 1)
            }
        } else {
            out.add(Triple(entryName, file, false))
        }
    }

    private fun writeTarEntry(out: java.io.OutputStream, name: String, file: File, isDir: Boolean) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 100) {
            // GNU long name：先写一个 'L' 头，再写真实头
            writeTarHeader(out, "././@LongLink", nameBytes.size.toLong(), 'L', 0L)
            out.write(nameBytes)
            val pad = (512 - nameBytes.size % 512) % 512
            if (pad > 0) out.write(ByteArray(pad))
        }
        val shortName = if (nameBytes.size > 100) name.takeLast(90) else name
        val size = if (isDir) 0L else file.length()
        writeTarHeader(out, shortName, size, if (isDir) '5' else '0', file.lastModified() / 1000)
        if (!isDir) {
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var written = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                }
                val pad = ((512 - written % 512) % 512).toInt()
                if (pad > 0) out.write(ByteArray(pad))
            }
        }
    }

    private fun writeTarHeader(out: java.io.OutputStream, name: String, size: Long, type: Char, mtime: Long) {
        val h = ByteArray(512)
        fun put(offset: Int, text: String, len: Int) {
            val b = text.toByteArray(Charsets.UTF_8)
            System.arraycopy(b, 0, h, offset, minOf(b.size, len))
        }
        put(0, name, 100)
        put(100, "0000644\u0000", 8)
        put(108, "0000000\u0000", 8)
        put(116, "0000000\u0000", 8)
        put(124, String.format("%011o", size), 12)
        put(136, String.format("%011o", mtime.coerceAtLeast(0)), 12)
        h[156] = type.code.toByte()
        put(257, "ustar", 6)
        put(263, "00", 2)
        put(265, "root", 32)
        put(297, "root", 32)
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        var sum = 0L
        for (b in h) sum += (b.toInt() and 0xFF)
        val chk = String.format("%06o", sum) + "\u0000 "
        put(148, chk, 8)
        out.write(h)
    }

    // ---------------- zip 内编辑 ----------------

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
        val entries = ArrayList<Triple<String, File, Boolean>>()
        for (src in sources) collect(src, src.name, entries, destPath, 0)
        val additions = entries.mapNotNull { (name, file, isDir) ->
            if (isDir) null else name to file.readBytes()
        }
        return if (additions.isEmpty()) 0 else {
            val ok = rewriteZip(zip, drop = emptySet(), rename = emptyMap(), replaced = emptyMap(), additions = additions)
            if (ok) additions.size else 0
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
