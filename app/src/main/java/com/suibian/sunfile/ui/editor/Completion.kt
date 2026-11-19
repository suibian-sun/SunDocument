package com.suibian.sunfile.ui.editor

/**
 * 代码补全引擎（对齐 VS Code 的行为）：
 *  - 前缀 / 驼峰 / 子序列三级模糊匹配，逐字符记录命中位置用于高亮
 *  - 文档内标识符按出现频次加权，同文件定义的符号额外提权
 *  - 根据光标前的字符推断语义上下文：成员访问、import、注解、普通位置
 *  - 返回替换区间，接受补全时只替换正在输入的这个词
 */

enum class CompletionKind(val tag: String) {
    KEYWORD("K"),
    SNIPPET("S"),
    SYMBOL("F"),
    MEMBER("M"),
    TYPE("T"),
    WORD("W")
}

data class CompletionItem(
    val label: String,
    val insert: String,
    val detail: String,
    val kind: CompletionKind,
    val score: Int,
    /** label 中被匹配到的字符下标，用于加粗高亮 */
    val hits: List<Int>,
    /** 插入后光标相对文本末尾的偏移（用 0 表示放到末尾） */
    val caretBack: Int = 0
)

data class CompletionResult(
    val items: List<CompletionItem>,
    val replaceStart: Int,
    val replaceEnd: Int,
    val typed: String
)

object CompletionEngine {

    private const val MAX_LABEL = 40

    // ---------------- 常用成员库 ----------------

    private val commonMembers = mapOf(
        Lang.KOTLIN to listOf(
            "length" to "Int", "isEmpty()" to "Boolean", "isNotEmpty()" to "Boolean",
            "trim()" to "String", "trimIndent()" to "String", "split()" to "List<String>",
            "replace()" to "String", "startsWith()" to "Boolean", "endsWith()" to "Boolean",
            "substring()" to "String", "indexOf()" to "Int", "lowercase()" to "String",
            "uppercase()" to "String", "toInt()" to "Int", "toString()" to "String",
            "map {}" to "List<R>", "filter {}" to "List<T>", "forEach {}" to "Unit",
            "let {}" to "R", "also {}" to "T", "apply {}" to "T", "run {}" to "R",
            "size" to "Int", "first()" to "T", "last()" to "T", "add()" to "Boolean",
            "remove()" to "Boolean", "contains()" to "Boolean", "get()" to "T",
            "put()" to "V", "keys" to "Set<K>", "values" to "Collection<V>",
            "readText()" to "String", "writeText()" to "Unit", "exists()" to "Boolean",
            "absolutePath" to "String", "name" to "String", "parentFile" to "File?",
            "listFiles()" to "Array<File>", "mkdirs()" to "Boolean", "delete()" to "Boolean",
            "copyTo()" to "File", "inputStream()" to "InputStream", "outputStream()" to "OutputStream"
        ),
        Lang.JAVA to listOf(
            "length()" to "int", "isEmpty()" to "boolean", "equals()" to "boolean",
            "toString()" to "String", "substring()" to "String", "indexOf()" to "int",
            "charAt()" to "char", "split()" to "String[]", "trim()" to "String",
            "size()" to "int", "get()" to "E", "add()" to "boolean", "put()" to "V",
            "containsKey()" to "boolean", "keySet()" to "Set<K>", "entrySet()" to "Set<Map.Entry<K,V>>",
            "getName()" to "String", "getAbsolutePath()" to "String", "exists()" to "boolean",
            "isDirectory()" to "boolean", "listFiles()" to "File[]", "mkdirs()" to "boolean"
        ),
        Lang.JAVASCRIPT to listOf(
            "length" to "number", "map()" to "Array", "filter()" to "Array", "forEach()" to "void",
            "reduce()" to "any", "push()" to "number", "pop()" to "any", "slice()" to "Array",
            "splice()" to "Array", "join()" to "string", "split()" to "Array<string>",
            "toLowerCase()" to "string", "toUpperCase()" to "string", "trim()" to "string",
            "includes()" to "boolean", "find()" to "any", "keys()" to "Iterator",
            "then()" to "Promise", "catch()" to "Promise", "json()" to "Promise<any>"
        ),
        Lang.PYTHON to listOf(
            "append()" to "None", "extend()" to "None", "split()" to "list", "join()" to "str",
            "strip()" to "str", "replace()" to "str", "startswith()" to "bool",
            "endswith()" to "bool", "format()" to "str", "keys()" to "dict_keys",
            "values()" to "dict_values", "items()" to "dict_items", "get()" to "any",
            "read()" to "str", "readlines()" to "list", "write()" to "int", "close()" to "None"
        )
    )

