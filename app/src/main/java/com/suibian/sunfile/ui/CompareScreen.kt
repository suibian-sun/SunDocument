package com.suibian.sunfile.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Compare
import com.suibian.sunfile.data.CompareResult
import com.suibian.sunfile.data.Diff
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 文件对比器：文本逐行着色，文件夹 / 压缩包输出清单式报告 */
@Composable
fun CompareOverlay(left: File, right: File, onClose: () -> Unit) {
    val result by produceState<CompareResult?>(initialValue = null, left, right) {
        value = withContext(Dispatchers.IO) { runCatching { Compare.of(left, right) }.getOrElse { CompareResult.Failed(it.message ?: "对比失败") } }
    }

    OverlayShell(
        title = "文件对比",
        subtitle = "${left.name}  ↔  ${right.name}",
        onClose = onClose
    ) {
        when (val r = result) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }

            is CompareResult.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(r.message, color = SunColors.TextSecondary, fontSize = 12.sp)
            }

            is CompareResult.Report -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp)
            ) {
                item {
                    Text(r.title, color = SunColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(r.body.split('\n')) { line ->
                    Text(
                        line.ifEmpty { " " },
                        color = when {
                            line.startsWith("仅左侧") -> SunColors.Danger
                            line.startsWith("仅右侧") -> Color(0xFF3FB950)
                            line.startsWith("大小不同") || line.startsWith("类型不同") -> SunColors.Accent
                            line.startsWith("对比") || line.startsWith("共 ") -> SunColors.TextSecondary
                            else -> SunColors.TextSecondary
                        },
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            is CompareResult.TextDiff -> {
                val (add, del, same) = Diff.stats(r.rows)
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(34.dp).background(SunColors.Surface2).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("+$add", color = Color(0xFF3FB950), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("-$del", color = SunColors.Danger, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("相同 $same 行", color = SunColors.TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        if (r.truncated) {
                            Text("仅对比前 1.5 MB", color = SunColors.TextTertiary, fontSize = 9.sp)
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(r.rows) { row ->
                            val bg = when (row.op) {
                                Diff.Op.ADD -> Color(0xFF3FB950).copy(alpha = 0.13f)
                                Diff.Op.DEL -> SunColors.Danger.copy(alpha = 0.13f)
                                Diff.Op.SAME -> Color.Transparent
                            }
                            val mark = when (row.op) {
                                Diff.Op.ADD -> "+"
                                Diff.Op.DEL -> "-"
                                Diff.Op.SAME -> " "
                            }
                            Row(
                                Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.oldLine.takeIf { it > 0 }?.toString() ?: "",
                                    color = SunColors.TextTertiary,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(34.dp)
                                )
                                Text(
                                    row.newLine.takeIf { it > 0 }?.toString() ?: "",
                                    color = SunColors.TextTertiary,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(34.dp)
                                )
                                Text(
                                    mark,
                                    color = when (row.op) {
                                        Diff.Op.ADD -> Color(0xFF3FB950)
                                        Diff.Op.DEL -> SunColors.Danger
                                        Diff.Op.SAME -> SunColors.TextTertiary
                                    },
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(12.dp)
                                )
                                Text(
                                    row.text,
                                    color = SunColors.TextPrimary,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
