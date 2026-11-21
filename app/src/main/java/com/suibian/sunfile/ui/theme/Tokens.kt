package com.suibian.sunfile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
    val CodeTag: Color,
    /** 顶部导航栏底色：始终比页面底色更暗，形成「栏 / 面」两层 */
    val TopBar: Color = Bg,
    /** 顶栏前景：两种模式下顶栏都是深色，文字固定用亮色 */
    val OnTopBar: Color = TextPrimary,
    val OnTopBarMuted: Color = TextSecondary,
    /** 双栏之间的高对比分割线 */
    val PaneDivider: Color = Hairline,
    /** 顶栏渐变起点/终点：两者相同则画成纯色，避免无谓的分支 */
    val TopBarGradientStart: Color = TopBar,
    val TopBarGradientEnd: Color = TopBar
)

val DarkPalette = SunPalette(
    dark = true,
    // 页面底色 #303030：中性灰，不偏蓝也不偏暖。
    // 与顶栏 #151515 形成明确的两层结构，长时间阅读不刺眼。
    Bg = Color(0xFF303030),
    Surface1 = Color(0xFF383839),
    Surface2 = Color(0xFF3D3D3F),
    Surface3 = Color(0xFF454547),
    Surface4 = Color(0xFF4E4E50),
    // 行内细分割：比底色略亮，用于列表行之间
    Hairline = Color(0xFF3C3C3E),
    TextPrimary = Color(0xFFF2F3F5),
    TextSecondary = Color(0xFFAFB2B7),
    TextTertiary = Color(0xFF82858B),
    Accent = Color(0xFF4A90F7),
    AccentSoft = Color(0x334A90F7),
    AccentLine = Color(0xFF2F6AB8),
    Folder = Color(0xFFF0B23E),
    File = Color(0xFF8B949F),
    Danger = Color(0xFFE5534B),
    Success = Color(0xFF3FB950),
    Warning = Color(0xFFD9A93F),
    Scrim = Color(0xCC000000),
    CodeKeyword = Color(0xFFC792EA),
    CodeString = Color(0xFF9ECE6A),
    CodeComment = Color(0xFF6B7682),
    CodeNumber = Color(0xFFE0AF68),
    CodeAnnotation = Color(0xFF7DCFFF),
    CodeTag = Color(0xFF7DCFFF),
    TopBar = Color(0xFF151515),
    OnTopBar = Color(0xFFF5F6F8),
    OnTopBarMuted = Color(0xFF9DA1A7),
    // 深灰底上用近黑细线：两栏一眼分开，不靠彩色
    PaneDivider = Color(0xFF101010)
)

val LightPalette = SunPalette(
    dark = false,
    // 页面底色 #FAFAFA：接近纯白但不反光过曝，长时间阅读更稳。
    Bg = Color(0xFFFAFAFA),
    Surface1 = Color(0xFFFFFFFF),
    Surface2 = Color(0xFFF2F3F5),
    Surface3 = Color(0xFFE9EBEE),
    Surface4 = Color(0xFFFFFFFF),
    Hairline = Color(0xFFE3E5E9),
    TextPrimary = Color(0xFF111315),
    TextSecondary = Color(0xFF565B62),
    TextTertiary = Color(0xFF8B9098),
    Accent = Color(0xFF1565D8),
    AccentSoft = Color(0x1F1565D8),
    AccentLine = Color(0xFFA8C4F0),
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
    CodeTag = Color(0xFFC13B2E),
    // 白天的顶栏同样是深灰 #303030：内容区亮、控制区暗，层级一眼可辨
    TopBar = Color(0xFF303030),
    OnTopBar = Color(0xFFF5F6F8),
    OnTopBarMuted = Color(0xFFB9BCC1),
    // 近白画布上的深色切割线，秩序感来自这条线本身
    PaneDivider = Color(0xFF2B2B2B)
)


