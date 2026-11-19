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
import com.suibian.sunfile.data.SortSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PaneState(
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val scrollToken: Int = 0
) {
    val currentDir: File get() = File(path)
    val selectionMode: Boolean get() = selected.isNotEmpty()
}

data class OpState(
    val title: String,
    val currentFile: String = "",
    val done: Long = 0,
    val total: Long = 0
) {
    val progress: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {

    val prefsStore = PrefsStore(app)

    private val _panes = MutableStateFlow(listOf(PaneState(), PaneState()))
    val panes: StateFlow<List<PaneState>> = _panes

    private val _clipboard = MutableStateFlow(ClipItem(emptyList(), false))
    val clipboard: StateFlow<ClipItem> = _clipboard

    private val _busy = MutableStateFlow<OpState?>(null)
    val busy: StateFlow<OpState?> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _updateTick = MutableStateFlow(0)
    val updateTick: StateFlow<Int> = _updateTick

    private var lastProgressTs = 0L
    private var initialized = false

    /** 权限状态确定后初始化两个面板 */
    val isInitialized: Boolean get() = initialized

    fun init(hasAllFilesAccess: Boolean) {
        if (initialized) return
        initialized = true
        val start = if (hasAllFilesAccess) {
            FileRepo.startDir(true).absolutePath
        } else {
            getApplication<Application>().getExternalFilesDir(null)?.absolutePath
                ?: getApplication<Application>().filesDir.absolutePath
        }
        navigate(0, start)
        navigate(1, start)
    }

    fun pane(index: Int): PaneState = _panes.value[index]

    private fun mutatePane(index: Int, block: (PaneState) -> PaneState) {
        _panes.value = _panes.value.mapIndexed { i, p -> if (i == index) block(p) else p }
    }

    fun navigate(index: Int, path: String) {
        val dir = File(path)
        mutatePane(index) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val cfg = prefsStore.prefs.value
            val result = withContext(Dispatchers.IO) {
                runCatching { FileRepo.list(dir, cfg.showHidden, cfg.sort) }
            }
            result.onSuccess { list ->
                mutatePane(index) {
                    it.copy(
                        path = dir.absolutePath,
                        entries = list,
                        loading = false,
                        selected = emptySet(),
                        error = null,
                        scrollToken = it.scrollToken + 1
                    )
                }
            }.onFailure { e ->
                mutatePane(index) { it.copy(loading = false, error = e.message ?: "无法打开目录") }
                _messages.tryEmit(e.message ?: "无法打开目录")
            }
        }
    }

    fun refresh(index: Int) {
        val p = _panes.value[index]
        if (p.path.isNotEmpty()) navigate(index, p.path)
    }

    fun refreshAll() {
        refresh(0)
        refresh(1)
    }

    fun navigateUp(index: Int) {
        val p = _panes.value[index]
        val parent = File(p.path).parentFile ?: return
        navigate(index, parent.absolutePath)
    }

    fun enter(index: Int, entry: FileEntry, onOpenFile: (FileEntry) -> Unit) {
        if (entry.isDir) navigate(index, entry.path) else onOpenFile(entry)
    }

    fun toggleSelect(index: Int, entry: FileEntry) {
        mutatePane(index) { p ->
            val set = p.selected.toMutableSet()
            if (!set.add(entry.path)) set.remove(entry.path)
            p.copy(selected = set)
        }
    }

    fun selectAll(index: Int) {
        mutatePane(index) { p -> p.copy(selected = p.entries.map { it.path }.toSet()) }
    }

    fun clearSelection(index: Int) {
        mutatePane(index) { it.copy(selected = emptySet()) }
    }

    fun setClipboard(index: Int, cut: Boolean) {
        val selected = _panes.value[index].selected.toList()
        if (selected.isEmpty()) {
            _messages.tryEmit("请先选择文件")
            return
        }
        _clipboard.value = ClipItem(selected, cut)
        _panes.value = _panes.value.map { it.copy(selected = emptySet()) }
        _messages.tryEmit("已${if (cut) "剪切" else "复制"} ${selected.size} 项，选择目标面板后粘贴")
    }

    fun clearClipboard() {
        _clipboard.value = ClipItem(emptyList(), false)
    }

    /** 把一栏选中的内容直接移动/复制到另一栏 */
    fun transfer(srcIndex: Int, destIndex: Int, cut: Boolean) {
        val paths = _panes.value[srcIndex].selected.toList()
        if (paths.isEmpty() || _busy.value != null) return
        val destDir = File(_panes.value[destIndex].path)
        if (!destDir.isDirectory) {
            _messages.tryEmit("目标不是有效目录")
            return
        }
        viewModelScope.launch {
            _busy.value = OpState(if (cut) "正在移动到对栏…" else "正在复制到对栏…")
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for (p in paths) {
                    val src = File(p)
                    if (!src.exists()) continue
                    if (src.isDirectory && FileRepo.isInside(src, destDir)) {
                        err = "不能把文件夹复制到自身内部：${src.name}"
                        break
                    }
                    try {
                        if (cut) FileRepo.move(src, destDir) { n, d, t -> reportProgress(n, d, t) }
                        else FileRepo.copy(src, destDir) { n, d, t -> reportProgress(n, d, t) }
                    } catch (e: Exception) {
                        err = e.message ?: "操作失败"
                        break
                    }
                }
                err
            }
            _busy.value = null
            mutatePane(srcIndex) { it.copy(selected = emptySet()) }
            refreshAll()
            _messages.tryEmit(fail ?: if (cut) "已移动到对栏" else "已复制到对栏")
        }
    }

    fun paste(destIndex: Int) {
        val clip = _clipboard.value
        if (clip.paths.isEmpty() || _busy.value != null) return
        val destDir = File(_panes.value[destIndex].path)
        if (!destDir.isDirectory) {
            _messages.tryEmit("目标不是有效目录")
            return
        }
        viewModelScope.launch {
            _busy.value = OpState(if (clip.cut) "正在移动…" else "正在复制…")
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for (p in clip.paths) {
                    val src = File(p)
                    if (!src.exists()) continue
                    if (src.isDirectory && FileRepo.isInside(src, destDir)) {
                        err = "不能把文件夹复制到自身内部：${src.name}"
                        break
                    }
                    try {
                        if (clip.cut) {
                            FileRepo.move(src, destDir) { name, done, total -> reportProgress(name, done, total) }
                        } else {
                            FileRepo.copy(src, destDir) { name, done, total -> reportProgress(name, done, total) }
                        }
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

    private fun reportProgress(name: String, done: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressTs < 80 && done < total) return
        lastProgressTs = now
        _busy.value = _busy.value?.copy(currentFile = name, done = done, total = total)
    }

    fun deleteSelected(index: Int) {
        val paths = _panes.value[index].selected.toList()
        if (paths.isEmpty() || _busy.value != null) return
        viewModelScope.launch {
            _busy.value = OpState("正在删除…", "", 0, paths.size.toLong())
            val fail = withContext(Dispatchers.IO) {
                var err: String? = null
                for ((i, p) in paths.withIndex()) {
                    try {
                        FileRepo.delete(File(p))
                    } catch (e: Exception) {
                        err = e.message
                        break
                    }
                    _busy.value = OpState("正在删除…", File(p).name, i + 1L, paths.size.toLong())
                }
                err
            }
            _busy.value = null
            refreshAll()
            _messages.tryEmit(fail ?: "已删除 ${paths.size} 项")
        }
    }

    fun rename(index: Int, path: String, newName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching { FileRepo.rename(File(path), newName) }.exceptionOrNull()?.message
            }
            refreshAll()
            onDone()
            _messages.tryEmit(err ?: "重命名成功")
        }
    }

    fun createFolder(index: Int, name: String, onDone: () -> Unit = {}) {
        createEntry(index, name, true, onDone)
    }

    fun createFile(index: Int, name: String, onDone: () -> Unit = {}) {
        createEntry(index, name, false, onDone)
    }

    private fun createEntry(index: Int, name: String, dir: Boolean, onDone: () -> Unit) {
        val parent = _panes.value[index].currentDir
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (dir) FileRepo.createDir(parent, name) else FileRepo.createFile(parent, name)
                }.exceptionOrNull()?.let { it.message ?: "创建失败" }
            }
            refresh(index)
            onDone()
            _messages.tryEmit(err ?: if (dir) "文件夹已创建" else "文件已创建")
        }
    }

    fun updatePrefs(block: (AppPrefs) -> AppPrefs) {
        prefsStore.update(block)
        refreshAll()
    }

    fun toggleHidden() = updatePrefs { it.copy(showHidden = !it.showHidden) }

    fun setSort(mode: SortMode) = updatePrefs {
        it.copy(sort = it.sort.nextFor(mode))
    }

    fun toggleAccelerator() = prefsStore.update { it.copy(useAccelerator = !it.useAccelerator) }

    fun toggleAutoCheck() = prefsStore.update { it.copy(autoCheckUpdate = !it.autoCheckUpdate) }

    fun requestUpdateCheck() {
        _updateTick.value = _updateTick.value + 1
    }

    /** 属性对话框用的统计信息 */
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
                if (ext.isEmpty()) "文件" else "${ext.uppercase()} 文件"
            }
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
        map["隐藏"] = if (file.isHidden) "是" else "否"
        map
    }
}
