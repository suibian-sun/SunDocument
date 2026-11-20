package com.suibian.sunfile.data.mtapi

import android.content.SharedPreferences
import com.suibian.sunfile.data.mtjava.Interp
import com.suibian.sunfile.data.mtjava.JClass
import com.suibian.sunfile.data.mtjava.JMethod
import com.suibian.sunfile.data.mtjava.JObj
import com.suibian.sunfile.data.mtjava.Param
import java.io.File
import java.io.InputStream

/**
 * MT 插件 API（`bin.mt.plugin.api`）的宿主实现。
 *
 * 插件是 Java 源码，由 [com.suibian.sunfile.data.mtjava] 里的解释器直接执行，
 * 所以这里的类不需要和 MT 的 jar 二进制兼容——只要方法名、参数、返回值一致，
 * 插件里的 `context.getPreferences()` 之类调用就能落到这里。
 */

/** 插件包里的 assets 目录 */
class MtAssets(private val dir: File) {

    fun stream(name: String): InputStream? {
        val f = File(dir, "assets/$name")
        return if (f.isFile) f.inputStream() else null
    }

    fun bytes(name: String): ByteArray? = runCatching {
        val f = File(dir, "assets/$name")
        if (f.isFile) f.readBytes() else null
    }.getOrNull()

    fun text(name: String): String? = bytes(name)?.toString(Charsets.UTF_8)

    fun localString(name: String, lang: String, country: String): LocalStringHost =
        LocalStringHost.fromDir(File(dir, "assets"), name, lang, country)
}

