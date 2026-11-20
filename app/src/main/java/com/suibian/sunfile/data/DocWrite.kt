package com.suibian.sunfile.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Office 文档的「按段落回写」。
 *
 * docx / pptx 本质是 zip + XML：预览时按顺序取出每段文字，
 * 保存时再按同一顺序把文字塞回原来的 `<w:p>` / `<a:p>`，
 * 段落属性（字号、颜色、级别）沿用原来第一个 run 的 rPr，
 * 因此改字不会把整篇样式打乱。
 */
object DocWrite {

    data class Para(val page: Int, val text: String)

    private const val MAX_DOC = 64 * 1024 * 1024

    // ---------------- 读取 ----------------

    fun readParas(file: File): List<Para> {
        val ext = FileKinds.extensionOf(file)
        return when (ext) {
            "docx", "docm" -> readDocxParas(file)
            "pptx", "pptm" -> readPptxParas(file)
            else -> emptyList()
        }
    }

    private fun readDocxParas(file: File): List<Para> {
        val xml = readEntry(file, "word/document.xml") ?: return emptyList()
        return paragraphs(xml, "w").map { Para(0, it) }
    }

    private fun readPptxParas(file: File): List<Para> {
        val out = ArrayList<Para>()
        slides(file).forEachIndexed { index, name ->
            val xml = readEntry(file, name) ?: return@forEachIndexed
            paragraphs(xml, "a").forEach { out.add(Para(index + 1, it)) }
        }
        return out
    }

