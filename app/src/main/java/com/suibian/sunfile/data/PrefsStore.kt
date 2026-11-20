package com.suibian.sunfile.data

import android.content.Context
import com.suibian.sunfile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    /** html / md 的打开方式：ASK 每次询问 / PREVIEW 直接预览 / EDIT 直接编辑 */
    val htmlMode: String = "ASK",
    val sort: SortSpec = SortSpec(),
    val useAccelerator: Boolean = true,
    val autoCheckUpdate: Boolean = true,
    /** 扩展名 -> 打开方式，用户勾选「总是用此方式打开」后写入 */
    val openWith: Map<String, OpenMode> = emptyMap(),
    val editorFontSize: Int = 13,
    val editorWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val editorCompletion: Boolean = true,
    val bookmarks: List<String> = emptyList(),
    /** 最近访问过的目录，最新在前 */
    val recent: List<String> = emptyList(),
    /** 最近搜索过的关键词，最新在前 */
    val recentSearches: List<String> = emptyList()
)

/**
 * 全局唯一的偏好存储：主题、编辑器、排序等由此驱动，
 * 保证设置页修改后主界面立即生效（多实例会各自持有独立 StateFlow）。
 */
class PrefsStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("sunfile", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<AppPrefs> = _prefs

    companion object {
        @Volatile
        private var instance: PrefsStore? = null

        fun get(context: Context): PrefsStore =
            instance ?: synchronized(this) {
                instance ?: PrefsStore(context.applicationContext).also { instance = it }
            }
    }

    private fun load(): AppPrefs = AppPrefs(
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("theme_mode", "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM),
        showHidden = sp.getBoolean("show_hidden", false),
        htmlMode = sp.getString("html_mode", "ASK") ?: "ASK",
        sort = SortSpec(
            mode = runCatching { SortMode.valueOf(sp.getString("sort_mode", "NAME")!!) }
                .getOrDefault(SortMode.NAME),
            ascending = sp.getBoolean("sort_asc", true)
        ),
        useAccelerator = sp.getBoolean("use_accelerator", true),
        autoCheckUpdate = sp.getBoolean("auto_check_update", true),
        openWith = decodeOpenWith(sp.getString("open_with", "") ?: ""),
        editorFontSize = sp.getInt("editor_font_size", 13).coerceIn(6, 40),
        editorWrap = sp.getBoolean("editor_wrap", false),
        editorLineNumbers = sp.getBoolean("editor_line_numbers", true),
        editorCompletion = sp.getBoolean("editor_completion", true),
        bookmarks = (sp.getString("bookmarks", "") ?: "").split('\n').filter { it.isNotBlank() }.distinct(),
        recent = (sp.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() }.distinct().take(20),
        recentSearches = (sp.getString("recent_search", "") ?: "")
            .split('\n').filter { it.isNotBlank() }.distinct().take(15)
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
        if (mode == null) Mt2.clearOpenMethod(app, ext) else Mt2.writeOpenMethod(app, ext, mode.name)
    }

    fun clearOpenWith() {
        _prefs.value.openWith.keys.forEach { Mt2.clearOpenMethod(app, it) }
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

    /** 记录访问过的目录（去重后置顶，最多 20 条） */
    fun pushRecent(path: String) {
        if (path.isBlank()) return
        val list = (_prefs.value.recent.filterNot { it == path }).toMutableList()
        list.add(0, path)
        val trimmed = list.take(20)
        if (trimmed == _prefs.value.recent) return
        _prefs.value = _prefs.value.copy(recent = trimmed)
        sp.edit().putString("recent", trimmed.joinToString("\n")).apply()
    }

    fun pushSearch(keyword: String) {
        if (keyword.isBlank()) return
        val list = (_prefs.value.recentSearches.filterNot { it == keyword }).toMutableList()
        list.add(0, keyword)
        val trimmed = list.take(15)
        _prefs.value = _prefs.value.copy(recentSearches = trimmed)
        sp.edit().putString("recent_search", trimmed.joinToString("\n")).apply()
    }

    fun clearSearches() {
        _prefs.value = _prefs.value.copy(recentSearches = emptyList())
        sp.edit().putString("recent_search", "").apply()
    }

    fun clearRecent() {
        _prefs.value = _prefs.value.copy(recent = emptyList())
        sp.edit().putString("recent", "").apply()
    }

    fun setHtmlMode(mode: String) {
        _prefs.value = _prefs.value.copy(htmlMode = mode)
        sp.edit().putString("html_mode", mode).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _prefs.value = _prefs.value.copy(themeMode = mode)
        sp.edit().putString("theme_mode", mode.name).apply()
    }

    fun update(block: (AppPrefs) -> AppPrefs) {
        val next = block(_prefs.value)
        _prefs.value = next
        sp.edit()
            .putString("theme_mode", next.themeMode.name)
            .putBoolean("show_hidden", next.showHidden)
            .putString("html_mode", next.htmlMode)
            .putString("sort_mode", next.sort.mode.name)
            .putBoolean("sort_asc", next.sort.ascending)
            .putBoolean("use_accelerator", next.useAccelerator)
            .putBoolean("auto_check_update", next.autoCheckUpdate)
            .putInt("editor_font_size", next.editorFontSize)
            .putBoolean("editor_wrap", next.editorWrap)
            .putBoolean("editor_line_numbers", next.editorLineNumbers)
            .putBoolean("editor_completion", next.editorCompletion)
            .putString("open_with", encodeOpenWith(next.openWith))
            .putString("bookmarks", next.bookmarks.joinToString("\n"))
            .putString("recent", next.recent.joinToString("\n"))
            .putString("recent_search", next.recentSearches.joinToString("\n"))
            .apply()
    }
}
