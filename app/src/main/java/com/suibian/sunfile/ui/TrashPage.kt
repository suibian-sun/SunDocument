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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.TrashItem
import com.suibian.sunfile.ui.components.MaterialFileIcon
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回收站：删除的东西先落在这里，可还原或彻底删除。
 * 30 天到期由 Trash.purgeExpired 在启动时清理，界面只展示剩余时间。
 */
@Composable
fun TrashPage(
    items: List<TrashItem>,
    onBack: () -> Unit,
    onRestore: (TrashItem) -> Unit,
    onPurge: (TrashItem) -> Unit,
    onEmpty: () -> Unit
) {
    var version by remember { mutableIntStateOf(0) }
    var confirmPurge by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(Mt.BarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = SunColors.TextPrimary, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("回收站", color = SunColors.TextPrimary, fontSize = Mt.Title)
                Text(
                    if (items.isEmpty()) "空" else "${items.size} 项 · 30 天后自动清除",
                    color = SunColors.TextTertiary,
                    fontSize = Mt.BarMeta
                )
            }
            if (items.isNotEmpty()) {
                Text(
                    "清空",
                    color = SunColors.Danger,
                    fontSize = 12.5.sp,
                    modifier = Modifier.clickable { confirmEmpty = true }.padding(horizontal = 14.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("回收站是空的", color = SunColors.TextTertiary, fontSize = 12.5.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.id }) { item ->
                    val daysLeft = ((item.deletedAt + 30L * 86_400_000L - System.currentTimeMillis()) / 86_400_000L)
                        .coerceAtLeast(0L)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialFileIcon(item.name, isDir = item.isDir, size = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = SunColors.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.origPath,
                                color = SunColors.TextTertiary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${fmt.format(Date(item.deletedAt))} · 剩余 $daysLeft 天" +
                                    if (!item.isDir) " · ${Fmt.size(item.size)}" else "",
                                color = SunColors.TextTertiary,
                                fontSize = 9.5.sp
                            )
                        }
                        IconButton(onClick = { onRestore(item); version++ }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Outlined.Restore, "还原", tint = SunColors.Accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { confirmPurge = item }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "彻底删除", tint = SunColors.Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(
                        Modifier
                            .padding(start = 14.dp)
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(SunColors.Hairline.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }

    confirmPurge?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            containerColor = SunColors.Surface4,
            shape = RoundedCornerShape(14.dp),
            title = { Text("彻底删除", color = SunColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "${item.name} 将被永久删除",
                    color = SunColors.TextSecondary,
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPurge(item)
                    confirmPurge = null
                }) { Text("删除", color = SunColors.Danger, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = null }) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = SunColors.Surface4,
            shape = RoundedCornerShape(14.dp),
            title = { Text("清空回收站", color = SunColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = { Text("${items.size} 项将被永久删除", color = SunColors.TextSecondary, fontSize = 12.5.sp) },
            confirmButton = {
                TextButton(onClick = {
                    onEmpty()
                    confirmEmpty = false
                }) { Text("清空", color = SunColors.Danger, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp) }
            }
        )
    }
}
