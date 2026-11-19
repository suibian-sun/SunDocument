package com.suibian.sunfile.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suibian.sunfile.ui.theme.SunColors
import java.io.File

/**
 * HTML / Markdown 预览：渲染在 WebView 里，样式跟随主题。
 * 相对路径的图片、CSS 以文件所在目录为基准，因此本地页面也能正常显示。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewOverlay(
    file: File,
    content: String,
    markdown: Boolean,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val palette = SunColors
    val html = remember(file, content, markdown, palette) {
        val body = if (markdown) Markdown.toHtml(content) else content
        wrapHtml(body, palette.dark, palette.Bg, palette.TextPrimary, palette.TextSecondary, palette.Accent)
    }
    val baseUrl = remember(file) { "file://" + (file.parentFile?.absolutePath ?: "/") + "/" }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = SunColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (markdown) "Markdown 预览" else "HTML 预览",
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkImage = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val current = view.tag as? String
                if (current != html) {
                    view.tag = html
                    view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                }
            }
        )
    }
}

private fun wrapHtml(
    body: String,
    dark: Boolean,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    sub: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color
): String {
    val b = hex(bg)
    val f = hex(fg)
    val s = hex(sub)
    val a = hex(accent)
    val codeBg = if (dark) "#1D232A" else "#EEF0F3"
    val preBg = if (dark) "#13171C" else "#F4F5F7"
    val line = if (dark) "#242B33" else "#DCE0E5"
    val quote = if (dark) "#2A5FAF" else "#9DBAF3"
    return """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3">
<style>
*{box-sizing:border-box}
html,body{margin:0;padding:0;background:$b;color:$f;
  font:15px/1.75 -apple-system,"Noto Sans CJK SC","PingFang SC",Roboto,sans-serif;
  word-wrap:break-word;-webkit-text-size-adjust:100%}
body{padding:18px 18px 44px}
h1,h2,h3,h4,h5,h6{color:$f;line-height:1.35;margin:1.4em 0 .6em;font-weight:600}
h1{font-size:1.6em}h2{font-size:1.35em}h3{font-size:1.18em}h4{font-size:1.05em}
p{margin:.7em 0;color:$s}
a{color:$a;text-decoration:none;border-bottom:1px solid ${a}55}
code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.88em;background:$codeBg;
  padding:.15em .4em;border-radius:5px;color:$f}
pre{background:$preBg;border:1px solid $line;border-radius:10px;padding:12px 14px;overflow:auto}
pre code{background:none;padding:0;font-size:.86em;line-height:1.6}
blockquote{margin:.9em 0;padding:.2em 14px;border-left:3px solid $quote;color:$s}
ul,ol{padding-left:1.4em;margin:.6em 0}
li{margin:.25em 0;color:$s}
hr{border:none;border-top:1px solid $line;margin:1.4em 0}
table{border-collapse:collapse;width:100%;margin:.9em 0;font-size:.92em}
th,td{border:1px solid $line;padding:7px 9px;text-align:left}
th{background:$codeBg;color:$f;font-weight:600}
td{color:$s}
img{max-width:100%;height:auto;border-radius:8px;margin:.5em 0}
input[type=checkbox]{margin-right:.4em}
</style></head><body>$body</body></html>
""".trimIndent()
}

private fun hex(c: androidx.compose.ui.graphics.Color): String {
    val r = (c.red * 255).toInt().coerceIn(0, 255)
    val g = (c.green * 255).toInt().coerceIn(0, 255)
    val b = (c.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

/** 极简 Markdown → HTML：覆盖日常写作需要的语法，不依赖第三方库 */
object Markdown {

