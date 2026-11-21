package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes

/**
 * 管理侧边栏工具：全部工具都在这里列出来，勾上就出现在侧边栏。
 *
 * 勾选顺序不影响展示顺序 —— 侧边栏按 [AppTool.entries] 的固定顺序排，
 * 这样反复增减也不会让菜单位置跳来跳去。
 */
@Composable
fun ToolPickerDialog(
    selected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val checked = remember(selected) { mutableStateListOf<String>().apply { addAll(selected) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface2,
        shape = SunShapes.Medium,
        title = { Text("管理工具", color = SunColors.TextPrimary, fontSize = 15.sp) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppTool.entries.forEach { tool ->
                    val on = checked.contains(tool.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (on) checked.remove(tool.id) else checked.add(tool.id)
                            }
                            .padding(horizontal = 6.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            tool.icon,
                            null,
                            tint = if (on) SunColors.TextPrimary else SunColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tool.label,
                            color = if (on) SunColors.TextPrimary else SunColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .size(17.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (on) SunColors.Accent else SunColors.TextTertiary.copy(alpha = 0.25f),
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) {
                                Icon(
                                    Icons.Outlined.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 按目录顺序写回，保证侧边栏顺序稳定
                val ids = AppTool.entries.filter { checked.contains(it.id) }.map { it.id }
                onConfirm(ids)
            }) {
                Text("完成", color = SunColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}
