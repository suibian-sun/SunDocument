package com.suibian.sunfile.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color

/** 语法高亮使用的语言 */
enum class Lang(val label: String) {
    PLAIN("纯文本"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    PYTHON("Python"),
    SHELL("Shell"),
    C("C"),
    CPP("C++"),
    CSHARP("C#"),
    GO("Go"),
    RUST("Rust"),
    PHP("PHP"),
    RUBY("Ruby"),
    SWIFT("Swift"),
    JSON("JSON"),
    XML("XML"),
    HTML("HTML"),
    CSS("CSS"),
    SQL("SQL"),
    YAML("YAML"),
    PROPERTIES("Properties"),
    MARKDOWN("Markdown"),
    SMALI("Smali"),
    GRADLE("Gradle"),
    LUA("Lua"),
    DART("Dart"),
    DOCKER("Dockerfile"),
    INI("INI")
}

data class LangSpec(
    val keywords: Set<String> = emptySet(),
    val lineComment: String? = null,
    val blockStart: String? = null,
    val blockEnd: String? = null,
    val annotation: Boolean = false,
    val hashPreprocessor: Boolean = false,
    val xmlLike: Boolean = false,
    val cssLike: Boolean = false
)

object Syntax {

    val KeywordColor = Color(0xFFC792EA)
    val StringColor = Color(0xFF9ECE6A)
    val CommentColor = Color(0xFF5F6B76)
    val NumberColor = Color(0xFFE0AF68)
    val AnnotationColor = Color(0xFF7DCFFF)
    val TagColor = Color(0xFF7DCFFF)
    val AttrColor = Color(0xFFE0AF68)

    private const val HIGHLIGHT_LIMIT = 90_000