    private val importCandidates = listOf(
        "android.app.", "android.content.", "android.os.", "android.util.", "android.view.",
        "android.widget.", "androidx.compose.", "androidx.lifecycle.", "java.io.", "java.util.",
        "java.nio.", "kotlinx.coroutines.", "kotlin.collections.", "kotlin.io.", "com.google.gson.",
        "androidx.room.", "okhttp3.", "retrofit2."
    )

    private val annotationCandidates = listOf(
        "Override", "Deprecated", "SuppressWarnings", "Nullable", "NonNull", "JvmStatic",
        "JvmField", "Synchronized", "Volatile", "Composable", "Preview", "Inject", "Test",
        "Before", "After", "Entity", "Dao", "Query", "Serializable", "FunctionalInterface"
    )

    private val snippetDefs: Map<Lang, List<Triple<String, String, String>>> = mapOf(
        Lang.KOTLIN to listOf(
            Triple("fun", "fun name() {\n    \n}", "函数"),
            Triple("if", "if (condition) {\n    \n}", "条件"),
            Triple("for", "for (i in 0 until n) {\n    \n}", "循环"),
            Triple("when", "when (value) {\n    else -> Unit\n}", "分支"),
            Triple("try", "try {\n    \n} catch (e: Exception) {\n    \n}", "异常"),
            Triple("class", "class Name {\n    \n}", "类"),
            Triple("logd", "android.util.Log.d(\"TAG\", \"\")", "日志")
        ),
        Lang.JAVA to listOf(
            Triple("main", "public static void main(String[] args) {\n    \n}", "入口"),
            Triple("if", "if (condition) {\n    \n}", "条件"),
            Triple("for", "for (int i = 0; i < n; i++) {\n    \n}", "循环"),
            Triple("try", "try {\n    \n} catch (Exception e) {\n    \n}", "异常"),
            Triple("class", "public class Name {\n    \n}", "类"),
            Triple("logd", "android.util.Log.d(\"TAG\", \"\");", "日志")
        ),
        Lang.PYTHON to listOf(
            Triple("def", "def name():\n    ", "函数"),
            Triple("if", "if condition:\n    ", "条件"),
            Triple("for", "for i in range(n):\n    ", "循环"),
            Triple("class", "class Name:\n    ", "类"),
            Triple("try", "try:\n    \nexcept Exception as e:\n    ", "异常")
        ),
        Lang.JAVASCRIPT to listOf(
            Triple("fn", "function name() {\n    \n}", "函数"),
            Triple("arrow", "const name = () => {\n    \n}", "箭头函数"),
            Triple("if", "if (condition) {\n    \n}", "条件"),
            Triple("for", "for (let i = 0; i < n; i++) {\n    \n}", "循环"),
            Triple("log", "console.log()", "输出")
        )
    )

    // ---------------- 入口 ----------------

