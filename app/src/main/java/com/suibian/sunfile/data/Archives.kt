package com.suibian.sunfile.data

import com.github.junrar.Archive as RarArchive
import net.lingala.zip4j.ZipFile as Z4File
import net.lingala.zip4j.model.FileHeader as Z4Header
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/**
 * 统一的压缩包读写层。
 *
 * zip 走 zip4j：它同时支持传统 ZipCrypto 与 WinZip AES-256，
 * 因此「带密码压缩」「打开别人的加密包」这两条路径用的是同一套代码。
 * 7z / tar / bz2 / xz 走 commons-compress，rar 走 junrar。
 */
object Archives {

    enum class Kind {
        ZIP, RAR, SEVEN_Z, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZIP, BZIP2, XZ, UNSUPPORTED
    }

    private const val MAX_ENTRIES = 60_000

    fun kindOf(file: File): Kind {
        val name = file.name.lowercase()
        val ext = FileKinds.extensionOf(file)
        return when {
            name.endsWith(".tar.gz") || ext == "tgz" -> Kind.TAR_GZ
            name.endsWith(".tar.bz2") || ext == "tbz" || ext == "tbz2" -> Kind.TAR_BZ2
            name.endsWith(".tar.xz") || ext == "txz" -> Kind.TAR_XZ
            ext in setOf("zip", "jar", "aar", "war", "ear", "whl", "epub", "apk", "xapk", "apks",
                "docx", "xlsx", "pptx", "odt", "ods", "odp", "wps", "et", "dps", "kmz", "ipa") -> Kind.ZIP
            ext == "rar" -> Kind.RAR
            ext == "7z" -> Kind.SEVEN_Z
            ext == "tar" -> Kind.TAR
            ext == "gz" -> Kind.GZIP
            ext == "bz2" -> Kind.BZIP2
            ext == "xz" -> Kind.XZ
            else -> Kind.UNSUPPORTED
        }
    }

    fun supported(file: File): Boolean = kindOf(file) != Kind.UNSUPPORTED

    /** 该包是否需要密码才能列出内容（7z 加密头 / rar 加密） */
    fun needsPassword(file: File): Boolean = runCatching {
        when (kindOf(file)) {
            Kind.SEVEN_Z -> {
                runCatching { SevenZFile(file).use { z -> z.entries.count() } }.isFailure
            }
            Kind.RAR -> {
                runCatching { RarArchive(file).use { it.isEncrypted } }.getOrDefault(false)
            }
            else -> false
        }
    }.getOrDefault(false)

    // ---------------- 列表 ----------------

    fun entries(file: File, password: String? = null): List<FileRepo.ZipItem> {
        return when (kindOf(file)) {
            Kind.ZIP -> zipEntries(file, password)
            Kind.RAR -> rarEntries(file, password)
            Kind.SEVEN_Z -> sevenZEntries(file, password)
            Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> tarEntries(file)
            Kind.GZIP, Kind.BZIP2, Kind.XZ -> listOf(
                FileRepo.ZipItem(
                    name = singleName(file),
                    isDir = false,
                    size = file.length(),
                    compressed = file.length(),
                    time = file.lastModified()
                )
            )
            Kind.UNSUPPORTED -> throw IOException("暂不支持这种压缩格式")
        }
    }

    private fun zipEntries(file: File, password: String?): List<FileRepo.ZipItem> {
        val pw = password?.takeIf { it.isNotEmpty() }
        if (pw != null) {
            val zf = Z4File(file, pw.toCharArray())
            return zf.fileHeaders.map { h ->
                FileRepo.ZipItem(
                    name = h.fileName,
                    isDir = h.isDirectory,
                    size = h.uncompressedSize.coerceAtLeast(0),
                    compressed = h.compressedSize.coerceAtLeast(0),
                    time = runCatching { h.lastModifiedTimeEpoch }.getOrDefault(0L)
                )
            }.take(MAX_ENTRIES)
        }
        return runCatching { FileRepo.zipEntries(file) }.getOrElse {
            // 加密包用普通 ZipFile 读条目名仍然可行，这里兜底到 zip4j
            val zf = Z4File(file)
            zf.fileHeaders.map { h ->
                FileRepo.ZipItem(
                    name = h.fileName,
                    isDir = h.isDirectory,
                    size = h.uncompressedSize.coerceAtLeast(0),
                    compressed = h.compressedSize.coerceAtLeast(0),
                    time = runCatching { h.lastModifiedTimeEpoch }.getOrDefault(0L)
                )
            }.take(MAX_ENTRIES)
        }
    }

