package com.suibian.sunfile.data

import android.content.Context
import com.suibian.sunfile.data.mtapi.LocalStringHost
import com.suibian.sunfile.data.mtapi.MtApi
import com.suibian.sunfile.data.mtapi.MtAssets
import com.suibian.sunfile.data.mtapi.MtContext
import com.suibian.sunfile.data.mtapi.PrefItem
import com.suibian.sunfile.data.mtapi.PreferenceBuilder
import com.suibian.sunfile.data.mtjava.Interp
import com.suibian.sunfile.data.mtjava.JarLoader
import com.suibian.sunfile.data.mtjava.JObj
import com.suibian.sunfile.data.mtjava.Lexer
import com.suibian.sunfile.data.mtjava.MtRuntimeException
import com.suibian.sunfile.data.mtjava.MtSyntaxException
import com.suibian.sunfile.data.mtjava.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * MT 插件管理器。
 *
 * MT 的插件包（.mtp）里放的是 **Java 源码**，MT 自己编译执行。这里没有编译器，
 * 所以走另一条路：把源码解析成语法树，用 [Interp] 解释执行，并把 MT 的
 * `bin.mt.plugin.api` 用 [MtApi] 顶上去。这样插件不用改一行代码就能跑。
 *
 * 覆盖范围：翻译引擎、设置界面、上下文（配置 / 日志 / 资源 / 语言包）。
 * 依赖第三方 jar 的插件跑不了——Android 上没法执行 jar 里的 JVM 字节码。
 */
object MtPlugins {

    data class Plugin(
        val id: String,
        val name: String,
        val description: String,
        val versionCode: Int,
        val versionName: String,
        val sdk: Int,
        val interfaces: List<String>,
        val mainPreference: String?,
        val dir: File,
        val enabled: Boolean,
        val sources: List<File>,
        val icon: File?
    ) {
        val hasPreference: Boolean get() = !mainPreference.isNullOrBlank()
        val engineClass: String? get() = interfaces.firstOrNull { it.isNotBlank() && it != mainPreference }
        val kind: String
            get() = when {
                hasPreference && engineClass != null -> "设置 + 引擎"
                hasPreference -> "设置界面"
                engineClass != null -> "接口"
                else -> "空插件"
            }
    }

    internal class Loaded(
        val plugin: Plugin,
        val interp: Interp,
        val context: MtContext,
        val ctxObj: JObj,
        val logs: MutableList<String>
    )

    data class RunResult(val ok: Boolean, val text: String, val logs: List<String>)

    fun root(context: Context): File = File(context.filesDir, "mtplugins").apply { mkdirs() }

    fun list(context: Context): List<Plugin> {
        val dirs = root(context).listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        return dirs.mapNotNull { read(it) }.sortedBy { it.name.lowercase() }
    }

    fun find(context: Context, id: String): Plugin? = list(context).firstOrNull { it.id == id }

