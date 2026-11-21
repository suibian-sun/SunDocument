package com.suibian.sunfile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 用户自行添加的本地存储：只存路径与显示名，不做挂载 */
data class LocalMount(val name: String, val path: String)

/** 网络存储：仅保存名称与链接，点击后交给远程浏览页处理 */
data class NetworkMount(
    val name: String,
    val uri: String,
    val group: String = DEFAULT_GROUP
) {
    /** smb:// / ftp:// / http:// / webdav:// 里的协议名 */
    val scheme: String get() = uri.substringBefore("://", "").lowercase()
}

const val DEFAULT_GROUP = "默认分组"

data class StorageConfig(
    val locals: List<LocalMount> = emptyList(),
    val networks: List<NetworkMount> = emptyList(),
    val groups: List<String> = listOf(DEFAULT_GROUP)
)

/**
 * 侧边栏「本地存储 / 网络存储 / 分组」的持久化。
 *
 * 单独一个单例而不是塞进 AppPrefs：这份数据由侧边栏直接读写，
 * 与主题、编辑器等偏好没有耦合，分开放也避免每次改动整份 prefs 落盘。
 */
object StorageBook {
    private const val SP = "sun_storages"
    private const val KEY = "config"

    private var sp: SharedPreferences? = null

    private val _config = MutableStateFlow(StorageConfig())
    val config: StateFlow<StorageConfig> = _config

    fun ensure(context: Context) {
        if (sp != null) return
        synchronized(this) {
            if (sp != null) return
            val p = context.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
            sp = p
            _config.value = decode(p.getString(KEY, null))
        }
    }

    fun addLocal(name: String, path: String): Boolean {
        val clean = path.trim().trimEnd('/').ifEmpty { "/" }
        if (clean.isBlank()) return false
        val cur = _config.value
        if (cur.locals.any { it.path == clean }) return false
        val label = name.trim().ifBlank { clean.substringAfterLast('/').ifBlank { clean } }
        commit(cur.copy(locals = cur.locals + LocalMount(label, clean)))
        return true
    }

    fun removeLocal(path: String) {
        val cur = _config.value
        commit(cur.copy(locals = cur.locals.filterNot { it.path == path }))
    }

    fun addNetwork(name: String, uri: String, group: String): Boolean {
        val u = uri.trim()
        if (u.isBlank()) return false
        val cur = _config.value
        if (cur.networks.any { it.uri == u }) return false
        val label = name.trim().ifBlank { u.substringAfter("://").substringBefore('/') }
        val g = group.ifBlank { DEFAULT_GROUP }
        commit(
            cur.copy(
                networks = cur.networks + NetworkMount(label, u, g),
                groups = (cur.groups + g).distinct()
            )
        )
        return true
    }

    fun removeNetwork(uri: String) {
        val cur = _config.value
        commit(cur.copy(networks = cur.networks.filterNot { it.uri == uri }))
    }

    fun addGroup(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank()) return false
        val cur = _config.value
        if (cur.groups.contains(n)) return false
        commit(cur.copy(groups = cur.groups + n))
        return true
    }

    fun renameGroup(from: String, to: String): Boolean {
        val target = to.trim()
        val cur = _config.value
        if (target.isBlank() || from == DEFAULT_GROUP || cur.groups.contains(target)) return false
        if (!cur.groups.contains(from)) return false
        commit(
            cur.copy(
                groups = cur.groups.map { if (it == from) target else it },
                networks = cur.networks.map { if (it.group == from) it.copy(group = target) else it }
            )
        )
        return true
    }

    /** 删除分组：组内网络存储退回默认分组，不连带删数据 */
    fun removeGroup(name: String) {
        if (name == DEFAULT_GROUP) return
        val cur = _config.value
        commit(
            cur.copy(
                groups = cur.groups.filterNot { it == name }.ifEmpty { listOf(DEFAULT_GROUP) },
                networks = cur.networks.map { if (it.group == name) it.copy(group = DEFAULT_GROUP) else it }
            )
        )
    }

    fun moveToGroup(uri: String, group: String) {
        val cur = _config.value
        val g = group.ifBlank { DEFAULT_GROUP }
        commit(
            cur.copy(
                groups = (cur.groups + g).distinct(),
                networks = cur.networks.map { if (it.uri == uri) it.copy(group = g) else it }
            )
        )
    }

    /** 组名 -> 该组下的网络存储 */
    fun grouped(config: StorageConfig): List<Pair<String, List<NetworkMount>>> {
        if (config.networks.isEmpty()) return emptyList()
        val order = config.groups.ifEmpty { listOf(DEFAULT_GROUP) }
        val byGroup = config.networks.groupBy { it.group }
        val known = order.mapNotNull { g -> byGroup[g]?.let { g to it } }
        val extra = byGroup.filterKeys { !order.contains(it) }.map { it.key to it.value }
        return known + extra
    }

    private fun commit(next: StorageConfig) {
        _config.value = next
        sp?.edit()?.putString(KEY, encode(next))?.apply()
    }

    private fun encode(c: StorageConfig): String {
        val root = JSONObject()
        root.put("locals", JSONArray().apply {
            c.locals.forEach { put(JSONObject().put("n", it.name).put("p", it.path)) }
        })
        root.put("networks", JSONArray().apply {
            c.networks.forEach {
                put(JSONObject().put("n", it.name).put("u", it.uri).put("g", it.group))
            }
        })
        root.put("groups", JSONArray().apply { c.groups.forEach { put(it) } })
        return root.toString()
    }

    private fun decode(raw: String?): StorageConfig {
        if (raw.isNullOrBlank()) return StorageConfig()
        return runCatching {
            val root = JSONObject(raw)
            val locals = root.optJSONArray("locals")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val p = o.optString("p")
                    if (p.isBlank()) null else LocalMount(o.optString("n", p), p)
                }
            } ?: emptyList()
            val nets = root.optJSONArray("networks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val u = o.optString("u")
                    if (u.isBlank()) null
                    else NetworkMount(o.optString("n", u), u, o.optString("g", DEFAULT_GROUP))
                }
            } ?: emptyList()
            val groups = root.optJSONArray("groups")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()
            StorageConfig(
                locals = locals,
                networks = nets,
                groups = (listOf(DEFAULT_GROUP) + groups).distinct()
            )
        }.getOrDefault(StorageConfig())
    }
}