    private fun rarEntries(file: File, password: String?): List<FileRepo.ZipItem> {
        val archive = if (password.isNullOrEmpty()) RarArchive(file) else RarArchive(file, password)
        return archive.use { a ->
            val list = ArrayList<FileRepo.ZipItem>()
            for (h in a.fileHeaders) {
                if (list.size >= MAX_ENTRIES) break
                val name = h.fileNameString ?: continue
                list.add(
                    FileRepo.ZipItem(
                        name = if (h.isDirectory && !name.endsWith("/")) "$name/" else name,
                        isDir = h.isDirectory,
                        size = h.fullUnpackSize.coerceAtLeast(0),
                        compressed = h.fullPackSize.coerceAtLeast(0),
                        time = runCatching { h.mTime?.time ?: 0L }.getOrDefault(0L)
                    )
                )
            }
            list
        }
    }

    private fun sevenZEntries(file: File, password: String?): List<FileRepo.ZipItem> {
        val zf = if (password.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, password.toCharArray())
        return zf.use { z ->
            val list = ArrayList<FileRepo.ZipItem>()
            for (e in z.entries) {
                if (list.size >= MAX_ENTRIES) break
                list.add(
                    FileRepo.ZipItem(
                        name = e.name,
                        isDir = e.isDirectory,
                        size = e.size.coerceAtLeast(0),
                        compressed = 0,
                        time = runCatching { e.lastModifiedDate?.time ?: 0L }.getOrDefault(0L)
                    )
                )
            }
            list
        }
    }

    private fun tarEntries(file: File): List<FileRepo.ZipItem> = openTar(file).use { input ->
        val list = ArrayList<FileRepo.ZipItem>()
        val tin = TarArchiveInputStream(input)
        while (list.size < MAX_ENTRIES) {
            val e = tin.nextEntry ?: break
            val raw = e.name
            val name = if (e.isDirectory && !raw.endsWith("/")) "$raw/" else raw
            list.add(
                FileRepo.ZipItem(
                    name = name,
                    isDir = e.isDirectory,
                    size = if (e.isDirectory) 0 else e.size.coerceAtLeast(0),
                    compressed = 0,
                    time = runCatching { e.lastModifiedDate?.time ?: 0L }.getOrDefault(0L)
                )
            )
        }
        list
    }

    // ---------------- 解压 ----------------

    fun extractEntry(file: File, entryName: String, destDir: File, password: String? = null): File =
        when (kindOf(file)) {
            Kind.ZIP -> extractZip(file, entryName, destDir, password)
            Kind.RAR -> extractRar(file, entryName, destDir, password)
            Kind.SEVEN_Z -> extractSevenZ(file, entryName, destDir, password)
            Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> extractTar(file, entryName, destDir)
            Kind.GZIP, Kind.BZIP2, Kind.XZ -> {
                val out = unique(destDir, singleName(file))
                decompressSingle(file).use { input ->
                    BufferedOutputStream(FileOutputStream(out), 128 * 1024).use { o -> input.copyTo(o, 128 * 1024) }
                }
                out
            }
            Kind.UNSUPPORTED -> throw IOException("暂不支持这种压缩格式")
        }

