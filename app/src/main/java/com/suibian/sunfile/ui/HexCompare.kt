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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.HexBuffer
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 十六进制对比：左右并排，差异字节直接染色，点右侧字节可以就地改，
 * 改完点「保存」写回右边那个文件——不用再来回切编辑器。
 */
@Composable
fun HexCompareBody(left: File, right: File, saveTick: Int, onSaved: () -> Unit) {
    val loaded by produceState<Pair<HexBuffer, HexBuffer>?>(initialValue = null, left, right) {
        value = withContext(Dispatchers.IO) {
            val a = HexBuffer.load(left)
            val b = HexBuffer.load(right)
            a.buffer to b.buffer
        }
    }

    val pair = loaded
    if (pair == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), color = SunColors.Accent, strokeWidth = 2.dp)
        }
        return
    }

    val (lb, rb) = pair
    var editAt by remember { mutableStateOf<Int?>(null) }
    var dirty by remember { mutableIntStateOf(0) }
    val rows = ((maxOf(lb.length, rb.length) + 7) / 8).coerceAtLeast(0)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        items(rows) { row ->
            val base = row * 8
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    base.toString(16).padStart(6, '0'),
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(46.dp)
                )
                HexSide(lb, rb, base, false) { }
                Spacer(Modifier.width(6.dp))
                HexSide(rb, lb, base, true) { at -> editAt = at }
            }
        }
    }

    editAt?.let { at ->
        var text by remember(at) { mutableStateOf(rb.byteAt(at).toString(16).padStart(2, '0')) }
        AlertDialog(
            onDismissRequest = { editAt = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = {
                Text(
                    "偏移 0x${at.toString(16).uppercase()}",
                    color = SunColors.TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("原值 ${rb.byteAt(at).toString(16).padStart(2, '0')}", color = SunColors.TextTertiary, fontSize = 11.5.sp)
                    Spacer(Modifier.width(14.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { raw ->
                            text = raw.filter { it.isLetterOrDigit() }.take(2).uppercase()
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = SunColors.TextPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(SunColors.Accent),
                        modifier = Modifier
                            .width(52.dp)
                            .background(SunColors.Surface2, SunShapes.Small)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = text.toIntOrNull(16)
                    if (v != null) {
                        rb.setByte(at, v)
                        dirty++
                    }
                    editAt = null
                }) { Text("写入", color = SunColors.Accent, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { editAt = null }) {
                    Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(saveTick) {
        if (saveTick == 0) return@LaunchedEffect
        // 只加载了前 6 MB 的文件不能整篇写回，否则会把后面的内容截掉
        if (right.length() > rb.length) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { rb.save(right) } }
        onSaved()
    }
}

@Composable
private fun HexSide(
    buffer: HexBuffer,
    other: HexBuffer,
    base: Int,
    editable: Boolean,
    onEdit: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (k in 0 until 8) {
            val pos = base + k
            val v = buffer.byteAt(pos)
            val o = other.byteAt(pos)
            val differs = v >= 0 && v != o
            val text = if (v < 0) "  " else v.toString(16).padStart(2, '0').uppercase()
            Text(
                text,
                color = if (v < 0) SunColors.TextTertiary else if (editable) SunColors.TextPrimary else SunColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        when {
                            v < 0 -> Color.Transparent
                            differs -> SunColors.AccentSoft
                            editable -> SunColors.Surface2
                            else -> SunColors.Surface1
                        },
                        SunShapes.Small
                    )
                    .clickable(enabled = editable && v >= 0) { onEdit(pos) }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}
