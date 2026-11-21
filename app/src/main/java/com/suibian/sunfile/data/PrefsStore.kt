package com.suibian.sunfile.data

import android.content.Context
import com.suibian.sunfile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 设置项：每一条都对应设置页上的一个可操作项，并且真的被业务读取 */
data class AppPrefs(
    // ---------- 已有 ----------
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val htmlMode: String = "ASK",
    val sort: SortSpec = SortSpec(),
    val useAccelerator: Boolean = true,
    val autoCheckUpdate: Boolean = true,
    val openWith: Map<String, OpenMode> = emptyMap(),
    val editorFontSize: Int = 13,
    val editorWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val editorCompletion: Boolean = true,
    val bookmarks: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val searchFavorites: List<String> = emptyList(),

    // ---------- 启动 ----------
    /** 自定义 su 命令，例如 magisk 的 su 路径或多用户 su */
    val customSuCommand: String = "su",
    val startPathLeft: String = "",
    val startPathRight: String = "",
    /** 打开应用后自动恢复上次的提权通道 */
    val autoStartService: Boolean = true,

    // ---------- 外观 ----------
    /** 主题色覆盖，0 表示跟随内置色板 */
    val accentColor: Long = 0L,
    /** 界面模板：classic / slate / paper / soft，决定密度、圆角、分割线与字号 */
    val skinId: String = "classic",
    /** -1 紧凑 / 0 标准 / 1 宽松 */
    val fileListSize: Int = 0,
    val fileNameMaxLines: Int = 1,
    /** 时间列显示创建时间（否则用修改时间） */
    val timeByCreated: Boolean = false,
    val hidePermissions: Boolean = false,
    val dateFormat: String = "yyyy-MM-dd HH:mm",

    // ---------- 常规 ----------
    val backupBeforeSave: Boolean = false,
    /** 复制/移动时保留原文件的修改时间 */
    val keepFileTime: Boolean = true,
    val inputFavorites: Boolean = true,
    val customMt2Dir: String = "",

    // ---------- 回收站 ----------
    val trashEnabled: Boolean = true,
    val defaultMoveToTrash: Boolean = true,
    /** 0 表示不自动清理 */
    val trashAutoCleanDays: Int = 30,
    val showDeleteWarning: Boolean = true,

    // ---------- 安装 ----------
    val verifyApk: Boolean = true,
    val deleteApkAfterInstall: Boolean = false,
    val confirmInstall: Boolean = true,
    val installViaShizuku: Boolean = true,
    val installViaDhizuku: Boolean = false,
    val installViaRoot: Boolean = false,
    val customInstaller: String = "",
    val installPriority: String = "SHIZUKU",

    // ---------- 书签与底栏 ----------
    val sidebarBookmarks: Boolean = true,
    val bookmarkToTop: Boolean = true,
    val bookmarkSwipeSplit: Boolean = false,
    val bottomBarExtra: Boolean = false,
    val bottomBarPadding: Int = 0,

    // ---------- 外置存储 ----------
    val externalThumbs: Boolean = true,
    val optimizeExternal: Boolean = true,

    // ---------- 网络存储 ----------
    val networkThumbs: Boolean = false,
    val networkWifiOnly: Boolean = true,
    val networkImageLimitKb: Int = 512,
    val networkTimeoutSec: Int = 15,

    // ---------- 其他 ----------
    val language: String = "zh",

    // ---------- 侧边栏 ----------
    /** 侧边栏工具栏显示哪些工具；空列表表示全部启用 */
    val sidebarTools: List<String> = emptyList()
)

