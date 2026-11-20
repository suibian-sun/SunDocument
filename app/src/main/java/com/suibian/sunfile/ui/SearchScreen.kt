package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.suibian.sunfile.vm.SearchMode
import com.suibian.sunfile.vm.SearchUiState

@Composable
fun SearchPage(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onResult: (FileEntry) -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onContentResult: (com.suibian.sunfile.data.FileRepo.ContentHit) -> Unit,
    dense: Boolean = false
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (dense) Modifier
                else Modifier.background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()
            )
    ) {
        Row(
            Modifier.fillMaxWidth().height(com.suibian.sunfile.ui.theme.Mt.BarHeight).background(SunColors.Surface1).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
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

        // 模式切换：名称 / 内容
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            SearchMode.values().forEach { mode ->
                val active = mode == state.mode
                Text(
                    mode.label,
                    color = if (active) SunColors.Surface4 else SunColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(SunShapes.Pill)
                        .background(if (active) SunColors.Accent else SunColors.Surface2)
                        .clickable { onModeChange(mode) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
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
                    Text("停止", color = SunColors.TextTertiary, fontSize = 9.5.sp)
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
            state.mode == SearchMode.CONTENT && state.contentHits.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    Text(
                        "${state.contentHits.size} 处命中",
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.contentHits.size) { idx ->
                    val hit = state.contentHits[idx]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onContentResult(hit) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "${hit.line}",
                            color = SunColors.Accent,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(38.dp).padding(top = 1.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.file.name,
                                color = SunColors.TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                hit.preview,
                                color = SunColors.TextSecondary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            state.finished && state.mode == SearchMode.CONTENT && state.contentHits.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Search, null, tint = SunColors.Surface4, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(10.dp))
                Text("没有匹配的内容", color = SunColors.TextTertiary, fontSize = 11.5.sp)
                Text("已扫描 ${state.scanned} 项", color = SunColors.TextTertiary, fontSize = 9.5.sp)
            }

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
                        com.suibian.sunfile.ui.components.FileTypeBadge(
                            file = r.entry.file,
                            isDir = r.entry.isDir,
                            size = 22.dp
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


/**
 * 搜索以弹窗呈现：不离开当前目录，结果点开就跳回列表。
 */
@Composable
fun SearchDialog(
    state: SearchUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onResult: (FileEntry) -> Unit,
    onModeChange: (SearchMode) -> Unit,
    onContentResult: (com.suibian.sunfile.data.FileRepo.ContentHit) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 44.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SunColors.Surface1)
            ) {
                SearchPage(
                    state = state,
                    onBack = onDismiss,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onCancel = onCancel,
                    onResult = onResult,
                    onModeChange = onModeChange,
                    onContentResult = onContentResult,
                    dense = true
                )
            }
        }
    }
}
