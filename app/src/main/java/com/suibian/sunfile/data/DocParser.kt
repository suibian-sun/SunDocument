package com.suibian.sunfile.data

import java.io.File
import java.util.zip.ZipFile

/** 文档里的一段内容，UI 按样式排版 */
enum class DocStyle { TITLE, HEADING, SUBHEADING, PARAGRAPH, BULLET, QUOTE, CODE }

data class DocBlock(val style: DocStyle, val text: String)

/** 表格页（xlsx / ods / csv），rows 只保留非空单元格 */
data class SheetTable(val name: String, val rows: List<List<String>>)

data class DocContent(
    val blocks: List<DocBlock> = emptyList(),
    val sheets: List<SheetTable> = emptyList(),
    val meta: String = ""
)

/**
 * Office 文档读取：只取文字与表格，不解析样式。
 * docx/xlsx/pptx 是 zip + xml，odt/ods/odp 同理，csv 直接切分；
 * 老式 doc/xls/ppt 是二进制格式，交给系统应用。
 */
object DocParser {

    private const val MAX_XML = 24 * 1024 * 1024

    fun parse(file: File): DocContent {
        val ext = FileKinds.extensionOf(file)
        return when (ext) {
            "docx", "docm" -> parseDocx(file)
            "xlsx", "xlsm" -> parseXlsx(file)
            "pptx", "pptm" -> parsePptx(file)
            "odt" -> parseOdf(file, tables = false)
            "ods" -> parseOdf(file, tables = true)
            "odp" -> parseOdf(file, tables = false)
            "csv", "tsv" -> DocContent(
                sheets = listOf(SheetTable(file.nameWithoutExtension, parseDelimited(file, if (ext == "tsv") '\t' else ','))),
                meta = "表格"
            )
            else -> throw java.io.IOException("这种格式需要其他应用打开")
        }
    }

    // ---------------- docx ----------------