    fun extractAll(
        file: File,
        destDir: File,
        password: String? = null,
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Int = when (kindOf(file)) {
        Kind.ZIP -> extractZipAll(file, destDir, password, onProgress)
        Kind.RAR -> extractRarAll(file, destDir, password, onProgress)
        Kind.SEVEN_Z -> extractSevenZAll(file, destDir, password, onProgress)
        Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> extractTarAll(file, destDir, onProgress)
        Kind.GZIP, Kind.BZIP2, Kind.XZ -> {
            val out = extractEntry(file, singleName(file), destDir, password)
            onProgress(out.name, 1, 1)
            1
        }
        Kind.UNSUPPORTED -> throw IOException("暂不支持这种压缩格式")
    }

    /** 读出单个条目的全部字节；[maxBytes] 为保护上限，超过则截断 */
    fun readEntry(file: File, entryName: String, maxBytes: Long = 512 * 1024, password: String? = null): ByteArray {
        return when (kindOf(file)) {
            Kind.ZIP -> readZipEntry(file, entryName, maxBytes, password)
            Kind.SEVEN_Z -> {
                val zf = if (password.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, password.toCharArray())
                zf.use { z ->
                    for (e in z.entries) {
                        if (e.name == entryName && !e.isDirectory) {
                            return readLimited(z.getInputStream(e), maxBytes)
                        }
                    }
                }
                throw IOException("压缩包内不存在该文件")
            }
            else -> {
                val tmpDir = File(file.parentFile ?: File("/tmp"), ".sunread").apply { mkdirs() }
                extractEntry(file, entryName, tmpDir, password).readBytes()
            }
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray = input.use {
        val buf = ByteArray(minOf(maxBytes, 16L * 1024 * 1024).toInt().coerceAtLeast(1))
        var read = 0
        while (read < buf.size) {
            val n = it.read(buf, read, buf.size - read)
            if (n <= 0) break
            read += n
        }
        buf.copyOf(read)
    }

    private fun readZipEntry(file: File, entryName: String, maxBytes: Long, password: String?): ByteArray {
        if (!password.isNullOrEmpty()) {
            val zf = Z4File(file, password.toCharArray())
            val header = zf.getFileHeader(entryName) ?: throw IOException("压缩包内不存在该文件")
            return readLimited(zf.getInputStream(header), maxBytes)
        }
        return runCatching { FileRepo.readZipEntry(file, entryName, maxBytes) }.getOrElse {
            val zf = Z4File(file)
            val header = zf.getFileHeader(entryName) ?: throw it
            readLimited(zf.getInputStream(header), maxBytes)
        }
    }

    private fun extractZip(file: File, entryName: String, destDir: File, password: String?): File {
        if (!password.isNullOrEmpty()) {
            val zf = Z4File(file, password.toCharArray())
            val header = zf.getFileHeader(entryName) ?: throw IOException("压缩包内不存在该文件")
            val out = unique(destDir, header.fileName.substringAfterLast('/').ifEmpty { "entry" })
            zf.getInputStream(header).use { input ->
                BufferedOutputStream(FileOutputStream(out), 128 * 1024).use { o -> input.copyTo(o, 128 * 1024) }
            }
            return out
        }
        return runCatching { FileRepo.extractZipEntry(file, entryName, destDir) }.getOrElse { e ->
            throw e
        }
    }

    private fun extractZipAll(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (String, Int, Int) -> Unit
    ): Int {
        val zf = if (password.isNullOrEmpty()) Z4File(file) else Z4File(file, password.toCharArray())
        val headers = zf.fileHeaders
        val total = headers.count { !it.isDirectory }
        var done = 0
        for (h in headers) {
            val name = h.fileName
            val target = safeTarget(destDir, name) ?: continue
            if (h.isDirectory) {
                target.mkdirs()
                continue
            }
            target.parentFile?.mkdirs()
            zf.getInputStream(h).use { input ->
                BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o -> input.copyTo(o, 128 * 1024) }
            }
            onProgress(name, ++done, total)
        }
        return done
    }

    private fun extractRar(file: File, entryName: String, destDir: File, password: String?): File {
        val archive = if (password.isNullOrEmpty()) RarArchive(file) else RarArchive(file, password)
        val found = archive.use { a ->
            var out: File? = null
            for (h in a.fileHeaders) {
                if (h.fileNameString != entryName || h.isDirectory) continue
                val target = unique(destDir, entryName.substringAfterLast('/').ifEmpty { "entry" })
                BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o -> a.extractFile(h, o) }
                out = target
                break
            }
            out
        }
        return found ?: throw IOException("压缩包内不存在该文件")
    }

    private fun extractRarAll(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (String, Int, Int) -> Unit
    ): Int {
        val archive = if (password.isNullOrEmpty()) RarArchive(file) else RarArchive(file, password)
        return archive.use { a ->
            var count = 0
            for (h in a.fileHeaders) {
                val name = h.fileNameString ?: continue
                val target = safeTarget(destDir, name) ?: continue
                if (h.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o -> a.extractFile(h, o) }
                onProgress(name, ++count, 0)
            }
            count
        }
    }

    private fun extractSevenZ(file: File, entryName: String, destDir: File, password: String?): File {
        val zf = if (password.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, password.toCharArray())
        val found = zf.use { z ->
            var out: File? = null
            for (e in z.entries) {
                if (e.name != entryName || e.isDirectory) continue
                val target = unique(destDir, entryName.substringAfterLast('/').ifEmpty { "entry" })
                BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o ->
                    z.getInputStream(e).copyTo(o, 128 * 1024)
                }
                out = target
                break
            }
            out
        }
        return found ?: throw IOException("压缩包内不存在该文件")
    }

