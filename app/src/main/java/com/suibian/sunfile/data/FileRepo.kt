package com.suibian.sunfile.data

import android.os.Environment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 目录读取结果：目录受系统限制时给出可读提示，而不是直接抛错 */
data class DirListing(
    val entries: List<FileEntry>,
    val restricted: Boolean = false,
    val message: String? = null
)

/** 文本读取结果，支持超大文件截断与只读 */
data class TextContent(
    val text: String,
    val encoding: String,
    val truncated: Boolean,
    val totalBytes: Long,
    val editable: Boolean
)

/** 文件系统的读写、遍历与增删改 */
object FileRepo {

    /** 可直接编辑的文本上限；超过后进入只读预览，避免 OOM 与输入卡顿 */
    const val TEXT_EDIT_LIMIT = 1024L * 1024
    /** 只读预览时最多载入的字节数 */
    const val TEXT_PREVIEW_BYTES = 256L * 1024
    /** 兼容旧调用 */
    const val MAX_TEXT_BYTES = TEXT_EDIT_LIMIT

    fun rootPath(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext else File("/")
    }

    fun startDir(hasAllFilesAccess: Boolean): File {
        if (hasAllFilesAccess) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null && ext.exists() && ext.canRead()) return ext
            return File("/")
        }
        return File("/storage/emulated/0")
    }

    // ---------------- 目录枚举 ----------------

    fun list(dir: File, showHidden: Boolean, sort: SortSpec): List<FileEntry> =
        listDetailed(dir, showHidden, sort).entries

    /**
     * 读取目录。系统根目录不可读时退化为虚拟根（列出挂载点）；
     * Android/data、/data 等受限目录返回空列表 + 说明，不再抛异常打断导航。
     */
    fun listDetailed(dir: File, showHidden: Boolean, sort: SortSpec): DirListing {
        if (!dir.exists()) return DirListing(emptyList(), true, "目录不存在：${dir.absolutePath}")
        if (!dir.isDirectory) return DirListing(emptyList(), true, "不是目录")

        val names: Array<String>? = try {
            dir.list()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

        if (names == null) {
            if (dir.absolutePath == "/") {
                val virtual = virtualRoot(showHidden)
                if (virtual.isNotEmpty()) {
                    return DirListing(virtual, false, "系统根目录受 SELinux 限制，已列出可访问的挂载点")
                }
            }
            return DirListing(emptyList(), true, restrictedMessage(dir))
        }

        val result = ArrayList<FileEntry>(names.size)
        for (n in names) {
            if (!showHidden && n.startsWith(".")) continue
            val f = File(dir, n)
            val isDir = try { f.isDirectory } catch (e: Exception) { false }
            val len = try { if (isDir) 0L else f.length() } catch (e: Exception) { 0L }
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
        val sorted = sort(result, sort)
        if (sorted.isEmpty() && isSystemRestricted(dir)) {
            return DirListing(emptyList(), true, restrictedMessage(dir))
        }
        if (sorted.isEmpty() && dir.absolutePath == "/storage/emulated/0" && !dir.canWrite()) {
            return DirListing(
                emptyList(),
                true,
                "没有存储访问权限。请点上方「去授权」开启「所有文件访问权限」后重试"
            )
        }
        return DirListing(sorted, false, null)
    }

    /** 根目录被限制时的兜底：列出真实存在的挂载点，不可读的也显示出来（置灰） */
    private fun virtualRoot(showHidden: Boolean): List<FileEntry> {
        val candidates = listOf(
            "/storage/emulated/0", "/sdcard", "/storage", "/mnt", "/system", "/vendor",
            "/product", "/apex", "/data", "/dev", "/proc", "/sys", "/etc"
        )
        val result = ArrayList<FileEntry>()
        for (p in candidates) {
            val f = File(p)
            if (!f.exists()) continue
            val readable = runCatching { f.list() != null }.getOrDefault(false)
            result.add(
                FileEntry(
                    file = f,
                    name = p,
                    isDir = true,
                    size = 0L,
                    lastModified = runCatching { f.lastModified() }.getOrDefault(0L),
                    isHidden = false,
                    canRead = readable,
                    canWrite = runCatching { f.canWrite() }.getOrDefault(false)
                )
            )
        }
        return if (showHidden) result else result
    }

    private fun isSystemRestricted(dir: File): Boolean {
        val p = dir.absolutePath
        if (p.contains("/Android/data") || p.contains("/Android/obb")) return true
        if (p == "/data" || p == "/data/app" || p == "/data/system" || p == "/data/misc" ||
            p == "/data/dalvik-cache" || p == "/data/vendor" || p == "/data/adb"
        ) return true
        if (p == "/proc" || p == "/sys" || p == "/system" || p == "/vendor" || p == "/product") return true
        return false
    }

    fun restrictedMessage(dir: File): String {
        val p = dir.absolutePath
        return when {
            p.contains("/Android/data") || p.contains("/Android/obb") ->
                "Android 11 起系统禁止访问其他应用的 data/obb 目录（MT 管理器同样受限，需要 root）"
            p == "/data" || p.startsWith("/data/") ->
                "该目录属于系统数据分区，需要 root 权限才能读取"
            p == "/system" || p.startsWith("/system/") || p.startsWith("/vendor") ->
                "系统分区为只读，普通应用无法列出内容"
            p == "/proc" || p == "/sys" || p.startsWith("/proc/") || p.startsWith("/sys/") ->
                "内核虚拟文件系统，普通应用无权访问"
            p == "/storage/emulated/0" || p.startsWith("/storage/emulated/0/") ->
                "未获得存储访问权限。请点上方「去授权」开启「所有文件访问权限」后重试"
            else -> "没有读取权限：$p"
        }
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
        if (!f.mkdirs()) throw IOException("创建文件夹失败，请检查存储权限")
        return f
    }

    fun createFile(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("已存在同名项")
        if (!f.createNewFile()) throw IOException("创建文件失败，请检查存储权限")
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
                    val buf = ByteArray(256 * 1024)
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

    fun move(src: File, destDir: File, onProgress: (String, Long, Long) -> Unit) {
        val target = uniqueTarget(destDir, src.name)
        if (src.renameTo(target)) {
            onProgress(src.name, 1, 1)
            return
        }
        copy(src, destDir, onProgress)
        delete(src)
    }

    // ---------------- 文本读写 ----------------

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "xml", "yml", "yaml", "ini", "conf", "cfg",
        "properties", "log", "csv", "tsv", "html", "htm", "css", "scss", "less", "js", "mjs",
        "ts", "jsx", "tsx", "vue", "java", "kt", "kts", "gradle", "groovy", "c", "h",
        "cpp", "hpp", "cc", "py", "sh", "bash", "zsh", "bat", "cmd", "ps1", "sql", "toml",
        "lock", "gitignore", "pro", "rc", "env", "php", "rb", "go", "rs", "swift",
        "m", "mm", "srt", "vtt", "tex", "asm", "s", "mk", "makefile", "patch", "diff",
        "smali", "lua", "dart", "r", "pl", "vim", "nginx", "service", "auth", "desktop",
        "proto", "graphql", "gql", "svelte", "astro", "plist", "xhtml", "editorconfig"
    )

    private val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "tiff",
        "mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "amr", "mp4", "mkv", "avi", "mov",
        "webm", "3gp", "flv", "m4v", "wmv", "zip", "rar", "7z", "gz", "tar", "bz2", "xz", "zst",
        "so", "dex", "jar", "apk", "xapk", "apks", "aar", "ttf", "otf", "woff", "woff2", "pdf",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "db", "sqlite", "bin", "class", "elf",
        "iso", "img", "vmdk", "bak", "dat", "pak", "obb", "wasm", "pyc", "o", "a", "lib"
    )

    /** 判断是否值得用内置编辑器打开：扩展名白名单，或文件头无 NUL 字节 */
    fun looksLikeText(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = file.extension.lowercase()
        if (ext in TEXT_EXTENSIONS) return true
        if (ext in BINARY_EXTENSIONS) return false
        if (file.length() > 4L * 1024 * 1024) return false
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
        if (file.length() > TEXT_EDIT_LIMIT) {
            throw IOException("文件超过 ${TEXT_EDIT_LIMIT / 1024 / 1024}MB，已切换为大文件只读模式")
        }
        return decodeText(file.readBytes(), file.length().toInt()).first
    }

    /**
     * 智能读取：小文件全量可编辑，大文件只读预览前 1MB，超大文件预览前 256KB。
     */
    fun readTextSmart(file: File): TextContent {
        val size = file.length()
        if (size <= TEXT_EDIT_LIMIT) {
            val bytes = file.readBytes()
            val (text, enc) = decodeText(bytes, size.toInt())
            return TextContent(text, enc, false, size, true)
        }
        val cap = when {
            size <= 16L * 1024 * 1024 -> TEXT_PREVIEW_BYTES
            size <= 128L * 1024 * 1024 -> 128L * 1024
            else -> 64L * 1024
        }
        val bytes = ByteArray(cap.toInt())
        var read = 0
        FileInputStream(file).use { input ->
            while (read < bytes.size) {
                val r = input.read(bytes, read, bytes.size - read)
                if (r <= 0) break
                read += r
            }
        }
        var len = read
        // 截断可能切在多字节字符中间，逐字节回退直到能按 UTF-8 解码
        var decoded: Pair<String, String>? = null
        while (len > 0 && decoded == null) {
            decoded = decodeTextStrict(bytes, len) ?: run { len--; null }
            if (read - len > 4) break
        }
        val (text, enc) = decoded ?: decodeText(bytes, read)
        return TextContent(text, enc, true, size, false)
    }

    fun writeText(file: File, text: String, encoding: String = "UTF-8") {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val cs = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
        file.writeBytes(text.toByteArray(cs))
    }

    private fun decodeText(bytes: ByteArray, length: Int): Pair<String, String> {
        decodeTextStrict(bytes, length)?.let { return it }
        // 非 UTF-8，按 GBK 兜底（中文文本常见）
        val gbk = runCatching { String(bytes, 0, length, Charset.forName("GBK")) }.getOrNull()
        if (gbk != null) return gbk to "GBK"
        return String(bytes, 0, length, Charsets.UTF_8) to "UTF-8"
    }

    private fun decodeTextStrict(bytes: ByteArray, length: Int): Pair<String, String>? {
        if (length >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, length - 3, Charsets.UTF_8) to "UTF-8"
        }
        if (length >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, length - 2, Charsets.UTF_16LE) to "UTF-16LE"
        }
        if (length >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, length - 2, Charsets.UTF_16BE) to "UTF-16BE"
        }
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes, 0, length)).toString() to "UTF-8"
        } catch (e: CharacterCodingException) {
            null
        }
    }

    // ---------------- 十六进制 ----------------

    /** 读取任意偏移的字节块，超大文件也不会占满内存 */
    fun readChunk(file: File, offset: Long, length: Int): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            if (offset >= size) return ByteArray(0)
            val toRead = minOf(length.toLong(), size - offset).toInt()
            raf.seek(offset)
            val buf = ByteArray(toRead)
            var read = 0
            while (read < toRead) {
                val r = raf.read(buf, read, toRead - read)
                if (r <= 0) break
                read += r
            }
            return if (read == toRead) buf else buf.copyOf(read)
        }
    }

    // ---------------- 压缩包 ----------------

    data class ZipItem(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val compressed: Long,
        val time: Long
    )

    fun zipEntries(file: File): List<ZipItem> = ZipFile(file).use { zf ->
        val list = ArrayList<ZipItem>()
        val e = zf.entries()
        while (e.hasMoreElements()) {
            val entry = e.nextElement()
            list.add(
                ZipItem(
                    name = entry.name,
                    isDir = entry.isDirectory,
                    size = entry.size,
                    compressed = entry.compressedSize,
                    time = entry.time
                )
            )
        }
        list
    }

    fun readZipEntry(file: File, name: String, maxBytes: Long = 512 * 1024): ByteArray =
        ZipFile(file).use { zf ->
            val entry = zf.getEntry(name) ?: throw IOException("压缩包内不存在该文件")
            zf.getInputStream(entry).use { input ->
                val limit = minOf(entry.size.coerceAtLeast(0), maxBytes).toInt()
                val buf = ByteArray(if (limit > 0) limit else 64 * 1024)
                var read = 0
                while (read < buf.size) {
                    val r = input.read(buf, read, buf.size - read)
                    if (r <= 0) break
                    read += r
                }
                if (read == buf.size) buf else buf.copyOf(read)
            }
        }

    /** 解压单个条目到目标目录 */
    fun extractZipEntry(zipFile: File, entryName: String, destDir: File): File {
        ZipFile(zipFile).use { zf ->
            val entry = zf.getEntry(entryName) ?: throw IOException("压缩包内不存在该文件")
            val name = entryName.substringAfterLast('/').ifEmpty { "entry" }
            val out = uniqueTarget(destDir, name)
            zf.getInputStream(entry).use { input ->
                BufferedOutputStream(FileOutputStream(out)).use { output ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        output.write(buf, 0, r)
                    }
                }
            }
            return out
        }
    }

    /** 解压全部，带路径穿越防护 */
    fun extractZip(zipFile: File, destDir: File, onProgress: (String, Int, Int) -> Unit): Int {
        val destCanonical = destDir.canonicalFile
        var count = 0
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            val total = zf.size()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(destCanonical.path + File.separator) &&
                    out.canonicalPath != destCanonical.path
                ) {
                    throw IOException("压缩包包含非法路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        BufferedOutputStream(FileOutputStream(out)).use { output ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val r = input.read(buf)
                                if (r <= 0) break
                                output.write(buf, 0, r)
                            }
                        }
                    }
                    runCatching { out.setLastModified(entry.time) }
                }
                count++
                onProgress(entry.name, count, total)
            }
        }
        return count
    }

    fun createZip(sources: List<File>, dest: File, onProgress: (String, Int, Int) -> Unit): Int {
        val total = sources.sumOf { if (it.isDirectory) countChildren(it).second else 1 }.coerceAtLeast(1)
        var done = 0
        val used = HashSet<String>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zos ->
            for (src in sources) {
                val base = uniqueZipName(used, src.name)
                addToZip(zos, src, base) {
                    done++
                    onProgress(it, done, total)
                }
            }
        }
        return done
    }

    private fun uniqueZipName(used: MutableSet<String>, name: String): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (used.add(candidate)) return candidate
            i++
        }
    }

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String, onFile: (String) -> Unit) {
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryName/").apply { time = file.lastModified() })
            zos.closeEntry()
            val children = file.listFiles() ?: return
            for (c in children) addToZip(zos, c, "$entryName/${c.name}", onFile)
        } else {
            zos.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    zos.write(buf, 0, r)
                }
            }
            zos.closeEntry()
            onFile(entryName)
        }
    }

    // ---------------- 校验和 ----------------

    fun checksum(file: File, algorithm: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        val md = MessageDigest.getInstance(algorithm)
        val total = file.length()
        var read = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
                read += r
                onProgress(read, total)
            }
        }
        return md.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    // ---------------- 搜索 ----------------

    fun search(
        root: File,
        query: String,
        maxResults: Int = 300,
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
            if (cancelled() || results.size >= maxResults || scanned > 120_000) break
            val dir = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (c in children) {
                scanned++
                val name = c.name
                if (name.lowercase().contains(keyword)) {
                    val isDir = runCatching { c.isDirectory }.getOrDefault(false)
                    results.add(
                        FileEntry(
                            file = c,
                            name = name,
                            isDir = isDir,
                            size = if (isDir) 0L else runCatching { c.length() }.getOrDefault(0L),
                            lastModified = runCatching { c.lastModified() }.getOrDefault(0L),
                            isHidden = name.startsWith("."),
                            canRead = runCatching { c.canRead() }.getOrDefault(false),
                            canWrite = runCatching { c.canWrite() }.getOrDefault(false)
                        )
                    )
                    if (results.size >= maxResults) break
                }
                if (runCatching { c.isDirectory }.getOrDefault(false)) queue.addLast(c)
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

    fun mimeOf(file: File, fallback: String = "*/*"): String {
        val ext = file.extension.lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallback
    }
}
