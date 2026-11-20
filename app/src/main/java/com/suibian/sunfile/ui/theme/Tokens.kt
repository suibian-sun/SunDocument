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
    // MT 的深色：近黑底 + 一支偏冷的灰阶，强调色是饱和度较高的蓝
    Bg = Color(0xFF0E0E0F),
    Surface1 = Color(0xFF1A1A1B),
    Surface2 = Color(0xFF212122),
    Surface3 = Color(0xFF28282A),
    Surface4 = Color(0xFF323234),
    Hairline = Color(0xFF2C2C2E),
    TextPrimary = Color(0xFFE6E8EA),
    TextSecondary = Color(0xFF9A9DA1),
    TextTertiary = Color(0xFF6E7176),
    Accent = Color(0xFF3B86F7),
    AccentSoft = Color(0x333B86F7),
    AccentLine = Color(0xFF2C5F9E),
    Folder = Color(0xFFF0B23E),
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
    Bg = Color(0xFFFFFFFF),
    Surface1 = Color(0xFFFFFFFF),
    Surface2 = Color(0xFFF6F7F8),
    Surface3 = Color(0xFFEDEEF0),
    Surface4 = Color(0xFFFFFFFF),
    Hairline = Color(0xFFE8E9EB),
    TextPrimary = Color(0xFF1A1B1D),
    TextSecondary = Color(0xFF8A8D91),
    TextTertiary = Color(0xFFA0A3A7),
    Accent = Color(0xFF1E6FD9),
    AccentSoft = Color(0x1F1E6FD9),
    AccentLine = Color(0xFFAECBF5),
    Folder = Color(0xFFE9A63C),
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
 * 整体节拍比常见默认值慢一档，让转场能看清「从哪来、到哪去」。
 */
object Motion {
    const val Fast = 180
    const val Base = 300
    const val Slow = 380
    const val Reveal = 460
    const val Page = 600

    val EaseIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.16f, 1f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.18f, 1f)
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
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    )
}

object SunShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = RoundedCornerShape(50)

    /** MT 的弹窗几乎是直角，只有 2dp 的收边 */
    val Dialog = RoundedCornerShape(3.dp)
    /** 打开方式里的彩色方块 */
    val Tile = RoundedCornerShape(15.dp)
}

/**
 * MT 管理器的基础度量。
 *
 * 文件列表、顶栏、底栏、全屏页共用同一套数字：一旦各页面自己写字号，
 * 半栏宽度下就会出现「标题不如正文醒目」的错位，所以这里集中一次。
 */
object Mt {
    val BarHeight = 56.dp
    val BottomHeight = 50.dp
    val RowHeight = 50.dp
    val RowIcon = 30.dp
    val RowGutter = 12.dp

    /** 顶栏主标题：路径本身就是这一页的名字 */
    val Title = 17.sp
    /** 顶栏副标题：文件夹数 / 文件数 / 存储 */
    val BarMeta = 10.5.sp
    /** 列表行主文字 */
    val Name = 14.sp
    /** 列表行元信息（时间、大小） */
    val Meta = 10.sp

    /** 路径越长标题越小，保证深路径也能读到尾部目录名 */
    fun titleSize(length: Int): androidx.compose.ui.unit.TextUnit = when {
        length <= 22 -> 17.sp
        length <= 30 -> 15.sp
        length <= 40 -> 13.sp
        else -> 12.sp
    }
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