    private fun extractSevenZAll(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (String, Int, Int) -> Unit
    ): Int {
        val zf = if (password.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, password.toCharArray())
        return zf.use { z ->
            var count = 0
            var entry: SevenZArchiveEntry? = z.nextEntry
            while (entry != null) {
                val e = entry
                val target = safeTarget(destDir, e.name)
                if (target != null) {
                    if (e.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o ->
                            z.getInputStream(e).copyTo(o, 128 * 1024)
                        }
                        onProgress(e.name, ++count, 0)
                    }
                }
                entry = z.nextEntry
            }
            count
        }
    }

    private fun extractTar(file: File, entryName: String, destDir: File): File {
        openTar(file).use { input ->
            val tin = TarArchiveInputStream(input)
            while (true) {
                val e = tin.nextEntry ?: break
                if (e.name != entryName || e.isDirectory) continue
                val out = unique(destDir, entryName.substringAfterLast('/').ifEmpty { "entry" })
                BufferedOutputStream(FileOutputStream(out), 128 * 1024).use { o -> tin.copyTo(o, 128 * 1024) }
                return out
            }
        }
        throw IOException("压缩包内不存在该文件")
    }

    private fun extractTarAll(file: File, destDir: File, onProgress: (String, Int, Int) -> Unit): Int {
        var count = 0
        openTar(file).use { input ->
            val tin = TarArchiveInputStream(input)
            while (true) {
                val e = tin.nextEntry ?: break
                val target = safeTarget(destDir, e.name)
                if (target != null) {
                    if (e.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(target), 128 * 1024).use { o -> tin.copyTo(o, 128 * 1024) }
                        onProgress(e.name, ++count, 0)
                    }
                }
            }
        }
        return count
    }

    // ---------------- 创建 ----------------

    enum class Format(val label: String, val ext: String) {
        ZIP("ZIP", "zip"),
        SEVEN_Z("7Z", "7z"),
        TAR("TAR", "tar"),
        TAR_GZ("TAR.GZ", "tar.gz")
    }

    fun formatOf(name: String): Format = when {
        name.endsWith(".7z", true) -> Format.SEVEN_Z
        name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true) -> Format.TAR_GZ
        name.endsWith(".tar", true) -> Format.TAR
        else -> Format.ZIP
    }

    /**
     * [level] 0 仅存储、1 快速、6 标准、9 极限。
     * [password] 仅对 ZIP 生效（AES-256）；7z 写入侧暂不支持加密。
     */
    fun create(
        sources: List<File>,
        dest: File,
        format: Format,
        level: Int = 6,
        password: String? = null,
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Int {
        val destPath = runCatching { dest.canonicalPath }.getOrDefault(dest.absolutePath)
        val entries = ArrayList<Triple<String, File, Boolean>>()
        for (src in sources) collect(src, src.name, entries, destPath, 0)
        if (entries.isEmpty()) return 0
        val total = entries.size
        var done = 0
        val lv = level.coerceIn(0, 9)
        when (format) {
            Format.ZIP -> {
                val pw = password?.takeIf { it.isNotEmpty() }
                val zf = if (pw != null) Z4File(dest, pw.toCharArray()) else Z4File(dest)
                for ((name, file, isDir) in entries) {
                    if (isDir) {
                        // 只保留空目录：有子项的目录会由子项的路径隐式创建
                        if ((file.listFiles()?.size ?: 0) == 0) {
                            runCatching {
                                zf.addStream(
                                    java.io.ByteArrayInputStream(ByteArray(0)),
                                    zipParams("${name.trimEnd('/')}/", lv, pw != null)
                                )
                            }
                        }
                    } else {
                        BufferedInputStream(FileInputStream(file), 128 * 1024).use { input ->
                            val params = zipParams(name, lv, pw != null)
                            params.setEntrySize(file.length())
                            zf.addStream(input, params)
                        }
                    }
                    onProgress(name, ++done, total)
                }
            }
            Format.SEVEN_Z -> {
                SevenZOutputFile(dest).use { out ->
                    out.setContentCompression(if (lv == 0) SevenZMethod.COPY else SevenZMethod.LZMA2)
                    for ((name, file, isDir) in entries) {
                        val entry = if (isDir) {
                            SevenZArchiveEntry().apply {
                                this.name = "$name/"
                                isDirectory = true
                            }
                        } else {
                            out.createArchiveEntry(file, name)
                        }
                        out.putArchiveEntry(entry)
                        if (!isDir) file.inputStream().use { input -> out.write(input) }
                        out.closeArchiveEntry()
                        onProgress(name, ++done, total)
                    }
                }
            }
            Format.TAR, Format.TAR_GZ -> {
                val raw = BufferedOutputStream(FileOutputStream(dest), 128 * 1024)
                val out: OutputStream = if (format == Format.TAR_GZ) {
                    val params = GzipParameters()
                    params.compressionLevel = lv.coerceIn(0, 9)
                    org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(raw, params)
                } else raw
                out.use { stream ->
                    val tout = org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(stream)
                    tout.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU)
                    for ((name, file, isDir) in entries) {
                        val entry = TarArchiveEntry(file, if (isDir) "$name/" else name)
                        tout.putArchiveEntry(entry)
                        if (!isDir) file.inputStream().use { input -> input.copyTo(tout, 128 * 1024) }
                        tout.closeArchiveEntry()
                        onProgress(name, ++done, total)
                    }
                    tout.finish()
                }
            }
        }
        return done
    }

    private fun zipParams(name: String, level: Int, encrypt: Boolean): ZipParameters {
        val p = ZipParameters()
        p.fileNameInZip = name
        p.compressionMethod = if (level == 0) CompressionMethod.STORE else CompressionMethod.DEFLATE
        if (level > 0) p.compressionLevel = levelEnum(level)
        p.setEncryptFiles(encrypt)
        if (encrypt) {
            p.setEncryptionMethod(EncryptionMethod.AES)
            p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
            p.setAesVersion(net.lingala.zip4j.model.enums.AesVersion.TWO)
        }
        return p
    }

    private fun levelEnum(level: Int): CompressionLevel = when {
        level <= 1 -> CompressionLevel.FASTEST
        level <= 3 -> CompressionLevel.FAST
        level <= 5 -> CompressionLevel.NORMAL
        level <= 7 -> CompressionLevel.HIGHER
        level <= 8 -> CompressionLevel.MAXIMUM
        else -> CompressionLevel.ULTRA
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

    // ---------------- 工具 ----------------

    private fun openTar(file: File): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), 128 * 1024)
        return when (kindOf(file)) {
            Kind.TAR -> raw
            Kind.TAR_GZ -> GzipCompressorInputStream(raw, true)
            Kind.TAR_BZ2 -> BZip2CompressorInputStream(raw, true)
            Kind.TAR_XZ -> XZCompressorInputStream(raw, true)
            else -> raw
        }
    }

    private fun decompressSingle(file: File): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), 128 * 1024)
        return when (kindOf(file)) {
            Kind.GZIP -> GzipCompressorInputStream(raw, true)
            Kind.BZIP2 -> BZip2CompressorInputStream(raw, true)
            Kind.XZ -> XZCompressorInputStream(raw, true)
            else -> raw
        }
    }

    private fun singleName(file: File): String {
        val n = file.name
        val cut = when {
            n.endsWith(".gz", true) -> n.dropLast(3)
            n.endsWith(".bz2", true) -> n.dropLast(4)
            n.endsWith(".xz", true) -> n.dropLast(3)
            else -> ""
        }
        return cut.ifEmpty { "content" }
    }

    private fun safeTarget(destDir: File, rawName: String): File? {
        val name = rawName.replace('\\', '/').trimStart('/')
        if (name.isEmpty()) return null
        if (name.split('/').any { it == ".." }) return null
        val root = runCatching { destDir.canonicalFile }.getOrDefault(destDir)
        val out = File(root, name)
        val path = runCatching { out.canonicalPath }.getOrDefault(out.absolutePath)
        if (!path.startsWith(root.path + File.separator) && path != root.path) return null
        return out
    }

    private fun unique(dir: File, name: String): File {
        dir.mkdirs()
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (target.exists() && i < 9999) {
            val n = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            target = File(dir, n)
            i++
        }
        return target
    }
}