    private fun read(dir: File): Plugin? {
        val mf = File(dir, "manifest.json")
        if (!mf.isFile) return null
        val json = LooseJson.parse(mf.readText()) ?: return null
        val id = json.optString("pluginID").ifBlank { dir.name }
        val interfaces = ArrayList<String>()
        json.optJSONArray("interfaces")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { interfaces.add(it) }
        }
        val srcDir = File(dir, "src")
        val sources = srcDir.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.toList()
        val icon = File(dir, "icon.png").takeIf { it.isFile } ?: File(dir, "icon.jpg").takeIf { it.isFile }
        return Plugin(
            id = id,
            name = json.optString("name").ifBlank { id },
            description = json.optString("description"),
            versionCode = json.optInt("versionCode", 0),
            versionName = json.optString("versionName"),
            sdk = json.optInt("pluginSdkVersion", 2),
            interfaces = interfaces,
            mainPreference = json.optString("mainPreference").ifBlank { null },
            dir = dir,
            enabled = !File(dir, ".disabled").exists(),
            sources = sources,
            icon = icon
        )
    }

    /** 安装一个 .mtp；返回装好的插件 */
    fun install(context: Context, mtp: File): Plugin {
        val tmp = File(root(context), ".install-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            ZipFile(mtp).use { zip ->
                for (e in zip.entries()) {
                    val name = e.name
                    if (name.contains("..") || name.startsWith("/")) continue
                    val out = File(tmp, name)
                    if (e.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            val mf = File(tmp, "manifest.json")
            if (!mf.isFile) throw MtRuntimeException("安装包里没有 manifest.json")
            val json = LooseJson.parse(mf.readText()) ?: throw MtRuntimeException("manifest.json 解析失败")
            val id = json.optString("pluginID")
            if (id.isBlank()) throw MtRuntimeException("manifest.json 缺少 pluginID")
            val target = File(root(context), id)
            if (target.exists()) target.deleteRecursively()
            if (!tmp.renameTo(target)) {
                tmp.copyRecursively(target, overwrite = true)
                tmp.deleteRecursively()
            }
            return read(target) ?: throw MtRuntimeException("安装后读取失败")
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
    }

    fun uninstall(context: Context, id: String) {
        File(root(context), id).deleteRecursively()
        context.getSharedPreferences("mtplugin_$id", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun setEnabled(context: Context, id: String, on: Boolean) {
        val dir = File(root(context), id)
        val flag = File(dir, ".disabled")
        if (on) flag.delete() else runCatching { flag.createNewFile() }
    }

    /** 把插件源码装进解释器 */
    internal fun prepare(context: Context, plugin: Plugin): Loaded {
        val logs = ArrayList<String>()
        val interp = Interp()
        val assets = MtAssets(plugin.dir)
        val lang = context.resources.configuration.locales[0].language.ifEmpty { "zh" }
        val country = context.resources.configuration.locales[0].country
        val prefs = context.getSharedPreferences("mtplugin_${plugin.id}", Context.MODE_PRIVATE)
        val files = File(context.filesDir, "mtplugin-data/${plugin.id}").apply { mkdirs() }
        val mtContext = MtContext(
            pluginId = plugin.id,
            versionCode = plugin.versionCode,
            versionName = plugin.versionName,
            prefs = prefs,
            files = files,
            assets = assets,
            lang = lang,
            country = country,
            logger = { line -> if (logs.size < 500) logs.add(line) },
            toaster = { line -> if (logs.size < 500) logs.add("· $line") }
        )
        interp.log = { line -> if (logs.size < 500) logs.add(line) }
        val ctxObj = MtApi.install(interp, mtContext) ?: throw MtRuntimeException("上下文初始化失败")

        val decls = ArrayList<com.suibian.sunfile.data.mtjava.ClassDecl>()
        var failed: String? = null
        for (f in plugin.sources) {
            try {
                decls.addAll(Parser(Lexer.lex(f.readText())).parseUnit())
            } catch (e: MtSyntaxException) {
                failed = "${f.name}: ${e.message}"
            } catch (e: Exception) {
                failed = "${f.name}: ${e.message}"
            }
        }
        if (failed != null) {
            logs.add("语法解析失败 $failed")
            throw MtSyntaxException("插件源码无法解析：$failed")
        }
        interp.load(decls)
        // libs 下的 jar：解成 class 直接进解释器，和源码类走同一套对象模型
        val libs = File(plugin.dir, "libs")
        if (libs.isDirectory) {
            val jarClasses = runCatching { JarLoader.load(libs) }
                .onFailure { logs.add("jar 读取失败：${it.message}") }
                .getOrDefault(emptyList())
            if (jarClasses.isNotEmpty()) {
                runCatching { interp.loadClasses(jarClasses) }
                    .onFailure { logs.add("jar 加载失败：${it.message}") }
                logs.add("已载入 ${jarClasses.size} 个类（${libs.listFiles()?.count { it.name.endsWith(".jar", true) } ?: 0} 个 jar）")
            }
        }
        if (decls.isEmpty() && interp.classes.isEmpty()) throw MtRuntimeException("插件里没有可执行的 Java 源码")
        return Loaded(plugin, interp, mtContext, ctxObj, logs)
    }

    private fun newInstance(loaded: Loaded, className: String): JObj {
        val cls = loaded.interp.classOf(className)
            ?: throw MtRuntimeException("找不到接口类 $className")
        return loaded.interp.construct(cls, emptyList())
    }

    /** 构造设置界面的选项列表 */
    fun buildPreference(context: Context, plugin: Plugin): Pair<List<PrefItem>, List<String>> {
        val loaded = prepare(context, plugin)
        val className = plugin.mainPreference ?: throw MtRuntimeException("没有声明设置界面")
        val obj = newInstance(loaded, className)
        val builder = PreferenceBuilder()
        loaded.interp.callMethod(obj, "onBuild", listOf(loaded.ctxObj, builder))
        builder.ensureLocal(loaded.context.getLocalString())
        return builder.items to loaded.logs
    }

    /** 翻译引擎名 */
    fun engineName(context: Context, plugin: Plugin): String {
        val className = plugin.engineClass ?: return ""
        val loaded = prepare(context, plugin)
        val obj = newInstance(loaded, className)
        loaded.interp.callMethod(obj, "init", listOf(loaded.ctxObj))
        runCatching { loaded.interp.callMethod(obj, "init", emptyList()) }
        return (loaded.interp.callMethod(obj, "name", emptyList()) as? String) ?: className
    }

    fun sourceLanguages(context: Context, plugin: Plugin): List<String> = languages(context, plugin, null)

    fun targetLanguages(context: Context, plugin: Plugin, source: String): List<String> =
        languages(context, plugin, source)

    private fun languages(context: Context, plugin: Plugin, source: String?): List<String> {
        val className = plugin.engineClass ?: return emptyList()
        return runCatching {
            val loaded = prepare(context, plugin)
            val obj = newInstance(loaded, className)
            loaded.interp.callMethod(obj, "init", listOf(loaded.ctxObj))
            runCatching { loaded.interp.callMethod(obj, "init", emptyList()) }
            val r = if (source == null) {
                loaded.interp.callMethod(obj, "loadSourceLanguages", emptyList())
            } else {
                loaded.interp.callMethod(obj, "loadTargetLanguages", listOf(source))
            }
            com.suibian.sunfile.data.mtjava.V.iterable(r).map { com.suibian.sunfile.data.mtjava.V.str(it) }
        }.getOrElse { emptyList() }
    }

    /** 跑一次翻译，用于测试与文本编辑器里的「用插件翻译」 */
    fun translate(context: Context, plugin: Plugin, text: String, source: String, target: String): RunResult {
        val className = plugin.engineClass
            ?: return RunResult(false, "", listOf("插件没有实现翻译接口"))
        val logs = ArrayList<String>()
        return try {
            val loaded = prepare(context, plugin)
            logs.addAll(loaded.logs)
            val obj = newInstance(loaded, className)
            loaded.interp.callMethod(obj, "init", listOf(loaded.ctxObj))
            runCatching { loaded.interp.callMethod(obj, "init", emptyList()) }
            runCatching { loaded.interp.callMethod(obj, "beforeStart", emptyList()) }
            runCatching { loaded.interp.callMethod(obj, "onStart", emptyList()) }
            val out = loaded.interp.callMethod(obj, "translate", listOf(text, source, target))
            runCatching { loaded.interp.callMethod(obj, "onFinish", emptyList()) }
            runCatching { loaded.interp.callMethod(obj, "afterFinish", emptyList()) }
            RunResult(true, out as? String ?: com.suibian.sunfile.data.mtjava.V.str(out), logs + loaded.logs)
        } catch (e: Throwable) {
            logs.add(e.message ?: e.javaClass.simpleName)
            RunResult(false, "", logs)
        }
    }

    /** 有没有可用的翻译引擎 */
    fun engines(context: Context): List<Plugin> =
        list(context).filter { it.enabled && it.engineClass != null }

    /** 内置示例插件：把选中文本转成大写 / 小写 */
    fun installDemo(context: Context) {
        val dir = File(root(context), ".demo-${System.currentTimeMillis()}")
        File(dir, "src").mkdirs()
        File(dir, "assets").mkdirs()
        File(dir, "manifest.json").writeText(
            """
            {
              // 示例插件：大小写转换
              pluginSdkVersion: 2,
              pluginID: "cc.sun.document.demo",
              versionCode: 1,
              versionName: "v1.0",
              name: "大小写转换",
              description: "把选中文本转成大写或小写，演示 MT 插件接口",
              interfaces: [ "CaseEngine" ],
              mainPreference: "CasePreference"
            }
            """.trimIndent()
        )
        File(dir, "src/CaseEngine.java").writeText(
            """
            import bin.mt.plugin.api.translation.BaseTranslationEngine;
            import java.util.Arrays;
            import java.util.List;

            public class CaseEngine extends BaseTranslationEngine {
                @Override
                public String name() {
                    return "大小写转换";
                }

                @Override
                public List<String> loadSourceLanguages() {
                    return Arrays.asList("src");
                }

                @Override
                public List<String> loadTargetLanguages(String sourceLanguage) {
                    return Arrays.asList("upper", "lower");
                }

                @Override
                public String getLanguageDisplayName(String language) {
                    if (language.equals("upper")) return "大写";
                    if (language.equals("lower")) return "小写";
                    return "原文";
                }

                @Override
                public String translate(String text, String sourceLanguage, String targetLanguage) {
                    if (targetLanguage.equals("upper")) {
                        return text.toUpperCase();
                    }
                    return text.toLowerCase();
                }
            }
            """.trimIndent()
        )
        File(dir, "src/CasePreference.java").writeText(
            """
            import bin.mt.plugin.api.MTPluginContext;
            import bin.mt.plugin.api.preference.PluginPreference;

            public class CasePreference implements PluginPreference {
                @Override
                public void onBuild(MTPluginContext context, Builder builder) {
                    builder.addHeader("转换");
                    builder.addText("大小写转换")
                            .summary("选中文本后可以在这里测试");
                    builder.addSwitch("默认转大写", "upper")
                            .defaultValue(true)
                            .summaryOn("转成 ABC")
                            .summaryOff("转成 abc");
                    builder.addInput("额外后缀", "suffix")
                            .summary("加在结果末尾")
                            .defaultValue("");
                }
            }
            """.trimIndent()
        )
        val target = File(root(context), "cc.sun.document.demo")
        if (target.exists()) target.deleteRecursively()
        dir.renameTo(target)
    }
}

/** 宽容的 JSON：允许注释、裸键、单引号、尾逗号 */
object LooseJson {

    fun parse(text: String): JSONObject? = runCatching { JSONObject(clean(text)) }.getOrNull()

    fun parseArray(text: String): JSONArray? = runCatching { JSONArray(clean(text)) }.getOrNull()

    private fun clean(src: String): String {
        val sb = StringBuilder(src.length)
        var i = 0
        var inStr = false
        var quote = ' '
        while (i < src.length) {
            val c = src[i]
            if (inStr) {
                sb.append(c)
                if (c == '\\' && i + 1 < src.length) {
                    sb.append(src[i + 1])
                    i += 2
                    continue
                }
                if (c == quote) inStr = false
                i++
                continue
            }
            if (c == '"' || c == '\'') {
                inStr = true
                quote = c
                sb.append('"')
                i++
                continue
            }
            if (c == '/' && i + 1 < src.length && src[i + 1] == '/') {
                while (i < src.length && src[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < src.length && src[i + 1] == '*') {
                i += 2
                while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) i++
                i += 2
                continue
            }
            if (c == ',' ) {
                // 尾逗号：后面只有空白和 } 或 ]
                var j = i + 1
                while (j < src.length && src[j].isWhitespace()) j++
                if (j < src.length && (src[j] == '}' || src[j] == ']')) {
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return quoteKeys(sb.toString())
    }

    /** 给裸键补引号 */
    private fun quoteKeys(s: String): String {
        val sb = StringBuilder(s.length + 32)
        var i = 0
        var inStr = false
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                sb.append(c)
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(s[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') inStr = false
                i++
                continue
            }
            if (c == '"') {
                inStr = true
                sb.append(c)
                i++
                continue
            }
            if (c == '{' || c == ',') {
                sb.append(c)
                i++
                var j = i
                while (j < s.length && s[j].isWhitespace()) j++
                if (j < s.length && (s[j].isLetter() || s[j] == '_')) {
                    // 前面可能有空白
                    sb.append(s, i, j)
                    val start = j
                    while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                    sb.append('"').append(s, start, j).append('"')
                    i = j
                }
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
