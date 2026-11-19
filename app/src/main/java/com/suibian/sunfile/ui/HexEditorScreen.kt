package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.HexBuffer
import com.suibian.sunfile.data.HexText
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private sealed interface HexAction {
    data class Edit(val pos: Int) : HexAction
    data class Insert(val pos: Int) : HexAction
    data class Remove(val pos: Int, val count: Int) : HexAction
    data object Goto : HexAction
    data object Find : HexAction
    data object Inspect : HexAction
}

/**
 * 可编辑的十六进制编辑器：选中、改写、插入、删除、搜索替换、检查、复制为。
 * 超过 6 MB 的文件只读加载，保存会被禁用，避免只写回前半段把文件写坏。
 */
@Composable
fun HexEditorOverlay(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var load by remember(file) { mutableStateOf<HexBuffer.Load?>(null) }
    var dirty by remember(file) { mutableStateOf(false) }
    var cursor by remember(file) { mutableIntStateOf(0) }
    var selAnchor by remember(file) { mutableIntStateOf(0) }
    var selEnd by remember(file) { mutableIntStateOf(0) }
    var action by remember { mutableStateOf<HexAction?>(null) }
    var note by remember { mutableStateOf("") }
    var version by remember(file) { mutableIntStateOf(0) }
    var copyMenu by remember { mutableStateOf(false) }

    val buffer = load?.buffer

    LaunchedEffect(file) {
        val l = withContext(Dispatchers.IO) { HexBuffer.load(file) }
        load = l
        note = if (l.truncated) "文件 ${Fmt.size(l.totalSize)}，仅前 ${Fmt.size(l.buffer.length.toLong())} 可编辑" else ""
    }

    fun clampPos(v: Int) = v.coerceIn(0, (buffer?.length ?: 1) - 1)

    fun goTo(pos: Int) {
        val p = clampPos(pos)
        cursor = p
        selAnchor = p
        selEnd = p
        scope.launch { listState.scrollToItem(p / 16) }
    }

    OverlayShell(
        title = file.name,
        subtitle = buffer?.let {
            "${Fmt.size(file.length())} · ${it.length} 可编辑字节" + if (load?.truncated == true) " · 只读" else ""
        } ?: "读取中…",
        onClose = {
            if (dirty) {
                Toast.makeText(context, "有未保存的修改", Toast.LENGTH_SHORT).show()
            } else onClose()
        },
        actions = {
            if (dirty && load?.truncated != true) {
                TextButton(onClick = {
                    val b = buffer ?: return@TextButton
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { b.save(file) }
                        if (ok) {
                            dirty = false
                            note = "已保存"
                        } else note = "保存失败：没有写入权限"
                    }
                }) { Text("保存", color = SunColors.Accent, fontSize = 12.sp) }
            }
        }
    ) {
        val b = buffer
        if (b == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }
            return@OverlayShell
        }

        Column(Modifier.fillMaxSize()) {
            // 工具栏
            Row(
                Modifier.fillMaxWidth().height(38.dp).background(SunColors.Surface2).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HexTool("跳转") { action = HexAction.Goto }
                HexTool("搜索") { action = HexAction.Find }
                HexTool("检查") { action = HexAction.Inspect }
                Box {
                    HexTool("复制为") { copyMenu = true }
                    DropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                        val sel = b.slice(minOf(selAnchor, selEnd), kotlin.math.abs(selEnd - selAnchor) + 1)
                        listOf("HEX", "ASCII", "Base64", "MD5", "SHA-1", "CRC32").forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind, color = SunColors.TextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    copyMenu = false
                                    val text = when (kind) {
                                        "HEX" -> HexText.encode(sel, 0, sel.size)
                                        "ASCII" -> HexText.ascii(sel, 0, sel.size)
                                        "Base64" -> HexText.base64(sel)
                                        "MD5" -> HexText.checksum(sel, "MD5")
                                        "SHA-1" -> HexText.checksum(sel, "SHA-1")
                                        else -> HexText.crc32(sel)
                                    }
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText(kind, text))
                                    Toast.makeText(context, "已复制 $kind", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "0x${cursor.toString(16).uppercase(Locale.US)}",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                Modifier.fillMaxWidth().height(26.dp).background(SunColors.Surface1).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("偏移", color = SunColors.TextTertiary, fontSize = 8.5.sp, modifier = Modifier.width(62.dp))
                Text("00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F", color = SunColors.TextTertiary, fontSize = 8.5.sp)
                Spacer(Modifier.weight(1f))
                Text("ASCII", color = SunColors.TextTertiary, fontSize = 8.5.sp)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                val rows = (b.length + 15) / 16
                items(rows) { row ->
                    HexLine(
                        buffer = b,
                        row = row,
                        version = version,
                        selFrom = minOf(selAnchor, selEnd),
                        selTo = maxOf(selAnchor, selEnd),
                        onPick = { pos ->
                            cursor = pos
                            selAnchor = pos
                            selEnd = pos
                        }
                    )
                }
            }

            // 底部操作条
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(SunColors.Surface1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HexTool("改写") { action = HexAction.Edit(cursor) }
                HexTool("插入") { action = HexAction.Insert(cursor) }
                HexTool("删除") {
                    val count = kotlin.math.abs(selEnd - selAnchor) + 1
                    action = HexAction.Remove(minOf(selAnchor, selEnd), count)
                }
                HexTool("整行") {
                    val row = cursor / 16
                    selAnchor = row * 16
                    selEnd = minOf(b.length - 1, row * 16 + 15)
                }
                Text(
                    "选中 ${kotlin.math.abs(selEnd - selAnchor) + 1} 字节",
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp
                )
            }
            if (note.isNotEmpty()) {
                Text(
                    note,
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth().background(SunColors.Surface2).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }

    when (val a = action) {
        is HexAction.Edit -> HexInputDialog(
            title = "改写 0x${a.pos.toString(16).uppercase(Locale.US)}",
            label = "字节（2 位十六进制）",
            initial = HexText.encode(buffer?.slice(a.pos, 1) ?: ByteArray(0), 0, 1),
            onDismiss = { action = null },
            onConfirm = { input ->
                val bytes = HexText.decode(input)
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(context, "请输入十六进制字节", Toast.LENGTH_SHORT).show()
                } else {
                    buffer?.setBytes(a.pos, bytes)
                    dirty = true
                    version++
                    action = null
                    cursor = (a.pos + bytes.size).coerceAtMost((buffer?.length ?: 1) - 1)
                }
            }
        )

        is HexAction.Insert -> HexInputDialog(
            title = "在 0x${a.pos.toString(16).uppercase(Locale.US)} 前插入",
            label = "字节（如 00 69 73 或 006973）",
            initial = "",
            onDismiss = { action = null },
            onConfirm = { input ->
                val bytes = HexText.decode(input)
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(context, "格式不对，示例：DE AD BE EF", Toast.LENGTH_SHORT).show()
                } else if (buffer?.insert(a.pos, bytes) == true) {
                    dirty = true
                    version++
                    action = null
                    goTo(a.pos)
                }
            }
        )

        is HexAction.Remove -> AlertDialog(
            onDismissRequest = { action = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = { Text("删除 ${a.count} 字节", color = SunColors.TextPrimary, fontSize = 14.sp) },
            text = {
                Text(
                    "从 0x${a.pos.toString(16).uppercase(Locale.US)} 起删除 ${a.count} 字节，文件长度会由 ${buffer?.length ?: 0} 变为 ${(buffer?.length ?: 0) - a.count}。",
                    color = SunColors.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    buffer?.delete(a.pos, a.count)
                    dirty = true
                    version++
                    action = null
                    cursor = a.pos.coerceAtMost((buffer?.length ?: 1) - 1)
                    selAnchor = cursor
                    selEnd = cursor
                }) { Text("删除", color = SunColors.Danger, fontSize = 12.sp) }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text("取消", color = SunColors.TextSecondary, fontSize = 12.sp) } }
        )

        HexAction.Goto -> HexInputDialog(
            title = "跳转到偏移",
            label = "十进制或 0x 十六进制",
            initial = cursor.toString(16),
            onDismiss = { action = null },
            onConfirm = { input ->
                val text = input.trim()
                val v = if (text.startsWith("0x", true)) text.substring(2).toIntOrNull(16) else text.toIntOrNull()
                if (v == null) {
                    Toast.makeText(context, "偏移格式不对", Toast.LENGTH_SHORT).show()
                } else {
                    action = null
                    goTo(v)
                }
            }
        )

        HexAction.Find -> HexFindDialog(
            onDismiss = { action = null },
            onFind = { needle, from ->
                val hit = buffer?.indexOf(needle, from) ?: -1
                if (hit >= 0) {
                    goTo(hit)
                    selEnd = minOf((buffer?.length ?: 1) - 1, hit + needle.size - 1)
                    note = "找到于 0x${hit.toString(16).uppercase(Locale.US)}"
                } else note = "没有找到"
            },
            onReplaceAll = { needle, replacement ->
                val n = buffer?.replaceAll(needle, replacement) ?: 0
                if (n > 0) {
                    dirty = true
                    version++
                }
                note = "替换 $n 处"
            }
        )

        HexAction.Inspect -> {
            val start = minOf(selAnchor, selEnd)
            val bytes = buffer?.slice(start, 8) ?: ByteArray(0)
            AlertDialog(
                onDismissRequest = { action = null },
                containerColor = SunColors.Surface4,
                shape = SunShapes.Medium,
                title = { Text("检查 0x${start.toString(16).uppercase(Locale.US)}", color = SunColors.TextPrimary, fontSize = 14.sp) },
                text = {
                    Column {
                        Text(HexText.encode(bytes, 0, bytes.size), color = SunColors.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(8.dp))
                        inspectLines(bytes).forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(k, color = SunColors.TextTertiary, fontSize = 10.sp, modifier = Modifier.width(96.dp))
                                Text(v, color = SunColors.TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { action = null }) { Text("关闭", color = SunColors.Accent, fontSize = 12.sp) } }
            )
        }

        null -> Unit
    }
}

