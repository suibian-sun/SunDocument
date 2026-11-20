package com.suibian.sunfile.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 文件图标主题：沿用 VS Code 的 material-icon-theme 映射规则。
 *
 * 图标在 assets 的 icons 目录里，映射表是 file-icons.json（已按存在性过滤）。
 * 规则优先级与 VS Code 一致：完整文件名 → 从长到短的扩展名 → 目录名 → 默认图标。
 */
object IconTheme {

    const val FALLBACK_FILE = "file"
    const val FALLBACK_FOLDER = "folder"

    private var fileNames: Map<String, String> = emptyMap()
    private var fileExts: Map<String, String> = emptyMap()
    private var folderNames: Map<String, String> = emptyMap()
    private var folderNamesOpen: Map<String, String> = emptyMap()
    private var defaults: Map<String, String> = emptyMap()

    @Volatile
    private var loaded = false

    /** 必须在后台线程调用；重复调用只解析一次 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val json = context.assets.open("file-icons.json").bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                defaults = mapOf(
                    "file" to root.optString("file", FALLBACK_FILE),
                    "folder" to root.optString("folder", FALLBACK_FOLDER),
                    "rootFolder" to root.optString("rootFolder", FALLBACK_FOLDER),
                    "folderExpanded" to root.optString("folderExpanded", "folder-open"),
                    "rootFolderExpanded" to root.optString("rootFolderExpanded", "folder-open")
                )
                fileNames = root.optJSONObject("fileNames").toStringMap()
                fileExts = root.optJSONObject("fileExtensions").toStringMap()
                folderNames = root.optJSONObject("folderNames").toStringMap()
                folderNamesOpen = root.optJSONObject("folderNamesExpanded").toStringMap()
            }
            loaded = true
        }
    }

    fun isLoaded(): Boolean = loaded

    /** 目录图标；expanded 为 true 时用展开态 */
    fun folderIcon(name: String, expanded: Boolean = false, root: Boolean = false): String {
        val key = name.lowercase()
        val byName = (if (expanded) folderNamesOpen else folderNames)[key]
        if (byName != null) return byName
        return when {
            root && expanded -> defaults["rootFolderExpanded"] ?: "folder-open"
            root -> defaults["rootFolder"] ?: FALLBACK_FOLDER
            expanded -> defaults["folderExpanded"] ?: "folder-open"
            else -> defaults["folder"] ?: FALLBACK_FOLDER
        }
    }

    /** 普通文件图标：先按全名，再按从长到短的复合扩展名 */
    fun fileIcon(name: String): String {
        val lower = name.lowercase()
        fileNames[lower]?.let { return it }
        var dot = lower.indexOf('.')
        while (dot >= 0 && dot < lower.length - 1) {
            val suffix = lower.substring(dot + 1)
            if (suffix.isNotEmpty()) fileExts[suffix]?.let { return it }
            dot = lower.indexOf('.', dot + 1)
        }
        return defaults["file"] ?: FALLBACK_FILE
    }

    fun iconFor(file: File, isDir: Boolean, expanded: Boolean = false): String =
        if (isDir) folderIcon(file.name, expanded) else fileIcon(file.name)

    fun iconForName(name: String, isDir: Boolean, expanded: Boolean = false): String =
        if (isDir) folderIcon(name, expanded) else fileIcon(name)

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = HashMap<String, String>(length() * 2)
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k.lowercase()] = optString(k)
        }
        return out
    }
}
