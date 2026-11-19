package com.suibian.sunfile.data

import java.io.File

/** 两个文件 / 文件夹 / 压缩包之间的差异结果 */
sealed interface CompareResult {
    data class TextDiff(
        val rows: List<Diff.Row>,
        val left: String,
        val right: String,
        val truncated: Boolean = false
    ) : CompareResult

    data class Report(val title: String, val body: String) : CompareResult

    data class Failed(val message: String) : CompareResult
}

object Compare {

    private const val MAX_TEXT = 1_500_000L

    fun of(a: File, b: File): CompareResult {
        if (!a.exists() || !b.exists()) return CompareResult.Failed("文件不存在")
        if (a.isDirectory && b.isDirectory) {
            val left = Diff.dirNodes(a)
            val right = Diff.dirNodes(b)
            return CompareResult.Report("文件夹对比", Diff.treeReport(a.absolutePath, b.absolutePath, left, right))
        }
        val aZip = !a.isDirectory && FileKinds.isZipReadable(a)
        val bZip = !b.isDirectory && FileKinds.isZipReadable(b)
        if (aZip && bZip) {
            val left = Diff.zipNodes(a)
            val right = Diff.zipNodes(b)
            return CompareResult.Report("压缩包对比", Diff.treeReport(a.name, b.name, left, right))
        }
        if (a.isDirectory != b.isDirectory) {
            return CompareResult.Failed("一个是文件夹，一个是文件，无法逐行对比")
        }
        val aText = FileRepo.looksLikeText(a)
        val bText = FileRepo.looksLikeText(b)
        if (aText && bText) {
            val truncated = a.length() > MAX_TEXT || b.length() > MAX_TEXT
            val left = runCatching { readCapped(a) }.getOrElse {
                return CompareResult.Failed("读取失败：${it.message}")
            }
            val right = runCatching { readCapped(b) }.getOrElse {
                return CompareResult.Failed("读取失败：${it.message}")
            }
            val rows = Diff.lines(left, right)
            return CompareResult.TextDiff(rows, a.name, b.name, truncated)
        }
        val sb = StringBuilder()
        sb.append("左侧  ").append(a.absolutePath).append('\n')
        sb.append("　大小  ").append(a.length()).append(" 字节\n")
        sb.append("　SHA-256  ").append(FileRepo.checksum(a, "SHA-256")).append('\n')
        sb.append('\n')
        sb.append("右侧  ").append(b.absolutePath).append('\n')
        sb.append("　大小  ").append(b.length()).append(" 字节\n")
        sb.append("　SHA-256  ").append(FileRepo.checksum(b, "SHA-256")).append('\n')
        sb.append('\n')
        sb.append(if (a.length() == b.length() && FileRepo.checksum(a, "MD5") == FileRepo.checksum(b, "MD5")) {
            "两个文件内容相同"
        } else {
            "两个文件内容不同（二进制）"
        })
        return CompareResult.Report("二进制对比", sb.toString())
    }

    private fun readCapped(file: File): String {
        if (file.length() <= MAX_TEXT) return FileRepo.readTextSmart(file).text
        return file.inputStream().use { input ->
            val buf = ByteArray(MAX_TEXT.toInt())
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
            String(buf, 0, read, Charsets.UTF_8)
        }
    }
}
