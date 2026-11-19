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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.suibian.sunfile.data.FileEntry
import com.suibian.sunfile.ui.components.FileTypeBadge
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import kotlin.math.roundToInt

/** 长按菜单中的一项 */
class EntryAction(
    val icon: ImageVector,
    val label: String,
    val hint: String? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 长按文件弹出的功能菜单（对齐 MT）：出现在手指附近，
 * 复制 / 移动默认目标是另一栏，点空白处收起。
 */
@Composable
fun EntryActionMenu(
    entry: FileEntry,
    anchor: Offset,
    actions: List<EntryAction>,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val widthDp = 212.dp
    val widthPx = with(density) { widthDp.toPx() }
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val estimated = with(density) { (56 + actions.size * 40 + 12).dp.toPx() }

    val x = anchor.x.roundToInt().coerceIn(with(density) { 6.dp.toPx() }.toInt(),
        (screenW - widthPx - with(density) { 6.dp.toPx() }).toInt().coerceAtLeast(0))
    val y = if (anchor.y + estimated > screenH) {
        (screenH - estimated).coerceAtLeast(with(density) { 20.dp.toPx() }).toInt()
    } else {
        anchor.y.roundToInt()
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x, y),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .width(widthDp)
                .heightIn(max = 440.dp)
                .shadow(18.dp, SunShapes.Large)
                .clip(SunShapes.Large)
                .background(SunColors.Surface4)
                .padding(vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileTypeBadge(entry.file, entry.isDir, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        color = SunColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (entry.isDir) "文件夹" else Fmt.size(entry.size),
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
            HorizontalDivider(color = SunColors.Hairline, thickness = 0.6.dp)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 3.dp)) {
                actions.forEach { action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            action.icon,
                            null,
                            tint = if (action.danger) SunColors.Danger else SunColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            action.label,
                            color = if (action.danger) SunColors.Danger else SunColors.TextPrimary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (action.hint != null) {
                            Text(action.hint, color = SunColors.TextTertiary, fontSize = 9.5.sp)
                        }
                    }
                }
            }
        }
    }
}
