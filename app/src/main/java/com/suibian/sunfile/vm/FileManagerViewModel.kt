package com.suibian.sunfile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.data.ClipItem
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.PrefsStore
import com.suibian.sunfile.data.Shell
import com.suibian.sunfile.data.SortMode
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
    /** 目录受限时的说明，列表为空但不算错误 */
    val notice: String? = null,
    val restricted: Boolean = false,
    val back: List<String> = emptyList(),
    val forward: List<String> = emptyList(),
    val token: Int = 0,
    val navDirection: Int = 0
) {
    // 列表可能上千项，计数只在真正用到时算一次
    val dirCount: Int by lazy { entries.count { it.isDir } }
    val fileCount: Int by lazy { entries.size - dirCount }
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

enum class SearchMode(val label: String) {
    NAME("名称"),
    CONTENT("内容")
}

data class SearchUiState(
    val root: String = "",
    val query: String = "",
    val running: Boolean = false,
    val scanned: Int = 0,
    val current: String = "",
    val results: List<SearchResult> = emptyList(),
    val contentHits: List<FileRepo.ContentHit> = emptyList(),
    val mode: SearchMode = SearchMode.NAME,
    val finished: Boolean = false
)

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {

    val prefsStore = PrefsStore.get(app)

    private val _panes = MutableStateFlow(listOf(PaneState(), PaneState()))
    val panes: StateFlow<List<PaneState>> = _panes

    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active

    private val _storage = MutableStateFlow<com.suibian.sunfile.data.StorageInfo?>(null)
    val storage: StateFlow<com.suibian.sunfile.data.StorageInfo?> = _storage

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
        // 首栏先出，第二栏稍后一点，避免启动瞬间两倍 IO 抢同一个目录；
        // 存储统计单独在后台跑，首帧不等它
        load(0, "/storage/emulated/0", record = false, pushHistory = false)
        viewModelScope.launch {
            kotlinx.coroutines.delay(140)
            load(1, "/storage/emulated/0", record = false, pushHistory = false)
        }
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) { StorageStats.of("/storage/emulated/0") }
        }
    }

    val activePaneState: PaneState get() = _panes.value[_active.value]

    fun pane(index: Int): PaneState = _panes.value[index]

    fun setActive(index: Int) {
        if (index in 0..1 && index != _active.value) {
            _active.value = index
            // 存储统计要遍历分区，放到后台算，避免切栏时卡一下
            val path = _panes.value[index].path
            if (path.isNotEmpty()) {
                viewModelScope.launch {
                    val info = withContext(Dispatchers.IO) { StorageStats.of(path) }
                    if (_active.value == index) _storage.value = info
                }
            }
        }
    }

    private fun mutate(index: Int, block: (PaneState) -> PaneState) {
        _panes.value = _panes.value.mapIndexed { i, p -> if (i == index) block(p) else p }
    }

    // ---------------- 导航 ----------------

    fun navigate(path: String, pushHistory: Boolean = true) {
        load(_active.value, path, record = true, pushHistory = pushHistory)
    }

    fun open(index: Int, path: String) = load(index, path, record = true, pushHistory = true)

    fun openInActive(index: Int, path: String) {
        setActive(index)
        load(index, path, record = true, pushHistory = true)
    }

    private fun load(
        index: Int,
        path: String,
        record: Boolean,
        pushHistory: Boolean,
        selectAfter: String? = null,
        direction: Int = 0
    ) {
        val dir = File(path)
        mutate(index) {
            it.copy(loading = true, error = null, notice = null, selected = emptySet(), firstLoad = it.entries.isEmpty())
        }
        viewModelScope.launch {
            val cfg = prefsStore.prefs.value
            val result = withContext(Dispatchers.IO) {
                runCatching { FileRepo.listDetailed(dir, cfg.showHidden, cfg.sort) }
            }
            result.onSuccess { listing ->
                if (record) {
                    prefsStore.saveLastPath(index, dir.absolutePath)
                    prefsStore.pushRecent(dir.absolutePath)
                }
                if (dir.absolutePath == _panes.value[_active.value].path || index == _active.value) {
                    _storage.value = withContext(Dispatchers.IO) { StorageStats.of(dir.absolutePath) }
                }
                mutate(index) {
                    it.copy(
                        path = dir.absolutePath,
                        entries = listing.entries,
                        loading = false,
                        firstLoad = false,
                        error = null,
                        notice = listing.message,
                        restricted = listing.restricted,
                        token = it.token + 1,
                        navDirection = direction,
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
                        restricted = true,
                        path = if (it.path.isEmpty()) dir.absolutePath else it.path
                    )
                }
                _messages.tryEmit(msg)
            }
        }
    }

    fun goBack(index: Int = _active.value) {
        val p = _panes.value[index]
        val prev = p.back.lastOrNull() ?: return
        mutate(index) { it.copy(back = it.back.dropLast(1), forward = (listOf(it.path) + it.forward).take(50)) }
        load(index, prev, record = true, pushHistory = false, direction = -1)
    }

    fun goForward(index: Int = _active.value) {
        val p = _panes.value[index]
        val next = p.forward.firstOrNull() ?: return
        mutate(index) { it.copy(forward = it.forward.drop(1), back = (it.back + it.path).takeLast(50)) }
        load(index, next, record = true, pushHistory = false, direction = 1)
    }

    fun goUp(index: Int = _active.value) {
        val p = _panes.value[index]
        val parent = File(p.path).parentFile ?: return
        load(index, parent.absolutePath, record = true, pushHistory = true, direction = -1)
    }

    fun refreshPane(index: Int) {
        val p = _panes.value[index]
        if (p.path.isNotEmpty()) load(index, p.path, record = false, pushHistory = false)
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
        val path = _panes.value[_active.value].path
        if (path.isNotEmpty()) {
            viewModelScope.launch {
                _storage.value = withContext(Dispatchers.IO) { StorageStats.of(path) }
            }
        }
    }

    /**
     * 交换两个窗口：交换路径与历史，然后各自重新枚举。
     * 之前的实现只换了对象，两栏本来就在同一目录时看不出任何变化，
     * 现在会强制刷新，列表、计数、滚动位置都跟着换过去。
     */
    fun swapPanes() {
        val list = _panes.value
        if (list.size < 2) return
        val a = list[0]
        val b = list[1]
        _panes.value = listOf(
            b.copy(selected = emptySet()),
            a.copy(selected = emptySet())
        )
        for (i in 0..1) {
            val p = _panes.value[i]
            if (p.path.isNotEmpty()) load(i, p.path, record = false, pushHistory = false)
        }
        val path = _panes.value[_active.value].path
        if (path.isNotEmpty()) {
            viewModelScope.launch {
                _storage.value = withContext(Dispatchers.IO) { StorageStats.of(path) }
            }
        }
        _messages.tryEmit("已交换两栏")
    }

    /** 把当前栏的目录同步给另一栏，让两边显示同一位置 */
    fun syncPanes() {
        val src = _panes.value[_active.value]
        val dst = 1 - _active.value
        if (src.path.isEmpty()) return
        if (_panes.value[dst].path == src.path) {
            _messages.tryEmit("两栏已在同一目录")
            return
        }
        load(dst, src.path, record = true, pushHistory = true)
        _messages.tryEmit("已同步到另一栏")
    }

    // ---------------- 选择 ----------------

    fun toggleSelect(entry: FileEntry, index: Int = _active.value) {
        mutate(index) { p ->
            val set = p.selected.toMutableSet()
            if (!set.add(entry.path)) set.remove(entry.path)
            p.copy(selected = set)
        }
    }

    fun selectOnly(entry: FileEntry, index: Int = _active.value) {
        mutate(index) { it.copy(selected = setOf(entry.path)) }
    }

    fun selectAll(index: Int = _active.value) {
        mutate(index) { it.copy(selected = it.entries.map { e -> e.path }.toSet()) }
    }

    fun invertSelection(index: Int = _active.value) {
        mutate(index) { p ->
            val all = p.entries.map { it.path }.toSet()
            p.copy(selected = all - p.selected)
        }
    }

    fun clearSelection(index: Int = _active.value) {
        mutate(index) { it.copy(selected = emptySet()) }
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
        _messages.tryEmit("已${if (cut) "剪切" else "复制"} ${p.selected.size} 项，切换窗口后粘贴")
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

    /**
     * 直接复制 / 移动到另一栏（MT 的双窗口操作）：
     * 不用先剪切再粘贴，长按菜单一次完成。
     */
    fun copyToOtherPane(paths: List<String> = activePaneState.selected.toList()) = transfer(paths, false)

    fun moveToOtherPane(paths: List<String> = activePaneState.selected.toList()) = transfer(paths, true)

    private fun transfer(paths: List<String>, cut: Boolean) {
        if (paths.isEmpty() || _busy.value != null) return
        val destIndex = 1 - _active.value
        val destDir = _panes.value[destIndex].currentDir
        if (!destDir.isDirectory) {
            _messages.tryEmit("另一栏不是有效目录")
            return
        }
        viewModelScope.launch {
            _busy.value = OpState(if (cut) "正在移动到另一栏…" else "正在复制到另一栏…")
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for (path in paths) {
                    val src = File(path)
                    if (!src.exists()) continue
                    if (src.isDirectory && FileRepo.isInside(src, destDir)) {
                        err = "不能把文件夹放到自身内部"
                        break
                    }
                    if (src.parentFile?.absolutePath == destDir.absolutePath) {
                        err = "源目录与目标目录相同"
                        break
                    }
                    try {
                        if (cut) FileRepo.move(src, destDir) { n, d, t -> report(n, d, t) }
                        else FileRepo.copy(src, destDir) { n, d, t -> report(n, d, t) }
                    } catch (e: Exception) {
                        err = e.message ?: "操作失败"
                        break
                    }
                }
                err
            }
            _busy.value = null
            mutate(_active.value) { it.copy(selected = emptySet()) }
            refreshAll()
            val destName = destDir.name.ifEmpty { "/" }
            _messages.tryEmit(fail ?: if (cut) "已移动到 $destName" else "已复制到 $destName")
        }
    }

    /** 解压到当前栏目录 */
    fun extractHere(file: File) {
        extractArchive(file, activePaneState.currentDir)
    }

    private fun report(name: String, done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressTs < 80 && done < total) return
        lastProgressTs = now
        _busy.value = _busy.value?.copy(currentFile = name, done = done, total = total)
    }

    fun deleteSelected() {
        deletePaths(activePaneState.selected.toList())
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

    /** 批量重命名：对名称做查找替换 */
    fun renameBatch(paths: List<String>, find: String, replace: String, onDone: (Int) -> Unit = {}) {
        if (paths.isEmpty() || find.isEmpty() || _busy.value != null) return
        viewModelScope.launch {
            _busy.value = OpState("正在重命名…", "", 0, paths.size.toLong())
            val result = withContext(Dispatchers.IO) {
                var ok = 0
                var err: String? = null
                for ((i, p) in paths.withIndex()) {
                    val f = File(p)
                    val newName = f.name.replace(find, replace)
                    if (newName != f.name && newName.isNotBlank() && !newName.contains("/")) {
                        runCatching { FileRepo.rename(f, newName) }
                            .onSuccess { ok++ }
                            .onFailure { err = it.message }
                    }
                    _busy.value = OpState("正在重命名…", f.name, i + 1L, paths.size.toLong())
                }
                ok to err
            }
            _busy.value = null
            mutate(_active.value) { it.copy(selected = emptySet()) }
            refreshAll()
            onDone(result.first)
            _messages.tryEmit(result.second ?: "已重命名 ${result.first} 项")
        }
    }

    fun create(createDir: Boolean, name: String, onDone: (File?) -> Unit = {}) {
        val parent = activePaneState.currentDir
        viewModelScope.launch {
            var created: File? = null
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    created = if (createDir) FileRepo.createDir(parent, name) else FileRepo.createFile(parent, name)
                }.exceptionOrNull()?.message
            }
            refresh()
            onDone(created)
            _messages.tryEmit(err ?: if (createDir) "文件夹已创建" else "文件已创建")
        }
    }

    // ---------------- 压缩包 ----------------

    fun extractArchive(archive: File, destDir: File, onDone: () -> Unit = {}) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = OpState("正在解压…", "", 0, 0)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    FileRepo.extractArchive(archive, destDir) { name, done, total ->
                        _busy.value = OpState("正在解压…", name, done.toLong(), total.toLong())
                    }
                }
            }
            _busy.value = null
            refreshAll()
            onDone()
            _messages.tryEmit(result.fold({ "已解压 ${it} 个文件" }, { it.message ?: "解压失败" }))
        }
    }

    fun compress(paths: List<String>, zipName: String, onDone: () -> Unit = {}) {
        if (paths.isEmpty() || _busy.value != null) return
        val destDir = activePaneState.currentDir
        val name = if (zipName.endsWith(".zip", true)) zipName else "$zipName.zip"
        viewModelScope.launch {
            _busy.value = OpState("正在压缩…", "", 0, 0)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = FileRepo.uniqueTarget(destDir, name)
                    FileRepo.createZip(paths.map { File(it) }, dest) { n, done, total ->
                        _busy.value = OpState("正在压缩…", n, done.toLong(), total.toLong())
                    }
                }
            }
            _busy.value = null
            mutate(_active.value) { it.copy(selected = emptySet()) }
            refreshAll()
            onDone()
            _messages.tryEmit(result.fold({ "已压缩 $it 个文件" }, { it.message ?: "压缩失败" }))
        }
    }

    suspend fun checksumOf(file: File, algorithm: String): String = withContext(Dispatchers.IO) {
        runCatching { FileRepo.checksum(file, algorithm) }.getOrElse { "计算失败：${it.message}" }
    }

    // ---------------- 偏好 ----------------

    fun updatePrefs(block: (AppPrefs) -> AppPrefs) {
        prefsStore.update(block)
        refreshAll()
    }

    fun toggleHidden() = updatePrefs { it.copy(showHidden = !it.showHidden) }

    fun setSort(mode: SortMode) = updatePrefs { it.copy(sort = it.sort.nextFor(mode)) }

    fun toggleAccelerator() = prefsStore.update { it.copy(useAccelerator = !it.useAccelerator) }

    fun toggleAutoCheck() = prefsStore.update { it.copy(autoCheckUpdate = !it.autoCheckUpdate) }

    fun setEditorPrefs(
        fontSize: Int? = null,
        wrap: Boolean? = null,
        lineNumbers: Boolean? = null,
        completion: Boolean? = null
    ) {
        prefsStore.update {
            it.copy(
                editorFontSize = fontSize ?: it.editorFontSize,
                editorWrap = wrap ?: it.editorWrap,
                editorLineNumbers = lineNumbers ?: it.editorLineNumbers,
                editorCompletion = completion ?: it.editorCompletion
            )
        }
    }

    /** 修改权限位（需要 root 才能作用于系统目录） */
    fun chmod(target: String, mode: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                Shell.chmod(target, mode) || runCatching {
                    val f = File(target)
                    var applied = true
                    val perms = mode.padStart(3, '0').takeLast(3)
                    val owner = perms[0].digitToInt()
                    applied = applied && f.setReadable(owner and 4 != 0, true)
                    applied = applied && f.setWritable(owner and 2 != 0, true)
                    applied = applied && f.setExecutable(owner and 1 != 0, true)
                    applied
                }.getOrDefault(false)
            }
            _messages.tryEmit(if (ok) "权限已修改为 $mode" else "修改权限失败")
            if (ok) refreshAll()
            onDone(ok)
        }
    }

    fun toggleBookmark(path: String): Boolean {
        val added = prefsStore.toggleBookmark(path)
        _messages.tryEmit(if (added) "已加入收藏" else "已取消收藏")
        return added
    }

    fun requestUpdateCheck() {
        _updateTick.value += 1
    }

    // ---------------- 搜索 ----------------

    fun openSearch() {
        _search.value = _search.value.copy(
            root = activePaneState.path,
            results = emptyList(),
            contentHits = emptyList(),
            scanned = 0,
            finished = false
        )
    }

    fun updateQuery(q: String) {
        _search.value = _search.value.copy(query = q)
    }

    fun setSearchMode(mode: SearchMode) {
        if (_search.value.mode == mode) return
        _search.value = _search.value.copy(
            mode = mode,
            results = emptyList(),
            contentHits = emptyList(),
            finished = false,
            scanned = 0
        )
    }

    fun startSearch() {
        val s = _search.value
        if (s.query.isBlank() || s.running) return
        searchJob?.cancel()
        _search.value = s.copy(
            running = true, results = emptyList(), contentHits = emptyList(), scanned = 0, finished = false
        )
        searchJob = viewModelScope.launch {
            val rootDir = File(s.root)
            if (s.mode == SearchMode.CONTENT) {
                val hits = withContext(Dispatchers.IO) {
                    FileRepo.searchContent(
                        root = rootDir,
                        query = s.query,
                        onProgress = { scanned, current ->
                            _search.value = _search.value.copy(scanned = scanned, current = current)
                        },
                        cancelled = { !isActive }
                    )
                }
                _search.value = _search.value.copy(running = false, finished = true, contentHits = hits)
            } else {
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
        map["类型"] = when {
            isDir -> "文件夹"
            else -> {
                val ext = file.extension.lowercase()
                if (ext.isEmpty()) "无扩展名文件" else "${ext.uppercase()} 文件"
            }
        }
        map["大小"] = com.suibian.sunfile.util.Fmt.size(size) +
            if (isDir) "（递归统计）" else "（${size} 字节）"
        if (isDir) {
            val (d, f) = FileRepo.countChildren(file)
            map["包含"] = "$d 个文件夹，$f 个文件"
        }
        map["修改时间"] = com.suibian.sunfile.util.Fmt.time(file.lastModified())
        map["权限"] = com.suibian.sunfile.util.Fmt.permissions(file)
        map["可读"] = if (file.canRead()) "是" else "否"
        map["可写"] = if (file.canWrite()) "是" else "否"
        if (!isDir && com.suibian.sunfile.data.FileKinds.isZipReadable(file)) {
            runCatching { FileRepo.zipEntries(file).size }.getOrNull()?.let { map["压缩包条目"] = "$it 项" }
        }
        map
    }
}