    fun toHtml(md: String): String {
        val src = md.replace("\r\n", "\n").replace('\r', '\n')
        // 先把围栏代码整块摘出来，避免内部内容被当作 Markdown
        val codes = ArrayList<String>()
        val masked = Regex("```([^\\n]*)\\n([\\s\\S]*?)```").replace(src) { m ->
            val lang = m.groupValues[1].trim()
            val code = escape(m.groupValues[2])
            val cls = if (lang.isEmpty()) "" else " class=\"language-$lang\""
            codes.add("<pre><code$cls>$code</code></pre>")
            "\u0000CODE${codes.size - 1}\u0000"
        }

        val out = StringBuilder()
        val lines = masked.split('\n')
        var i = 0
        var listType = ""
        var inQuote = false

        fun closeList() {
            if (listType.isNotEmpty()) {
                out.append("</").append(listType).append(">\n")
                listType = ""
            }
        }
        fun closeQuote() {
            if (inQuote) {
                out.append("</blockquote>\n")
                inQuote = false
            }
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            // 占位的代码块
            if (line.startsWith("\u0000CODE")) {
                closeList(); closeQuote()
                val idx = line.trim('\u0000').removePrefix("CODE").toIntOrNull()
                if (idx != null) out.append(codes.getOrElse(idx) { "" }).append('\n')
                i++
                continue
            }

            when {
                line.isBlank() -> {
                    closeList(); closeQuote()
                    i++
                }

                line.startsWith("#") -> {
                    closeList(); closeQuote()
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = line.drop(level).trim()
                    out.append("<h$level>").append(inline(text)).append("</h$level>\n")
                    i++
                }

                Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$").matches(line) -> {
                    closeList(); closeQuote()
                    out.append("<hr>\n")
                    i++
                }

                line.startsWith(">") -> {
                    closeList()
                    if (!inQuote) { out.append("<blockquote>\n"); inQuote = true }
                    out.append("<p>").append(inline(line.removePrefix(">").trim())).append("</p>\n")
                    i++
                }

                isTableStart(lines, i) -> {
                    closeList(); closeQuote()
                    val header = splitRow(line)
                    i += 2
                    out.append("<table><thead><tr>")
                    header.forEach { out.append("<th>").append(inline(it)).append("</th>") }
                    out.append("</tr></thead><tbody>\n")
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        val cells = splitRow(lines[i])
                        out.append("<tr>")
                        cells.forEach { out.append("<td>").append(inline(it)).append("</td>") }
                        out.append("</tr>\n")
                        i++
                    }
                    out.append("</tbody></table>\n")
                }

                Regex("^\\s*([-*+])\\s+").containsMatchIn(line) -> {
                    closeQuote()
                    if (listType != "ul") { closeList(); out.append("<ul>\n"); listType = "ul" }
                    out.append("<li>").append(inline(line.replaceFirst(Regex("^\\s*[-*+]\\s+"), ""))).append("</li>\n")
                    i++
                }

                Regex("^\\s*\\d+\\.\\s+").containsMatchIn(line) -> {
                    closeQuote()
                    if (listType != "ol") { closeList(); out.append("<ol>\n"); listType = "ol" }
                    out.append("<li>").append(inline(line.replaceFirst(Regex("^\\s*\\d+\\.\\s+"), ""))).append("</li>\n")
                    i++
                }

                else -> {
                    closeList(); closeQuote()
                    // 连续普通行合并成一段
                    val para = StringBuilder(line)
                    var j = i + 1
                    while (j < lines.size) {
                        val nxt = lines[j]
                        if (nxt.isBlank() || nxt.startsWith("#") || nxt.startsWith(">") ||
                            nxt.startsWith("```") || Regex("^\\s*([-*+]|\\d+\\.)\\s+").containsMatchIn(nxt)
                        ) break
                        para.append('\n').append(nxt)
                        j++
                    }
                    out.append("<p>").append(inline(para.toString())).append("</p>\n")
                    i = j
                }
            }
        }
        closeList(); closeQuote()
        return out.toString()
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        if (!lines[i].contains('|')) return false
        return Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$").matches(lines[i + 1])
    }

    private fun splitRow(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun inline(text: String): String {
        var s = escape(text)
        s = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)").replace(s) { m ->
            "<img alt=\"${m.groupValues[1]}\" src=\"${m.groupValues[2]}\">"
        }
        s = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)").replace(s) { m ->
            "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
        }
        s = Regex("`([^`]+)`").replace(s) { "<code>${it.groupValues[1]}</code>" }
        s = Regex("\\*\\*([^*]+)\\*\\*").replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = Regex("__([^_]+)__").replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)").replace(s) { "<em>${it.groupValues[1]}</em>" }
        s = Regex("~~([^~]+)~~").replace(s) { "<del>${it.groupValues[1]}</del>" }
        s = s.replace("\n", "<br>")
        return s
    }

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
