package com.suibian.sunfile.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.DocParser
import com.suibian.sunfile.data.DocWrite
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.SheetTable
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文档编辑：docx / pptx 改段落文字，xlsx / csv 改单元格。
 *
 * 只改内容不动版式——回写时沿用原段落的 rPr / 单元格样式，
 * 因此打开原文件不会出现「整篇变样」。
 */
@Composable
fun DocEditPane(file: File, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = FileKinds.extensionOf(file)
    val isSheet = ext in setOf("xlsx", "xlsm", "csv", "tsv", "ods")
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var paras by remember { mutableStateOf(listOf<DocWrite.Para>()) }
    var sheetIndex by remember { mutableIntStateOf(0) }
    var sheets by remember { mutableStateOf(listOf<SheetTable>()) }
    val edits = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (isSheet) DocParser.parse(file).sheets to emptyList<DocWrite.Para>()
                else emptyList<SheetTable>() to DocWrite.readParas(file)
            }
        }
        result.onSuccess { (s, p) ->
            sheets = s
            paras = p
            if (!isSheet && p.isEmpty()) errorMsg = "这个文档没有可编辑的段落"
        }.onFailure { errorMsg = it.message ?: "无法读取" }
        loading = false
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    if (isSheet) {
                        val table = sheets.getOrNull(sheetIndex) ?: return@runCatching "没有可编辑的表格"
                        if (ext == "csv" || ext == "tsv") {
                            val rows = table.rows.map { it.toMutableList() }.toMutableList()
                            edits.forEach { (k, v) ->
                                val (r, c) = k.split(':').map { it.toInt() }
                                while (rows.size <= r) rows.add(mutableListOf())
                                while (rows[r].size <= c) rows[r].add("")
                                rows[r][c] = v
                            }
                            DocWrite.writeCsv(file, rows, if (ext == "tsv") '\t' else ',')
                        } else {
                            val cells = edits.mapKeys { (k, _) ->
                                val (r, c) = k.split(':').map { it.toInt() }
                                colName(c) + (r + 1)
                            }
                            if (!DocWrite.writeCells(file, sheetIndex, cells)) error("写入失败")
                        }
                    } else {
                        val texts = paras.mapIndexed { i, p ->
                            edits["p:$i"] ?: p.text
                        }
                        if (!DocWrite.writeParas(file, texts)) error("写入失败")
                    }
                    null
                }.getOrElse { it.message ?: "保存失败" }
            }
            saving = false
            if (err == null) {
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().topBarBackground().statusBarsPadding().height(Mt.BarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = SunColors.OnTopBar, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = SunColors.OnTopBar,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isSheet) "编辑单元格" else "编辑文字",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
            TextButton(onClick = { save() }, enabled = !saving) {
                Text(if (saving) "保存中…" else "保存", color = SunColors.Accent, fontSize = 13.5.sp)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }

            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg ?: "", color = SunColors.TextSecondary, fontSize = 12.5.sp)
            }

            isSheet -> SheetEdit(
                sheets = sheets,
                index = sheetIndex,
                onIndex = { sheetIndex = it },
                edits = edits,
                onChange = { key, value ->
                    if (value.isEmpty()) edits.remove(key) else edits[key] = value
                }
            )

            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp)) {
                itemsIndexed(paras) { i, p ->
                    val prevPage = paras.getOrNull(i - 1)?.page ?: 0
                    if (p.page > 0 && p.page != prevPage) {
                        Text(
                            "第 ${p.page} 页",
                            color = SunColors.Accent,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    val value = edits["p:$i"] ?: p.text
                    BasicTextField(
                        value = value,
                        onValueChange = { v -> if (v == p.text) edits.remove("p:$i") else edits["p:$i"] = v },
                        textStyle = TextStyle(
                            color = SunColors.TextPrimary,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp
                        ),
                        cursorBrush = SolidColor(SunColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default, keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (edits.containsKey("p:$i")) SunColors.AccentSoft else SunColors.Surface1)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetEdit(
    sheets: List<SheetTable>,
    index: Int,
    onIndex: (Int) -> Unit,
    edits: Map<String, String>,
    onChange: (String, String) -> Unit
) {
    if (sheets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有可编辑的表格", color = SunColors.TextTertiary, fontSize = 12.5.sp)
        }
        return
    }
    val table = sheets[index.coerceIn(0, sheets.lastIndex)]
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var draft by remember { mutableStateOf("") }
    val scroll = remember { androidx.compose.foundation.ScrollState(0) }
    val columns = table.rows.maxOfOrNull { it.size } ?: 0

    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sheets.forEachIndexed { i, s ->
                    val active = i == index
                    Text(
                        s.name,
                        color = if (active) SunColors.Accent else SunColors.TextSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) SunColors.AccentSoft else SunColors.Surface2)
                            .clickable { onIndex(i) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            itemsIndexed(table.rows) { r, row ->
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    repeat(columns) { c ->
                        val key = "$r:$c"
                        val text = edits[key] ?: row.getOrNull(c).orEmpty()
                        Box(
                            Modifier
                                .width(112.dp)
                                .height(38.dp)
                                .background(if (r == 0) SunColors.Surface3 else SunColors.Bg)
                                .clickable {
                                    editing = r to c
                                    draft = text
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text,
                                color = if (r == 0) SunColors.TextPrimary else SunColors.TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(SunColors.Hairline.copy(alpha = 0.5f)))
            }
        }
    }

    editing?.let { (r, c) ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = SunColors.Surface4,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    "${colName(c)}${r + 1}",
                    color = SunColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(SunColors.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(SunColors.Surface2)
                        .padding(10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange("$r:$c", draft)
                    editing = null
                }) { Text("确定", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
            }
        )
    }
}

/** 0 -> A, 25 -> Z, 26 -> AA */
private fun colName(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
        sb.insert(0, ('A' + i % 26))
        i = i / 26 - 1
    }
    return sb.toString()
}
