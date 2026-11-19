package com.suibian.sunfile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.util.Fmt
import com.suibian.sunfile.vm.PaneState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PanePanel(
    index: Int,
    state: PaneState,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onPathClick: () -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onEntryMenu: (FileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollToken) {
        if (state.entries.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(modifier.background(SunColors.Panel)) {
        // 路径栏
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(SunColors.PanelAlt),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUp, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上级目录", tint = SunColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(onClick = onPathClick, onLongClick = onPathClick)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            ) {
                Text(
                    text = laneLabel(index) + " " + shortPath(state.path),
                    color = SunColors.TextPrimary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = SunColors.TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.entries.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(22.dp),
                        color = SunColors.Accent,
                        strokeWidth = 2.dp
                    )
                }

                state.error != null && state.entries.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("无法读取", color = SunColors.TextSecondary, fontSize = 12.sp)
                        Text(
                            state.error,
                            color = SunColors.TextDim,
                            fontSize = 10.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                state.entries.isEmpty() -> {
                    Text(
                        "空文件夹",
                        color = SunColors.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.entries, key = { it.path }) { entry ->
                            FileRow(
                                entry = entry,
                                selected = state.selected.contains(entry.path),
                                onClick = { onEntryClick(entry) },
                                onLongClick = { onEntryLongClick(entry) },
                                onMenu = { onEntryMenu(entry) }
                            )
                        }
                    }
                }
            }
        }

        // 面板底部状态条
        Row(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(SunColors.PanelAlt)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.selectionMode) "已选 ${state.selected.size} 项" else "${state.entries.size} 项",
                color = if (state.selectionMode) SunColors.Accent else SunColors.TextDim,
                fontSize = 9.5.sp
            )
            Text(
                text = if (state.loading) "读取中…" else "",
                color = SunColors.TextDim,
                fontSize = 9.5.sp
            )
        }
    }
}

private fun laneLabel(index: Int) = if (index == 0) "A" else "B"

private fun shortPath(path: String): String {
    if (path.isEmpty()) return "/"
    val ext = "/storage/emulated/0"
    return when {
        path == ext -> "内部存储"
        path.startsWith("$ext/") -> path.removePrefix(ext)
        else -> path
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(if (selected) SunColors.AccentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 5.dp, end = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDir) SunColors.Folder else SunColors.File,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = if (selected) SunColors.Accent else SunColors.TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text = if (entry.isDir) "文件夹 · ${Fmt.time(entry.lastModified)}"
                else "${Fmt.size(entry.size)} · ${Fmt.time(entry.lastModified)}",
                color = SunColors.TextDim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = SunColors.TextDim,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
