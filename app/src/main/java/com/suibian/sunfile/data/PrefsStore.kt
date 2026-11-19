package com.suibian.sunfile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppPrefs(
    val showHidden: Boolean = false,
    val sort: SortSpec = SortSpec(),
    val useAccelerator: Boolean = true,
    val autoCheckUpdate: Boolean = true,
    /** 扩展名 -> 打开方式，用户勾选「总是用此方式打开」后写入 */
    val openWith: Map<String, OpenMode> = emptyMap(),
    val editorFontSize: Int = 13,
    val editorWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val bookmarks: List<String> = emptyList()
)

class PrefsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("sunfile", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<AppPrefs> = _prefs

    private fun load(): AppPrefs = AppPrefs(
        showHidden = sp.getBoolean("show_hidden", false),
        sort = SortSpec(
            mode = runCatching { SortMode.valueOf(sp.getString("sort_mode", "NAME")!!) }
                .getOrDefault(SortMode.NAME),
            ascending = sp.getBoolean("sort_asc", true)
        ),
        useAccelerator = sp.getBoolean("use_accelerator", true),
        autoCheckUpdate = sp.getBoolean("auto_check_update", true),
        openWith = decodeOpenWith(sp.getString("open_with", "") ?: ""),
        editorFontSize = sp.getInt("editor_font_size", 13).coerceIn(10, 22),
        editorWrap = sp.getBoolean("editor_wrap", false),
        editorLineNumbers = sp.getBoolean("editor_line_numbers", true),
        bookmarks = (sp.getString("bookmarks", "") ?: "").split('\n').filter { it.isNotBlank() }.distinct()
    )

    private fun decodeOpenWith(raw: String): Map<String, OpenMode> {
        if (raw.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, OpenMode>()
        raw.split(';').forEach { seg ->
            val parts = seg.split('=')
            if (parts.size == 2) {
                runCatching { OpenMode.valueOf(parts[1]) }.getOrNull()?.let { map[parts[0]] = it }
            }
        }
        return map
    }

    private fun encodeOpenWith(map: Map<String, OpenMode>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun lastPath(pane: Int): String? = sp.getString("last_path_$pane", null)

    fun saveLastPath(pane: Int, path: String) {
        sp.edit().putString("last_path_$pane", path).apply()
    }

    fun setOpenWith(ext: String, mode: OpenMode?) {
        val next = _prefs.value.openWith.toMutableMap()
        if (mode == null) next.remove(ext) else next[ext] = mode
        _prefs.value = _prefs.value.copy(openWith = next)
        sp.edit().putString("open_with", encodeOpenWith(next)).apply()
    }

    fun clearOpenWith() {
        _prefs.value = _prefs.value.copy(openWith = emptyMap())
        sp.edit().putString("open_with", "").apply()
    }

    fun toggleBookmark(path: String): Boolean {
        val list = _prefs.value.bookmarks.toMutableList()
        val added = if (list.contains(path)) {
            list.remove(path)
            false
        } else {
            list.add(path)
            true
        }
        _prefs.value = _prefs.value.copy(bookmarks = list)
        sp.edit().putString("bookmarks", list.joinToString("\n")).apply()
        return added
    }

    fun removeBookmark(path: String) {
        val list = _prefs.value.bookmarks.filterNot { it == path }
        _prefs.value = _prefs.value.copy(bookmarks = list)
        sp.edit().putString("bookmarks", list.joinToString("\n")).apply()
    }

    fun update(block: (AppPrefs) -> AppPrefs) {
        val next = block(_prefs.value)
        _prefs.value = next
        sp.edit()
            .putBoolean("show_hidden", next.showHidden)
            .putString("sort_mode", next.sort.mode.name)
            .putBoolean("sort_asc", next.sort.ascending)
            .putBoolean("use_accelerator", next.useAccelerator)
            .putBoolean("auto_check_update", next.autoCheckUpdate)
            .putInt("editor_font_size", next.editorFontSize)
            .putBoolean("editor_wrap", next.editorWrap)
            .putBoolean("editor_line_numbers", next.editorLineNumbers)
            .putString("open_with", encodeOpenWith(next.openWith))
            .putString("bookmarks", next.bookmarks.joinToString("\n"))
            .apply()
    }
}