private fun inspectLines(bytes: ByteArray): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    var v = 0L
    for (i in 0 until minOf(8, bytes.size)) {
        v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        when (i) {
            0 -> out += "int8" to (bytes[0].toInt().toString())
            1 -> out += "int16" to (v.toShort().toInt().toString())
            3 -> {
                out += "int32" to (v.toInt().toString())
                out += "float" to java.lang.Float.intBitsToFloat(v.toInt()).toString()
            }
            7 -> {
                out += "int64" to v.toString()
                out += "double" to java.lang.Double.longBitsToDouble(v).toString()
            }
        }
    }
    out += "ASCII" to HexText.ascii(bytes, 0, bytes.size)
    return out
}

@Composable
private fun HexLine(
    buffer: HexBuffer,
    row: Int,
    version: Int,
    selFrom: Int,
    selTo: Int,
    onPick: (Int) -> Unit
) {
    val base = row * 16
    var layout by remember(row) { mutableStateOf<TextLayoutResult?>(null) }
    val accent = SunColors.Accent
    val primary = SunColors.TextPrimary
    val text = remember(row, version, selFrom, selTo, buffer.length, accent, primary) {
        buildAnnotatedString {
            val n = minOf(16, buffer.length - base)
            for (i in 0 until n) {
                val pos = base + i
                val v = buffer.byteAt(pos)
                val hex = "%02X".format(v)
                val selected = pos in selFrom..selTo
                if (selected) {
                    withStyle(SpanStyle(background = accent.copy(alpha = 0.32f), color = primary)) {
                        append(hex)
                    }
                } else {
                    withStyle(SpanStyle(color = if (pos == selFrom) accent else primary)) {
                        append(hex)
                    }
                }
                if (i == 7) append("  ") else if (i != 15 && i != n - 1) append(' ')
            }
        }
    }
    val ascii = remember(row, version, buffer.length) {
        HexText.ascii(buffer.slice(base, 16), 0, minOf(16, buffer.length - base))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%08X".format(base),
            color = SunColors.TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(62.dp)
        )
        Text(
            text,
            color = SunColors.TextPrimary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            onTextLayout = { layout = it },
            modifier = Modifier
                .weight(1f)
                .pointerInput(row, version) {
                    detectTapGestures { pos ->
                        val l = layout ?: return@detectTapGestures
                        val ch = l.getOffsetForPosition(pos).coerceAtLeast(0)
                        var acc = 0
                        var picked = 15
                        for (i in 0 until 16) {
                            val len = if (i == 7) 3 else 2
                            if (ch < acc + len) {
                                picked = i
                                break
                            }
                            acc += len
                        }
                        val target = base + picked
                        if (target < buffer.length) onPick(target)
                    }
                }
        )
        Text(
            ascii,
            color = SunColors.TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(120.dp)
        )
    }
}

