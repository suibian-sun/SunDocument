package com.suibian.sunfile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 主题模式：跟随系统 / 常亮 / 常暗 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("白天"),
    DARK("黑夜");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/**
 * 一套完整色板。层级之间只用 1%–3% 的明度差区分，不使用描边。
 * 亮色下靠阴影 + 白度差建立层次，暗色下靠"光"的抬升。
 */
@Immutable
data class SunPalette(
    val dark: Boolean,
    val Bg: Color,
    val Surface1: Color,
    val Surface2: Color,
    val Surface3: Color,
    val Surface4: Color,
    val Hairline: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val Accent: Color,
    val AccentSoft: Color,
    val AccentLine: Color,
    val Folder: Color,
    val File: Color,
    val Danger: Color,
    val Success: Color,
    val Warning: Color,
    val Scrim: Color,
    // 语法高亮
    val CodeKeyword: Color,
    val CodeString: Color,
    val CodeComment: Color,
    val CodeNumber: Color,
    val CodeAnnotation: Color,
    val CodeTag: Color
)

val DarkPalette = SunPalette(
    dark = true,
    Bg = Color(0xFF0F1318),
    Surface1 = Color(0xFF12171D),
    Surface2 = Color(0xFF151B22),
    Surface3 = Color(0xFF181F27),
    Surface4 = Color(0xFF1C242D),
    Hairline = Color(0xFF1D2530),
    TextPrimary = Color(0xFFE8EDF2),
    TextSecondary = Color(0xFF98A2AE),
    TextTertiary = Color(0xFF67717D),
    Accent = Color(0xFF4B8DF8),
    AccentSoft = Color(0x294B8DF8),
    AccentLine = Color(0xFF2B5FA8),
    Folder = Color(0xFFD9A93F),
    File = Color(0xFF7E8895),
    Danger = Color(0xFFE5534B),
    Success = Color(0xFF3FB950),
    Warning = Color(0xFFD9A93F),
    Scrim = Color(0xCC000000),
    CodeKeyword = Color(0xFFC792EA),
    CodeString = Color(0xFF9ECE6A),
    CodeComment = Color(0xFF5F6B76),
    CodeNumber = Color(0xFFE0AF68),
    CodeAnnotation = Color(0xFF7DCFFF),
    CodeTag = Color(0xFF7DCFFF)
)

val LightPalette = SunPalette(
    dark = false,
    Bg = Color(0xFFF7F8FA),
    Surface1 = Color(0xFFFFFFFF),
    Surface2 = Color(0xFFF2F4F7),
    Surface3 = Color(0xFFEAEDF1),
    Surface4 = Color(0xFFFFFFFF),
    Hairline = Color(0xFFE1E5EA),
    TextPrimary = Color(0xFF16191D),
    TextSecondary = Color(0xFF59636D),
    TextTertiary = Color(0xFF8B959F),
    Accent = Color(0xFF2563EB),
    AccentSoft = Color(0x1F2563EB),
    AccentLine = Color(0xFF93B4F5),
    Folder = Color(0xFFC0841B),
    File = Color(0xFF6B7580),
    Danger = Color(0xFFD1362D),
    Success = Color(0xFF1A7F37),
    Warning = Color(0xFFB7790B),
    Scrim = Color(0x66000000),
    CodeKeyword = Color(0xFFA626A4),
    CodeString = Color(0xFF3E8E41),
    CodeComment = Color(0xFF9AA1A9),
    CodeNumber = Color(0xFF9A6700),
    CodeAnnotation = Color(0xFF1D6FD0),
    CodeTag = Color(0xFFC13B2E)
)

private val LocalSunPalette = staticCompositionLocalOf { DarkPalette }

/** 全站取色入口：`SunColors.Accent` 在暗/亮主题下自动切换 */
val SunColors: SunPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalSunPalette.current

/**
 * 动效令牌：进场缓出、退场缓入、页面内移动缓入缓出。
 * 时长统一走 150 / 250 / 300 / 380 / 520 五档，全站复用。
 */
object Motion {
    const val Fast = 150
    const val Base = 250
    const val Slow = 300
    const val Reveal = 380
    const val Page = 520

    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

    /** 弹性到位：轻微过冲，用于浮层出现 */
    fun pop() = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessLow
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
fun SunDocumentTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.Accent,
            onPrimary = Color.White,
            primaryContainer = palette.AccentLine,
            onPrimaryContainer = Color.White,
            background = palette.Bg,
            onBackground = palette.TextPrimary,
            surface = palette.Surface2,
            onSurface = palette.TextPrimary,
            surfaceVariant = palette.Surface3,
            onSurfaceVariant = palette.TextSecondary,
            surfaceContainer = palette.Surface3,
            surfaceContainerHigh = palette.Surface4,
            outline = palette.Hairline,
            error = palette.Danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.Accent,
            onPrimary = Color.White,
            primaryContainer = palette.AccentLine,
            onPrimaryContainer = Color.White,
            background = palette.Bg,
            onBackground = palette.TextPrimary,
            surface = palette.Surface1,
            onSurface = palette.TextPrimary,
            surfaceVariant = palette.Surface3,
            onSurfaceVariant = palette.TextSecondary,
            surfaceContainer = palette.Surface1,
            surfaceContainerHigh = palette.Surface4,
            outline = palette.Hairline,
            error = palette.Danger,
            onError = Color.White
        )
    }
    CompositionLocalProvider(LocalSunPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SunTypography,
            content = content
        )
    }
}
