package com.suibian.sunfile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 界面模板（Skin）。
 *
 * 早期版本把行高、圆角、字号直接写死在各页面里，想换一种观感必须逐处改数字，
 * 结果就是「每个页面都对，但整机不一致」。这里把一整套视觉决策收拢成数据：
 * 形状、密度、字号、分割线、按压反馈、层级表达、图标着色，全部由模板给出。
 *
 * 页面只读模板，不再自己决定像素。
 */

/** 列表行之间的分割方式 */
enum class DividerMode(val label: String, val desc: String) {
    NONE("无", "靠留白与层级区分，行与行不画线"),
    EDGE("通栏", "细线从屏幕左缘贯到右缘"),
    INSET("内缩", "细线从文字左缘起，让图标列保持干净")
}

/** 按下时的反馈 */
enum class PressMode(val label: String, val desc: String) {
    RIPPLE("水波纹", "Material 默认扩散"),
    TINT("高亮", "整行纯色压暗，无扩散"),
    SCALE("缩放", "整行轻微回弹")
}

/** 层级怎么表达 */
enum class SurfaceMode(val label: String, val desc: String) {
    FLAT("扁平", "无色差无阴影，只靠分割线"),
    LAYERED("层级", "相邻面 1%–3% 明度差，浮层自带光影"),
    CARD("卡片", "内容装进圆角卡片，卡片之间留缝")
}

/** 文件图标的着色 */
enum class IconTintMode(val label: String, val desc: String) {
    MONO("单色", "全部同色，最克制"),
    TYPE("分类型", "文件夹黄、文件灰、安装包绿")
}

@Immutable
data class SkinSpec(
    val id: String,
    val name: String,
    val note: String,

    // ---- 形状 ----
    val dialog: RoundedCornerShape,
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
    val sheet: RoundedCornerShape,
    val tile: RoundedCornerShape,
    val pill: RoundedCornerShape,

    // ---- 密度 ----
    val barHeight: Dp,
    val bottomHeight: Dp,
    val rowHeight: Dp,
    val rowIcon: Dp,
    val rowGutter: Dp,

    // ---- 字号 ----
    val title: TextUnit,
    val barMeta: TextUnit,
    val nameSize: TextUnit,
    val meta: TextUnit,

    // ---- 表达 ----
    val divider: DividerMode,
    val press: PressMode,
    val surface: SurfaceMode,
    val iconTint: IconTintMode,
    val nameWeight: FontWeight,
    val letterGap: TextUnit,
    val metaMono: Boolean,
    /** 选中行左侧的强调竖条 */
    val selectionBar: Boolean,
    /** 使用二次元半透明色板（面颜色带透明度，壁纸从底下透出） */
    val anime: Boolean = false
)

object Skins {

    /**
     * 二次元（默认）：蓝系工具界面。
     *
     * 信息区按高密度工具做（白卡片、54dp 行高、内缩细线），
     * 二次元的情绪全部退到空状态插画、设置页头图和图标描边里。
     */
    val Anime = SkinSpec(
        id = "anime",
        name = "二次元",
        note = "蓝系 · 白卡片 · 内缩细线",
        dialog = RoundedCornerShape(18.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp),
        sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tile = RoundedCornerShape(20.dp),
        pill = RoundedCornerShape(50),
        barHeight = 56.dp,
        bottomHeight = 56.dp,
        rowHeight = 54.dp,
        rowIcon = 30.dp,
        rowGutter = 14.dp,
        title = 17.sp,
        barMeta = 10.5.sp,
        nameSize = 14.5.sp,
        meta = 10.sp,
        divider = DividerMode.INSET,
        press = PressMode.TINT,
        surface = SurfaceMode.CARD,
        iconTint = IconTintMode.TYPE,
        nameWeight = FontWeight.Medium,
        letterGap = 0.15.sp,
        metaMono = false,
        selectionBar = false,
        anime = true
    )