    /** 取所有段落（含空段），保证保存时的下标与原文一一对应 */
    private fun paragraphs(xml: String, prefix: String): List<String> {
        val re = Regex("<$prefix:p(?:\\s[^>]*)?>.*?</$prefix:p>", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(xml).map { m ->
            Regex("<$prefix:t(?:\\s[^>]*)?>(.*?)</$prefix:t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(m.value)
                .joinToString("") { DocParser.unescape(it.groupValues[1]) }
        }.toList()
    }

    private fun slides(file: File): List<String> = runCatching {
        ZipFile(file).use { zf ->
            zf.entries().asSequence()
                .map { it.name }
                .filter { it.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
                .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
                .toList()
        }
    }.getOrDefault(emptyList())

    // ---------------- 写回 ----------------

    /** 保存段落；paras 顺序必须与 readParas 一致 */
    fun writeParas(file: File, paras: List<String>): Boolean {
        val ext = FileKinds.extensionOf(file)
        return when (ext) {
            "docx", "docm" -> rewriteEntry(file, "word/document.xml") { xml ->
                replaceParagraphs(xml, "w", paras)
            }
            "pptx", "pptm" -> {
                var index = 0
                val sets = LinkedHashMap<String, String>()
                slides(file).forEach { name ->
                    val xml = readEntry(file, name) ?: return@forEach
                    val count = paragraphs(xml, "a").size
                    val slice = paras.subList(index.coerceAtMost(paras.size), (index + count).coerceAtMost(paras.size))
                    index += count
                    sets[name] = replaceParagraphs(xml, "a", slice)
                }
                rewriteEntries(file, sets.mapValues { (_, v) -> { _: String -> v } })
            }
            else -> false
        }
    }

    private fun replaceParagraphs(xml: String, prefix: String, texts: List<String>): String {
        var i = 0
        val re = Regex("<$prefix:p(?:\\s[^>]*)?>.*?</$prefix:p>", RegexOption.DOT_MATCHES_ALL)
        return re.replace(xml) { m ->
            if (i >= texts.size) return@replace m.value
            val original = m.value
            val inner = original.substringAfter('>').substringBeforeLast("</$prefix:p>")
            val oldText = Regex("<$prefix:t(?:\\s[^>]*)?>(.*?)</$prefix:t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(inner)
                .joinToString("") { DocParser.unescape(it.groupValues[1]) }
            val newText = texts[i++]
            if (newText == oldText) return@replace original
            val pPr = Regex("<$prefix:pPr>.*?</$prefix:pPr>", RegexOption.DOT_MATCHES_ALL).find(inner)?.value ?: ""
            val rPr = Regex("<$prefix:rPr>.*?</$prefix:rPr>", RegexOption.DOT_MATCHES_ALL).find(inner)?.value ?: ""
            val open = original.substringBefore('>') + ">"
            if (newText.isEmpty()) {
                "$open$pPr</$prefix:p>"
            } else {
                val run = if (prefix == "w") {
                    "<w:r>$rPr<w:t xml:space=\"preserve\">${escape(newText)}</w:t></w:r>"
                } else {
                    "<a:r>$rPr<a:t>${escape(newText)}</a:t></a:r>"
                }
                "$open$pPr$run</$prefix:p>"
            }
        }
    }

    // ---------------- xlsx 单元格 ----------------

    /**
     * 按索引写单元格。值写成 inlineStr，避免动 sharedStrings——
     * Excel / WPS / 各类解析器都认这种写法，风险最小。
     */
    fun writeCells(file: File, sheetIndex: Int, cells: Map<String, String>): Boolean {
        val names = runCatching {
            ZipFile(file).use { zf ->
                zf.entries().asSequence().map { it.name }
                    .filter { it.matches(Regex("""xl/worksheets/sheet\d+\.xml""")) }
                    .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
                    .toList()
            }
        }.getOrDefault(emptyList())
        val entry = names.getOrNull(sheetIndex) ?: return false
        return rewriteEntry(file, entry) { xml -> applyCells(xml, cells) }
    }

    private fun applyCells(xml: String, cells: Map<String, String>): String {
        var out = xml
        for ((ref, value) in cells) {
            val existing = Regex("""<c r="$ref"[^>]*>.*?</c>|<c r="$ref"[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
                .find(out)
            val attrMatch = existing?.value?.let { Regex("""<c r="$ref"([^>]*)""").find(it) }
            val keepStyle = attrMatch?.groupValues?.get(1)
                ?.let { Regex("""s="\d+"""").find(it)?.value }
            val num = value.toDoubleOrNull()
            val newCell = if (num != null && value.isNotBlank()) {
                val style = keepStyle?.let { " $it" } ?: ""
                """<c r="$ref"$style><v>$value</v></c>"""
            } else {
                val style = keepStyle?.let { " $it" } ?: ""
                """<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>"""
            }
            out = if (existing != null) {
                out.replaceRange(existing.range, newCell)
            } else {
                insertCell(out, ref, newCell)
            }
        }
        return out
    }

    /** 行不存在就建行，单元格插在行尾（Excel 容忍乱序，显示仍按 r 引用定位） */
    private fun insertCell(xml: String, ref: String, cell: String): String {
        val row = ref.filter { it.isDigit() }
        val rowRe = Regex("""<row r="$row"[^>]*?(/>|>.*?</row>)""", RegexOption.DOT_MATCHES_ALL)
        val m = rowRe.find(xml) ?: return xml
        val v = m.value
        if (v.endsWith("/>")) {
            val open = v.removeSuffix("/>") + ">"
            return xml.replaceRange(m.range, "$open$cell</row>")
        }
        val closeIdx = v.lastIndexOf("</row>")
        if (closeIdx < 0) return xml
        val patched = v.substring(0, closeIdx) + cell + v.substring(closeIdx)
        return xml.replaceRange(m.range, patched)
    }

    // ---------------- zip 工具 ----------------

    private fun readEntry(file: File, name: String): String? = runCatching {
        ZipFile(file).use { zf ->
            val e = zf.getEntry(name) ?: return null
            if (e.size > MAX_DOC) return null
            zf.getInputStream(e).bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    private fun rewriteEntry(file: File, name: String, transform: (String) -> String): Boolean =
        rewriteEntries(file, mapOf(name to transform))

    private fun rewriteEntries(file: File, transforms: Map<String, (String) -> String>): Boolean = runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        ZipFile(file).use { zf ->
            ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                zf.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val transform = transforms[entry.name]
                    out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    val bytes = zf.getInputStream(entry).use { it.readBytes() }
                    if (transform == null) {
                        out.write(bytes)
                    } else {
                        val text = String(bytes, Charsets.UTF_8)
                        out.write(transform(text).toByteArray(Charsets.UTF_8))
                    }
                    out.closeEntry()
                }
            }
        }
        val backup = File(file.parentFile, file.name + ".bak")
        if (!file.renameTo(backup)) return false
        if (tmp.renameTo(file)) {
            backup.delete()
            true
        } else {
            backup.renameTo(file)
            tmp.delete()
            false
        }
    }.getOrDefault(false)

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** csv 直接重写，保留原分隔符 */
    fun writeCsv(file: File, rows: List<List<String>>, delimiter: Char) {
        val sb = StringBuilder()
        rows.forEach { row ->
            row.forEachIndexed { i, cell ->
                if (i > 0) sb.append(delimiter)
                if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
                    sb.append('"').append(cell.replace("\"", "\"\"")).append('"')
                } else {
                    sb.append(cell)
                }
            }
            sb.append('\n')
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }
}
