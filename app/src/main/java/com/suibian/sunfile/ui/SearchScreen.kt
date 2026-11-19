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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.SearchUiState

@Composable
fun SearchPage(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onResult: (FileEntry) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            SunTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "搜索 ${compactPath(state.root)}",
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                imeAction = ImeAction.Search,
                onDone = onSearch
            )
            IconButton(onClick = { if (state.running) onCancel() else onSearch() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.Search,
                    "搜索",
                    tint = if (state.running) SunColors.Danger else SunColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (state.running) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = SunColors.Accent,
                    trackColor = SunColors.Surface2
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "已扫描 ${state.scanned} 项",
                        color = SunColors.TextTertiary,
                        style = NumberStyle
                    )
                    Text(
                        "点击右侧按钮停止",
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp
                    )
                }
                if (state.current.isNotEmpty()) {
                    Text(
                        compactPath(state.current),
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        when {
            state.finished && state.results.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Search, null, tint = SunColors.Surface4, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(10.dp))
                Text("没有找到匹配项", color = SunColors.TextTertiary, fontSize = 11.5.sp)
                Text("已扫描 ${state.scanned} 项", color = SunColors.TextTertiary, fontSize = 9.5.sp)
            }

            state.results.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    Text(
                        "${state.results.size} 个结果",
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.results, key = { it.entry.path }) { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onResult(r.entry) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = com.suibian.sunfile.ui.components.iconOf(r.entry.file, r.entry.isDir),
                            contentDescription = null,
                            tint = com.suibian.sunfile.ui.components.tintOf(r.entry.file, r.entry.isDir),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(12.dp))
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
                                fontSize = 9.sp,
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

            else -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("输入关键词后点击搜索", color = SunColors.TextTertiary, fontSize = 11.5.sp)
                Text(
                    "在 ${compactPath(state.root)} 下递归查找",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