/**
 * 二次元色板。
 *
 * 关键是「半透明」：所有面颜色留 15%–25% 的透明度，让壁纸从面板底下透出来，
 * 形成玻璃卡片浮在插画上的层次。透明度压太狠会失去可读性，所以文字色
 * 单独提到接近纯白/纯黑，并且默认叠一层暗化遮罩来兜底对比度。
 */
val AnimeDark = SunPalette(
    dark = true,
    Bg = Color(0x8C140F1F),
    Surface1 = Color(0xA61C1629),
    Surface2 = Color(0xB8231B33),
    Surface3 = Color(0xCC2B2140),
    Surface4 = Color(0xE633274D),
    Hairline = Color(0x33FFD6E8),
    TextPrimary = Color(0xFFFBF3FF),
    TextSecondary = Color(0xFFCDBFE0),
    TextTertiary = Color(0xFF9D8FB8),
    Accent = Color(0xFFFF7EB6),
    AccentSoft = Color(0x45FF7EB6),
    AccentLine = Color(0x8CFF7EB6),
    Folder = Color(0xFFFFC978),
    File = Color(0xFFA294C0),
    Danger = Color(0xFFFF6B8A),
    Success = Color(0xFF6FE3B8),
    Warning = Color(0xFFFFD36E),
    Scrim = Color(0xB3000000),
    CodeKeyword = Color(0xFFFF9ED2),
    CodeString = Color(0xFF9EE6C8),
    CodeComment = Color(0xFF8A7CA6),
    CodeNumber = Color(0xFFFFC978),
    CodeAnnotation = Color(0xFF9BD8FF),
    CodeTag = Color(0xFFB9A6FF),
    TopBar = Color(0x99100B18),
    OnTopBar = Color(0xFFFDF6FF),
    OnTopBarMuted = Color(0xFFC3B4D6),
    PaneDivider = Color(0x40FF7EB6),
    TopBarGradientStart = Color(0xCC2C1B44),
    TopBarGradientEnd = Color(0xB3100B18)
)