    fun suggest(lang: Lang, document: String, caret: Int, limit: Int = 8): CompletionResult? {
        if (caret < 0 || caret > document.length) return null
        val (start, typed) = wordBefore(document, caret)
        val before = document.substring(0, start).trimEnd()
        val context = contextOf(before)

        // 刚输入 "." 或 "@" 或 "import " 时也给出候选
        val forceContext = typed.isEmpty()

        val index = indexOf(document)
        // 光标停在一个空白位置时不主动弹面板，只有 "." "import" "@" 才提示
        if (typed.isEmpty() && context == Ctx.PLAIN) return null

        val pool = ArrayList<CompletionItem>(64)
        val seen = HashSet<String>()

        fun add(item: CompletionItem) {
            if (item.label.isEmpty() || item.label.length > MAX_LABEL) return
            if (item.label == typed) return
            if (!seen.add(item.label + "|" + item.kind)) return
            pool.add(item)
        }

        when (context) {
            Ctx.MEMBER -> {
                commonMembers[lang]?.forEach { (name, type) ->
                    add(scored(name, name, type, CompletionKind.MEMBER, typed, 120))
                }
                // 文档里出现过的成员
                index.members.forEach { name ->
                    add(scored(name, name, "文档内成员", CompletionKind.MEMBER, typed, 60))
                }
            }

            Ctx.IMPORT -> {
                importCandidates.forEach { pkg ->
                    add(scored(pkg, pkg, "包", CompletionKind.TYPE, typed, 160))
                }
            }

            Ctx.ANNOTATION -> {
                annotationCandidates.forEach { a ->
                    add(scored(a, "@$a", "注解", CompletionKind.TYPE, typed, 160))
                }
            }

            Ctx.PLAIN -> {
                // 片段优先（短输入时给出结构）
                if (typed.isNotEmpty()) {
                    snippetDefs[lang]?.forEach { (key, body, desc) ->
                        add(scored(key, body, desc, CompletionKind.SNIPPET, typed, 40, labelOverride = key))
                    }
                }
                // 同文件符号
                index.symbols.forEach { (name, kindName) ->
                    add(scored(name, name, kindName, CompletionKind.SYMBOL, typed, 90))
                }
                // 关键字
                keywordsOf(lang).forEach { kw ->
                    add(scored(kw, kw, "关键字", CompletionKind.KEYWORD, typed, 30))
                }
                // 文档词频
                index.freq.forEach { (word, freq) ->
                    val bonus = (freq * 6).coerceAtMost(100)
                    add(scored(word, word, "出现 $freq 次", CompletionKind.WORD, typed, bonus))
                }
            }
        }

        if (pool.isEmpty() && !forceContext) return null
        val sorted = pool.sortedWith(
            compareByDescending<CompletionItem> { it.score }.thenBy { it.label.length }.thenBy { it.label }
        ).take(limit)
        if (sorted.isEmpty()) return null
        return CompletionResult(sorted, start, caret, typed)
    }

    private enum class Ctx { PLAIN, MEMBER, IMPORT, ANNOTATION }

    private fun contextOf(before: String): Ctx {
        if (before.endsWith("@")) return Ctx.ANNOTATION
        val line = before.substringAfterLast('\n')
        if (line.trimStart().startsWith("import ") && !line.trimEnd().endsWith(";")) return Ctx.IMPORT
        if (before.endsWith(".") && !before.endsWith("..")) {
            // 排除数字小数点
            val prev = before.dropLast(1).lastOrNull()
            if (prev != null && (prev.isLetterOrDigit() || prev == '_' || prev == ')' || prev == ']')) return Ctx.MEMBER
        }
        return Ctx.PLAIN
    }

    /** 取光标前正在输入的词与起点 */
    private fun wordBefore(document: String, caret: Int): Pair<Int, String> {
        var s = caret
        while (s > 0) {
            val c = document[s - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '$') s-- else break
        }
        return s to document.substring(s, caret)
    }

    // ---------------- 评分 ----------------

    private fun scored(
        label: String,
        insert: String,
        detail: String,
        kind: CompletionKind,
        typed: String,
        base: Int,
        labelOverride: String? = null
    ): CompletionItem {
        val (score, hits) = match(label, typed)
        val caretBack = 0
        return CompletionItem(
            label = labelOverride ?: label,
            insert = insert,
            detail = detail,
            kind = kind,
            score = base + score,
            hits = hits,
            caretBack = caretBack
        )
    }