    private fun parseDocx(file: File): DocContent {
        val xml = readZipText(file, "word/document.xml") ?: throw java.io.IOException("无法读取文档内容")
        val blocks = ArrayList<DocBlock>()
        Regex("""<w:p[ >].*?</w:p>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val p = m.value
            val style = Regex("""<w:pStyle\s+w:val="([^"]+)"""")
                .find(p)?.groupValues?.get(1)?.lowercase()
            val body = buildString {
                Regex("""<w:t(?: [^>]*)?>(.*?)</w:t>""", RegexOption.DOT_MATCHES_ALL).findAll(p).forEach {
                    append(unescape(it.groupValues[1]))
                }
            }.trim()
            if (body.isEmpty()) return@forEach
            val s = when {
                style == null -> DocStyle.PARAGRAPH
                style.contains("title") -> DocStyle.TITLE
                style.startsWith("heading1") || style.contains("heading 1") -> DocStyle.HEADING
                style.startsWith("heading") -> DocStyle.SUBHEADING
                style.contains("quote") -> DocStyle.QUOTE
                style.contains("code") -> DocStyle.CODE
                else -> DocStyle.PARAGRAPH
            }
            blocks.add(DocBlock(s, body))
            if (blocks.size > 20000) return@forEach
        }
        return DocContent(blocks = blocks, meta = "${blocks.size} 段")
    }

    // ---------------- xlsx ----------------

    private fun parseXlsx(file: File): DocContent {
        val shared = ArrayList<String>()
        readZipText(file, "xl/sharedStrings.xml")?.let { xml ->
            Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { si ->
                val text = Regex("""<t(?: [^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
                shared.add(text)
            }
        }
        val names = ArrayList<String>()
        readZipText(file, "xl/workbook.xml")?.let { xml ->
            Regex("""<sheet\s+[^>]*name="([^"]+)"""").findAll(xml).forEach { names.add(unescape(it.groupValues[1])) }
        }
        val entries = ZipFile(file).use { zf ->
            zf.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .map { it.name }
                .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
                .toList()
        }
        val sheets = ArrayList<SheetTable>()
        entries.forEachIndexed { idx, name ->
            val xml = readZipText(file, name) ?: return@forEachIndexed
            val rows = ArrayList<List<String>>()
            Regex("""<row([^>]*)>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { row ->
                if (rows.size >= 5000) return@forEach
                val cells = sortedMapOf<Int, String>()
                Regex("""<c([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[2]).forEach { c ->
                    val attrs = c.groupValues[1]
                    val col = colIndex(Regex("""r="([A-Z]+)\d+"""").find(attrs)?.groupValues?.get(1) ?: "")
                    val type = Regex("""t="([^"]+)"""").find(attrs)?.groupValues?.get(1)
                    val inner = c.groupValues[2]
                    val raw = when (type) {
                        "s" -> Regex("""<v>(.*?)</v>""").find(inner)?.groupValues?.get(1)
                            ?.toIntOrNull()?.let { shared.getOrNull(it) }
                        "inlineStr", "str" -> Regex("""<t(?: [^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                            .find(inner)?.groupValues?.get(1)?.let { unescape(it) }
                        else -> Regex("""<v>(.*?)</v>""").find(inner)?.groupValues?.get(1)
                    }
                    if (!raw.isNullOrBlank()) cells[col] = raw.trim()
                }
                if (cells.isNotEmpty()) {
                    val maxCol = cells.keys.max()
                    rows.add((0..maxCol).map { cells[it] ?: "" })
                }
            }
            val title = names.getOrNull(idx) ?: "Sheet${idx + 1}"
            if (rows.isNotEmpty()) sheets.add(SheetTable(title, rows))
        }
        if (sheets.isEmpty()) throw java.io.IOException("这个表格没有可显示的单元格")
        return DocContent(sheets = sheets, meta = "${sheets.size} 个工作表")
    }

    private fun colIndex(letters: String): Int {
        if (letters.isEmpty()) return 0
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return (n - 1).coerceAtLeast(0)
    }

    // ---------------- pptx ----------------

    private fun parsePptx(file: File): DocContent {
        val slides = ZipFile(file).use { zf ->
            zf.entries().asSequence()
                .filter { it.name.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
                .map { it.name }
                .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
                .toList()
        }
        val blocks = ArrayList<DocBlock>()
        slides.forEachIndexed { i, name ->
            val xml = readZipText(file, name) ?: return@forEachIndexed
            var first = true
            Regex("""<a:p>.*?</a:p>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { p ->
                val text = Regex("""<a:t>(.*?)</a:t>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(p.value).joinToString("") { unescape(it.groupValues[1]) }.trim()
                if (text.isEmpty()) return@forEach
                if (first) {
                    blocks.add(DocBlock(DocStyle.HEADING, "第 ${i + 1} 页 · $text"))
                    first = false
                } else {
                    blocks.add(DocBlock(DocStyle.PARAGRAPH, text))
                }
            }
        }
        return DocContent(blocks = blocks, meta = "${slides.size} 页")
    }

    // ---------------- odf ----------------

    private fun parseOdf(file: File, tables: Boolean): DocContent {
        val xml = readZipText(file, "content.xml") ?: throw java.io.IOException("无法读取文档内容")
        val sheets = ArrayList<SheetTable>()
        if (tables) {
            Regex("""<table:table\s+[^>]*table:name="([^"]*)"[^>]*>(.*?)</table:table>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml).forEach { t ->
                    val rows = ArrayList<List<String>>()
                    Regex("""<table:table-row[^>]*>(.*?)</table:table-row>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(t.groupValues[2]).forEach { r ->
                            if (rows.size >= 5000) return@forEach
                            val cells = Regex("""<table:table-cell[^>]*>(.*?)</table:table-cell>""", RegexOption.DOT_MATCHES_ALL)
                                .findAll(r.groupValues[1])
                                .map { stripTags(it.groupValues[1]).trim() }
                                .toList()
                            if (cells.any { it.isNotEmpty() }) rows.add(cells)
                        }
                    if (rows.isNotEmpty()) {
                        sheets.add(SheetTable(t.groupValues[1].ifEmpty { "Sheet${sheets.size + 1}" }, rows))
                    }
                }
            return DocContent(sheets = sheets, meta = "${sheets.size} 个工作表")
        }
        val blocks = ArrayList<DocBlock>()
        Regex("""<text:(p|h)[^>]*>(.*?)</text:\1>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val text = stripTags(m.groupValues[2]).trim()
            if (text.isEmpty()) return@forEach
            val style = if (m.groupValues[1] == "h") DocStyle.HEADING else DocStyle.PARAGRAPH
            blocks.add(DocBlock(style, text))
        }
        return DocContent(blocks = blocks, meta = "${blocks.size} 段")
    }

    // ---------------- csv ----------------

    private fun parseDelimited(file: File, delimiter: Char): List<List<String>> {
        val text = file.inputStream().use { input ->
            val buf = ByteArray(minOf(file.length(), 4L * 1024 * 1024).toInt().coerceAtLeast(1))
            val n = input.read(buf)
            String(buf, 0, n.coerceAtLeast(0), FileRepo.charsetOf("UTF-8") ?: Charsets.UTF_8)
                .let { if (it.contains('\uFFFD')) String(buf, 0, n, FileRepo.charsetOf("GB18030")!!) else it }
        }
        val rows = ArrayList<List<String>>()
        val cell = StringBuilder()
        val row = ArrayList<String>()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    if (quoted && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"'); i++
                    } else quoted = !quoted
                }
                ch == delimiter && !quoted -> {
                    row.add(cell.toString()); cell.clear()
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row.toList())
                    row.clear()
                }
                else -> cell.append(ch)
            }
            i++
            if (rows.size > 5000) break
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row.toList())
        }
        return rows
    }

    // ---------------- 工具 ----------------

    private fun readZipText(file: File, name: String): String? = runCatching {
        ZipFile(file).use { zf ->
            val entry = zf.getEntry(name) ?: return null
            if (entry.size > MAX_XML) return null
            zf.getInputStream(entry).use { input ->
                val out = java.io.ByteArrayOutputStream()
                input.copyTo(out, 64 * 1024)
                out.toString("UTF-8")
            }
        }
    }.getOrNull()

    private fun stripTags(xml: String): String =
        unescape(xml.replace(Regex("""<[^>]+>"""), " "))

    fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val end = s.indexOf(';', i + 1)
                if (end in (i + 2)..(i + 12)) {
                    val body = s.substring(i + 1, end)
                    val rep = when {
                        body == "amp" -> "&"
                        body == "lt" -> "<"
                        body == "gt" -> ">"
                        body == "quot" -> "\""
                        body == "apos" -> "'"
                        body == "nbsp" -> " "
                        body.startsWith("#x") || body.startsWith("#X") ->
                            body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        body.startsWith("#") ->
                            body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (rep != null) {
                        sb.append(rep)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