/** 本地化文本，对应 MT 的 LocalString */
class LocalStringHost(
    private val name: String,
    private val packs: Map<String, Map<String, String>>,
    private val lang: String,
    private val country: String
) {

    fun getName(): String = name

    fun get(key: String): String = get(key, language())

    fun get(key: String, languageCode: String): String {
        packs[languageCode]?.get(key)?.let { return it }
        packs[languageCode.substringBefore('-')]?.get(key)?.let { return it }
        return packs[""]?.get(key) ?: key
    }

    fun getOrDefault(key: String, def: String): String {
        val v = get(key, language())
        return if (v == key) def else v
    }

    fun getOrDefault(key: String, def: String, languageCode: String): String {
        val v = get(key, languageCode)
        return if (v == key) def else v
    }

    fun keySet(): Set<String> = packs[""]?.keys ?: emptySet()

    private fun language(): String = if (country.isEmpty()) lang else "$lang-$country"

    /** 把 "{key}" 换成文本 */
    fun apply(text: String): String {
        if (!text.contains('{')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '{') {
                val end = text.indexOf('}', i + 1)
                if (end > i + 1) {
                    val key = text.substring(i + 1, end)
                    sb.append(get(key))
                    i = end + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    companion object {
        fun fromDir(assets: File, name: String, lang: String, country: String): LocalStringHost {
            val packs = HashMap<String, Map<String, String>>()
            parseFile(File(assets, "$name.mtl"))?.let { packs[""] = it }
            val dirs = assets.listFiles()?.filter { it.isFile && it.name.startsWith("$name-") && it.name.endsWith(".mtl") }
                ?: emptyList()
            for (f in dirs) {
                val code = f.name.removePrefix("$name-").removeSuffix(".mtl")
                parseFile(f)?.let { packs[code] = it }
            }
            return LocalStringHost(name, packs, lang, country)
        }

        fun fromFiles(dir: File, name: String, lang: String, country: String): LocalStringHost =
            fromDir(dir, name, lang, country)

        private fun parseFile(f: File): Map<String, String>? {
            if (!f.isFile) return null
            val map = LinkedHashMap<String, String>()
            runCatching {
                f.readLines(Charsets.UTF_8).forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val idx = line.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = line.substring(0, idx).trim()
                    var v = line.substring(idx + 1)
                    if (v.startsWith(" ")) v = v.substring(1)
                    map[key] = v.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
                }
            }
            return map
        }
    }
}

/** 插件上下文，对应 MTPluginContext */
class MtContext(
    private val pluginId: String,
    private val versionCode: Int,
    private val versionName: String,
    private val prefs: SharedPreferences,
    private val files: File,
    private val assets: MtAssets,
    private val lang: String,
    private val country: String,
    private val logger: (String) -> Unit,
    private val toaster: (String) -> Unit
) {

    fun getPluginId(): String = pluginId
    fun getPluginVersionCode(): Int = versionCode
    fun getPluginVersionName(): String = versionName
    fun getPreferences(): SharedPreferences = prefs
    fun getLanguage(): String = lang
    fun getCountry(): String = country
    fun getLanguageCountry(): String = if (country.isEmpty()) lang else "$lang-$country"

    fun getLocalString(): LocalStringHost = getAssetLocalString("String")

    fun getAssetLocalString(name: String): LocalStringHost = assets.localString(name, lang, country)

    fun getFileLocalString(dir: File, name: String): LocalStringHost =
        LocalStringHost.fromFiles(dir, name, lang, country)

    fun getLanguageNameLocalString(): LocalStringHost =
        LocalStringHost("LanguageName", mapOf("" to LANGUAGE_NAMES), lang, country)

    fun getAssetsAsStream(name: String): InputStream? = assets.stream(name)

    fun getFilesDir(): File = files

    fun log(msg: String) = logger(msg)

    fun log(msg: String, e: Throwable) = logger("$msg\n${e.stackTraceToString()}")

    fun log(e: Throwable) = logger(e.stackTraceToString())

    fun showToast(msg: String) = toaster(msg)

    fun showToastL(msg: String) = toaster(msg)

    private companion object {
        val LANGUAGE_NAMES: Map<String, String> = mapOf(
            "auto" to "自动检测", "zh" to "中文", "zh-CN" to "简体中文", "zh-TW" to "繁体中文",
            "en" to "英语", "ja" to "日语", "ko" to "韩语", "ru" to "俄语", "fr" to "法语",
            "de" to "德语", "es" to "西班牙语", "pt" to "葡萄牙语", "it" to "意大利语",
            "ar" to "阿拉伯语", "hi" to "印地语", "th" to "泰语", "vi" to "越南语",
            "id" to "印尼语", "ms" to "马来语", "tr" to "土耳其语", "pl" to "波兰语",
            "nl" to "荷兰语", "sv" to "瑞典语", "da" to "丹麦语", "fi" to "芬兰语",
            "no" to "挪威语", "cs" to "捷克语", "el" to "希腊语", "hu" to "匈牙利语",
            "ro" to "罗马尼亚语", "uk" to "乌克兰语", "he" to "希伯来语", "fa" to "波斯语",
            "bn" to "孟加拉语", "ta" to "泰米尔语", "ur" to "乌尔都语", "my" to "缅甸语",
            "km" to "高棉语", "lo" to "老挝语", "mn" to "蒙古语", "ne" to "尼泊尔语",
            "si" to "僧伽罗语", "ka" to "格鲁吉亚语", "hy" to "亚美尼亚语", "az" to "阿塞拜疆语",
            "kk" to "哈萨克语", "uz" to "乌兹别克语", "sr" to "塞尔维亚语", "hr" to "克罗地亚语",
            "sk" to "斯洛伐克语", "sl" to "斯洛文尼亚语", "bg" to "保加利亚语", "lt" to "立陶宛语",
            "lv" to "拉脱维亚语", "et" to "爱沙尼亚语", "tl" to "菲律宾语", "sw" to "斯瓦希里语"
        )
    }
}

/** 翻译引擎配置 */
class ConfigurationHost(
    val acceptTranslated: Boolean = false,
    val forceNotToSkipTranslated: Boolean = false,
    val targetLanguageMutable: Boolean = false,
    val autoRepairFormatControlError: Boolean = false,
    val disableAutoHideLanguage: Boolean = false
)

/** ConfigurationBuilder，链式调用后 build() */
class ConfigurationBuilderHost {
    private var acceptTranslated = false
    private var forceNotToSkipTranslated = false
    private var targetLanguageMutable = false
    private var autoRepairFormatControlError = false
    private var disableAutoHideLanguage = false

    fun setAcceptTranslated(v: Boolean): ConfigurationBuilderHost {
        acceptTranslated = v; return this
    }

    fun setForceNotToSkipTranslated(v: Boolean): ConfigurationBuilderHost {
        forceNotToSkipTranslated = v; return this
    }

    fun setTargetLanguageMutable(v: Boolean): ConfigurationBuilderHost {
        targetLanguageMutable = v; return this
    }

    fun setAutoRepairFormatControlError(v: Boolean): ConfigurationBuilderHost {
        autoRepairFormatControlError = v; return this
    }

    fun setDisableAutoHideLanguage(v: Boolean): ConfigurationBuilderHost {
        disableAutoHideLanguage = v; return this
    }

    fun build(): ConfigurationHost = ConfigurationHost(
        acceptTranslated, forceNotToSkipTranslated, targetLanguageMutable,
        autoRepairFormatControlError, disableAutoHideLanguage
    )
}

/** BaseTranslationEngine 的宿主部分：插件没重写的方法落到这里 */
class BaseEngineHost(config: Any?) {
    private val configuration: ConfigurationHost =
        config as? ConfigurationHost ?: ConfigurationHost()
    private var context: Any? = null

    fun init(ctx: Any?) {
        context = ctx
    }

    fun getContext(): Any? = context
    fun getConfiguration(): ConfigurationHost = configuration
    fun name(): String = "未命名引擎"
    fun loadSourceLanguages(): List<String> = listOf("auto")
    fun loadTargetLanguages(sourceLanguage: String): List<String> = listOf("zh-CN")
    fun getLanguageDisplayName(language: String): String = language
    fun beforeStart() {}
    fun onStart() {}
    fun onFinish() {}
    fun afterFinish() {}
    fun onError(e: Throwable): Boolean {
        (context as? MtContext)?.log(e)
        return false
    }

    fun translate(text: String, sourceLanguage: String, targetLanguage: String): String = text
}

// ---------------------------------------------------------------- 设置界面

sealed class PrefItem {
    class Header(val title: String) : PrefItem()
    class Text(val title: String) : PrefItem() {
        var summary: String? = null
        var url: String? = null
    }

    class Input(val title: String, val key: String) : PrefItem() {
        var def: String? = null
        var summary: String? = null
        var valueAsSummary = false
        var inputType = 0
        var hasValidator = false
    }

    class Switch(val title: String, val key: String) : PrefItem() {
        var def = false
        var summary: String? = null
        var summaryOn: String? = null
        var summaryOff: String? = null
    }

    class ListPref(val title: String, val key: String) : PrefItem() {
        var def: String? = null
        var summary: String? = null
        val items = ArrayList<Pair<String, String?>>()
        val itemSummaries = HashMap<String, String>()
    }
}

class TextOpt(private val item: PrefItem.Text) {
    fun summary(s: String): TextOpt { item.summary = s; return this }
    fun url(u: String): TextOpt { item.url = u; return this }
}

class InputOpt(private val item: PrefItem.Input) {
    fun defaultValue(v: String): InputOpt { item.def = v; return this }
    fun summary(s: String): InputOpt { item.summary = s; return this }
    fun valueAsSummary(): InputOpt { item.valueAsSummary = true; return this }
    fun inputType(t: Int): InputOpt { item.inputType = t; return this }
    fun validator(v: Any?): InputOpt { item.hasValidator = v != null; return this }
}

class SwitchOpt(private val item: PrefItem.Switch) {
    fun defaultValue(v: Boolean): SwitchOpt { item.def = v; return this }
    fun summary(s: String): SwitchOpt { item.summary = s; return this }
    fun summaryOn(s: String): SwitchOpt { item.summaryOn = s; return this }
    fun summaryOff(s: String): SwitchOpt { item.summaryOff = s; return this }
}

class ListOpt(private val item: PrefItem.ListPref) {
    fun defaultValue(v: String): ListOpt { item.def = v; return this }
    fun summary(s: String): ListOpt { item.summary = s; return this }
    fun addItem(name: String, value: String?): ItemOpt {
        item.items.add(name to value)
        return ItemOpt(item, value)
    }
}

class ItemOpt(private val item: PrefItem.ListPref, private val value: String?) {
    fun summary(s: String): ItemOpt {
        if (value != null) item.itemSummaries[value] = s
        return this
    }

    fun addItem(name: String, v: String?): ItemOpt {
        item.items.add(name to v)
        return ItemOpt(item, v)
    }
}

/** PluginPreference.Builder 的宿主实现 */
class PreferenceBuilder {

    val items = ArrayList<PrefItem>()
    private var local: LocalStringHost? = null

    fun setLocalString(l: LocalStringHost) {
        local = l
    }

    private fun t(s: String): String = local?.apply(s) ?: s

    fun addHeader(title: String) {
        items.add(PrefItem.Header(t(title)))
    }

    fun addText(title: String): TextOpt {
        val it = PrefItem.Text(t(title))
        items.add(it)
        return TextOpt(it)
    }

    fun addInput(title: String, key: String): InputOpt {
        val it = PrefItem.Input(t(title), key)
        items.add(it)
        return InputOpt(it)
    }

    fun addSwitch(title: String, key: String): SwitchOpt {
        val it = PrefItem.Switch(t(title), key)
        items.add(it)
        return SwitchOpt(it)
    }

    fun addList(title: String, key: String): ListOpt {
        val it = PrefItem.ListPref(t(title), key)
        items.add(it)
        return ListOpt(it)
    }

    /** 插件没调 setLocalString 时，宿主自己补上 */
    fun ensureLocal(l: LocalStringHost) {
        if (local == null) local = l
        // 已经加进来的标题里可能带 {key}
        if (items.isNotEmpty()) {
            for (i in items) {
                when (i) {
                    is PrefItem.Header -> items[items.indexOf(i)] = PrefItem.Header(l.apply(i.title))
                    else -> {}
                }
            }
        }
    }
}

/** 把 MT 的 API 装进解释器 */
object MtApi {

    internal fun install(interp: Interp, context: MtContext?): JObj? {
        interp.factories["ConfigurationBuilder"] = { ConfigurationBuilderHost() }
        interp.factories["Configuration"] = { ConfigurationHost() }

        var ctxObj: JObj? = null
        if (context != null) {
            val ctxClass = JClass("MTPluginContext")
            ctxClass.hostFactory = { it.native = context }
            interp.classes["MTPluginContext"] = ctxClass
            ctxObj = JObj(ctxClass).also { it.native = context }
        }

        val base = JClass("BaseTranslationEngine")
        base.ctors.add(
            JMethod("<init>", emptyList(), null, false, native = { _, self, _ ->
                self?.native = BaseEngineHost(null)
                null
            })
        )
        base.ctors.add(
            JMethod("<init>", listOf(Param("Configuration", "configuration")), null, false, native = { _, self, args ->
                self?.native = BaseEngineHost(args.getOrNull(0))
                null
            })
        )
        interp.classes["BaseTranslationEngine"] = base
        interp.relink()
        return ctxObj
    }
}