    private val kotlin = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "vararg", "typealias",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue", "throw", "try", "catch",
        "finally", "is", "as", "in", "out", "by", "where", "init", "constructor", "this", "super", "null",
        "true", "false", "abstract", "open", "final", "override", "private", "public", "protected",
        "internal", "data", "sealed", "enum", "companion", "inline", "suspend", "lateinit", "const",
        "crossinline", "noinline", "reified", "operator", "infix", "tailrec", "external", "annotation",
        "get", "set", "field", "it", "until", "downTo", "step", "async", "await"
    )
    private val java = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "record", "sealed", "permits", "yield"
    )
    private val js = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do",
        "else", "export", "extends", "finally", "for", "function", "if", "import", "in", "instanceof",
        "let", "new", "return", "super", "switch", "this", "throw", "try", "typeof", "var", "void",
        "while", "with", "yield", "async", "await", "true", "false", "null", "undefined", "interface",
        "type", "enum", "implements", "readonly", "public", "private", "protected", "static", "of"
    )
    private val python = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "True",
        "False", "None", "self", "match", "case"
    )
    private val shell = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac",
        "function", "in", "select", "time", "return", "exit", "break", "continue", "local", "export",
        "readonly", "declare", "unset", "shift", "source", "echo", "cd", "sudo", "rm", "cp", "mv",
        "chmod", "chown", "grep", "sed", "awk", "cat", "find", "xargs", "test"
    )
    private val cLike = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict",
        "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
        "unsigned", "void", "volatile", "while", "class", "namespace", "template", "typename", "public",
        "private", "protected", "virtual", "override", "new", "delete", "try", "catch", "throw",
        "using", "constexpr", "nullptr", "true", "false", "include", "define", "ifdef", "ifndef", "endif"
    )
    private val csharp = cLike + setOf(
        "string", "bool", "decimal", "var", "foreach", "in", "is", "as", "async", "await", "partial",
        "readonly", "sealed", "params", "base", "this", "get", "set", "value"
    )
    private val go = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for",
        "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select",
        "struct", "switch", "type", "var", "nil", "true", "false", "make", "new", "len", "cap", "append"
    )
    private val rust = setOf(
        "as", "break", "const", "continue", "crate", "else", "enum", "extern", "false", "fn", "for",
        "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
        "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe", "use", "where",
        "while", "async", "await", "dyn"
    )
    private val php = setOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone", "const",
        "continue", "declare", "default", "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor",
        "endforeach", "endif", "endswitch", "endwhile", "extends", "final", "finally", "fn", "for",
        "foreach", "function", "global", "goto", "if", "implements", "include", "instanceof", "insteadof",
        "interface", "isset", "list", "match", "namespace", "new", "or", "print", "private", "protected",
        "public", "readonly", "require", "return", "static", "switch", "throw", "trait", "try", "unset",
        "use", "var", "while", "xor", "yield", "true", "false", "null"
    )
    private val ruby = setOf(
        "BEGIN", "END", "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do",
        "else", "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not",
        "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef", "unless",
        "until", "when", "while", "yield", "require", "attr_accessor", "puts"
    )
    private val swift = setOf(
        "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import",
        "init", "inout", "internal", "let", "open", "operator", "private", "protocol", "public",
        "static", "struct", "subscript", "typealias", "var", "break", "case", "continue", "default",
        "defer", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch",
        "where", "while", "as", "catch", "false", "is", "nil", "rethrows", "super", "self", "throw",
        "throws", "true", "try", "await", "async", "some", "any"
    )
    private val sql = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
        "TABLE", "ALTER", "DROP", "INDEX", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP",
        "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "AS", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY",
        "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
        "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "ALL", "EXISTS", "LIKE", "IN", "BETWEEN"
    )
    private val smali = setOf(
        ".class", ".super", ".source", ".field", ".method", ".end", ".annotation", ".param", ".line",
        ".local", ".registers", ".locals", ".prologue", ".catch", ".array-data", ".packed-switch",
        "invoke-virtual", "invoke-static", "invoke-direct", "invoke-super", "invoke-interface",
        "move-result", "move-result-wide", "move-object", "move", "const", "const-string", "const/4",
        "const/16", "const-wide", "new-instance", "new-array", "iget", "iput", "sget", "sput", "return",
        "return-void", "return-object", "if-eq", "if-ne", "if-lt", "if-ge", "if-eqz", "if-nez",
        "goto", "packed-switch", "check-cast", "instance-of", "throw", "monitor-enter", "monitor-exit"
    )
    private val lua = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while", "require"
    )
    private val dart = setOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "default", "deferred", "do", "dynamic", "else", "enum", "export", "extends",
        "extension", "external", "factory", "false", "final", "finally", "for", "Function", "get",
        "hide", "if", "implements", "import", "in", "interface", "is", "late", "library", "mixin",
        "new", "null", "on", "operator", "part", "required", "rethrow", "return", "set", "show",
        "static", "super", "switch", "sync", "this", "throw", "true", "try", "typedef", "var", "void",
        "while", "with", "yield"
    )

    fun langFor(fileName: String): Lang {
        val name = fileName.lowercase()
        val ext = name.substringAfterLast('.', "")
        return when {
            name == "dockerfile" || name.startsWith("dockerfile.") -> Lang.DOCKER
            name == "makefile" || name == "cmakelists.txt" -> Lang.SHELL
            ext == "kt" || ext == "kts" -> Lang.KOTLIN
            ext == "java" -> Lang.JAVA
            ext == "js" || ext == "mjs" || ext == "cjs" || ext == "jsx" || ext == "vue" || ext == "svelte" -> Lang.JAVASCRIPT
            ext == "ts" || ext == "tsx" -> Lang.TYPESCRIPT
            ext == "py" -> Lang.PYTHON
            ext == "sh" || ext == "bash" || ext == "zsh" || ext == "bat" || ext == "cmd" || ext == "ps1" -> Lang.SHELL
            ext == "c" || ext == "h" -> Lang.C
            ext == "cpp" || ext == "cc" || ext == "cxx" || ext == "hpp" || ext == "hh" -> Lang.CPP
            ext == "cs" -> Lang.CSHARP
            ext == "go" -> Lang.GO
            ext == "rs" -> Lang.RUST
            ext == "php" -> Lang.PHP
            ext == "rb" -> Lang.RUBY
            ext == "swift" -> Lang.SWIFT
            ext == "json" || ext == "json5" -> Lang.JSON
            ext == "xml" || ext == "plist" || ext == "svg" || ext == "xhtml" -> Lang.XML
            ext == "html" || ext == "htm" -> Lang.HTML
            ext == "css" || ext == "scss" || ext == "less" -> Lang.CSS
            ext == "sql" -> Lang.SQL
            ext == "yml" || ext == "yaml" -> Lang.YAML
            ext == "properties" || ext == "conf" || ext == "cfg" || ext == "ini" || ext == "env" -> Lang.PROPERTIES
            ext == "md" || ext == "markdown" -> Lang.MARKDOWN
            ext == "smali" -> Lang.SMALI
            ext == "gradle" || ext == "groovy" -> Lang.GRADLE
            ext == "lua" -> Lang.LUA
            ext == "dart" -> Lang.DART
            else -> Lang.PLAIN
        }
    }

    private fun specOf(lang: Lang): LangSpec = when (lang) {
        Lang.KOTLIN -> LangSpec(kotlin, "//", "/*", "*/", annotation = true)
        Lang.JAVA -> LangSpec(java, "//", "/*", "*/", annotation = true)
        Lang.JAVASCRIPT, Lang.TYPESCRIPT, Lang.DART -> LangSpec(js, "//", "/*", "*/", annotation = true)
        Lang.PYTHON -> LangSpec(python, "#", "\"\"\"", "\"\"\"", annotation = true)
        Lang.SHELL, Lang.DOCKER -> LangSpec(shell, "#")
        Lang.C, Lang.CPP -> LangSpec(cLike, "//", "/*", "*/", hashPreprocessor = true)
        Lang.CSHARP -> LangSpec(csharp, "//", "/*", "*/", annotation = true)
        Lang.GO -> LangSpec(go, "//", "/*", "*/")
        Lang.RUST -> LangSpec(rust, "//", "/*", "*/", annotation = true)
        Lang.PHP -> LangSpec(php, "//", "/*", "*/")
        Lang.RUBY -> LangSpec(ruby, "#")
        Lang.SWIFT -> LangSpec(swift, "//", "/*", "*/", annotation = true)
        Lang.JSON -> LangSpec(emptySet(), null, null, null)
        Lang.XML, Lang.HTML -> LangSpec(emptySet(), null, "<!--", "-->", xmlLike = true)
        Lang.CSS -> LangSpec(emptySet(), null, "/*", "*/", cssLike = true)
        Lang.SQL -> LangSpec(sql, "--", "/*", "*/")
        Lang.YAML -> LangSpec(emptySet(), "#")
        Lang.PROPERTIES, Lang.INI -> LangSpec(emptySet(), "#")
        Lang.MARKDOWN -> LangSpec(emptySet(), null, "<!--", "-->")
        Lang.SMALI -> LangSpec(smali, "#", null, null, annotation = true)
        Lang.GRADLE -> LangSpec(kotlin, "//", "/*", "*/", annotation = true)
        Lang.LUA -> LangSpec(lua, "--", "--[[", "]]")
        Lang.PLAIN -> LangSpec()
    }

    fun highlight(text: String, lang: Lang): AnnotatedString {
        if (lang == Lang.PLAIN || text.length > HIGHLIGHT_LIMIT) return AnnotatedString(text)
        val spec = specOf(lang)
        val builder = AnnotatedString.Builder(text)
        val n = text.length
        var i = 0

        fun style(color: Color, start: Int, end: Int, italic: Boolean = false, bold: Boolean = false) {
            if (end <= start) return
            builder.addStyle(
                SpanStyle(
                    color = color,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    fontWeight = if (bold) FontWeight.Medium else null
                ),
                start,
                end.coerceAtMost(n)
            )
        }

        while (i < n) {
            val c = text[i]

            // 块注释
            if (spec.blockStart != null && spec.blockEnd != null && text.startsWith(spec.blockStart, i)) {
                val close = text.indexOf(spec.blockEnd, i + spec.blockStart.length)
                val end = if (close < 0) n else close + spec.blockEnd.length
                style(CommentColor, i, end, italic = true)
                i = end
                continue
            }

            // 行注释
            if (spec.lineComment != null && text.startsWith(spec.lineComment, i)) {
                val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                style(CommentColor, i, end, italic = true)
                i = end
                continue
            }

            // 预处理指令
            if (spec.hashPreprocessor && c == '#' && (i == 0 || text[i - 1] == '\n')) {
                val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                style(AnnotationColor, i, end)
                i = end
                continue
            }

            // XML/HTML 标签名
            if (spec.xmlLike && c == '<') {
                var j = i + 1
                if (j < n && (text[j] == '/' || text[j] == '?' || text[j] == '!')) j++
                val nameStart = j
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == ':' || text[j] == '_')) j++
                if (j > nameStart) style(TagColor, nameStart, j, bold = true)
                if (i + 1 < n && text[i + 1] == '!') {
                    // <!DOCTYPE ...> 或注释已处理
                }
                i++
                continue
            }

            // 字符串
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                var end = n
                while (j < n) {
                    val ch = text[j]
                    if (ch == '\\') {
                        j += 2
                        continue
                    }
                    if (ch == c) {
                        end = j + 1
                        break
                    }
                    if (ch == '\n' && c != '`') {
                        end = j
                        break
                    }
                    j++
                }
                style(StringColor, i, end)
                i = end
                continue
            }

            // 注解 / 装饰器
            if (spec.annotation && c == '@') {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.')) j++
                style(AnnotationColor, i, j)
                i = j
                continue
            }

            // 数字
            if (c.isDigit()) {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_' || text[j] == 'x')) j++
                style(NumberColor, i, j)
                i = j
                continue
            }

            // 标识符 / 关键字
            if (c.isLetter() || c == '_' || (c == '.' && lang == Lang.SMALI)) {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.') ) j++
                val word = text.substring(i, j)
                val lower = if (lang == Lang.SQL) word.uppercase() else word
                if (lower in spec.keywords) {
                    style(KeywordColor, i, j, bold = true)
                }
                i = j
                continue
            }

            i++
        }
        return builder.toAnnotatedString()
    }

    /** 语法高亮的 VisualTransformation */
    class CodeHighlightTransformation(private val lang: Lang) :
        androidx.compose.ui.text.input.VisualTransformation {
        override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText =
            androidx.compose.ui.text.input.TransformedText(
                highlight(text.text, lang),
                androidx.compose.ui.text.input.OffsetMapping.Identity
            )
    }

    // ---------------- 代码补全 ----------------

    private val snippets = mapOf(
        "fun" to "fun name() {\n    \n}",
        "if" to "if (condition) {\n    \n}",
        "for" to "for (i in 0 until n) {\n    \n}",
        "while" to "while (condition) {\n    \n}",
        "when" to "when (value) {\n    else -> Unit\n}",
        "try" to "try {\n    \n} catch (e: Exception) {\n    \n}",
        "class" to "class Name {\n    \n}",
        "logd" to "android.util.Log.d(\"TAG\", \"message\")",
        "toast" to "Toast.makeText(context, \"message\", Toast.LENGTH_SHORT).show()"
    )

    fun completions(lang: Lang, prefix: String, document: String, limit: Int = 8): List<String> {
        if (prefix.isEmpty() || prefix.length > 24) return emptyList()
        val spec = specOf(lang)
        val out = LinkedHashSet<String>()
        if (lang != Lang.PLAIN) {
            spec.keywords.filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
                .take(4).forEach { out.add(it) }
            snippets.keys.filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
                .take(2).forEach { out.add("$it  ⤶") }
        }
        if (prefix.length >= 2) {
            val tail = if (document.length > 30_000) document.takeLast(30_000) else document
            Regex("[A-Za-z_][A-Za-z0-9_]{2,}").findAll(tail)
                .map { it.value }
                .filter { it.startsWith(prefix) && it != prefix && it.length <= 30 }
                .take(24)
                .forEach { out.add(it) }
        }
        return out.take(limit).toList()
    }

    /** 补全项对应的真实插入文本（列表里的 ⤶ 只是提示） */
    fun applySnippet(item: String, prefix: String): String {
        val key = item.removeSuffix("  ⤶")
        return snippets[key] ?: item
    }

    // ---------------- 智能编辑 ----------------

    fun smartEdit(new: TextFieldValue, old: TextFieldValue): TextFieldValue {
        if (new.composition != null) return new
        // 输入了单个字符
        if (new.text.length == old.text.length + 1 && new.selection.collapsed) {
            val caret = new.selection.start
            if (caret <= 0 || caret > new.text.length) return new
            val inserted = new.text[caret - 1]
            if (inserted == '\n') {
                val before = new.text.substring(0, caret - 1)
                val lineStart = before.lastIndexOf('\n') + 1
                val prevLine = before.substring(lineStart)
                var indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                val trimmed = prevLine.trimEnd()
                if (trimmed.endsWith("{") || trimmed.endsWith(":") || trimmed.endsWith("(") ||
                    trimmed.endsWith("[") || trimmed.endsWith("->")
                ) {
                    indent += "    "
                }
                if (indent.isNotEmpty()) {
                    val text = new.text.substring(0, caret) + indent + new.text.substring(caret)
                    return TextFieldValue(text, TextRange(caret + indent.length))
                }
                return new
            }
            if (inserted == '{' || inserted == '(' || inserted == '[') {
                val close = when (inserted) {
                    '{' -> '}'
                    '(' -> ')'
                    else -> ']'
                }
                if (caret < new.text.length && new.text[caret] == close) return new
                val text = new.text.substring(0, caret) + close + new.text.substring(caret)
                return TextFieldValue(text, TextRange(caret))
            }
        }
        return new
    }
}
