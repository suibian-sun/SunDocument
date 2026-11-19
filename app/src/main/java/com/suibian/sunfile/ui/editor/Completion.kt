package com.suibian.sunfile.ui.editor

/**
 * 代码补全引擎（对齐 VS Code 的行为）：
 *  - 前缀 / 驼峰 / 子序列三级模糊匹配，逐字符记录命中位置用于高亮
 *  - 文档内标识符按出现频次加权，同文件定义的符号额外提权
 *  - 根据光标前的字符推断语义上下文：成员访问、import、注解、普通位置
 *  - 每一项都带一句说明，浮层下方展示「它是干什么用的」
 *  - 返回替换区间，接受补全时只替换正在输入的这个词
 */

enum class CompletionKind(val tag: String, val label: String) {
    KEYWORD("K", "关键字"),
    SNIPPET("S", "片段"),
    SYMBOL("F", "本文件"),
    MEMBER("M", "成员"),
    TYPE("T", "类型"),
    WORD("W", "文中词")
}

data class CompletionItem(
    val label: String,
    val insert: String,
    val detail: String,
    val kind: CompletionKind,
    val score: Int,
    /** label 中被匹配到的字符下标，用于加粗高亮 */
    val hits: List<Int>,
    /** 一句话说明这个补全项是做什么的 */
    val documentation: String = "",
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

    private const val MAX_LABEL = 48

    // ---------------- 常用成员库 ----------------

    private val commonMembers = mapOf(
        Lang.KOTLIN to listOf(
            Triple("length", "Int", "字符串长度"),
            Triple("isEmpty()", "Boolean", "判断是否为空"),
            Triple("isNotEmpty()", "Boolean", "判断是否非空"),
            Triple("trim()", "String", "去掉首尾空白"),
            Triple("trimIndent()", "String", "去掉多行字符串的公共缩进"),
            Triple("split()", "List<String>", "按分隔符切成列表"),
            Triple("replace()", "String", "替换子串，返回新字符串"),
            Triple("startsWith()", "Boolean", "是否以指定前缀开头"),
            Triple("endsWith()", "Boolean", "是否以指定后缀结尾"),
            Triple("substring()", "String", "截取区间"),
            Triple("indexOf()", "Int", "查找子串位置，找不到返回 -1"),
            Triple("lowercase()", "String", "转小写"),
            Triple("uppercase()", "String", "转大写"),
            Triple("toInt()", "Int", "解析为整数"),
            Triple("toString()", "String", "转为文本"),
            Triple("map {}", "List<R>", "对每个元素做变换，返回新列表"),
            Triple("filter {}", "List<T>", "保留满足条件的元素"),
            Triple("forEach {}", "Unit", "遍历每个元素"),
            Triple("let {}", "R", "把接收者作为 it 交给代码块"),
            Triple("also {}", "T", "附加操作，返回原对象"),
            Triple("apply {}", "T", "配置对象属性，返回原对象"),
            Triple("run {}", "R", "在接收者作用域内执行并返回结果"),
            Triple("size", "Int", "集合元素个数"),
            Triple("first()", "T", "第一个元素"),
            Triple("last()", "T", "最后一个元素"),
            Triple("add()", "Boolean", "向集合添加元素"),
            Triple("remove()", "Boolean", "移除元素"),
            Triple("contains()", "Boolean", "是否包含指定元素"),
            Triple("get()", "T", "按下标或键取值"),
            Triple("put()", "V", "写入键值对"),
            Triple("keys", "Set<K>", "所有键"),
            Triple("values", "Collection<V>", "所有值"),
            Triple("readText()", "String", "把整个文件读成字符串"),
            Triple("writeText()", "Unit", "把字符串整体写入文件"),
            Triple("exists()", "Boolean", "文件或目录是否存在"),
            Triple("absolutePath", "String", "绝对路径"),
            Triple("name", "String", "文件名（不含目录）"),
            Triple("parentFile", "File?", "所在目录"),
            Triple("listFiles()", "Array<File>", "列出子项"),
            Triple("mkdirs()", "Boolean", "递归创建目录"),
            Triple("delete()", "Boolean", "删除文件或空目录"),
            Triple("copyTo()", "File", "复制到目标位置"),
            Triple("inputStream()", "InputStream", "打开输入流"),
            Triple("outputStream()", "OutputStream", "打开输出流")
        ),
        Lang.JAVA to listOf(
            Triple("length()", "int", "字符串长度"),
            Triple("isEmpty()", "boolean", "判断是否为空"),
            Triple("equals()", "boolean", "按内容比较"),
            Triple("toString()", "String", "转为文本"),
            Triple("substring()", "String", "截取区间"),
            Triple("indexOf()", "int", "查找子串位置"),
            Triple("charAt()", "char", "取指定位置的字符"),
            Triple("split()", "String[]", "按分隔符切分"),
            Triple("trim()", "String", "去掉首尾空白"),
            Triple("size()", "int", "集合元素个数"),
            Triple("get()", "E", "按下标或键取值"),
            Triple("add()", "boolean", "向集合添加元素"),
            Triple("put()", "V", "写入键值对"),
            Triple("containsKey()", "boolean", "是否包含指定键"),
            Triple("keySet()", "Set<K>", "所有键"),
            Triple("entrySet()", "Set<Map.Entry<K,V>>", "键值对集合，用于遍历"),
            Triple("getName()", "String", "文件名"),
            Triple("getAbsolutePath()", "String", "绝对路径"),
            Triple("exists()", "boolean", "是否存在"),
            Triple("isDirectory()", "boolean", "是否是目录"),
            Triple("listFiles()", "File[]", "列出子项"),
            Triple("mkdirs()", "boolean", "递归创建目录")
        ),
        Lang.JAVASCRIPT to listOf(
            Triple("length", "number", "长度"),
            Triple("map()", "Array", "映射为新数组"),
            Triple("filter()", "Array", "按条件筛选"),
            Triple("forEach()", "void", "遍历"),
            Triple("reduce()", "any", "归约为单个值"),
            Triple("push()", "number", "尾部追加，返回新长度"),
            Triple("pop()", "any", "弹出末尾元素"),
            Triple("slice()", "Array", "截取片段，不改原数组"),
            Triple("splice()", "Array", "增删元素，会改原数组"),
            Triple("join()", "string", "用分隔符拼接"),
            Triple("split()", "Array<string>", "按分隔符切分"),
            Triple("toLowerCase()", "string", "转小写"),
            Triple("toUpperCase()", "string", "转大写"),
            Triple("trim()", "string", "去掉首尾空白"),
            Triple("includes()", "boolean", "是否包含"),
            Triple("find()", "any", "找到第一个满足条件的元素"),
            Triple("keys()", "Iterator", "键迭代器"),
            Triple("then()", "Promise", "Promise 成功回调"),
            Triple("catch()", "Promise", "Promise 失败回调"),
            Triple("json()", "Promise<any>", "把响应体解析为 JSON")
        ),
        Lang.PYTHON to listOf(
            Triple("append()", "None", "列表尾部追加"),
            Triple("extend()", "None", "用另一个列表扩展"),
            Triple("split()", "list", "按分隔符切分"),
            Triple("join()", "str", "用分隔符拼接"),
            Triple("strip()", "str", "去掉首尾空白"),
            Triple("replace()", "str", "替换子串"),
            Triple("startswith()", "bool", "是否以指定前缀开头"),
            Triple("endswith()", "bool", "是否以指定后缀结尾"),
            Triple("format()", "str", "格式化字符串"),
            Triple("keys()", "dict_keys", "字典所有键"),
            Triple("values()", "dict_values", "字典所有值"),
            Triple("items()", "dict_items", "键值对"),
            Triple("get()", "any", "按键取值，可给默认值"),
            Triple("read()", "str", "读取全部内容"),
            Triple("readlines()", "list", "按行读取为列表"),
            Triple("write()", "int", "写入并返回字符数"),
            Triple("close()", "None", "关闭资源")
        )
    )

    /** import 场景下给出真实类名 + 用途，而不是只列包名 */
    private data class ImportHint(val fqcn: String, val doc: String)

    private val importHints = listOf(
        ImportHint("android.app.Activity", "界面组件基类，持有窗口与生命周期"),
        ImportHint("android.app.Application", "应用级单例，适合做全局初始化"),
        ImportHint("android.app.AlertDialog", "系统风格弹窗，用于确认与提示"),
        ImportHint("android.content.Context", "访问资源、启动组件、取系统服务的入口"),
        ImportHint("android.content.Intent", "组件间跳转与传参的意图对象"),
        ImportHint("android.content.SharedPreferences", "轻量键值存储，适合保存设置"),
        ImportHint("android.content.ClipboardManager", "读写系统剪贴板"),
        ImportHint("android.os.Bundle", "键值容器，页面之间传参"),
        ImportHint("android.os.Handler", "向指定线程投递延时或循环任务"),
        ImportHint("android.os.Environment", "获取外部存储等公共目录"),
        ImportHint("android.util.Log", "日志输出：Log.d / i / w / e"),
        ImportHint("android.view.View", "所有控件的基类，负责布局与触摸"),
        ImportHint("android.view.LayoutInflater", "把 XML 布局实例化为视图"),
        ImportHint("android.widget.Toast", "短暂提示，显示后自动消失"),
        ImportHint("android.widget.TextView", "文本显示控件"),
        ImportHint("android.net.Uri", "统一资源标识，表示文件或内容地址"),
        ImportHint("androidx.compose.runtime.Composable", "声明式 UI 函数注解"),
        ImportHint("androidx.compose.runtime.remember", "在重组之间保留一份状态"),
        ImportHint("androidx.lifecycle.ViewModel", "跨配置变更保存界面状态"),
        ImportHint("androidx.lifecycle.viewModelScope", "随 ViewModel 销毁的协程作用域"),
        ImportHint("kotlinx.coroutines.launch", "在协程作用域内启动新协程"),
        ImportHint("kotlinx.coroutines.Dispatchers", "指定协程运行的线程：IO / Default / Main"),
        ImportHint("java.io.File", "文件与目录的抽象，负责读写与遍历"),
        ImportHint("java.io.IOException", "输入输出异常"),
        ImportHint("java.util.ArrayList", "可变长数组，随机访问快"),
        ImportHint("java.util.HashMap", "哈希表，按键快速查找"),
        ImportHint("java.nio.charset.Charset", "字符编码，如 UTF-8 / GBK"),
        ImportHint("java.security.MessageDigest", "计算 MD5 / SHA 摘要"),
        ImportHint("java.util.zip.ZipFile", "以只读方式浏览 zip 压缩包")
    )

    private val annotationDocs = mapOf(
        "Override" to "重写父类或接口中的方法",
        "Deprecated" to "标记为已废弃，调用方会收到警告",
        "SuppressWarnings" to "关闭指定类型的编译警告",
        "Nullable" to "显式声明可以为 null",
        "NonNull" to "显式声明不允许为 null",
        "JvmStatic" to "让伴生对象成员生成静态方法",
        "JvmField" to "去掉属性的 getter/setter，直接暴露字段",
        "Synchronized" to "方法或代码块加锁",
        "Volatile" to "保证多线程下的可见性",
        "Composable" to "声明一个可组合函数",
        "Preview" to "在 IDE 中预览该界面",
        "Inject" to "由依赖注入框架提供实例",
        "Test" to "标记一个单元测试方法",
        "Before" to "在每个测试方法之前执行",
        "After" to "在每个测试方法之后执行",
        "Entity" to "声明数据库表对应的实体",
        "Dao" to "声明数据库访问对象",
        "Query" to "在 Dao 上声明一条 SQL 查询",
        "Serializable" to "可被序列化"
    )

    private val keywordDocs = mapOf(
        "fun" to "声明函数",
        "val" to "声明只读变量",
        "var" to "声明可变变量",
        "class" to "声明类",
        "object" to "声明单例对象",
        "interface" to "声明接口",
        "data" to "数据类：自动生成 equals / hashCode / copy",
        "sealed" to "密封类：子类限定在同一文件内",
        "enum" to "枚举类型",
        "when" to "分支表达式，可代替 switch",
        "if" to "条件判断",
        "else" to "否则分支",
        "for" to "循环遍历",
        "while" to "条件循环",
        "return" to "从函数返回",
        "try" to "捕获可能抛出的异常",
        "catch" to "处理捕获到的异常",
        "finally" to "无论是否异常都会执行的代码块",
        "throw" to "抛出异常",
        "import" to "引入其他包中的类型",
        "package" to "声明当前文件所属的包",
        "suspend" to "挂起函数：可等待而不阻塞线程",
        "inline" to "内联函数：编译期展开，减少调用开销",
        "lateinit" to "延迟初始化，使用前必须赋值",
        "companion" to "伴生对象，相当于静态成员",
        "override" to "重写父类成员",
        "private" to "仅当前文件或类可见",
        "public" to "任意位置可见",
        "internal" to "同一模块内可见",
        "abstract" to "抽象成员，需子类实现",
        "constructor" to "构造函数",
        "this" to "当前对象引用",
        "super" to "父类引用",
        "null" to "空值",
        "true" to "布尔真",
        "false" to "布尔假",
        "def" to "定义函数（Python）",
        "lambda" to "匿名函数（Python）",
        "None" to "空值（Python）",
        "self" to "实例自身（Python）",
        "function" to "声明函数（JS）",
        "const" to "声明常量",
        "let" to "块级作用域变量（JS）",
        "new" to "创建实例",
        "typeof" to "取类型（JS）",
        "await" to "等待异步结果",
        "async" to "标记异步函数",
        "static" to "静态成员",
        "void" to "无返回值",
        "int" to "32 位整数",
        "boolean" to "布尔类型",
        "String" to "字符串类型",
        "extends" to "继承父类",
        "implements" to "实现接口",
        "final" to "不可再被继承或修改",
        "synchronized" to "同步代码块，保证线程安全",
        "switch" to "多分支选择",
        "case" to "分支标签",
        "break" to "跳出循环或分支",
        "continue" to "进入下一次循环",
        "struct" to "结构体",
        "func" to "声明函数（Go）",
        "fn" to "声明函数（Rust）",
        "match" to "模式匹配",
        "SELECT" to "查询数据",
        "FROM" to "指定数据表",
        "WHERE" to "过滤条件",
        "INSERT" to "插入数据",
        "UPDATE" to "更新数据",
        "DELETE" to "删除数据"
    )

    private val snippetDefs: Map<Lang, List<Snippet>> = mapOf(
        Lang.KOTLIN to listOf(
            Snippet("fun", "fun name() {\n    \n}", "函数", "声明一个函数，光标停在函数体内"),
            Snippet("if", "if (condition) {\n    \n}", "条件", "条件分支，光标停在花括号内"),
            Snippet("for", "for (i in 0 until n) {\n    \n}", "循环", "按次数循环"),
            Snippet("when", "when (value) {\n    else -> Unit\n}", "分支", "多分支表达式"),
            Snippet("try", "try {\n    \n} catch (e: Exception) {\n    \n}", "异常", "捕获并处理异常"),
            Snippet("class", "class Name {\n    \n}", "类", "声明一个类"),
            Snippet("logd", "android.util.Log.d(\"TAG\", \"\")", "日志", "输出一条调试日志")
        ),
        Lang.JAVA to listOf(
            Snippet("main", "public static void main(String[] args) {\n    \n}", "入口", "程序入口方法"),
            Snippet("if", "if (condition) {\n    \n}", "条件", "条件分支"),
            Snippet("for", "for (int i = 0; i < n; i++) {\n    \n}", "循环", "计数循环"),
            Snippet("try", "try {\n    \n} catch (Exception e) {\n    \n}", "异常", "捕获并处理异常"),
            Snippet("class", "public class Name {\n    \n}", "类", "声明一个公有类"),
            Snippet("logd", "android.util.Log.d(\"TAG\", \"\");", "日志", "输出一条调试日志")
        ),
        Lang.PYTHON to listOf(
            Snippet("def", "def name():\n    ", "函数", "定义一个函数"),
            Snippet("if", "if condition:\n    ", "条件", "条件分支"),
            Snippet("for", "for i in range(n):\n    ", "循环", "按次数循环"),
            Snippet("class", "class Name:\n    ", "类", "定义一个类"),
            Snippet("try", "try:\n    \nexcept Exception as e:\n    ", "异常", "捕获并处理异常")
        ),
        Lang.JAVASCRIPT to listOf(
            Snippet("fn", "function name() {\n    \n}", "函数", "声明一个函数"),
            Snippet("arrow", "const name = () => {\n    \n}", "箭头函数", "声明一个箭头函数"),
            Snippet("if", "if (condition) {\n    \n}", "条件", "条件分支"),
            Snippet("for", "for (let i = 0; i < n; i++) {\n    \n}", "循环", "计数循环"),
            Snippet("log", "console.log()", "输出", "在控制台打印")
        )
    )

    private data class Snippet(val key: String, val body: String, val title: String, val doc: String)

    // ---------------- 入口 ----------------

    fun suggest(lang: Lang, document: String, caret: Int, limit: Int = 8): CompletionResult? {
        if (caret < 0 || caret > document.length) return null
        val (start, typed) = wordBefore(document, caret)
        // 只看光标前面一小段：不复制整篇文档，长文本下每键都调用也不会卡
        val from = (start - 260).coerceAtLeast(0)
        val before = document.substring(from, start).trimEnd()
        val context = contextOf(before)

        val forceContext = typed.isEmpty()

        // 明显不需要补全的输入直接返回，省掉一次索引与匹配
        if (typed.length > 40) return null
        if (typed.isNotEmpty() && typed.all { it.isDigit() }) return null
        // 光标停在空白位置时不主动弹面板，只有 "." "import" "@" 才提示
        if (typed.isEmpty() && context == Ctx.PLAIN) return null

        val index = indexOf(document)

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
                commonMembers[lang]?.forEach { (name, type, doc) ->
                    add(scored(name, name, type, CompletionKind.MEMBER, typed, 120, doc))
                }
                index.members.forEach { name ->
                    add(
                        scored(
                            name, name, "本文件成员", CompletionKind.MEMBER, typed, 60,
                            "在当前文件中以 .$name 的形式出现过"
                        )
                    )
                }
            }

            Ctx.IMPORT -> {
                importHints.forEach { hint ->
                    add(
                        scored(
                            hint.fqcn, hint.fqcn, hint.fqcn.substringBeforeLast('.'),
                            CompletionKind.TYPE, typed, 160, hint.doc
                        )
                    )
                }
            }

            Ctx.ANNOTATION -> {
                annotationDocs.forEach { (name, doc) ->
                    add(scored(name, "@$name", "注解", CompletionKind.TYPE, typed, 160, doc))
                }
            }

            Ctx.PLAIN -> {
                if (typed.isNotEmpty()) {
                    snippetDefs[lang]?.forEach { sn ->
                        add(
                            scored(
                                sn.key, sn.body, sn.title, CompletionKind.SNIPPET, typed, 40,
                                sn.doc, labelOverride = sn.key
                            )
                        )
                    }
                }
                index.symbols.forEach { (name, kindName) ->
                    add(
                        scored(
                            name, name, kindName, CompletionKind.SYMBOL, typed, 90,
                            "本文件${kindName}「$name」"
                        )
                    )
                }
                keywordsOf(lang).forEach { kw ->
                    add(
                        scored(
                            kw, kw, "关键字", CompletionKind.KEYWORD, typed, 30,
                            keywordDocs[kw] ?: "${lang.label} 关键字"
                        )
                    )
                }
                index.freq.forEach { (word, freq) ->
                    val bonus = (freq * 6).coerceAtMost(100)
                    add(
                        scored(
                            word, word, "出现 $freq 次", CompletionKind.WORD, typed, bonus,
                            "文档中已出现过 $freq 次的词"
                        )
                    )
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
        documentation: String = "",
        labelOverride: String? = null
    ): CompletionItem {
        val (score, hits) = match(label, typed)
        return CompletionItem(
            label = labelOverride ?: label,
            insert = insert,
            detail = detail,
            kind = kind,
            score = base + score,
            hits = hits,
            documentation = documentation,
            caretBack = 0
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

    // ---------------- 文档索引缓存 ----------------

    private data class DocIndex(
        val freq: List<Pair<String, Int>>,
        val symbols: List<Pair<String, String>>,
        val members: List<String>
    )

    private var cacheKey: Long = -1L
    private var cache: DocIndex? = null
    private var cacheAt: Long = 0L

    /**
     * 以「长度 + 首尾片段」做键，O(1) 判断文档是否变化；
     * 重建至少间隔 400ms，连续输入时沿用上一份索引，避免每键都全量扫描。
     */
    private fun indexOf(document: String): DocIndex {
        val head = if (document.length > 48) document.substring(0, 48) else document
        val tail = if (document.length > 48) document.substring(document.length - 48) else document
        val key = document.length.toLong() * 1_000_003L + head.hashCode() * 31L + tail.hashCode()
        cache?.let {
            if (key == cacheKey) return it
            if (System.currentTimeMillis() - cacheAt < 400) return it
        }
        val built = DocIndex(wordFreq(document), symbolDefs(document), memberNamesOf(document))
        cacheKey = key
        cache = built
        cacheAt = System.currentTimeMillis()
        return built
    }

    private val wordRegex = Regex("[A-Za-z_][A-Za-z0-9_]{1,30}")
    private val memberRegex = Regex("\\.([A-Za-z_][A-Za-z0-9_]{1,24})")
    private val symbolRegex = Regex(
        "\\b(fun|def|function|class|interface|object|struct|enum|trait|val|var|let|const|type)\\s+([A-Za-z_][A-Za-z0-9_]{1,30})"
    )

    private fun wordFreq(document: String): List<Pair<String, Int>> {
        val tail = if (document.length > 40_000) document.takeLast(40_000) else document
        val map = HashMap<String, Int>(512)
        wordRegex.findAll(tail).forEach {
            val w = it.value
            map[w] = (map[w] ?: 0) + 1
        }
        return map.entries
            .sortedByDescending { it.value }
            .take(120)
            .map { it.key to it.value }
    }

    private fun memberNamesOf(document: String): List<String> {
        val tail = if (document.length > 40_000) document.takeLast(40_000) else document
        return memberRegex.findAll(tail)
            .map { it.groupValues[1] }
            .distinct()
            .take(60)
            .toList()
    }

    private fun symbolDefs(document: String): List<Pair<String, String>> {
        val tail = if (document.length > 40_000) document.takeLast(40_000) else document
        val out = LinkedHashMap<String, String>()
        for (m in symbolRegex.findAll(tail)) {
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
