package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suibian.sunfile.data.SmaliTool
import com.suibian.sunfile.update.Installer
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.vm.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Smali 编辑：把 APK 里的 classesN.dex 摊成类文件，点开就是熟悉的编辑器，
 * 改完点「编译」把整目录汇编回 dex、塞回包，再重签一份可直接安装的产物。
 *
 * 列表只显示类名与包路径，改动用右侧一个小点表示——不加说明文字，布局自己说话。
 */
@Composable
fun SmaliOverlay(
    apk: File,
    prefs: AppPrefs,
    onEditorPrefs: (Int?, Boolean?, Boolean?, Boolean?) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editorVm: EditorViewModel = viewModel()

    var session by remember(apk) { mutableStateOf<SmaliTool.Session?>(null) }
    var error by remember(apk) { mutableStateOf<String?>(null) }
    var step by remember(apk) { mutableStateOf("") }
    var partIndex by remember(apk) { mutableIntStateOf(0) }
    var query by remember(apk) { mutableStateOf("") }
    var editing by remember(apk) { mutableStateOf<File?>(null) }
    var tick by remember(apk) { mutableIntStateOf(0) }
    var saving by remember(apk) { mutableStateOf<String?>(null) }
    var produced by remember(apk) { mutableStateOf<File?>(null) }
    var failure by remember(apk) { mutableStateOf<String?>(null) }

    LaunchedEffect(apk) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = SmaliTool.cacheDir(context, apk)
                SmaliTool.open(apk, dir) { done, total, name ->
                    step = if (name.isEmpty()) "" else "反汇编 $name（${done + 1}/$total）"
                }
            }.onSuccess { session = it; step = "" }
                .onFailure { error = it.message ?: "反汇编失败" }
        }
    }

    val part = session?.parts?.getOrNull(partIndex)

    val files by produceState(emptyList<File>(), part, tick) {
        value = if (part == null) emptyList() else withContext(Dispatchers.Default) {
            SmaliTool.classesOf(part)
        }
    }
    val dirty by produceState(0, part, tick) {
        value = if (part == null) 0 else withContext(Dispatchers.Default) { SmaliTool.dirtyCount(part) }
    }
    // 编译按钮的可用性：任意一个 dex 有改动即可，放在后台算，别在组合期扫目录
    val anyDirty by produceState(false, session, tick) {
        value = session?.let { s -> withContext(Dispatchers.Default) { s.parts.any { SmaliTool.isDirty(it) } } } ?: false
    }

    val shown = remember(files, query) {
        if (query.isBlank()) files else files.filter {
            it.name.contains(query, true) || it.path.contains(query, true)
        }
    }
    val listState = rememberLazyListState()

    fun save() {
        val s = session ?: return
        if (saving != null) return
        saving = "准备编译"
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { SmaliTool.writeBack(context, s) { msg -> saving = msg } }
            }
            saving = null
            r.onSuccess { produced = it.getOrThrow(); session = null }
                .onFailure { failure = it.message ?: "编译失败" }
        }
    }

    // 编辑器盖在列表之上：编辑完直接回到原位置
    editing?.let { file ->
        EditorOverlay(
            file = file,
            origin = Offset.Zero,
            vm = editorVm,
            prefs = prefs,
            onEditorPrefs = onEditorPrefs,
            onOpenHex = {},
            onClose = {
                editing = null
                tick++
            }
        )
        return
    }

    OverlayShell(
        title = apk.name,
        subtitle = when {
            error != null -> "无法反汇编"
            session == null -> step.ifEmpty { "正在读取 dex…" }
            saving != null -> saving!!
            dirty > 0 -> "${part?.display.orEmpty()} · $dirty 处改动"
            else -> "${part?.display.orEmpty()} · ${files.size} 个类"
        },
        onClose = onClose,
        actions = {
            val busy = saving != null
            val can = anyDirty && !busy
            Text(
                if (busy) "编译中" else "编译",
                color = if (can) Color.White else SunColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(if (can) SunColors.Accent else SunColors.Surface2, RoundedCornerShape(8.dp))
                    .clickable(enabled = can) { save() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    ) {
        when {
            error != null -> Center(error!!)
            session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }
            else -> Column(Modifier.fillMaxSize()) {
                if (session!!.parts.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session!!.parts.forEachIndexed { i, p ->
                            val active = i == partIndex
                            Text(
                                p.display,
                                color = if (active) Color.White else SunColors.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(
                                        if (active) SunColors.Accent else SunColors.Surface2,
                                        RoundedCornerShape(7.dp)
                                    )
                                    .clickable { partIndex = i; query = "" }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                SearchBox(query) { query = it }
                if (shown.isEmpty()) {
                    Center(if (query.isBlank()) "这个 dex 里没有类" else "没有匹配的类")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(shown, key = { it.absolutePath }) { f ->
                            ClassRow(part!!, f) { editing = f }
                        }
                    }
                }
            }
        }
    }

    produced?.let { out ->
        ProduceDialog(
            file = out,
            onInstall = { Installer.install(context, out) },
            onDismiss = { produced = null; onClose() }
        )
    }
    failure?.let { msg ->
        MessageDialog(title = "编译失败", body = msg, onDismiss = { failure = null })
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = SunColors.TextTertiary, fontSize = 12.5.sp)
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(SunColors.Surface2, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text("搜索类名", color = SunColors.TextTertiary, fontSize = 12.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 12.5.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ClassRow(part: SmaliTool.Part, file: File, onClick: () -> Unit) {
    val name = SmaliTool.className(part, file)
    val simple = name.substringAfterLast('.')
    val pkg = name.substringBeforeLast('.', "")
    val changed = file.lastModified() > File(part.dir, ".ok").lastModified()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(simple, color = SunColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pkg.isNotEmpty()) {
                Text(pkg, color = SunColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (changed) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(SunColors.Accent, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun ProduceDialog(file: File, onInstall: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface1,
        title = { Text("已生成并重签", color = SunColors.TextPrimary, fontSize = 15.sp) },
        text = {
            Column {
                Text(file.name, color = SunColors.TextPrimary, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
                Text(
                    file.parent ?: "",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Text(
                "安装",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(SunColors.Accent, RoundedCornerShape(8.dp))
                    .clickable { onInstall(); onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        },
        dismissButton = {
            Text(
                "关闭",
                color = SunColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    )
}

@Composable
internal fun MessageDialog(title: String, body: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface1,
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 15.sp) },
        text = {
            Text(
                body,
                color = SunColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.height(200.dp).fillMaxWidth()
            )
        },
        confirmButton = {
            Text(
                "知道了",
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    )
}
