package com.suibian.sunfile.data

/**
 * 仿 MT 的文件名过滤表达式。
 *
 * | 输入        | 含义                               |
 * | ----------- | ---------------------------------- |
 * | `zip`       | 名称包含 zip（忽略大小写）          |
 * | `!zip`      | 名称不包含 zip                      |
 * | `/\d`       | 正则匹配                            |
 * | `!/\.png$`  | 正则否定匹配                        |
 *
 * 正则写错时不抛异常，退化为普通包含匹配，避免列表直接空掉。
 */
object Query {

    data class Spec(
        val raw: String,
        val negate: Boolean,
        val regex: Regex?,
        val keyword: String
    ) {
        val isActive: Boolean get() = raw.isNotBlank()
    }

    fun parse(raw: String): Spec {
        val text = raw.trim()
        if (text.isEmpty()) return Spec("", false, null, "")
        return when {
            text.startsWith("!/") -> Spec(text, true, compile(text.substring(2)), text.substring(2))
            text.startsWith("/") -> Spec(text, false, compile(text.substring(1)), text.substring(1))
            text.startsWith("!") -> Spec(text, true, null, text.substring(1))
            else -> Spec(text, false, null, text)
        }
    }

    private fun compile(pattern: String): Regex? =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()

    fun matches(spec: Spec, name: String): Boolean {
        if (spec.raw.isBlank()) return true
        val hit = when {
            spec.regex != null -> spec.regex.containsMatchIn(name)
            spec.keyword.isNotEmpty() -> name.contains(spec.keyword, ignoreCase = true)
            else -> true
        }
        return if (spec.negate) !hit else hit
    }

    fun filter(entries: List<FileEntry>, raw: String): List<FileEntry> {
        val spec = parse(raw)
        if (!spec.isActive) return entries
        return entries.filter { matches(spec, it.name) }
    }
}