val AnimeLight = SunPalette(
    dark = false,
    Bg = Color(0xB8FFF6FB),
    Surface1 = Color(0xCCFFFFFF),
    Surface2 = Color(0xD9FDF1F8),
    Surface3 = Color(0xE6F7E6F2),
    Surface4 = Color(0xFFFFFFFF),
    Hairline = Color(0x269B6F92),
    TextPrimary = Color(0xFF2A1F35),
    TextSecondary = Color(0xFF5F4E70),
    TextTertiary = Color(0xFF95859F),
    Accent = Color(0xFFE2558F),
    AccentSoft = Color(0x2EE2558F),
    AccentLine = Color(0xFFF3B8D0),
    Folder = Color(0xFFE89B3C),
    File = Color(0xFF7C6B90),
    Danger = Color(0xFFD63E5C),
    Success = Color(0xFF1F9E76),
    Warning = Color(0xFFB87B12),
    Scrim = Color(0x66000000),
    CodeKeyword = Color(0xFFC2185B),
    CodeString = Color(0xFF2E7D5B),
    CodeComment = Color(0xFF9C8CA8),
    CodeNumber = Color(0xFFB26A00),
    CodeAnnotation = Color(0xFF3B6FD4),
    CodeTag = Color(0xFFD13B6B),
    TopBar = Color(0xC7F7E9F3),
    OnTopBar = Color(0xFF2A1F35),
    OnTopBarMuted = Color(0xFF6B5878),
    PaneDivider = Color(0x40E2558F),
    TopBarGradientStart = Color(0xE6FFE3F3),
    TopBarGradientEnd = Color(0xCCEFE3FF)
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

/**
 * 形状与度量的当前模板。
 *
 * 这里用「可绑定的全局只读视图」而不是 CompositionLocal 直取，原因有二：
 * 1. `Modifier.clip(SunShapes.Small)` 这类调用散落在 80 多处，很多在非
 *    @Composable 的普通函数里，改成 composable getter 会连锁改一大片；
 * 2. 模板切换只会发生在主题重建时，`SunDocumentTheme` 先于子组件执行，
 *    绑定新模板后整棵子树重组，读到的自然就是新值。
 */
private object SkinHolder {
    @Volatile
    var spec: SkinSpec = Skins.Anime
}

/** 当前模板（需要读更多字段时用这个） */
val LocalSkin = staticCompositionLocalOf { Skins.Anime }

/** 取当前模板，等价于 `LocalSkin.current` */
val SunSkin: SkinSpec
    @Composable
    @ReadOnlyComposable
    get() = LocalSkin.current

object SunShapes {
    val Small get() = SkinHolder.spec.small
    val Medium get() = SkinHolder.spec.medium
    val Large get() = SkinHolder.spec.large
    val Sheet get() = SkinHolder.spec.sheet
    val Pill get() = SkinHolder.spec.pill
    val Dialog get() = SkinHolder.spec.dialog
    val Tile get() = SkinHolder.spec.tile
}

/**
 * 全站共用的一套度量。
 *
 * 文件列表、顶栏、底栏、全屏页读的是同一份数字：一旦各页面自己定行高，
 * 半栏宽度下就会出现「标题不如正文醒目」的错位，所以只在这里定义一次。
 */
object Mt {
    private val s get() = SkinHolder.spec

    val BarHeight get() = s.barHeight
    val BottomHeight get() = s.bottomHeight
    val RowHeight get() = s.rowHeight
    val RowIcon get() = s.rowIcon
    val RowGutter get() = s.rowGutter

    /** 顶栏主标题：路径本身就是这一页的名字 */
    val Title get() = s.title
    /** 顶栏副标题：文件夹数 / 文件数 / 存储 */
    val BarMeta get() = s.barMeta
    /** 列表行主文字 */
    val Name get() = s.nameSize
    /** 列表行元信息（时间、大小） */
    val Meta get() = s.meta

    /** 路径越长标题越小，保证深路径也能读到尾部目录名 */
    fun titleSize(length: Int): androidx.compose.ui.unit.TextUnit {
        val base = s.title.value
        return when {
            length <= 22 -> base.sp
            length <= 30 -> (base - 2f).sp
            length <= 40 -> (base - 4f).sp
            else -> (base - 5f).sp
        }
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
    accent: Long = 0L,
    skinId: String = Skins.Classic.id,
    content: @Composable () -> Unit
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val skin = remember(skinId) { Skins.byId(skinId) }
    // 先绑定模板，再渲染子树：任何一处读 SunShapes / Mt 都是新值
    SkinHolder.spec = skin
    // 二次元模板有自己的一套半透明色板，其余模板共用中性色板
    val base = when {
        skin.anime -> if (dark) AnimeDark else AnimeLight
        dark -> DarkPalette
        else -> LightPalette
    }
    // 用户在设置里挑了主题色就替换强调色，其余语义色不动
    val palette = if (accent != 0L) {
        val c = Color(accent)
        base.copy(
            Accent = c,
            AccentSoft = c.copy(alpha = if (dark) 0.20f else 0.12f),
            AccentLine = c.copy(alpha = if (dark) 0.55f else 0.35f)
        )
    } else base
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
    CompositionLocalProvider(LocalSunPalette provides palette, LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SunTypography,
            shapes = MaterialTheme.shapes.copy(
                extraSmall = skin.small,
                small = skin.small,
                medium = skin.medium,
                large = skin.large,
                extraLarge = skin.sheet
            ),
            content = {
                // 换模板时整棵子树重建：SunShapes / Mt 是全局只读视图，
                // 被跳过的子树不会自己重读，用 key 保证新形状一定落到每一行上。
                key(skin.id) { content() }
            }
        )
    }
}


/**
 * 顶栏底色。
 *
 * 中性模板的两个渐变端同色，画出来就是原来的纯色；二次元模板给出粉紫渐变。
 * 统一走这一个入口，避免十几个页面各自判断一次主题。
 */
@Composable
fun Modifier.topBarBackground(): Modifier = this.background(
    Brush.verticalGradient(
        listOf(
            LocalSunPalette.current.TopBarGradientStart,
            LocalSunPalette.current.TopBarGradientEnd
        )
    )
)
