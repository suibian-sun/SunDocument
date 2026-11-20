package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.vm.SearchUiState
import com.suibian.sunfile.util.Fmt

/** 大小过滤的预设，与 MT 一致的「任意 / 区间」语义 */
private data class SizePreset(val label: String, val min: Long, val max: Long) {
    fun matches(o: FileRepo.SearchOptions) = o.minSize == min && o.maxSize == max
}

private val SIZE_PRESETS = listOf(
    SizePreset("任意大小", 0L, Long.MAX_VALUE),
    SizePreset("小于 1 MB", 0L, 1024L * 1024),
    SizePreset("1 MB – 10 MB", 1024L * 1024, 10L * 1024 * 1024),
    SizePreset("10 MB – 100 MB", 10L * 1024 * 1024, 100L * 1024 * 1024),
    SizePreset("大于 100 MB", 100L * 1024 * 1024, Long.MAX_VALUE)
)

private data class TimePreset(val label: String, val days: Int)

private val TIME_PRESETS = listOf(
    TimePreset("任意时间", 0),
    TimePreset("今天", 1),
    TimePreset("最近 7 天", 7),
    TimePreset("最近 30 天", 30),
    TimePreset("最近一年", 365)
)

/**
 * 搜索弹窗：按 MT 的面板组织——匹配方式、通配符输入、子目录开关，
 * 展开「高级搜索」后再出现大小 / 时间 / 内容 / 大小写 / 正则。
 */
