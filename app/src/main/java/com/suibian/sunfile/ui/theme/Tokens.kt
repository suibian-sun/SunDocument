package com.suibian.sunfile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 色板：层级之间只用 1%–3% 的明度差区分，不使用描边。
 * 背景越靠上层越亮，弹层最亮，靠"光"而不是"框"建立层次。
 */
object SunColors {
    val Bg = Color(0xFF0F1318)          // 窗口底
    val Surface1 = Color(0xFF12171D)    // 顶栏/底栏（+1.5%）
    val Surface2 = Color(0xFF151B22)    // 列表悬浮态（+2%）
    val Surface3 = Color(0xFF181F27)    // 卡片/抽屉（+2.5%）
    val Surface4 = Color(0xFF1C242D)    // 弹层/底部面板（+3%）
    val Hairline = Color(0xFF1D2530)    // 极淡分隔

    val TextPrimary = Color(0xFFE8EDF2)
    val TextSecondary = Color(0xFF98A2AE)
    val TextTertiary = Color(0xFF67717D)

    val Accent = Color(0xFF4B8DF8)
    val AccentSoft = Color(0x294B8DF8)
    val AccentLine = Color(0xFF2B5FA8)

    val Folder = Color(0xFFD9A93F)
    val File = Color(0xFF7E8895)
    val Danger = Color(0xFFE5534B)
    val Success = Color(0xFF3FB950)
    val Warning = Color(0xFFD9A93F)
}

/**
 * 动效令牌：进场缓出、退场缓入、页面内移动缓入缓出。
 * 时长统一走 150 / 250 / 300 / 380 四档，全站复用。
 */
object Motion {
    const val Fast = 150
    const val Base = 250
    const val Slow = 300
    const val Reveal = 380

    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 按压回弹：有重量、不过冲 */
    fun press() = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 元素位移/展开 */
    fun settle() = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium
    )
}

object SunShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = RoundedCornerShape(50)
}

private val SunTypography = Typography(
    // 暗色背景下整体降低字重，抵消亮字在深底上的光渗（发光变胖）
    displaySmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 9.5.sp, letterSpacing = 0.4.sp)
)

/** 数字统一使用等宽字体，避免滚动时宽度跳动 */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    letterSpacing = 0.sp
)

@Composable
fun SunDocumentTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = SunColors.Accent,
        onPrimary = Color.White,
        primaryContainer = SunColors.AccentLine,
        onPrimaryContainer = Color.White,
        background = SunColors.Bg,
        onBackground = SunColors.TextPrimary,
        surface = SunColors.Surface2,
        onSurface = SunColors.TextPrimary,
        surfaceVariant = SunColors.Surface3,
        onSurfaceVariant = SunColors.TextSecondary,
        surfaceContainer = SunColors.Surface3,
        surfaceContainerHigh = SunColors.Surface4,
        outline = SunColors.Hairline,
        error = SunColors.Danger,
        onError = Color.White
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = SunTypography,
        content = content
    )
}
