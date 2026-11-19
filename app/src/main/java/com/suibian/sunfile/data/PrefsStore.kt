package com.suibian.sunfile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppPrefs(
    val showHidden: Boolean = false,
    val sort: SortSpec = SortSpec(),
    val useAccelerator: Boolean = true,
    val autoCheckUpdate: Boolean = true
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
        autoCheckUpdate = sp.getBoolean("auto_check_update", true)
    )

    fun lastPath(pane: Int): String? = sp.getString("last_path_$pane", null)

    fun saveLastPath(pane: Int, path: String) {
        sp.edit().putString("last_path_$pane", path).apply()
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
            .apply()
    }
}
