package com.suibian.sunfile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.data.ClipItem
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.PrefsStore
import com.suibian.sunfile.data.SortMode
import com.suibian.sunfile.data.StorageInfo
import com.suibian.sunfile.data.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PaneState(
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val firstLoad: Boolean = true,
    val error: String? = null,
    val back: List<String> = emptyList(),
    val forward: List<String> = emptyList(),
    val token: Int = 0
) {
    val dirCount: Int get() = entries.count { it.isDir }
    val fileCount: Int get() = entries.size - dirCount
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val canBack: Boolean get() = back.isNotEmpty()
    val canForward: Boolean get() = forward.isNotEmpty()
    val canUp: Boolean get() = path.length > 1
    val currentDir: File get() = File(path)
}

data class OpState(
    val title: String,
    val currentFile: String = "",
    val done: Long = 0,
    val total: Long = 0
) {
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class SearchResult(val entry: FileEntry, val relative: String)

data class SearchUiState(
    val root: String = "",
    val query: String = "",
    val running: Boolean = false,
    val scanned: Int = 0,
    val current: String = "",
    val results: List<SearchResult> = emptyList(),
    val finished: Boolean = false
)

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {

    val prefsStore = PrefsStore(app)

    private val _panes = MutableStateFlow(listOf(PaneState(), PaneState()))
    val panes: StateFlow<List<PaneState>> = _panes

    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage

    private val _clipboard = MutableStateFlow(ClipItem(emptyList(), false))
    val clipboard: StateFlow<ClipItem> = _clipboard

    private val _busy = MutableStateFlow<OpState?>(null)
    val busy: StateFlow<OpState?> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search

    private val _updateTick = MutableStateFlow(0)
    val updateTick: StateFlow<Int> = _updateTick

    private var searchJob: Job? = null
    private var lastProgressTs = 0L
    private var initialized = false

    val isInitialized: Boolean get() = initialized

    fun init() {
        if (initialized) return
        initialized = true
        // 每次启动都从内部存储开始，符合"进入即内部存储"的预期
        for (i in 0..1) {
            load(i, "/storage/emulated/0", record = false, pushHistory = false)
        }
    }

    val activePaneState: PaneState get() = _panes.value[_active.value]

    fun pane(index: Int): PaneState = _panes.value[index]

    fun setActive(index: Int) {
        if (index in 0..1) _active.value = index
    }

    private fun mutate(index: Int, block: (PaneState) -> PaneState) {
        _panes.value = _panes.value.mapIndexed { i, p -> if (i == index) block(p) else p }
    }

    // ---------------- 导航 ----------------

    fun navigate(path: String, pushHistory: Boolean = true) {
        load(_active.value, path, record = true, pushHistory = pushHistory)
    }

    fun open(index: Int, path: String) = load(index, path, record = true, pushHistory = true)

    private fun load(
        index: Int,
        path: String,
        record: Boolean,
        pushHistory: Boolean,
        selectAfter: String? = null
    ) {
        val dir = File(path)
        mutate(index) {
            it.copy(loading = true, error = null, selected = emptySet(), firstLoad = it.entries.isEmpty())
        }
        viewModelScope.launch {
            val cfg = prefsStore.prefs.value
            val result = withContext(Dispatchers.IO) {
                runCatching { FileRepo.list(dir, cfg.showHidden, cfg.sort) }
            }
            result.onSuccess { list ->
                if (record) prefsStore.saveLastPath(index, dir.absolutePath)
                _storage.value = withContext(Dispatchers.IO) { StorageStats.of(dir.absolutePath) }
                mutate(index) {
                    it.copy(
                        path = dir.absolutePath,
                        entries = list,
                        loading = false,
                        firstLoad = false,
                        error = null,
                        token = it.token + 1,
                        back = if (pushHistory && it.path.isNotEmpty() && it.path != dir.absolutePath) {
                            (it.back + it.path).takeLast(50)
                        } else it.back,
                        forward = if (pushHistory) emptyList() else it.forward,
                        selected = if (selectAfter != null) setOf(selectAfter) else emptySet()
                    )
                }
            }.onFailure { e ->
                val msg = e.message ?: "无法打开目录"
                mutate(index) {
                    it.copy(
                        loading = false,
                        firstLoad = false,
                        error = msg,
                        path = if (it.path.isEmpty()) dir.absolutePath else it.path
                    )
                }
                _messages.tryEmit(msg)
            }
        }
    }

    fun goBack() {
        val i = _active.value
        val p = _panes.value[i]
        val prev = p.back.lastOrNull() ?: return
        mutate(i) { it.copy(back = it.back.dropLast(1), forward = (listOf(it.path) + it.forward).take(50)) }
        load(i, prev, record = true, pushHistory = false)
    }

    fun goForward() {
        val i = _active.value
        val p = _panes.value[i]
        val next = p.forward.firstOrNull() ?: return
        mutate(i) { it.copy(forward = it.forward.drop(1), back = (it.back + it.path).takeLast(50)) }
        load(i, next, record = true, pushHistory = false)
    }

    fun goUp() {
        val p = _panes.value[_active.value]
        val parent = File(p.path).parentFile ?: return
        navigate(parent.absolutePath)
    }

    fun refresh() {
        val p = _panes.value[_active.value]
        if (p.path.isNotEmpty()) load(_active.value, p.path, record = false, pushHistory = false)
    }

    fun refreshAll() {
        for (i in 0..1) {
            val p = _panes.value[i]
            if (p.path.isNotEmpty()) load(i, p.path, record = false, pushHistory = false)
        }
        _storage.value = StorageStats.of(_panes.value[_active.value].path)
    }

    /** 交换两个窗口的内容：当前看到的目录随之换成另一栏 */
    fun swapPanes() {
        val list = _panes.value
        _panes.value = listOf(list[1], list[0])
        _storage.value = StorageStats.of(_panes.value[_active.value].path)
        _messages.tryEmit("已交换 A / B 窗口")
    }

    // ---------------- 选择 ----------------

    fun toggleSelect(entry: FileEntry) {
        val i = _active.value
        mutate(i) { p ->
            val set = p.selected.toMutableSet()
            if (!set.add(entry.path)) set.remove(entry.path)
            p.copy(selected = set)
        }
    }

    fun selectOnly(entry: FileEntry) {
        mutate(_active.value) { it.copy(selected = setOf(entry.path)) }
    }

    fun selectAll() {
        mutate(_active.value) { it.copy(selected = it.entries.map { e -> e.path }.toSet()) }
    }

    fun clearSelection() {
        mutate(_active.value) { it.copy(selected = emptySet()) }
    }

    // ---------------- 剪贴板与批量操作 ----------------

    fun setClipboard(cut: Boolean) {
        val p = activePaneState
        if (p.selected.isEmpty()) {
            _messages.tryEmit("请先选择文件")
            return
        }
        _clipboard.value = ClipItem(p.selected.toList(), cut)
        mutate(_active.value) { it.copy(selected = emptySet()) }
        _messages.tryEmit("已${if (cut) "剪切" else "复制"} ${p.selected.size} 项，可交换窗口后粘贴")
    }

    fun clearClipboard() {
        _clipboard.value = ClipItem(emptyList(), false)
    }

    fun paste() {
        val clip = _clipboard.value
        if (clip.paths.isEmpty() || _busy.value != null) return
        val destIndex = _active.value
        val destDir = _panes.value[destIndex].currentDir
        if (!destDir.isDirectory) {
            _messages.tryEmit("目标不是有效目录")
            return
        }
        viewModelScope.launch {
            _busy.value = OpState(if (clip.cut) "正在移动…" else "正在复制…")
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for (path in clip.paths) {
                    val src = File(path)
                    if (!src.exists()) continue
                    if (src.isDirectory && FileRepo.isInside(src, destDir)) {
                        err = "不能把文件夹复制到自身内部"
                        break
                    }
                    try {
                        if (clip.cut) FileRepo.move(src, destDir) { n, d, t -> report(n, d, t) }
                        else FileRepo.copy(src, destDir) { n, d, t -> report(n, d, t) }
                    } catch (e: Exception) {
                        err = e.message ?: "操作失败"
                        break
                    }
                }
                err
            }
            _busy.value = null
            if (clip.cut) _clipboard.value = ClipItem(emptyList(), false)
            refreshAll()
            _messages.tryEmit(fail ?: if (clip.cut) "移动完成" else "复制完成")
        }
    }

    private fun report(name: String, done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressTs < 80 && done < total) return
        lastProgressTs = now
        _busy.value = _busy.value?.copy(currentFile = name, done = done, total = total)
    }

    fun deleteSelected() {
        val p = activePaneState
        val paths = p.selected.toList()
        if (paths.isEmpty() || _busy.value != null) return
        viewModelScope.launch {
            _busy.value = OpState("正在删除…", "", 0, paths.size.toLong())
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for ((i, path) in paths.withIndex()) {
                    try {
                        FileRepo.delete(File(path))
                    } catch (e: Exception) {
                        err = e.message
                        break
                    }
                    _busy.value = OpState("正在删除…", File(path).name, i + 1L, paths.size.toLong())
                }
                err
            }
            _busy.value = null
            mutate(_active.value) { it.copy(selected = emptySet()) }
            refreshAll()
            _messages.tryEmit(fail ?: "已删除 ${paths.size} 项")
        }
    }

    fun deletePaths(paths: List<String>, onDone: () -> Unit = {}) {
        if (paths.isEmpty() || _busy.value != null) return
        viewModelScope.launch {
            _busy.value = OpState("正在删除…", "", 0, paths.size.toLong())
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for ((i, path) in paths.withIndex()) {
                    try {
                        FileRepo.delete(File(path))
                    } catch (e: Exception) {
                        err = e.message
                        break
                    }
                    _busy.value = OpState("正在删除…", File(path).name, i + 1L, paths.size.toLong())
                }
                err
            }
            _busy.value = null
            mutate(_active.value) { it.copy(selected = emptySet()) }
            refreshAll()
            onDone()
            _messages.tryEmit(fail ?: "已删除 ${paths.size} 项")
        }
    }

    fun deletePath(path: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching { FileRepo.delete(File(path)) }.exceptionOrNull()?.message
            }
            refreshAll()
            onDone()
            _messages.tryEmit(err ?: "已删除")
        }
    }

    fun rename(path: String, newName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching { FileRepo.rename(File(path), newName) }.exceptionOrNull()?.message
            }
            refreshAll()
            onDone()
            _messages.tryEmit(err ?: "重命名成功")
        }
    }

    fun create(createDir: Boolean, name: String, onDone: () -> Unit = {}) {
        val parent = activePaneState.currentDir
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (createDir) FileRepo.createDir(parent, name) else FileRepo.createFile(parent, name)
                }.exceptionOrNull()?.message
            }
            refresh()
            onDone()
            _messages.tryEmit(err ?: if (createDir) "文件夹已创建" else "文件已创建")
        }
    }

    // ---------------- 偏好 ----------------

    fun updatePrefs(block: (AppPrefs) -> AppPrefs) {
        prefsStore.update(block)
        refresh()
    }

    fun toggleHidden() = updatePrefs { it.copy(showHidden = !it.showHidden) }

    fun setSort(mode: SortMode) = updatePrefs { it.copy(sort = it.sort.nextFor(mode)) }

    fun toggleAccelerator() = prefsStore.update { it.copy(useAccelerator = !it.useAccelerator) }

    fun toggleAutoCheck() = prefsStore.update { it.copy(autoCheckUpdate = !it.autoCheckUpdate) }

    fun requestUpdateCheck() {
        _updateTick.value += 1
    }

    // ---------------- 搜索 ----------------

    fun openSearch() {
        _search.value = _search.value.copy(
            root = activePaneState.path,
            results = emptyList(),
            scanned = 0,
            finished = false
        )
    }

    fun updateQuery(q: String) {
        _search.value = _search.value.copy(query = q)
    }

    fun startSearch() {
        val s = _search.value
        if (s.query.isBlank() || s.running) return
        searchJob?.cancel()
        _search.value = s.copy(running = true, results = emptyList(), scanned = 0, finished = false)
        searchJob = viewModelScope.launch {
            val rootDir = File(s.root)
            val list = withContext(Dispatchers.IO) {
                FileRepo.search(
                    root = rootDir,
                    query = s.query,
                    onProgress = { scanned, current ->
                        _search.value = _search.value.copy(scanned = scanned, current = current)
                    },
                    cancelled = { !isActive }
                )
            }
            _search.value = _search.value.copy(
                running = false,
                finished = true,
                results = list.map { SearchResult(it, FileRepo.relativeTo(s.root, it.path)) }
            )
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _search.value = _search.value.copy(running = false, finished = true)
    }

    /** 从搜索结果跳回文件列表并选中 */
    fun revealPath(target: FileEntry) {
        val parent = target.file.parent ?: return
        load(_active.value, parent, record = true, pushHistory = true, selectAfter = target.path)
    }

    suspend fun propertiesOf(file: File): Map<String, String> = withContext(Dispatchers.IO) {
        val isDir = file.isDirectory
        val size = if (isDir) FileRepo.dirSize(file) else file.length()
        val map = linkedMapOf<String, String>()
        map["名称"] = file.name
        map["路径"] = file.absolutePath
        map["类型"] = if (isDir) "文件夹" else {
            val ext = file.extension.lowercase()
            if (ext.isEmpty()) "文件" else "${ext.uppercase()} 文件"
        }
        map["大小"] = com.suibian.sunfile.util.Fmt.size(size) + if (isDir) "（含子项）" else ""
        if (isDir) {
            val (d, f) = FileRepo.countChildren(file)
            map["包含"] = "$d 个文件夹，$f 个文件"
        }
        map["修改时间"] = com.suibian.sunfile.util.Fmt.time(file.lastModified())
        map["权限"] = com.suibian.sunfile.util.Fmt.permissions(file)
        map["可读"] = if (file.canRead()) "是" else "否"
        map["可写"] = if (file.canWrite()) "是" else "否"
        map
    }
}
