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

data class EditorState(
    val file: File? = null,
    val text: String = "",
    val savedText: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val revisionCount: Int = 0,
    val encoding: String = "UTF-8",
    val lineCount: Int = 0,
    val readOnly: Boolean = false,
    val truncated: Boolean = false,
    val totalBytes: Long = 0,
    val lang: Lang = Lang.PLAIN,
    /** 外部替换文本（打开、撤销、恢复历史）时自增，用于让输入框重新同步 */
    val syncToken: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val dirty: Boolean get() = file != null && text != savedText
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryStore(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val toast: SharedFlow<String> = _toast

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSnapshotAt = 0L

    fun open(file: File) {
        undoStack.clear()
        redoStack.clear()
        _state.value = EditorState(file = file, loading = true, lang = Syntax.langFor(file.name))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FileRepo.readTextSmart(file) }
            }
            result.onSuccess { content ->
                _state.value = EditorState(
                    file = file,
                    text = content.text,
                    savedText = content.text,
                    loading = false,
                    revisionCount = history.revisions(file).size,
                    lineCount = content.text.count { it == '\n' } + 1,
                    readOnly = !content.editable,
                    truncated = content.truncated,
                    totalBytes = content.totalBytes,
                    encoding = content.encoding,
                    lang = Syntax.langFor(file.name),
                    syncToken = 1
                )
            }.onFailure { e ->
                _state.value = EditorState(file = file, loading = false, error = e.message ?: "读取失败")
            }
        }
    }

    fun updateText(text: String) {
        val s = _state.value
        if (s.readOnly) return
        val now = System.currentTimeMillis()
        if (now - lastSnapshotAt > 450 && s.text != text && s.text.isNotEmpty()) {
            lastSnapshotAt = now
            undoStack.addLast(s.text)
            while (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
        _state.value = s.copy(
            text = text,
            lineCount = text.count { it == '\n' } + 1,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun undo() {
        val s = _state.value
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(s.text)
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
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(s.text)
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
        undoStack.addLast(s.text)
        redoStack.clear()
        _state.value = s.copy(
            text = text,
            lineCount = text.count { it == '\n' } + 1,
            syncToken = s.syncToken + 1,
            canUndo = true,
            canRedo = false
        )
    }

    /** 保存：先把磁盘上的旧内容存为历史版本，再写入新内容 */
    fun save(onSaved: (Boolean) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
        if (s.readOnly) {
            _toast.tryEmit("大文件只读模式，请使用「另存为」")
            onSaved(false)
            return
        }
        if (!s.dirty) {
            _toast.tryEmit("内容没有变化")
            onSaved(false)
            return
        }
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val previous = runCatching {
                        if (file.exists() && file.length() <= FileRepo.TEXT_EDIT_LIMIT) {
                            runCatching { FileRepo.readText(file) }.getOrNull()
                        } else null
                    }.getOrNull()
                    if (previous != null && previous != s.text) {
                        history.snapshot(file, previous)
                    }
                    FileRepo.writeText(file, s.text, s.encoding)
                }
            }
            result.onSuccess {
                undoStack.clear()
                redoStack.clear()
                _state.value = _state.value.copy(
                    savedText = s.text,
                    saving = false,
                    revisionCount = history.revisions(file).size,
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

    fun saveAs(name: String, onDone: (Boolean) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
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
                    FileRepo.writeText(target, s.text, s.encoding)
                    target
                }
            }
            result.onSuccess { target ->
                _state.value = _state.value.copy(
                    file = target,
                    savedText = s.text,
                    saving = false,
                    lang = Syntax.langFor(target.name),
                    readOnly = false,
                    truncated = false
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

    /** 文件被外部修改后重新载入 */
    fun reload() {
        val f = _state.value.file ?: return
        open(f)
    }

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        _state.value = EditorState()
    }
}