@Composable
private fun HexTool(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(label, color = SunColors.TextSecondary, fontSize = 10.5.sp)
    }
}

@Composable
private fun HexInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 14.sp) },
        text = {
            Column {
                Text(label, color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = SunColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SunColors.Surface3, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("确定", color = SunColors.Accent, fontSize = 12.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 12.sp) } }
    )
}

@Composable
private fun HexFindDialog(
    onDismiss: () -> Unit,
    onFind: (ByteArray, Int) -> Unit,
    onReplaceAll: (ByteArray, ByteArray) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var asText by remember { mutableStateOf(true) }
    fun bytes(s: String): ByteArray? = if (asText) s.toByteArray() else HexText.decode(s)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = { Text("搜索 / 替换", color = SunColors.TextPrimary, fontSize = 14.sp) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(true to "文本", false to "十六进制").forEach { (t, label) ->
                        Text(
                            label,
                            color = if (asText == t) SunColors.Accent else SunColors.TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (asText == t) SunColors.AccentSoft else Color.Transparent)
                                .clickable { asText = t }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = SunColors.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SunColors.Surface3, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = SunColors.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SunColors.Surface3, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("第二行留空表示只搜索", color = SunColors.TextTertiary, fontSize = 9.5.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val needle = bytes(query) ?: return@TextButton
                if (needle.isEmpty()) return@TextButton
                val repl = bytes(replace)
                if (repl != null && repl.isNotEmpty()) onReplaceAll(needle, repl) else onFind(needle, 0)
                onDismiss()
            }) { Text("执行", color = SunColors.Accent, fontSize = 12.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 12.sp) } }
    )
}