/**
 * 全局唯一的偏好存储：主题、编辑器、排序等由此驱动，
 * 保证设置页修改后主界面立即生效（多实例会各自持有独立 StateFlow）。
 *
 * 写入策略统一为「落盘 + 重读」：设置项很多，逐个手写 copy 和 put 容易漏，
 * 重读一份几十个字段的成本远低于状态不一致带来的 bug。
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
            .split('\n').filter { it.isNotBlank() }.distinct().take(15),
        searchFavorites = (sp.getString("search_favorites", "") ?: "")
            .split('\n').filter { it.isNotBlank() }.distinct().take(50),

        customSuCommand = sp.getString("custom_su", "su") ?: "su",
        startPathLeft = sp.getString("start_left", "") ?: "",
        startPathRight = sp.getString("start_right", "") ?: "",
        autoStartService = sp.getBoolean("auto_start_service", true),

        accentColor = sp.getLong("accent_color", 0L),
        skinId = sp.getString("skin_id", "classic") ?: "classic",
        fileListSize = sp.getInt("file_list_size", 0),
        fileNameMaxLines = sp.getInt("file_name_lines", 1).coerceIn(1, 3),
        timeByCreated = sp.getBoolean("time_by_created", false),
        hidePermissions = sp.getBoolean("hide_permissions", false),
        dateFormat = sp.getString("date_format", "yyyy-MM-dd HH:mm") ?: "yyyy-MM-dd HH:mm",

        backupBeforeSave = sp.getBoolean("backup_before_save", false),
        keepFileTime = sp.getBoolean("keep_file_time", true),
        inputFavorites = sp.getBoolean("input_favorites", true),
        customMt2Dir = sp.getString("custom_mt2_dir", "") ?: "",

        trashEnabled = sp.getBoolean("trash_enabled", true),
        defaultMoveToTrash = sp.getBoolean("default_trash", true),
        trashAutoCleanDays = sp.getInt("trash_clean_days", 30),
        showDeleteWarning = sp.getBoolean("delete_warning", true),

        verifyApk = sp.getBoolean("verify_apk", true),
        deleteApkAfterInstall = sp.getBoolean("delete_apk", false),
        confirmInstall = sp.getBoolean("confirm_install", true),
        installViaShizuku = sp.getBoolean("install_shizuku", true),
        installViaDhizuku = sp.getBoolean("install_dhizuku", false),
        installViaRoot = sp.getBoolean("install_root", false),
        customInstaller = sp.getString("custom_installer", "") ?: "",
        installPriority = sp.getString("install_priority", "SHIZUKU") ?: "SHIZUKU",

        sidebarBookmarks = sp.getBoolean("sidebar_bookmarks", true),
        bookmarkToTop = sp.getBoolean("bookmark_top", true),
        bookmarkSwipeSplit = sp.getBoolean("bookmark_swipe_split", false),
        bottomBarExtra = sp.getBoolean("bottom_bar_extra", false),
        bottomBarPadding = sp.getInt("bottom_bar_padding", 0),

        externalThumbs = sp.getBoolean("external_thumbs", true),
        optimizeExternal = sp.getBoolean("optimize_external", true),

        networkThumbs = sp.getBoolean("network_thumbs", false),
        networkWifiOnly = sp.getBoolean("network_wifi_only", true),
        networkImageLimitKb = sp.getInt("network_image_kb", 512),
        networkTimeoutSec = sp.getInt("network_timeout", 15),

        language = sp.getString("language", "zh") ?: "zh",
        sidebarTools = (sp.getString("sidebar_tools", "") ?: "")
            .split(',').filter { it.isNotBlank() }
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

    // ---------------- 通用写入 ----------------

    /**
     * 所有开关类设置都走这里：落盘后整体重读。
     * key 必须与 [load] 里读取时用的字符串一致。
     */
    fun setBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
        _prefs.value = load()
    }

    fun setInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
        _prefs.value = load()
    }

    fun setString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
        _prefs.value = load()
    }

    fun setLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
        _prefs.value = load()
    }

    // ---------------- 侧边栏工具 ----------------

    /** 空列表表示全部启用；写回时用逗号分隔的工具 id */
    fun setSidebarTools(ids: List<String>) {
        sp.edit().putString("sidebar_tools", ids.joinToString(",")).apply()
        _prefs.value = load()
    }

    // ---------------- 原有操作 ----------------

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
            // 新增书签放顶部还是追加到末尾，由设置项决定
            if (_prefs.value.bookmarkToTop) list.add(0, path) else list.add(path)
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

    /** 收藏 / 取消收藏一个搜索词，返回操作后是否处于收藏状态 */
    fun toggleSearchFavorite(keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isBlank()) return false
        val list = _prefs.value.searchFavorites.toMutableList()
        val added = if (list.contains(k)) {
            list.remove(k); false
        } else {
            list.add(0, k); true
        }
        _prefs.value = _prefs.value.copy(searchFavorites = list)
        sp.edit().putString("search_favorites", list.joinToString("\n")).apply()
        return added
    }

    fun removeSearchFavorite(keyword: String) {
        val list = _prefs.value.searchFavorites.filterNot { it == keyword }
        _prefs.value = _prefs.value.copy(searchFavorites = list)
        sp.edit().putString("search_favorites", list.joinToString("\n")).apply()
    }

    fun clearSearchFavorites() {
        _prefs.value = _prefs.value.copy(searchFavorites = emptyList())
        sp.edit().putString("search_favorites", "").apply()
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
        _prefs.value = block(_prefs.value)
        persist(_prefs.value)
    }

    /** 全量落盘：新增设置项时只改这里和 [load]，不会出现「界面变了、重启又回去」 */
    private fun persist(p: AppPrefs) {
        sp.edit()
            .putString("theme_mode", p.themeMode.name)
            .putBoolean("show_hidden", p.showHidden)
            .putString("html_mode", p.htmlMode)
            .putString("sort_mode", p.sort.mode.name)
            .putBoolean("sort_asc", p.sort.ascending)
            .putBoolean("use_accelerator", p.useAccelerator)
            .putBoolean("auto_check_update", p.autoCheckUpdate)
            .putInt("editor_font_size", p.editorFontSize)
            .putBoolean("editor_wrap", p.editorWrap)
            .putBoolean("editor_line_numbers", p.editorLineNumbers)
            .putBoolean("editor_completion", p.editorCompletion)
            .putString("open_with", encodeOpenWith(p.openWith))
            .putString("bookmarks", p.bookmarks.joinToString("\n"))
            .putString("recent", p.recent.joinToString("\n"))
            .putString("recent_search", p.recentSearches.joinToString("\n"))
            .putString("search_favorites", p.searchFavorites.joinToString("\n"))
            .putString("custom_su", p.customSuCommand)
            .putString("start_left", p.startPathLeft)
            .putString("start_right", p.startPathRight)
            .putBoolean("auto_start_service", p.autoStartService)
            .putLong("accent_color", p.accentColor)
            .putString("skin_id", p.skinId)
            .putInt("file_list_size", p.fileListSize)
            .putInt("file_name_lines", p.fileNameMaxLines)
            .putBoolean("time_by_created", p.timeByCreated)
            .putBoolean("hide_permissions", p.hidePermissions)
            .putString("date_format", p.dateFormat)
            .putBoolean("backup_before_save", p.backupBeforeSave)
            .putBoolean("keep_file_time", p.keepFileTime)
            .putBoolean("input_favorites", p.inputFavorites)
            .putString("custom_mt2_dir", p.customMt2Dir)
            .putBoolean("trash_enabled", p.trashEnabled)
            .putBoolean("default_trash", p.defaultMoveToTrash)
            .putInt("trash_clean_days", p.trashAutoCleanDays)
            .putBoolean("delete_warning", p.showDeleteWarning)
            .putBoolean("verify_apk", p.verifyApk)
            .putBoolean("delete_apk", p.deleteApkAfterInstall)
            .putBoolean("confirm_install", p.confirmInstall)
            .putBoolean("install_shizuku", p.installViaShizuku)
            .putBoolean("install_dhizuku", p.installViaDhizuku)
            .putBoolean("install_root", p.installViaRoot)
            .putString("custom_installer", p.customInstaller)
            .putString("install_priority", p.installPriority)
            .putBoolean("sidebar_bookmarks", p.sidebarBookmarks)
            .putBoolean("bookmark_top", p.bookmarkToTop)
            .putBoolean("bookmark_swipe_split", p.bookmarkSwipeSplit)
            .putBoolean("bottom_bar_extra", p.bottomBarExtra)
            .putInt("bottom_bar_padding", p.bottomBarPadding)
            .putBoolean("external_thumbs", p.externalThumbs)
            .putBoolean("optimize_external", p.optimizeExternal)
            .putBoolean("network_thumbs", p.networkThumbs)
            .putBoolean("network_wifi_only", p.networkWifiOnly)
            .putInt("network_image_kb", p.networkImageLimitKb)
            .putInt("network_timeout", p.networkTimeoutSec)
            .putString("language", p.language)
            .putString("sidebar_tools", p.sidebarTools.joinToString(","))
            .apply()
    }
}
