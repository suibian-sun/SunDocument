package com.suibian.sunfile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.HistoryStore
import com.suibian.sunfile.data.Revision
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
    val lineCount: Int = 0
) {
    val dirty: Boolean get() = file != null && text != savedText
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryStore(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val toast: SharedFlow<String> = _toast

    fun open(file: File) {
        _state.value = EditorState(file = file, loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FileRepo.readText(file) }
            }
            result.onSuccess { text ->
                _state.value = EditorState(
                    file = file,
                    text = text,
                    savedText = text,
                    loading = false,
                    revisionCount = history.revisions(file).size,
                    lineCount = text.count { it == '\n' } + 1
                )
            }.onFailure { e ->
                _state.value = EditorState(file = file, loading = false, error = e.message ?: "读取失败")
            }
        }
    }

    fun updateText(text: String) {
        _state.value = _state.value.copy(
            text = text,
            lineCount = text.count { it == '\n' } + 1
        )
    }

    /** 保存：先把磁盘上的旧内容存为历史版本，再写入新内容 */
    fun save(onSaved: (Boolean) -> Unit = {}) {
        val s = _state.value
        val file = s.file ?: return
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
                        if (file.exists() && file.length() <= FileRepo.MAX_TEXT_BYTES) file.readText() else null
                    }.getOrNull()
                    if (previous != null && previous != s.text) {
                        history.snapshot(file, previous)
                    }
                    FileRepo.writeText(file, s.text)
                }
            }
            result.onSuccess {
                _state.value = _state.value.copy(
                    savedText = s.text,
                    saving = false,
                    revisionCount = history.revisions(file).size
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

    fun revisions(): List<Revision> {
        val file = _state.value.file ?: return emptyList()
        return history.revisions(file)
    }

    fun revisionContent(rev: Revision): String = history.read(rev)

    /** 载入历史版本到编辑器（保存后才会真正覆盖文件） */
    fun restoreToEditor(rev: Revision): String {
        val content = history.read(rev)
        _state.value = _state.value.copy(
            text = content,
            lineCount = content.count { it == '\n' } + 1
        )
        _toast.tryEmit("已载入历史版本，保存后生效")
        return content
    }

    fun clearHistory() {
        val f = _state.value.file ?: return
        history.clear(f)
        _state.value = _state.value.copy(revisionCount = 0)
        _toast.tryEmit("已清除该文件的历史版本")
    }

    fun reset() {
        _state.value = EditorState()
    }
}