    /**
     * 三级匹配：
     *  1. 前缀（忽略大小写）→ 最高分，越接近完整词越高
     *  2. 驼峰缩写（如 NPE → NullPointerException）
     *  3. 子序列（如 fns → findNextStatement），按间隔扣分
     */
    private fun match(label: String, typed: String): Pair<Int, List<Int>> {
        if (typed.isEmpty()) return 0 to emptyList()
        val lower = label.lowercase()
        val t = typed.lowercase()

        if (lower.startsWith(t)) {
            val bonus = 400 - (label.length - typed.length).coerceAtLeast(0) * 4
            return (600 + bonus) to (0 until typed.length).toList()
        }

        // 驼峰缩写
        val boundaries = ArrayList<Int>()
        for (i in label.indices) {
            if (i == 0 || label[i].isUpperCase() || label[i] == '_' || label[i] == '.') boundaries.add(i)
        }
        if (t.length <= boundaries.size) {
            val hits = ArrayList<Int>(t.length)
            var ok = true
            for (k in t.indices) {
                val idx = boundaries.getOrNull(k) ?: run { ok = false; -1 }
                if (idx < 0 || label[idx].lowercaseChar() != t[k]) {
                    ok = false; break
                }
                hits.add(idx)
            }
            if (ok) return (420 - hits.sum()) to hits
        }

        // 子序列
        val hits = ArrayList<Int>(t.length)
        var cursor = 0
        var gaps = 0
        for (ch in t) {
            var found = -1
            for (i in cursor until label.length) {
                if (label[i].lowercaseChar() == ch) {
                    found = i; break
                }
            }
            if (found < 0) return Int.MIN_VALUE / 2 to emptyList()
            if (hits.isNotEmpty()) gaps += found - hits.last() - 1
            hits.add(found)
            cursor = found + 1
        }
        if (hits.firstOrNull() != 0 && gaps > 6) return Int.MIN_VALUE / 2 to emptyList()
        return (260 - gaps * 8 - label.length) to hits
    }

    // ---------------- 文档分析 ----------------

    // ---------------- 文档索引缓存 ----------------

    private data class DocIndex(
        val freq: List<Pair<String, Int>>,
        val symbols: List<Pair<String, String>>,
        val members: List<String>
    )

    private var cacheKey: Long = -1L
    private var cache: DocIndex? = null

    /** 以「长度 + 首尾片段」做键，O(1) 判断文档是否变化，避免每键都全量扫描 */
    private fun indexOf(document: String): DocIndex {
        val head = if (document.length > 48) document.substring(0, 48) else document
        val tail = if (document.length > 48) document.substring(document.length - 48) else document
        val key = document.length.toLong() * 1_000_003L + head.hashCode() * 31L + tail.hashCode()
        cache?.let { if (key == cacheKey) return it }
        val built = DocIndex(wordFreq(document), symbolDefs(document), memberNamesOf(document))
        cacheKey = key
        cache = built
        return built
    }

    private fun wordFreq(document: String): List<Pair<String, Int>> {
        val tail = if (document.length > 120_000) document.takeLast(120_000) else document
        val map = HashMap<String, Int>(512)
        Regex("[A-Za-z_][A-Za-z0-9_]{1,30}").findAll(tail).forEach {
            val w = it.value
            map[w] = (map[w] ?: 0) + 1
        }
        return map.entries
            .sortedByDescending { it.value }
            .take(120)
            .map { it.key to it.value }
    }

    private fun memberNamesOf(document: String): List<String> {
        val tail = if (document.length > 60_000) document.takeLast(60_000) else document
        return Regex("\\.([A-Za-z_][A-Za-z0-9_]{1,24})").findAll(tail)
            .map { it.groupValues[1] }
            .distinct()
            .take(60)
            .toList()
    }

    private fun symbolDefs(document: String): List<Pair<String, String>> {
        val tail = if (document.length > 120_000) document.takeLast(120_000) else document
        val out = LinkedHashMap<String, String>()
        val regex = Regex(
            "\\b(fun|def|function|class|interface|object|struct|enum|trait|val|var|let|const|type)\\s+([A-Za-z_][A-Za-z0-9_]{1,30})"
        )
        for (m in regex.findAll(tail)) {
            val kind = when (m.groupValues[1]) {
                "fun", "def", "function" -> "函数"
                "class", "interface", "object", "struct", "enum", "trait" -> "类型"
                "val", "var", "let", "const", "type" -> "变量"
                else -> "符号"
            }
            out.putIfAbsent(m.groupValues[2], kind)
        }
        return out.entries.map { it.key to it.value }.take(80)
    }

    private fun keywordsOf(lang: Lang): Set<String> = Syntax.keywordsOf(lang)
}