@Composable
fun SearchDialog(
    state: SearchUiState,
    history: List<String>,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOptionsChange: (FileRepo.SearchOptions) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onResult: (FileEntry) -> Unit,
    onHistoryPick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val o = state.options
    var sizeMenu by remember { mutableStateOf(false) }
    var timeMenu by remember { mutableStateOf(false) }
    var matchMenu by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(o.hasSizeFilter || o.hasTimeFilter || o.contains.isNotBlank() || o.caseSensitive || o.match == FileRepo.MatchMode.REGEX) }
    var contentEditing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SunColors.Surface2)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
            ) {
                // 标题 + 历史
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索", color = SunColors.TextPrimary, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Box {
                        Icon(
                            Icons.Outlined.History,
                            "搜索历史",
                            tint = SunColors.TextSecondary,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { historyMenu = true }
                                .padding(5.dp)
                        )
                        DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                            if (history.isEmpty()) {
                                DropdownMenuItem(text = { Text("暂无历史", fontSize = 13.sp) }, onClick = { historyMenu = false })
                            } else {
                                history.forEach { h ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                h,
                                                fontSize = 13.sp,
                                                color = SunColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            historyMenu = false
                                            onHistoryPick(h)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("清除历史", fontSize = 13.sp, color = SunColors.Danger) },
                                    onClick = {
                                        historyMenu = false
                                        onClearHistory()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 匹配方式下拉
                Box {
                    Row(
                        Modifier.fillMaxWidth().clickable { matchMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            o.match.label,
                            color = SunColors.TextPrimary,
                            fontSize = 14.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text("▾", color = SunColors.TextSecondary, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = matchMenu, onDismissRequest = { matchMenu = false }) {
                        FileRepo.MatchMode.values().forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        m.label,
                                        fontSize = 13.5.sp,
                                        color = if (m == o.match) SunColors.Accent else SunColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    matchMenu = false
                                    onOptionsChange(o.copy(match = m))
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 输入框 + 下划线
                val lineColor = if (state.query.isNotEmpty()) SunColors.Accent else SunColors.TextTertiary.copy(alpha = 0.5f)
                Column(Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(SunColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(lineColor))
                }

                Spacer(Modifier.height(12.dp))

                MtCheck(o.recursive, { onOptionsChange(o.copy(recursive = it)) }, "搜索子目录")
                MtCheck(advanced, { advanced = it }, "高级搜索")

                if (advanced) {
                    Spacer(Modifier.height(6.dp))

                    Box {
                        FilterRow(
                            title = "按文件大小过滤",
                            value = SIZE_PRESETS.firstOrNull { it.matches(o) }?.label ?: "任意大小"
                        ) { sizeMenu = true }
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            SIZE_PRESETS.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            p.label,
                                            fontSize = 13.5.sp,
                                            color = if (p.matches(o)) SunColors.Accent else SunColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        sizeMenu = false
                                        onOptionsChange(o.copy(minSize = p.min, maxSize = p.max))
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        FilterRow(
                            title = "按修改时间过滤",
                            value = TIME_PRESETS.firstOrNull { p ->
                                if (p.days == 0) o.since == 0L
                                else o.since > 0L && o.since >= System.currentTimeMillis() - p.days * 86_400_000L - 86_400_000L
                            }?.label ?: "任意时间"
                        ) { timeMenu = true }
                        DropdownMenu(expanded = timeMenu, onDismissRequest = { timeMenu = false }) {
                            TIME_PRESETS.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            p.label,
                                            fontSize = 13.5.sp,
                                            color = if (p.days == 0 && o.since == 0L) SunColors.Accent else SunColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        timeMenu = false
                                        val since = if (p.days == 0) 0L
                                        else System.currentTimeMillis() - p.days * 86_400_000L
                                        onOptionsChange(o.copy(since = since))
                                    }
                                )
                            }
                        }
                    }

                    if (contentEditing) {
                        ContentInput(
                            value = o.contains,
                            onChange = { onOptionsChange(o.copy(contains = it)) },
                            onDone = { contentEditing = false }
                        )
                    } else {
                        FilterRow(title = "文件中包含内容", value = o.contains.ifBlank { "不限" }) {
                            contentEditing = true
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(SunColors.Hairline))
                    Spacer(Modifier.height(6.dp))

                    MtCheck(o.caseSensitive, { onOptionsChange(o.copy(caseSensitive = it)) }, "区分大小写")
                    MtCheck(
                        o.match == FileRepo.MatchMode.REGEX,
                        { onOptionsChange(o.copy(match = if (it) FileRepo.MatchMode.REGEX else FileRepo.MatchMode.GLOB)) },
                        "正则表达式"
                    )
                }

                // 结果区：搜索开始后在同一弹窗里展开
                if (state.running || state.finished || state.results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(SunColors.Hairline))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.running) "已扫描 ${state.scanned} 项" else "${state.results.size} 个结果",
                            color = SunColors.TextTertiary,
                            style = NumberStyle
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.running) {
                            Text(
                                "停止",
                                color = SunColors.Accent,
                                fontSize = 12.5.sp,
                                modifier = Modifier.clickable { onCancel() }
                            )
                        }
                    }
                    if (state.results.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                            items(state.results, key = { it.entry.path }) { r ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onResult(r.entry) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.suibian.sunfile.ui.components.FileTypeBadge(
                                        file = r.entry.file,
                                        isDir = r.entry.isDir,
                                        size = 24.dp
                                    )
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            r.entry.name,
                                            color = SunColors.TextPrimary,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            r.relative,
                                            color = SunColors.TextTertiary,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!r.entry.isDir) {
                                        Text(Fmt.size(r.entry.size), color = SunColors.TextTertiary, style = NumberStyle)
                                    }
                                }
                            }
                        }
                    } else if (state.finished && !state.running) {
                        Text(
                            "没有找到匹配项",
                            color = SunColors.TextTertiary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        )
                    }
                }

                // 取消 / 确定
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = SunColors.Accent, fontSize = 14.5.sp)
                    }
                    TextButton(onClick = onSearch) {
                        Text("确定", color = SunColors.Accent, fontSize = 14.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentInput(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.sp),
        cursorBrush = SolidColor(SunColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)
    )
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(SunColors.Hairline))
}

/** 两行式过滤项：标题在上、当前取值在下，点开是预设菜单 */
@Composable
private fun FilterRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 7.dp)) {
        Text(title, color = SunColors.TextSecondary, fontSize = 13.5.sp)
        Text(value, color = SunColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** MT 的方框勾选：未选为细边框，选中为实心蓝 + 白勾 */
@Composable
private fun MtCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) SunColors.Accent else Color.Transparent)
                .border(1.5.dp, if (checked) SunColors.Accent else SunColors.TextSecondary.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(label, color = SunColors.TextPrimary, fontSize = 14.sp)
    }
}
