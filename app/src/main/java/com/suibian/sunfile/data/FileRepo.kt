package com.suibian.sunfile.data

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** 文件系统的读写、遍历与增删改 */
object FileRepo {

    fun rootPath(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext else File("/")
    }

    /** 首次启动进入的目录 */
    fun startDir(hasAllFilesAccess: Boolean): File {
        if (hasAllFilesAccess) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null && ext.exists() && ext.canRead()) return ext
            return File("/")
        }
        return File("/storage/emulated/0")
    }

    fun list(dir: File, showHidden: Boolean, sort: SortSpec): List<FileEntry> {
        if (!dir.exists()) throw IOException("目录不存在：${dir.absolutePath}")
        if (!dir.isDirectory) throw IOException("不是目录：${dir.absolutePath}")
        val names = dir.list() ?: throw IOException("无法读取目录（无权限）：${dir.absolutePath}")
        val result = ArrayList<FileEntry>(names.size)
        for (n in names) {
            if (!showHidden && n.startsWith(".")) continue
            val f = File(dir, n)
            val isDir = f.isDirectory
            val len = try {
                if (isDir) 0L else f.length()
            } catch (e: Exception) {
                0L
            }
            result.add(
                FileEntry(
                    file = f,
                    name = n,
                    isDir = isDir,
                    size = len,
                    lastModified = try { f.lastModified() } catch (e: Exception) { 0L },
                    isHidden = n.startsWith(".") || try { f.isHidden } catch (e: Exception) { false },
                    canRead = try { f.canRead() } catch (e: Exception) { false },
                    canWrite = try { f.canWrite() } catch (e: Exception) { false }
                )
            )
        }
        return sort(result, sort)
    }

    fun sort(items: List<FileEntry>, spec: SortSpec): List<FileEntry> {
        val cmp = when (spec.mode) {
            SortMode.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.DATE -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy<FileEntry> { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy { it.name.lowercase() }
        }
        val sorted = if (spec.ascending) items.sortedWith(cmp) else items.sortedWith(cmp.reversed())
        // 目录始终排在文件前面，与 MT 管理器行为一致
        return sorted.sortedBy { if (it.isDir) 0 else 1 }
    }

    fun dirSize(file: File): Long {
        if (!file.exists()) return 0
        if (file.isFile) return file.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(file)
        var guard = 0
        while (stack.isNotEmpty() && guard < 200_000) {
            guard++
            val d = stack.removeLast()
            val children = d.listFiles() ?: continue
            for (c in children) {
                if (c.isDirectory) stack.addLast(c) else total += c.length()
            }
        }
        return total
    }

    fun countChildren(file: File): Pair<Int, Int> {
        var dirs = 0
        var files = 0
        val stack = ArrayDeque<File>()
        stack.addLast(file)
        var guard = 0
        while (stack.isNotEmpty() && guard < 200_000) {
            guard++
            val d = stack.removeLast()
            val children = d.listFiles() ?: continue
            for (c in children) {
                if (c.isDirectory) {
                    dirs++
                    stack.addLast(c)
                } else files++
            }
        }
        return dirs to files
    }

    /** 目标目录已存在同名项时，生成 "名称 (1).ext" 形式的可用文件名 */
    fun uniqueTarget(destDir: File, name: String): File {
        var target = File(destDir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (i < 10_000) {
            target = File(destDir, "$base ($i)$ext")
            if (!target.exists()) return target
            i++
        }
        return File(destDir, "$base (${System.currentTimeMillis()})$ext")
    }

    fun createDir(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("已存在同名项")
        if (!f.mkdirs()) throw IOException("创建文件夹失败")
        return f
    }

    fun createFile(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("已存在同名项")
        if (!f.createNewFile()) throw IOException("创建文件失败")
        return f
    }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        if (target.exists() && target.absolutePath != file.absolutePath) throw IOException("目标名称已存在")
        if (!file.renameTo(target)) throw IOException("重命名失败")
        return target
    }

    fun delete(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) for (c in children) delete(c)
        }
        if (!file.delete()) throw IOException("删除失败：${file.name}")
    }

    /**
     * 复制文件或目录到目标目录，回调进度。
     * @param onProgress (当前文件相对路径, 已完成字节, 总字节)
     */
    fun copy(src: File, destDir: File, onProgress: (String, Long, Long) -> Unit) {
        val total = if (src.isFile) src.length() else dirSize(src)
        copyRec(src, destDir, total, LongArray(1), onProgress)
        onProgress(src.name, total, total)
    }

    private fun copyRec(
        src: File,
        destDir: File,
        total: Long,
        counter: LongArray,
        onProgress: (String, Long, Long) -> Unit
    ) {
        if (src.isDirectory) {
            val target = uniqueTarget(destDir, src.name)
            if (!target.mkdirs() && !target.isDirectory) throw IOException("无法创建目录 ${target.name}")
            val children = src.listFiles() ?: return
            for (c in children) copyRec(c, target, total, counter, onProgress)
        } else {
            val target = uniqueTarget(destDir, src.name)
            FileInputStream(src).use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        counter[0] = counter[0] + read
                        onProgress(src.name, counter[0], total)
                    }
                    output.flush()
                }
            }
            target.setLastModified(src.lastModified())
        }
    }

    /**
     * 移动；同分区直接 rename，跨分区退化为复制 + 删除。
     */
    fun move(src: File, destDir: File, onProgress: (String, Long, Long) -> Unit) {
        val target = uniqueTarget(destDir, src.name)
        if (src.renameTo(target)) {
            onProgress(src.name, 1, 1)
            return
        }
        copy(src, destDir, onProgress)
        delete(src)
    }


    // ---------------- 文本读写与搜索 ----------------

    const val MAX_TEXT_BYTES = 3L * 1024 * 1024

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "xml", "yml", "yaml", "ini", "conf", "cfg",
        "properties", "log", "csv", "tsv", "html", "htm", "css", "scss", "js", "mjs",
        "ts", "jsx", "tsx", "vue", "java", "kt", "kts", "gradle", "groovy", "c", "h",
        "cpp", "hpp", "cc", "py", "sh", "bash", "zsh", "bat", "ps1", "sql", "toml",
        "lock", "gitignore", "pro", "rc", "env", "php", "rb", "go", "rs", "swift",
        "m", "mm", "srt", "vtt", "tex", "asm", "s", "mk", "makefile", "patch", "diff",
        "smali", "lua", "dart", "r", "pl", "vim", "nginx", "service"
    )

    /** 判断是否值得用内置编辑器打开：扩展名白名单，或文件头无 NUL 字节 */
    fun looksLikeText(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = file.extension.lowercase()
        if (ext in TEXT_EXTENSIONS) return true
        if (file.length() > MAX_TEXT_BYTES) return false
        if (ext.isNotEmpty()) {
            // 已知二进制扩展名直接排除
            if (ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "mp3", "mp4", "mkv", "avi",
                    "zip", "rar", "7z", "gz", "tar", "so", "dex", "jar", "ttf", "otf", "woff", "pdf",
                    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "db", "sqlite", "bin", "class")
            ) return false
        }
        return runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(4096)
                val read = input.read(buf)
                if (read <= 0) return true
                for (i in 0 until read) if (buf[i] == 0.toByte()) return false
                true
            }
        }.getOrDefault(false)
    }

    fun readText(file: File): String {
        if (file.length() > MAX_TEXT_BYTES) {
            throw IOException("文件超过 ${MAX_TEXT_BYTES / 1024 / 1024}MB，请用其他编辑器打开")
        }
        return file.readText()
    }

    fun writeText(file: File, text: String) {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeText(text)
    }

    /**
     * 递归搜索文件名，广度优先，带访问上限与进度回调。
     */
    fun search(
        root: File,
        query: String,
        maxResults: Int = 200,
        onProgress: (scanned: Int, current: String) -> Unit,
        cancelled: () -> Boolean
    ): List<FileEntry> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return emptyList()
        val results = ArrayList<FileEntry>()
        val queue = ArrayDeque<File>()
        queue.addLast(root)
        var scanned = 0
        var tick = 0L
        while (queue.isNotEmpty()) {
            if (cancelled() || results.size >= maxResults || scanned > 60_000) break
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (c in children) {
                scanned++
                if (c.name.lowercase().contains(keyword)) {
                    results.add(
                        FileEntry(
                            file = c,
                            name = c.name,
                            isDir = c.isDirectory,
                            size = if (c.isDirectory) 0L else c.length(),
                            lastModified = c.lastModified(),
                            isHidden = c.name.startsWith("."),
                            canRead = c.canRead(),
                            canWrite = c.canWrite()
                        )
                    )
                    if (results.size >= maxResults) break
                }
                if (c.isDirectory) queue.addLast(c)
            }
            val now = System.currentTimeMillis()
            if (now - tick > 120) {
                tick = now
                onProgress(scanned, dir.absolutePath)
            }
        }
        onProgress(scanned, "")
        return results
    }

    /** 把相对路径显示出来（搜索结果用） */
    fun relativeTo(rootPath: String, childPath: String): String {
        if (childPath.startsWith(rootPath)) {
            val rel = childPath.removePrefix(rootPath).removePrefix("/")
            return if (rel.isEmpty()) childPath else rel
        }
        return childPath
    }

    fun isInside(parent: File, child: File): Boolean {
        val p = try { parent.canonicalPath } catch (e: Exception) { parent.absolutePath }
        val c = try { child.canonicalPath } catch (e: Exception) { child.absolutePath }
        return c == p || c.startsWith(p + File.separator)
    }
}
