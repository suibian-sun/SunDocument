package com.suibian.sunfile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.HistoryStore
import com.suibian.sunfile.data.Revision
import com.suibian.sunfile.ui.editor.Lang
import com.suibian.sunfile.ui.editor.Syntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * 编辑器状态：文本按「段」逐步载入，
 * [loadedBytes] 是已经进入编辑缓冲区的字节数，超过它的部分仍留在磁盘上。
 */
data class EditorState(
    val file: File? = null,
    val text: String = "",
    /** 上一次保存时的内容，只用于判断是否有改动 */
    val baseline: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val revisionCount: Int = 0,
    val encoding: String = "UTF-8",
    val lineCount: Int = 0,
    val readOnly: Boolean = false,
    val totalBytes: Long = 0,
    val lang: Lang = Lang.PLAIN,
    /** 外部替换文本（打开、撤销、恢复历史）时自增，让输入框重新同步 */
    val syncToken: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** 已经载入的字节数（从文件开头算起） */
    val loadedBytes: Long = 0,
    /** 文件还有未载入的尾段 */
    val partial: Boolean = false,
    val loadingMore: Boolean = false,
    val bom: Boolean = false,
    /** 写出文件时使用的换行符：LF / CRLF */
    val newline: String = "LF"
) {
    val dirty: Boolean get() = file != null && text != baseline
    val hasMore: Boolean get() = partial && loadedBytes < totalBytes
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryStore(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val toast: SharedFlow<String> = _toast

    private val undoStack = ArrayDeque<Pair<String, Int>>()
    private val redoStack = ArrayDeque<Pair<String, Int>>()
    private var openToken = 0
    private var lastSnapshotAt = 0L
    private var lastSnapshotLen = 0
    private var undoChars = 0L

    private val maxUndoChars = 8L * 1024 * 1024

    fun open(file: File, encoding: String? = null) {
        undoStack.clear()
        redoStack.clear()
        undoChars = 0
        lastSnapshotLen = 0
        _state.value = EditorState(
            file = file,
            loading = true,
            encoding = encoding ?: "UTF-8",
            lang = Syntax.langFor(file.name)
        )
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { FileRepo.readTextSmart(file, encoding) }
            }
            content.onSuccess { c ->
                _state.value = EditorState(
                    file = file,
                    text = c.text,
                    baseline = c.text,
                    loading = false,
                    revisionCount = history.revisions(file).size,
                    lineCount = c.text.count { it == '\n' } + 1,
                    totalBytes = c.totalBytes,
                    encoding = c.encoding,
                    lang = Syntax.langFor(file.name),
                    syncToken = ++openToken,
                    loadedBytes = c.loadedBytes,
                    partial = c.partial,
                    bom = c.bom,
                    readOnly = false
                )
                lastSnapshotLen = c.text.length
            }.onFailure { e ->
                _state.value = EditorState(file = file, loading = false, error = e.message ?: "读取失败")
            }
        }
    }

    /**
     * 向下滚动到末尾时继续读取下一段。文本由界面持有，
     * 这里同步追加到状态里（用于保存），并通过回调把新片段交给输入框。
     */
    fun loadMore(onAppend: (String) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
        if (!s.hasMore || s.loadingMore || s.loading) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val w = withContext(Dispatchers.IO) {
                runCatching { FileRepo.readWindow(file, s.loadedBytes) }.getOrNull()
            }
            if (w == null || w.text.isEmpty()) {
                _state.value = _state.value.copy(loadingMore = false, partial = false)
                return@launch
            }
            val cur = _state.value
            val nextText = cur.text + w.text
            _state.value = cur.copy(
                text = nextText,
                baseline = cur.baseline + w.text,
                loadedBytes = w.to,
                partial = w.partial,
                loadingMore = false,
                lineCount = nextText.count { it == '\n' } + 1
            )
            onAppend(w.text)
        }
    }

    /** 输入时光标所在行等内容由界面自己算；这里只记录文本与撤销栈 */
    fun updateText(text: String) {
        val s = _state.value
        if (s.readOnly || s.text == text) return
        val now = System.currentTimeMillis()
        val grew = kotlin.math.abs(text.length - lastSnapshotLen)
        if (now - lastSnapshotAt > 600 && s.text.isNotEmpty() && grew > 0) {
            lastSnapshotAt = now
            lastSnapshotLen = text.length
            pushUndo(s.text)
        }
        _state.value = s.copy(
            text = text,
            // 分段载入的文件撤销会与未载入的尾段错位，这里直接关闭，避免写坏文件
            canUndo = !s.partial && undoStack.isNotEmpty(),
            canRedo = !s.partial && redoStack.isNotEmpty()
        )
    }

    private fun pushUndo(text: String) {
        undoStack.addLast(text to text.length)
        undoChars += text.length
        while (undoStack.size > 24 || (undoChars > maxUndoChars && undoStack.size > 1)) {
            val removed = undoStack.removeFirst()
            undoChars -= removed.second
        }
        redoStack.clear()
    }

    fun undo() {
        val s = _state.value
        if (s.partial) {
            _toast.tryEmit("超长文件暂不支持撤销")
            return
        }
        val (prev, len) = undoStack.removeLastOrNull() ?: return
        undoChars -= len
        redoStack.addLast(s.text to s.text.length)
        _state.value = s.copy(
            text = prev,
            lineCount = prev.count { it == '\n' } + 1,
            syncToken = s.syncToken + 1,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
    }

    fun redo() {
        val s = _state.value
        if (s.partial) return
        val (next, _) = redoStack.removeLastOrNull() ?: return
        pushUndo(s.text)
        _state.value = s.copy(
            text = next,
            lineCount = next.count { it == '\n' } + 1,
            syncToken = s.syncToken + 1,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }

    /** 用新文本替换编辑器内容（历史版本恢复、格式化等） */
    fun replaceText(text: String) {
        val s = _state.value
        pushUndo(s.text)
        _state.value = s.copy(
            text = text,
            lineCount = text.count { it == '\n' } + 1,
            syncToken = s.syncToken + 1,
            canUndo = true,
            canRedo = false
        )
    }

    /**
     * 保存：完整文件直接覆盖；只载入了一段时，把未加载的尾部原样接回去。
     * [text] 由界面传入，保证写入的是用户此刻看到的内容。
     */
    fun save(text: String? = null, onSaved: (Boolean) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
        val content = text ?: s.text
        if (content == s.baseline) {
            _toast.tryEmit("内容没有变化")
            onSaved(false)
            return
        }
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cs = runCatching { Charset.forName(s.encoding) }.getOrDefault(Charsets.UTF_8)
                    val payload = if (s.newline == "CRLF") {
                        content.replace("\r\n", "\n").replace("\n", "\r\n")
                    } else {
                        content.replace("\r\n", "\n")
                    }
                    if (s.partial) {
                        val bytes = payload.toByteArray(cs)
                        val ok = FileRepo.writeHeadAndTail(file, bytes, s.loadedBytes, s.bom)
                        if (ok) (if (s.bom) 3 else 0) + bytes.size else -1
                    } else {
                        val previous = runCatching { FileRepo.readText(file) }.getOrNull()
                        if (previous != null && previous != content) history.snapshot(file, previous)
                        FileRepo.writeText(file, payload, s.encoding)
                        0
                    }
                }
            }
            result.onSuccess { newLoaded ->
                if (newLoaded < 0) {
                    _state.value = _state.value.copy(saving = false)
                    _toast.tryEmit("保存失败：没有写入权限")
                    onSaved(false)
                    return@onSuccess
                }
                undoStack.clear()
                redoStack.clear()
                undoChars = 0
                lastSnapshotLen = content.length
                _state.value = _state.value.copy(
                    text = content,
                    baseline = content,
                    saving = false,
                    revisionCount = history.revisions(file).size,
                    totalBytes = runCatching { file.length() }.getOrDefault(s.totalBytes),
                    loadedBytes = if (s.partial) newLoaded.toLong() else runCatching { file.length() }.getOrDefault(0L),
                    canUndo = false,
                    canRedo = false
                )
                _toast.tryEmit("已保存")
                onSaved(true)
            }.onFailure { e ->
                _state.value = _state.value.copy(saving = false)
                _toast.tryEmit(e.message ?: "保存失败")
                onSaved(false)
            }
        }
    }

    fun saveAs(name: String, text: String? = null, onDone: (Boolean) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
        val content = text ?: s.text
        if (name.isBlank() || name.contains("/")) {
            _toast.tryEmit("文件名不合法")
            onDone(false)
            return
        }
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(file.parentFile, name)
                    if (target.exists() && target.absolutePath != file.absolutePath) {
                        throw java.io.IOException("同名文件已存在")
                    }
                    val cs = runCatching { Charset.forName(s.encoding) }.getOrDefault(Charsets.UTF_8)
                    if (s.partial) {
                        FileRepo.writeHeadAndTail(target, content.toByteArray(cs), s.loadedBytes, s.bom)
                        Unit
                    } else {
                        FileRepo.writeText(target, content, s.encoding)
                    }
                    target
                }
            }
            result.onSuccess { target ->
                _state.value = _state.value.copy(
                    file = target,
                    text = content,
                    baseline = content,
                    saving = false,
                    lang = Syntax.langFor(target.name),
                    readOnly = false
                )
                _toast.tryEmit("已另存为 ${target.name}")
                onDone(true)
            }.onFailure { e ->
                _state.value = _state.value.copy(saving = false)
                _toast.tryEmit(e.message ?: "另存失败")
                onDone(false)
            }
        }
    }

    fun revisions(): List<Revision> {
        val file = _state.value.file ?: return emptyList()
        return history.revisions(file)
    }

    fun revisionContent(rev: Revision): String = history.read(rev)

    fun restoreToEditor(rev: Revision): String {
        val content = history.read(rev)
        replaceText(content)
        _toast.tryEmit("已载入历史版本，保存后生效")
        return content
    }

    fun clearHistory() {
        val f = _state.value.file ?: return
        history.clear(f)
        _state.value = _state.value.copy(revisionCount = 0)
        _toast.tryEmit("已清除该文件的历史版本")
    }

    fun reload() {
        val f = _state.value.file ?: return
        open(f, _state.value.encoding.ifBlank { null })
    }

    /** 换一种编码重新读入；未保存的改动会被丢弃 */
    fun setEncoding(name: String) {
        val f = _state.value.file ?: return
        if (name == _state.value.encoding) return
        open(f, name)
        _toast.tryEmit("已按 $name 重新读取")
    }

    fun setNewline(mode: String) {
        val m = if (mode.equals("CRLF", true)) "CRLF" else "LF"
        if (m == _state.value.newline) return
        _state.value = _state.value.copy(newline = m)
        _toast.tryEmit("保存时使用 ${if (m == "CRLF") "CRLF" else "LF"}")
    }

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        undoChars = 0
        lastSnapshotLen = 0
        _state.value = EditorState()
    }
}