    /**
     * 专业文件管理器观感：紧凑、直角、细线、克制。
     * 48dp 行高 + 24dp 图标 + 内缩分割线，一屏能看下 12 条以上。
     */
    val Classic = SkinSpec(
        id = "classic",
        name = "经典",
        note = "紧凑 · 直角 · 内缩细线",
        dialog = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        sheet = RoundedCornerShape(0.dp),
        tile = RoundedCornerShape(4.dp),
        pill = RoundedCornerShape(50),
        barHeight = 48.dp,
        bottomHeight = 46.dp,
        rowHeight = 48.dp,
        rowIcon = 24.dp,
        rowGutter = 10.dp,
        title = 16.sp,
        barMeta = 10.sp,
        nameSize = 14.sp,
        meta = 9.5.sp,
        divider = DividerMode.INSET,
        press = PressMode.RIPPLE,
        surface = SurfaceMode.FLAT,
        iconTint = IconTintMode.TYPE,
        nameWeight = FontWeight.Normal,
        letterGap = 0.sp,
        metaMono = false,
        selectionBar = true
    )

    /**
     * 层级光影：去掉所有分割线，用 1%–3% 的明度差把栏、面、浮层分开。
     * 行更高、留白更多，适合长时间翻阅。
     */
    val Slate = SkinSpec(
        id = "slate",
        name = "层级",
        note = "无分割线 · 光影分层 · 单色图标",
        dialog = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(16.dp),
        sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tile = RoundedCornerShape(10.dp),
        pill = RoundedCornerShape(50),
        barHeight = 52.dp,
        bottomHeight = 52.dp,
        rowHeight = 52.dp,
        rowIcon = 30.dp,
        rowGutter = 14.dp,
        title = 17.sp,
        barMeta = 10.5.sp,
        nameSize = 14.5.sp,
        meta = 10.sp,
        divider = DividerMode.NONE,
        press = PressMode.TINT,
        surface = SurfaceMode.LAYERED,
        iconTint = IconTintMode.MONO,
        nameWeight = FontWeight.Normal,
        letterGap = 0.1.sp,
        metaMono = false,
        selectionBar = false
    )

    /**
     * 纸墨：接近印刷品的黑白秩序，通栏细线像稿纸，元信息用等宽字体对齐。
     * 适合代码、日志、清单类内容。
     */
    val Paper = SkinSpec(
        id = "paper",
        name = "纸墨",
        note = "高对比 · 通栏细线 · 等宽元信息",
        dialog = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        sheet = RoundedCornerShape(0.dp),
        tile = RoundedCornerShape(2.dp),
        pill = RoundedCornerShape(50),
        barHeight = 56.dp,
        bottomHeight = 48.dp,
        rowHeight = 54.dp,
        rowIcon = 22.dp,
        rowGutter = 14.dp,
        title = 15.sp,
        barMeta = 9.5.sp,
        nameSize = 14.5.sp,
        meta = 10.sp,
        divider = DividerMode.EDGE,
        press = PressMode.RIPPLE,
        surface = SurfaceMode.FLAT,
        iconTint = IconTintMode.MONO,
        nameWeight = FontWeight.Medium,
        letterGap = 0.25.sp,
        metaMono = true,
        selectionBar = true
    )

    /** 圆角柔和：卡片式、行更高、图标更大，触屏第一。 */
    val Soft = SkinSpec(
        id = "soft",
        name = "柔和",
        note = "大圆角 · 卡片 · 大图标",
        dialog = RoundedCornerShape(20.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tile = RoundedCornerShape(18.dp),
        pill = RoundedCornerShape(50),
        barHeight = 60.dp,
        bottomHeight = 58.dp,
        rowHeight = 58.dp,
        rowIcon = 34.dp,
        rowGutter = 16.dp,
        title = 18.sp,
        barMeta = 11.sp,
        nameSize = 15.sp,
        meta = 10.5.sp,
        divider = DividerMode.NONE,
        press = PressMode.SCALE,
        surface = SurfaceMode.CARD,
        iconTint = IconTintMode.TYPE,
        nameWeight = FontWeight.Normal,
        letterGap = 0.sp,
        metaMono = false,
        selectionBar = false
    )

    val all: List<SkinSpec> = listOf(Anime, Classic, Slate, Paper, Soft)

    fun byId(id: String?): SkinSpec =
        all.firstOrNull { it.id == id } ?: Anime
}
