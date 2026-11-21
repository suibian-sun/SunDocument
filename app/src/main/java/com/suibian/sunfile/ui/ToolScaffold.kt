package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes

/**
 * 工具页统一骨架：深色顶栏 + 可滚动内容。
 * 顶栏与侧边栏、文件页共用 TopBar 色，页面切换时不会出现一条突兀的色带。
 */
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .topBarBackground()
                .statusBarsPadding()
                .height(Mt.BarHeight)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = SunColors.OnTopBar,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = SunColors.OnTopBarMuted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        Column(Modifier.fillMaxSize()) { content() }
    }
}

/** 深色顶栏上的图标按钮，自动用亮色 */
@Composable
fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            description,
            tint = if (enabled) SunColors.OnTopBar else SunColors.OnTopBarMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(19.dp)
        )
    }
}

/** 带底色的输入框：无描边，靠 2dp 圆角和底色区分 */
@Composable
fun SunField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    singleLine: Boolean = true,
    actionLabel: String = "完成",
    onAction: (() -> Unit)? = null
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SunColors.Surface2)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .then(modifier)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = SunColors.TextTertiary, fontSize = 12.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = SunColors.TextPrimary,
                fontSize = 12.5.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            ),
            cursorBrush = SolidColor(SunColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onAction?.invoke() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 主按钮：整宽、圆角、按压变暗 */
@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = SunColors.Accent,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .then(modifier)
            .height(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) color else SunColors.Surface3)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color.White else SunColors.TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 次要按钮：描边 */
@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = SunColors.TextSecondary,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .then(modifier)
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, SunColors.Hairline, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 12.sp)
    }
}

/** 统一的短提示；工具页大量使用，集中一处避免各页重复定义 */
internal fun toast(context: android.content.Context, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = SunColors.TextTertiary,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyState(text: String, hint: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = SunColors.TextSecondary, fontSize = 13.sp)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, color = SunColors.TextTertiary, fontSize = 11.sp)
        }
    }
}

/** 卡片式信息块 */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SunColors.Surface1)
            .padding(14.dp),
        content = content
    )
}

/** 键值一行 */
@Composable
fun KeyValue(key: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(key, color = SunColors.TextTertiary, fontSize = 11.5.sp, modifier = Modifier.width(88.dp))
        Text(
            value,
            color = SunColors.TextPrimary,
            fontSize = 11.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

/** 细分隔线 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))
}

/** 开关：细长胶囊，不引入 Material 默认的紫色 */
@Composable
fun SunSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        Modifier
            .then(modifier)
            .size(width = 38.dp, height = 22.dp)
            .clip(SunShapes.Pill)
            .background(if (checked) SunColors.Accent else SunColors.Surface3)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(18.dp).clip(SunShapes.Pill).background(Color.White))
    }
}
