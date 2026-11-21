package com.suibian.sunfile.data

import android.os.Environment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import android.system.Os
import android.system.OsConstants
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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 目录读取结果：目录受系统限制时给出可读提示，而不是直接抛错 */
data class DirListing(
    val entries: List<FileEntry>,
    val restricted: Boolean = false,
    val message: String? = null
)

/** 文本读取结果：编辑器首屏的内容，超出部分按需继续追加 */
data class TextContent(
    val text: String,
    val encoding: String,
    val truncated: Boolean,
    val totalBytes: Long,
    val editable: Boolean,
    val loadedBytes: Long = 0,
    val partial: Boolean = false,
    val bom: Boolean = false
)

/** 逐段读取的一段文本：从文件偏移 from 到 to，拼起来就是完整原文 */
data class TextWindow(
    val text: String,
    val encoding: String,
    val from: Long,
    val to: Long,
    val totalBytes: Long,
    val partial: Boolean,
    val bom: Boolean = false
)

/** 文件系统的读写、遍历与增删改 */
object FileRepo {

    /**
     * 一次性载入输入框的文本上限。超过此值改用逐段加载，
     * 因为 Compose 对超大文本的排版开销会直接拖垮滚动，而不是内存不够。
     */
    const val TEXT_EDIT_LIMIT = 8L * 1024 * 1024
    /** 编辑器首屏与每次追加的段大小 */
    const val EDITOR_WINDOW = 64 * 1024
    /** 旧常量保留，供历史代码引用 */
    const val TEXT_PREVIEW_BYTES = TEXT_EDIT_LIMIT
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
     * 读取目录。普通 API 受限时自动改用 root shell 枚举真实内容；
     * 两者都不可用时返回空列表与一句原因，不再伪造挂载点条目。
     */
    fun listDetailed(dir: File, showHidden: Boolean, sort: SortSpec): DirListing {
        if (!dir.exists() && !Shell.rootAvailable) {
            return DirListing(emptyList(), true, "目录不存在")
        }
        if (dir.exists() && !dir.isDirectory) return DirListing(emptyList(), true, "不是目录")

        // 根目录与系统分区：普通 API 要么直接失败，要么只吐出零星几条（隐藏了大部分）。
        // 既然已经拿到了提权通道，就直接用它读，避免出现「看着有内容其实不全」的假目录。
        if (Shell.rootAvailable && (dir.absolutePath == "/" || isSystemRestricted(dir))) {
            val viaShell = Shell.list(dir.absolutePath, showHidden)
            if (viaShell != null) return DirListing(sort(viaShell, sort), false, null)
        }

        val names: Array<String>? = try {
            dir.list()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

        if (names == null) {
            // 系统限制 → 交给 root 拿真实数据
            val viaRoot = if (Shell.rootAvailable) Shell.list(dir.absolutePath, showHidden) else null
            if (viaRoot != null) return DirListing(sort(viaRoot, sort), false, null)
            return DirListing(emptyList(), true, restrictedMessage(dir))
        }

        val result = ArrayList<FileEntry>(names.size)
        for (n in names) {
            if (!showHidden && n.startsWith(".")) continue
            val f = File(dir, n)
            // 一次 stat 同时拿到类型 / 大小 / 修改时间，避免每项 3 次独立系统调用。
            // 千级目录下这一处能省下几千次 syscall，滚动前的等待明显缩短。
            val st = runCatching { Os.stat(f.absolutePath) }.getOrNull()
            val isDir = if (st != null) {
                (st.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFDIR
            } else {
                try { f.isDirectory } catch (e: Exception) { false }
            }
            val len = when {
                isDir -> 0L
                st != null -> st.st_size
                else -> try { f.length() } catch (e: Exception) { 0L }
            }
            val mtime = when {
                st != null -> st.st_mtime * 1000L
                else -> try { f.lastModified() } catch (e: Exception) { 0L }
            }
            val ctime = when {
                st != null -> st.st_ctime * 1000L
                else -> 0L
            }
            result.add(
                FileEntry(
                    file = f,
                    name = n,
                    isDir = isDir,
                    size = len,
                    lastModified = mtime,
                    created = ctime,
                    isHidden = n.startsWith("."),
                    canRead = try { f.canRead() } catch (e: Exception) { false },
                    canWrite = try { f.canWrite() } catch (e: Exception) { false }
                )
            )
        }
        if (result.isEmpty()) {
            // 空可能是权限导致的"假空目录"，用 root 复核一次
            val viaRoot = if (Shell.rootAvailable) Shell.list(dir.absolutePath, showHidden) else null
            if (viaRoot != null && viaRoot.isNotEmpty()) return DirListing(sort(viaRoot, sort), false, null)
        }
        val sorted = sort(result, sort)
        if (sorted.isEmpty() && isSystemRestricted(dir)) {
            return DirListing(emptyList(), true, restrictedMessage(dir))
        }
        if (sorted.isEmpty() && dir.absolutePath == "/storage/emulated/0" && !dir.canWrite()) {
            return DirListing(emptyList(), true, "没有存储访问权限")
        }
        return DirListing(sorted, false, null)
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
        val base = when {
            p.contains("/Android/data") || p.contains("/Android/obb") -> "需要 Root 才能读取应用数据目录"
            p == "/data" || p.startsWith("/data/") -> "需要 Root 才能读取系统数据分区"
            p.startsWith("/system") || p.startsWith("/vendor") -> "需要 Root 才能读取系统分区"
            p.startsWith("/proc") || p.startsWith("/sys") -> "需要 Root 才能读取内核文件系统"
            p == "/storage/emulated/0" || p.startsWith("/storage/emulated/0/") -> "缺少存储访问权限"
            else -> "没有读取权限"
        }
        // 还没开任何通道时，明确告诉用户去哪儿开；开了 Shizuku 仍读不到，说明确实要 Root
        return when (Shell.engine) {
            ShellEngine.NONE -> "$base · 可在侧边栏「工具 → 更多工具」或设置里启用 Shizuku"
            ShellEngine.SHIZUKU -> "$base（Shizuku 是 shell 身份，此目录需要真正的 Root）"
            ShellEngine.SU -> base
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
        if (!f.mkdirs()) {
            if (Shell.rootAvailable && Shell.mkdir(f.absolutePath)) return f
            throw IOException("创建文件夹失败，请检查存储权限")
        }
        return f
    }

    fun createFile(parent: File, name: String): File {
        val f = File(parent, name)
        if (f.exists()) throw IOException("已存在同名项")
        if (!f.createNewFile()) {
            if (Shell.rootAvailable && Shell.writeAll(f.absolutePath, ByteArray(0))) return f
            throw IOException("创建文件失败，请检查存储权限")
        }
        return f
    }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        if (target.exists() && target.absolutePath != file.absolutePath) throw IOException("目标名称已存在")
        if (!file.renameTo(target)) {
            if (Shell.rootAvailable && Shell.rename(file.absolutePath, target.absolutePath)) return target
            throw IOException("重命名失败")
        }
        return target
    }

    fun delete(file: File) {
        if (!file.exists()) {
            // 受限目录里普通 API 看不到，但仍可能是真实存在的条目
            if (Shell.rootAvailable && Shell.delete(file.absolutePath)) return
            throw IOException("文件不存在")
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) for (c in children) delete(c)
        }
        if (!file.delete()) {
            if (Shell.rootAvailable && Shell.delete(file.absolutePath)) return
            throw IOException("删除失败：${file.name}")
        }
    }

    /** 受限目录下拿到真实大小与时间 */
    fun statSize(file: File): Long {
        val len = runCatching { file.length() }.getOrDefault(0L)
        if (len > 0) return len
        if (!Shell.rootAvailable) return len
        val r = Shell.sh("stat -c %s ${Shell.q(file.absolutePath)}")
        return r.out.trim().toLongOrNull() ?: len
    }

    /**
     * 复制文件或目录到目标目录，回调进度。
     * @param onProgress (当前文件相对路径, 已完成字节, 总字节)
     */
    fun copy(
        src: File,
        destDir: File,
        keepTime: Boolean = true,
        onProgress: (String, Long, Long) -> Unit
    ) {
        val total = if (src.isFile) src.length() else dirSize(src)
        copyRec(src, destDir, total, LongArray(1), keepTime, onProgress)
        onProgress(src.name, total, total)
    }

    private fun copyRec(
        src: File,
        destDir: File,
        total: Long,
        counter: LongArray,
        keepTime: Boolean,
        onProgress: (String, Long, Long) -> Unit
    ) {
        if (src.isDirectory) {
            val target = uniqueTarget(destDir, src.name)
            if (!target.mkdirs() && !target.isDirectory) throw IOException("无法创建目录 ${target.name}")
            val children = src.listFiles() ?: return
            for (c in children) copyRec(c, target, total, counter, keepTime, onProgress)
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
            // 保留原文件时间：整理素材时排序不会因为复制而全跳到今天
            if (keepTime) target.setLastModified(src.lastModified())
        }
    }

    fun move(
        src: File,
        destDir: File,
        keepTime: Boolean = true,
        onProgress: (String, Long, Long) -> Unit
    ) {
        val target = uniqueTarget(destDir, src.name)
        if (src.renameTo(target)) {
            onProgress(src.name, 1, 1)
            return
        }
        copy(src, destDir, keepTime, onProgress)
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
        if (file.length() > 64L * 1024 * 1024) return false
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
        if (file.length() > TEXT_EDIT_LIMIT) throw IOException("文件过大，请用十六进制编辑器打开")
        return decodeText(file.readBytes(), file.length().toInt()).first
    }

    /**
     * 打开文本：只取首屏一段（默认 200KB），后续内容在用户向下滑动时继续追加。
     * 小文件一次读完，行为与普通编辑器完全一致。
     */
    fun readTextSmart(file: File, encoding: String? = null): TextContent {
        val w = readWindow(file, 0L, EDITOR_WINDOW, encoding)
        return TextContent(
            text = w.text,
            encoding = w.encoding,
            truncated = w.partial,
            totalBytes = w.totalBytes,
            editable = true,
            loadedBytes = w.to,
            partial = w.partial,
            bom = w.bom
        )
    }

    /** 编辑器里可切换的编码，顺序即菜单顺序 */
    val ENCODINGS = listOf(
        "UTF-8", "GB18030", "GBK", "Big5", "Shift_JIS", "EUC-KR",
        "UTF-16LE", "UTF-16BE", "ISO-8859-1", "windows-1252"
    )

    fun charsetOf(name: String?): Charset? =
        name?.let { runCatching { Charset.forName(it) }.getOrNull() }

    /**
     * 读取文件头判断编码：先看 BOM，再严格试 UTF-8，最后按中文环境兜底 GB18030。
     * 用户可以在编辑器里手动改成 Big5 / Shift_JIS 等。
     */
    fun detectCharset(file: File): Charset {
        val head = ByteArray(32768)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (n >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (n >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (n >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(head, 0, n))
            true
        }.getOrDefault(false)
        if (utf8) return Charsets.UTF_8
        // 非 UTF-8：GB18030 覆盖面最广，适合作为中文默认
        return charsetOf("GB18030") ?: Charsets.UTF_8
    }

    /**
     * 逐段读取一段文本：起点对齐到字符首字节，终点优先落在行尾，
     * 因此相邻两段拼起来既不重复也不丢字，且每段都以完整行结束。
     */
    fun readWindow(
        file: File,
        from: Long,
        maxBytes: Int = EDITOR_WINDOW,
        encoding: String? = null
    ): TextWindow {
        val size = file.length()
        val charset = charsetOf(encoding) ?: detectCharset(file)
        val wide = charset.name().startsWith("UTF-16")
        // UTF-16 按 2 字节对齐，不能套用 UTF-8 的续字节回退
        val start = when {
            from <= 0L -> 0L
            wide -> from - (from % 2)
            else -> boundaryBack(file, from)
        }
        if (start >= size) {
            return TextWindow("", charset.name(), start, start, size, false, false)
        }
        val rawEnd = minOf(start + maxBytes.toLong().coerceAtLeast(4096L), size)
        var end = rawEnd
        if (rawEnd < size) {
            val chunk = readChunk(file, start, (rawEnd - start).toInt())
            var nl = -1
            // UTF-16 的换行是 0A 00 / 00 0A，直接找 0A 也能对齐
            for (i in chunk.indices.reversed()) {
                if (chunk[i] == '\n'.code.toByte()) { nl = i; break }
            }
            end = if (nl >= chunk.size / 3) start + nl + 1 else if (wide) rawEnd else boundaryBack(file, rawEnd)
        }
        val bytes = readChunk(file, start, (end - start).toInt().coerceAtLeast(0))
        val hasBom = start == 0L && bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val offset = if (hasBom) 3 else 0
        var text = String(bytes, offset, bytes.size - offset, charset)
        if (start == 0L && text.startsWith('\uFEFF')) text = text.substring(1)
        return TextWindow(
            text = text,
            encoding = charset.name(),
            from = start,
            to = end,
            totalBytes = size,
            partial = end < size,
            bom = hasBom
        )
    }

    /** 从 pos 向前找到最近的字符首字节，返回其后的位置（UTF-8 续字节为 10xxxxxx） */
    private fun boundaryBack(file: File, pos: Long): Long {
        if (pos <= 0) return 0
        val back = minOf(4L, pos)
        val buf = runCatching { readChunk(file, pos - back, back.toInt()) }.getOrNull()
            ?: return pos
        var i = buf.size - 1
        while (i >= 0 && (buf[i].toInt() and 0xC0) == 0x80) i--
        return if (i < 0) pos else pos - back + (i + 1)
    }

    /**
     * 逐段编辑后的保存：写入新的前段，再把尚未加载的尾部原样接到后面。
     * 这样即使文件有几百 MB，也只重写屏幕上看得到的部分。
     */
    fun writeHeadAndTail(
        file: File,
        head: ByteArray,
        tailFrom: Long,
        bom: Boolean = false
    ): Boolean = runCatching {
        val tmp = File(file.parentFile, ".${file.name}.suntmp")
        val executable = runCatching { file.canExecute() }.getOrDefault(false)
        FileInputStream(file).use { input ->
            FileOutputStream(tmp).use { out ->
                if (bom) out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(head)
                if (tailFrom < file.length()) {
                    input.channel.position(tailFrom)
                    input.copyTo(out, 128 * 1024)
                }
                out.flush()
                out.fd.sync()
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        runCatching { if (executable) file.setExecutable(true) }
        true
    }.getOrDefault(false)

    /**
     * 替换文件中的一段字节：前段原样复制 → 写入新内容 → 后段原样复制。
     * 只占用一个缓冲区，长度任意变化都能正确处理。
     */
    fun replaceRange(file: File, offset: Long, oldLength: Long, newBytes: ByteArray): Boolean {
        val tmp = File(file.parentFile, ".${file.name}.suntmp")
        return try {
            val executable = runCatching { file.canExecute() }.getOrDefault(false)
            FileInputStream(file).use { input ->
                FileOutputStream(tmp).use { out ->
                    copyExactly(input, out, offset)
                    out.write(newBytes)
                    skipExactly(input, oldLength)
                    input.copyTo(out, 128 * 1024)
                    out.flush()
                    out.fd.sync()
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            runCatching { if (executable) file.setExecutable(true) }
            true
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            false
        }
    }

    private fun copyExactly(input: FileInputStream, out: FileOutputStream, count: Long) {
        var left = count
        val buf = ByteArray(128 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skipExactly(input: FileInputStream, count: Long) {
        var left = count
        while (left > 0) {
            val n = input.skip(left)
            if (n <= 0) {
                if (input.read() < 0) break
                left -= 1
            } else {
                left -= n
            }
        }
    }

    fun writeText(file: File, text: String, encoding: String = "UTF-8") {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && Shell.rootAvailable) Shell.mkdir(parent.absolutePath)
        }
        val cs = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
        val bytes = text.toByteArray(cs)
        val ok = runCatching { file.writeBytes(bytes) }.isSuccess
        // 普通 API 无权限时走 root 覆盖写，保证系统分区外也能保存
        if (!ok && Shell.rootAvailable) {
            Shell.writeAll(file.absolutePath, bytes)
        } else if (!ok) {
            throw IOException("保存失败：没有写入权限")
        }
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

    /** 读取任意偏移的字节块，超大文件也不会占满内存；受限文件走 root */
    fun readChunk(file: File, offset: Long, length: Int): ByteArray {
        return try {
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
                if (read == toRead) buf else buf.copyOf(read)
            }
        } catch (e: Exception) {
            if (Shell.rootAvailable) Shell.readRange(file.absolutePath, offset, length) else throw e
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

    // ---------------- 压缩包：zip / tar / tar.gz / gz ----------------

    enum class ArchiveKind { ZIP, TAR, GZIP, SEVEN_Z, RAR, BZIP2, XZ, UNSUPPORTED }

    fun archiveKindOf(file: File): ArchiveKind = when (Archives.kindOf(file)) {
        Archives.Kind.ZIP -> ArchiveKind.ZIP
        Archives.Kind.RAR -> ArchiveKind.RAR
        Archives.Kind.SEVEN_Z -> ArchiveKind.SEVEN_Z
        Archives.Kind.TAR, Archives.Kind.TAR_GZ, Archives.Kind.TAR_BZ2, Archives.Kind.TAR_XZ,
        Archives.Kind.TAR_SPLIT -> ArchiveKind.TAR
        Archives.Kind.GZIP -> ArchiveKind.GZIP
        Archives.Kind.BZIP2 -> ArchiveKind.BZIP2
        Archives.Kind.XZ -> ArchiveKind.XZ
        Archives.Kind.UNSUPPORTED -> ArchiveKind.UNSUPPORTED
    }

    /** 统一的条目列表，zip / rar / 7z / tar 族 / 单流压缩全部走 Archives */
    fun archiveEntries(file: File, password: String? = null): List<ZipItem> =
        Archives.entries(file, password)

    private fun openTar(file: File): java.io.InputStream {
        val ext = FileKinds.extensionOf(file)
        val raw = BufferedInputStream(FileInputStream(file), 128 * 1024)
        return if (ext in setOf("tar.gz", "tgz") || ext == "gz") GZIPInputStream(raw, 128 * 1024) else raw
    }

    /** 极简 tar 解析：只读名字、大小、类型，足够列目录与解压 */
    private fun tarEntries(input: java.io.InputStream): List<ZipItem> {
        val list = ArrayList<ZipItem>()
        input.use { stream ->
            val header = ByteArray(512)
            var longName: String? = null
            while (list.size < 50_000) {
                if (!readFully(stream, header, 512)) break
                if (header.all { it == 0.toByte() }) break
                var name = cString(header, 0, 100)
                val prefix = cString(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
                val size = cString(header, 124, 12).trim().toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val dataBlocks = ((size + 511) / 512)

                when (type) {
                    'L' -> {
                        val buf = ByteArray(minOf(size, 4096L).toInt())
                        readFully(stream, buf, buf.size)
                        longName = String(buf, Charsets.UTF_8).trimEnd('\u0000')
                        skipFully(stream, dataBlocks * 512L - buf.size)
                    }
                    '5' -> {
                        list.add(ZipItem(name.trimEnd('/') + "/", true, 0, 0, 0))
                        skipFully(stream, dataBlocks * 512L)
                    }
                    '0', '\u0000', '7' -> {
                        list.add(ZipItem(longName ?: name, false, size, size, 0))
                        longName = null
                        skipFully(stream, dataBlocks * 512L)
                    }
                    else -> skipFully(stream, dataBlocks * 512L)
                }
            }
        }
        return list
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: java.io.InputStream, count: Long) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) return
            left -= n
        }
    }

    private fun cString(buf: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val max = minOf(offset + length, buf.size)
        while (end < max && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8)
    }

    fun readArchiveEntry(
        file: File,
        name: String,
        maxBytes: Long = 512 * 1024,
        password: String? = null
    ): ByteArray = Archives.readEntry(file, name, maxBytes, password)

    /** 顺序扫 tar，把命中条目的数据流交给回调；返回 true 表示已找到并停止 */
    private fun scanTar(file: File, onEntry: (String, java.io.InputStream, Long) -> Boolean) {
        openTar(file).use { stream ->
            val header = ByteArray(512)
            var longName: String? = null
            while (true) {
                if (!readFully(stream, header, 512)) return
                if (header.all { it == 0.toByte() }) return
                var name = cString(header, 0, 100)
                val prefix = cString(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
                val size = cString(header, 124, 12).trim().toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val blocks = (size + 511) / 512 * 512
                if (type == 'L') {
                    val buf = ByteArray(minOf(size, 4096L).toInt())
                    readFully(stream, buf, buf.size)
                    longName = String(buf, Charsets.UTF_8).trimEnd('\u0000')
                    skipFully(stream, blocks - buf.size)
                    continue
                }
                val real = longName ?: name
                longName = null
                if (type != '5' && onEntry(real, stream, size)) return
                skipFully(stream, blocks)
            }
        }
    }

    fun extractArchiveEntry(
        archive: File,
        entryName: String,
        destDir: File,
        password: String? = null
    ): File = Archives.extractEntry(archive, entryName, destDir, password)

    fun extractArchive(
        archive: File,
        destDir: File,
        onProgress: (String, Int, Int) -> Unit,
        password: String? = null
    ): Int = Archives.extractAll(archive, destDir, password, onProgress)

    /** tar 一遍流式解压：每个条目只读一次，不再为每个文件重新扫全包 */
    private fun extractTarAll(archive: File, destDir: File, onProgress: (String, Int, Int) -> Unit): Int {
        val destRoot = destDir.canonicalFile
        var count = 0
        openTar(archive).use { stream ->
            val header = ByteArray(512)
            var longName: String? = null
            while (true) {
                if (!readFully(stream, header, 512)) break
                if (header.all { it == 0.toByte() }) break
                var name = cString(header, 0, 100)
                val prefix = cString(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
                val size = cString(header, 124, 12).trim().toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val blocks = (size + 511) / 512 * 512

                if (type == 'L') {
                    val buf = ByteArray(minOf(size, 4096L).toInt())
                    readFully(stream, buf, buf.size)
                    longName = String(buf, Charsets.UTF_8).trimEnd('\u0000')
                    skipFully(stream, blocks - buf.size)
                    continue
                }
                val real = longName ?: name
                longName = null
                val out = File(destDir, real)
                if (!out.canonicalPath.startsWith(destRoot.path + File.separator) &&
                    out.canonicalPath != destRoot.path
                ) {
                    throw IOException("压缩包包含非法路径：$real")
                }
                if (type == '5') {
                    out.mkdirs()
                    skipFully(stream, blocks)
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { o -> copyN(stream, o, size) }
                    skipFully(stream, blocks - size)
                    onProgress(real, ++count, 0)
                }
            }
        }
        return count
    }

    private fun copyN(input: java.io.InputStream, out: java.io.OutputStream, count: Long) {
        var left = count
        val buf = ByteArray(128 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

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

    /** 文件名匹配方式，对应搜索面板最上面那个下拉 */
    enum class MatchMode(val label: String) {
        GLOB("文件名通配表达式（*和?）"),
        CONTAINS("文件名包含"),
        REGEX("文件名正则表达式")
    }

    /** 搜索条件：与 MT 搜索面板逐项对应 */
    data class SearchOptions(
        val match: MatchMode = MatchMode.GLOB,
        val caseSensitive: Boolean = false,
        val recursive: Boolean = true,
        val minSize: Long = 0L,
        val maxSize: Long = Long.MAX_VALUE,
        /** 只要这个时间点之后修改过的文件，0 表示不限 */
        val since: Long = 0L,
        /** 文件内容必须包含的文字，空表示不限 */
        val contains: String = ""
    ) {
        val hasSizeFilter: Boolean get() = minSize > 0L || maxSize != Long.MAX_VALUE
        val hasTimeFilter: Boolean get() = since > 0L
    }

    fun search(
        root: File,
        query: String,
        maxResults: Int = 300,
        onProgress: (scanned: Int, current: String) -> Unit,
        cancelled: () -> Boolean
    ): List<FileEntry> = search(root, query, SearchOptions(), maxResults, onProgress, cancelled)

    fun search(
        root: File,
        query: String,
        options: SearchOptions,
        maxResults: Int = 300,
        onProgress: (scanned: Int, current: String) -> Unit,
        cancelled: () -> Boolean
    ): List<FileEntry> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val accept = matcherOf(keyword, options)
        val contains = options.contains.trim()
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
                val isDir = runCatching { c.isDirectory }.getOrDefault(false)
                if (!accept(name)) {
                    if (isDir && options.recursive) queue.addLast(c)
                    continue
                }
                val size = if (isDir) 0L else runCatching { c.length() }.getOrDefault(0L)
                val modified = runCatching { c.lastModified() }.getOrDefault(0L)
                if (!isDir) {
                    if (size < options.minSize || size > options.maxSize) continue
                    if (options.since > 0L && modified < options.since) continue
                    if (contains.isNotEmpty() && !fileContains(c, contains, options.caseSensitive)) continue
                }
                results.add(
                    FileEntry(
                        file = c,
                        name = name,
                        isDir = isDir,
                        size = size,
                        lastModified = modified,
                        isHidden = name.startsWith("."),
                        canRead = runCatching { c.canRead() }.getOrDefault(false),
                        canWrite = runCatching { c.canWrite() }.getOrDefault(false)
                    )
                )
                if (results.size >= maxResults) break
                if (isDir && options.recursive) queue.addLast(c)
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

    private fun fileContains(file: File, text: String, caseSensitive: Boolean): Boolean {
        if (file.length() > 4L * 1024 * 1024) return false
        if (!looksLikeText(file)) return false
        return runCatching {
            file.useLines { lines ->
                lines.any { it.contains(text, ignoreCase = !caseSensitive) }
            }
        }.getOrDefault(false)
    }

    /** 把通配符表达式转成正则：* 任意串、? 单个字符，其余字符按字面量 */
    fun matcherOf(query: String, options: SearchOptions): (String) -> Boolean {
        val flags = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return when (options.match) {
            MatchMode.CONTAINS -> { name -> name.contains(query, ignoreCase = !options.caseSensitive) }
            MatchMode.REGEX -> {
                val rx = runCatching { Regex(query, flags) }.getOrNull() ?: return { false }
                { name -> rx.containsMatchIn(name) }
            }
            MatchMode.GLOB -> {
                val hasWildcard = query.any { it == '*' || it == '?' }
                val body = buildString {
                    if (!hasWildcard) append(".*")
                    query.forEach { ch ->
                        when (ch) {
                            '*' -> append(".*")
                            '?' -> append('.')
                            else -> append(Regex.escape(ch.toString()))
                        }
                    }
                    if (!hasWildcard) append(".*")
                }
                val rx = runCatching { Regex(body, flags) }.getOrNull() ?: return { false }
                { name -> rx.matches(name) }
            }
        }
    }

    /** 正文命中：文件 + 行号 + 该行片段 */
    data class ContentHit(val file: File, val line: Int, val preview: String)

    /**
     * 全文搜索：只扫描文本文件，单文件 4MB 以内，跳过 .git 等目录。
     * 与 MT 管理器的「搜索内容」一致，命中后可直接跳到对应行。
     */
    fun searchContent(
        root: File,
        query: String,
        maxResults: Int = 200,
        onProgress: (scanned: Int, current: String) -> Unit,
        cancelled: () -> Boolean
    ): List<ContentHit> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val results = ArrayList<ContentHit>()
        val queue = ArrayDeque<File>()
        queue.addLast(root)
        var scanned = 0
        var tick = 0L
        val skipDirs = setOf(".git", "node_modules", ".gradle", "build", ".idea", "__pycache__")
        while (queue.isNotEmpty()) {
            if (cancelled() || results.size >= maxResults || scanned > 20_000) break
            val dir = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (c in children) {
                scanned++
                if (c.isDirectory) {
                    if (c.name !in skipDirs) queue.addLast(c)
                    continue
                }
                if (c.length() == 0L || c.length() > 4L * 1024 * 1024) continue
                if (!looksLikeText(c)) continue
                val hit = runCatching {
                    c.useLines { lines ->
                        var no = 0
                        for (line in lines) {
                            no++
                            if (line.contains(keyword, ignoreCase = true)) {
                                return@useLines ContentHit(
                                    c,
                                    no,
                                    line.trim().take(160)
                                )
                            }
                            if (no > 40_000) break
                        }
                        null
                    }
                }.getOrNull()
                if (hit != null) {
                    results.add(hit)
                    if (results.size >= maxResults) break
                }
                val now = System.currentTimeMillis()
                if (now - tick > 150) {
                    tick = now
                    onProgress(scanned, c.absolutePath)
                }
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
