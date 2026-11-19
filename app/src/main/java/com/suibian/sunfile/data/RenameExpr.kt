package com.suibian.sunfile.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 批量重命名表达式，语法与 MT 一致：
 *
 * `{P}` 文件名前缀（abc.txt → abc）　`{S}` 后缀（abc.txt → .txt）
 * `{N}` 从 N 开始递增　`{zN}` 补零对齐（{z8} → 08 09 10）
 * `{T}` / `{T:yyyyMMdd}` 修改时间
 * `{E}` 图片 EXIF 时间（缺失时退化为修改时间）
 * `{AN}` `{AP}` `{AV}` `{AC}` APK 名称 / 包名 / 版本名 / 版本号
 */
object RenameExpr {

    const val DEFAULT_EXPR = "{P}{S}"

    data class Meta(
        val appName: String? = null,
        val packageName: String? = null,
        val versionName: String? = null,
        val versionCode: Long? = null
    )

    data class Item(
        val name: String,
        val lastModified: Long,
        /** 图片文件若读到 EXIF 时间则填这里 */
        val exifTime: Long? = null,
        val meta: Meta? = null
    )

    fun render(expr: String, item: Item, index: Int, timeFormat: String = "yyyyMMdd_HHmmss"): String {
        if (expr.isEmpty()) return item.name
        val dot = item.name.lastIndexOf('.')
        val prefix = if (dot > 0) item.name.substring(0, dot) else item.name
        val suffix = if (dot > 0) item.name.substring(dot) else ""
        val sb = StringBuilder(expr.length + 16)
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c != '{') {
                sb.append(c)
                i++
                continue
            }
            val end = expr.indexOf('}', i + 1)
            if (end < 0) {
                sb.append(expr, i, expr.length)
                break
            }
            val token = expr.substring(i + 1, end)
            sb.append(tokenValue(token, item, index, prefix, suffix, timeFormat))
            i = end + 1
        }
        return sb.toString()
    }

    private fun tokenValue(
        token: String,
        item: Item,
        index: Int,
        prefix: String,
        suffix: String,
        timeFormat: String
    ): String {
        if (token.isEmpty()) return "{}"
        return when {
            token == "P" -> prefix
            token == "S" -> suffix
            token == "E" -> format(item.exifTime ?: item.lastModified, "yyyyMMdd_HHmmss")
            token.startsWith("T") -> {
                val fmt = token.substringAfter(':', timeFormat).ifBlank { timeFormat }
                format(item.lastModified, fmt)
            }
            token == "AN" -> item.meta?.appName.orEmpty()
            token == "AP" -> item.meta?.packageName.orEmpty()
            token == "AV" -> item.meta?.versionName.orEmpty()
            token == "AC" -> item.meta?.versionCode?.toString().orEmpty()
            token.startsWith("z") && token.drop(1).toIntOrNull() != null -> {
                val start = token.drop(1).toInt()
                val width = start.toString().length.coerceAtLeast(2)
                (start + index).toString().padStart(width, '0')
            }
            token == "AI" || token == "AB" -> ""
            token.toIntOrNull() != null -> (token.toInt() + index).toString()
            else -> "{$token}"
        }
    }

    private fun format(millis: Long, pattern: String): String =
        runCatching {
            SimpleDateFormat(pattern, Locale.US).format(Date(millis))
        }.getOrDefault(millis.toString())

    fun preview(expr: String, items: List<Item>, timeFormat: String = "yyyyMMdd_HHmmss"): List<String> =
        items.mapIndexed { i, item -> render(expr, item, i, timeFormat) }

    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    /** 去掉文件名里不能出现的字符，避免重命名直接失败 */
    fun sanitize(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) if (c !in ILLEGAL) sb.append(c)
        val s = sb.toString().trim()
        return if (s.isEmpty() || s == "." || s == "..") "" else s
    }
}
