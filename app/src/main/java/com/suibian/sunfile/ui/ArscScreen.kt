package com.suibian.sunfile.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.ArchiveEdit
import com.suibian.sunfile.data.Arsc
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 资源表来源：APK 内的 resources.arsc 或磁盘上的 .arsc。
 * entry 非空表示要改的是压缩包里的条目，保存时整包重写。
 */
data class ArscSource(val file: File, val entry: String? = null) {
    val label: String get() = entry?.let { "${file.name} / $it" } ?: file.name
}

@Composable
fun ArscOverlay(source: ArscSource, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Arsc.Entry?>(null) }
    var editingString by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val loaded by produceState<Loaded?>(null, source, tick) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (source.entry != null) {
                    FileRepo.readZipEntry(source.file, source.entry, 64L * 1024 * 1024)
                } else {
                    source.file.readBytes()
                }
                Loaded(bytes, Arsc.parse(bytes))
            }.getOrElse { Loaded(ByteArray(0), Arsc.Table(error = it.message ?: "读取失败")) }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2200)
            message = null
        }
    }

    fun save(bytes: ByteArray) {
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (source.entry != null) {
                        ArchiveEdit.zipPut(source.file, source.entry, bytes)
                    } else {
                        source.file.writeBytes(bytes)
                        true
                    }
                }.getOrDefault(false)
            }
            busy = false
            message = if (ok) "已保存" else "保存失败"
            if (ok) tick++
        }
    }

    val table = loaded?.table
    val bytes = loaded?.bytes
    val entries = remember(table, query) {
        table?.entries.orEmpty().filter { e ->
            query.isBlank() ||
                e.name.contains(query, true) ||
                e.kind.contains(query, true) ||
                e.text.contains(query, true) ||
                e.config.contains(query, true)
        }
    }
    val groups = remember(entries) { entries.groupBy { it.kind }.toList() }
    val strings = remember(table, query, tab) {
        if (table == null || tab != 1) emptyList()
        else table.strings.withIndex().filter { query.isBlank() || it.value.contains(query, true) }
    }

    OverlayShell(
        title = source.label,
        subtitle = table?.let {
            "${it.packages.joinToString("/").ifEmpty { "资源表" }} · ${it.entries.size} 项 · ${it.strings.size} 字符串"
        } ?: "解析中…",
        onClose = onClose,
        actions = {
            Row(Modifier.padding(end = 6.dp)) {
                listOf("资源", "字符串").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (tab == i) SunColors.Accent else SunColors.TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = if (tab == i) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(
                                if (tab == i) SunColors.AccentSoft else Color.Transparent,
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { tab = i }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(34.dp)
                    .background(SunColors.Surface2, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 12.5.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SunColors.Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("搜索名称、类型或值", color = SunColors.TextTertiary, fontSize = 12.5.sp)
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    Text(
                        "清除",
                        color = SunColors.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { query = "" }.padding(start = 8.dp)
                    )
                }
            }

            when {
                busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
                }
                table?.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(table.error ?: "解析失败", color = SunColors.TextSecondary, fontSize = 12.5.sp)
                }
                tab == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    groups.forEach { (kind, list) ->
                        item(key = "h-$kind") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(kind, color = SunColors.TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "  ${list.size}",
                                    color = SunColors.TextTertiary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                        itemsIndexed(list) { _, e ->
                            ArscRow(e) { editing = e }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(strings, key = { it.index }) { item ->
                        Column(
                            Modifier.fillMaxWidth().clickable { editingString = item.index }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                item.value.ifEmpty { "(空)" },
                                color = SunColors.TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "#${item.index}",
                                color = SunColors.TextTertiary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        ArscEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { input ->
                editing = null
                val src = bytes ?: return@ArscEditDialog
                val next = applyEdit(src, entry, input)
                if (next == null) message = "值格式不对" else save(next)
            }
        )
    }

    editingString?.let { index ->
        val current = table?.strings?.getOrNull(index) ?: ""
        ArscEditDialog(
            entry = null,
            stringIndex = index,
            initial = current,
            onDismiss = { editingString = null },
            onSave = { input ->
                editingString = null
                val src = bytes ?: return@ArscEditDialog
                val next = Arsc.replaceString(src, index, input)
                if (next == null) message = "写入失败" else save(next)
            }
        )
    }

    message?.let { msg ->
        Box(
            Modifier.fillMaxSize().padding(bottom = 40.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                msg,
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(SunColors.Surface4, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

private class Loaded(val bytes: ByteArray, val table: Arsc.Table)

@Composable
private fun ArscRow(e: Arsc.Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                e.name,
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (e.config != "默认") {
                Text(e.config, color = SunColors.TextTertiary, fontSize = 9.5.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            e.text,
            color = if (e.valueType == Arsc.TYPE_STRING) SunColors.TextSecondary else SunColors.Accent,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp)
        )
    }
}

@Composable
private fun ArscEditDialog(
    entry: Arsc.Entry?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    stringIndex: Int = -1,
    initial: String = ""
) {
    val start = entry?.text ?: initial
    var value by remember(entry, stringIndex) { mutableStateOf(start) }
    val editable = entry == null || entry.valueType in setOf(
        Arsc.TYPE_STRING, Arsc.TYPE_INT_DEC, Arsc.TYPE_INT_HEX, Arsc.TYPE_INT_BOOLEAN,
        Arsc.TYPE_FLOAT, Arsc.TYPE_INT_COLOR_ARGB8, Arsc.TYPE_REFERENCE, Arsc.TYPE_ATTRIBUTE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface4,
        shape = com.suibian.sunfile.ui.theme.SunShapes.Medium,
        title = {
            Column {
                Text(
                    entry?.let { "${it.kind}/${it.name}" } ?: "字符串 #$stringIndex",
                    color = SunColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry?.let {
                    Text(
                        "0x${it.resId.toUInt().toString(16)} · ${it.config}",
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column {
                if (!editable) {
                    Text(
                        "${entry?.text ?: ""}（复合值，暂不支持编辑）",
                        color = SunColors.TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        textStyle = TextStyle(
                            color = SunColors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = if (entry == null || entry.valueType == Arsc.TYPE_STRING) FontFamily.Default
                            else FontFamily.Monospace
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(SunColors.Accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (entry == null || entry.valueType == Arsc.TYPE_STRING) KeyboardType.Text
                            else KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .background(SunColors.Surface2, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    )
                    val hint = when (entry?.valueType) {
                        Arsc.TYPE_INT_HEX -> "十六进制，可带 0x 前缀"
                        Arsc.TYPE_INT_BOOLEAN -> "true / false"
                        Arsc.TYPE_FLOAT -> "小数"
                        Arsc.TYPE_INT_COLOR_ARGB8 -> "#AARRGGBB 或 #RRGGBB"
                        Arsc.TYPE_REFERENCE, Arsc.TYPE_ATTRIBUTE -> "@0x7f010001"
                        null -> null
                        else -> null
                    }
                    if (hint != null) {
                        Text(hint, color = SunColors.TextTertiary, fontSize = 9.5.sp, modifier = Modifier.padding(top = 7.dp, start = 4.dp))
                    }
                    Text(
                        "改动会直接写入文件，APK 需重新签名",
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (editable) {
                TextButton(onClick = { onSave(value) }) {
                    Text("保存", color = SunColors.Accent, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
        }
    )
}

/** 把用户输入写回成新的资源表字节 */
private fun applyEdit(bytes: ByteArray, e: Arsc.Entry, input: String): ByteArray? {
    val text = input.trim()
    return when (e.valueType) {
        Arsc.TYPE_STRING -> Arsc.replaceString(bytes, e.stringIndex, text)
        Arsc.TYPE_INT_DEC -> Arsc.replaceValue(bytes, e.dataOffset, text.toLongOrNull()?.toInt() ?: return null)
        Arsc.TYPE_INT_HEX -> {
            val v = text.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return null
            Arsc.replaceValue(bytes, e.dataOffset, v.toInt())
        }
        Arsc.TYPE_INT_BOOLEAN -> Arsc.replaceValue(
            bytes, e.dataOffset, if (text.equals("true", true) || text == "1") 1 else 0
        )
        Arsc.TYPE_FLOAT -> Arsc.replaceValue(bytes, e.dataOffset, text.toFloatOrNull()?.toRawBits() ?: return null)
        Arsc.TYPE_INT_COLOR_ARGB8 -> {
            val hex = text.removePrefix("#")
            val v = if (hex.length == 6) ("FF$hex").toLongOrNull(16) else hex.toLongOrNull(16)
            Arsc.replaceValue(bytes, e.dataOffset, (v ?: return null).toInt())
        }
        Arsc.TYPE_REFERENCE, Arsc.TYPE_ATTRIBUTE -> {
            val v = text.removePrefix("@").removePrefix("?").removePrefix("0x").removePrefix("0X").toLongOrNull(16)
                ?: return null
            Arsc.replaceValue(bytes, e.dataOffset, v.toInt())
        }
        else -> null
    }
}

/** 给逆向页用的资源概览：类型 → 条目数 */
@Composable
fun ArscTypeSummary(entries: List<Arsc.Entry>) {
    val groups = remember(entries) { entries.groupBy { it.kind }.toList().sortedByDescending { it.second.size } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
        items(groups, key = { it.first }) { (kind, list) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kind, color = SunColors.TextPrimary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Text("${list.size}", color = SunColors.TextTertiary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
