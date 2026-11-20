package com.suibian.sunfile.ui.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用户语法插件。
 *
 * 一个插件就是一个 JSON：扩展名、关键字、注释符号。
 * 放在外部目录的 plugins/syntax 下，重启编辑器后生效；
 * 首次运行会写一份 toml.json 当样例，照抄改名字即可加语言。
 */
data class SyntaxPlugin(
    val id: String,
    val name: String,
    val extensions: List<String>,
    val keywords: Set<String>,
    val lineComment: String?,
    val blockStart: String?,
    val blockEnd: String?,
    val caseInsensitive: Boolean
)

object SyntaxPlugins {

    private const val DIR = "plugins/syntax"

    @Volatile
    private var loaded = false

    private var plugins: List<SyntaxPlugin> = emptyList()
    private var byExt: Map<String, SyntaxPlugin> = emptyMap()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val result = LinkedHashMap<String, SyntaxPlugin>()
            runCatching {
                context.assets.list(DIR)?.forEach { name ->
                    if (!name.endsWith(".json")) return@forEach
                    context.assets.open("$DIR/$name").bufferedReader().use {
                        parse(it.readText())?.let { p -> result[p.id] = p }
                    }
                }
            }
            userDir(context).listFiles()?.forEach { f ->
                if (!f.name.endsWith(".json")) return@forEach
                runCatching {
                    f.bufferedReader().use { parse(it.readText())?.let { p -> result[p.id] = p } }
                }
            }
            plugins = result.values.toList()
            byExt = result.values.flatMap { p -> p.extensions.map { it.lowercase() to p } }.toMap()
            loaded = true
            installSample(context)
        }
    }

    fun all(): List<SyntaxPlugin> = plugins

    fun byId(id: String?): SyntaxPlugin? = plugins.firstOrNull { it.id == id }

    fun specFor(fileName: String): SyntaxPlugin? {
        if (!loaded) return null
        val lower = fileName.lowercase()
        return byExt[lower.substringAfterLast('.', "")]
    }

    fun userDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIR).apply { if (!exists()) mkdirs() }
    }

    /** 只在目录为空时写入样例，避免覆盖用户改过的文件 */
    private fun installSample(context: Context) {
        runCatching {
            val dir = userDir(context)
            if (dir.listFiles()?.isNotEmpty() == true) return
            File(dir, "toml.json").writeText(
                """
                {
                  "id": "toml",
                  "name": "TOML",
                  "extensions": ["toml", "lock"],
                  "caseInsensitive": false,
                  "lineComment": "#",
                  "keywords": ["true", "false", "table", "array", "key", "value", "datetime"]
                }
                """.trimIndent()
            )
            File(dir, "README.txt").writeText(
                """
                每写一个 .json 就多一种语言，字段：
                  id           唯一标识
                  name         显示名
                  extensions   扩展名数组，不带点
                  keywords     关键字数组
                  lineComment  行注释前缀，可省略
                  blockStart / blockEnd  块注释，可省略
                  caseInsensitive  关键字是否忽略大小写
                改完重新打开编辑器生效。
                """.trimIndent()
            )
        }
    }

    private fun parse(raw: String): SyntaxPlugin? = runCatching {
        val o = JSONObject(raw)
        val id = o.optString("id").ifBlank { return null }
        val exts = ArrayList<String>()
        (o.opt("extensions") as? JSONArray)?.let { arr ->
            for (i in 0 until arr.length()) exts.add(arr.optString(i))
        }
        val keywords = HashSet<String>()
        (o.opt("keywords") as? JSONArray)?.let { arr ->
            for (i in 0 until arr.length()) keywords.add(arr.optString(i))
        }
        SyntaxPlugin(
            id = id,
            name = o.optString("name", id),
            extensions = exts,
            keywords = keywords,
            lineComment = o.optString("lineComment").ifBlank { null },
            blockStart = o.optString("blockStart").ifBlank { null },
            blockEnd = o.optString("blockEnd").ifBlank { null },
            caseInsensitive = o.optBoolean("caseInsensitive", false)
        )
    }.getOrNull()
}
