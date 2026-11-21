package com.suibian.sunfile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suibian.sunfile.ui.theme.SunColors

/**
 * 空状态插画。
 *
 * 这是整份设计里唯一允许"二次元"的地方：空文件夹、搜不到、出错。
 * 之所以放在这里，是因为这三屏本来就没有信息要读，情绪是有空位的；
 * 一旦列表里有内容，插画就必须让位给文件名。
 *
 * 全部用 Canvas 画，不引入图片资源：换主题色时插画跟着变，也不用为
 * 每个 dpi 出图。笔触统一 2px 圆角线性 + 小高光。
 */
enum class EmptyMood { FOLDER, SEARCH, ERROR }

@Composable
fun EmptyArt(
    mood: EmptyMood,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp
) {
    val accent = SunColors.Accent
    val neon = SunColors.Neon
    val soft = SunColors.AccentSoft
    val star = SunColors.Star
    val pink = SunColors.AccentPink
    val line = SunColors.TextTertiary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // 底盘：一圈不到 10% 的淡蓝，把插画和背景轻轻托住
        drawCircle(
            color = soft,
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.54f)
        )
        when (mood) {
            EmptyMood.FOLDER -> drawFolder(w, h, accent, neon, star, pink)
            EmptyMood.SEARCH -> drawSearch(w, h, accent, neon, line, star)
            EmptyMood.ERROR -> drawError(w, h, accent, neon, star, pink)
        }
    }
}

/** 文件夹：主体三段式，右上角一颗星，表示"这里可以放东西" */
private fun DrawScope.drawFolder(
    w: Float, h: Float,
    accent: Color, neon: Color, star: Color, pink: Color
) {
    val left = w * 0.22f
    val right = w * 0.78f
    val top = h * 0.40f
    val bottom = h * 0.76f
    val tabRight = w * 0.50f

    // 后板（略高，露出标签页的厚度）
    val back = Path().apply {
        moveTo(left, top - h * 0.06f)
        quadraticBezierTo(left, top - h * 0.10f, left + w * 0.05f, top - h * 0.10f)
        lineTo(tabRight, top - h * 0.10f)
        lineTo(tabRight + w * 0.06f, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
    drawPath(back, color = accent.copy(alpha = 0.30f))

    // 前板：蓝到青的渐变，只在这一块出现
    val front = Path().apply {
        moveTo(left, top + h * 0.05f)
        quadraticBezierTo(left, top, left + w * 0.04f, top)
        lineTo(tabRight - w * 0.02f, top)
        lineTo(tabRight + w * 0.03f, top + h * 0.06f)
        lineTo(right - w * 0.03f, top + h * 0.06f)
        quadraticBezierTo(right, top + h * 0.06f, right, top + h * 0.12f)
        lineTo(right, bottom - h * 0.05f)
        quadraticBezierTo(right, bottom, right - w * 0.04f, bottom)
        lineTo(left + w * 0.04f, bottom)
        quadraticBezierTo(left, bottom, left, bottom - h * 0.05f)
        close()
    }
    drawPath(
        front,
        brush = Brush.verticalGradient(
            listOf(accent, neon),
            startY = top,
            endY = bottom
        )
    )
    // 顶边一道 12% 白光，模拟塑料件的高光
    drawPath(
        front,
        color = Color.White.copy(alpha = 0.16f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.012f)
    )

    drawSparkle(Offset(w * 0.78f, h * 0.26f), w * 0.075f, star)
    drawSparkle(Offset(w * 0.30f, h * 0.20f), w * 0.045f, pink.copy(alpha = 0.85f))
    drawCircle(neon.copy(alpha = 0.55f), w * 0.012f, Offset(w * 0.62f, h * 0.17f))
}

/** 搜索：放大镜 + 一条被划掉的空列表 */
private fun DrawScope.drawSearch(
    w: Float, h: Float,
    accent: Color, neon: Color, line: Color, star: Color
) {
    val cx = w * 0.46f
    val cy = h * 0.48f
    val r = w * 0.19f
    drawCircle(
        color = accent.copy(alpha = 0.10f),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = accent,
        radius = r,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.024f)
    )
    // 手柄
    drawLine(
        color = accent,
        start = Offset(cx + r * 0.72f, cy + r * 0.72f),
        end = Offset(cx + r * 1.45f, cy + r * 1.45f),
        strokeWidth = w * 0.030f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    // 镜片里三条横线：一条是青的，像正在匹配的那一行
    val lx = cx - r * 0.5f
    for (i in 0..2) {
        val y = cy - r * 0.38f + i * r * 0.38f
        drawLine(
            color = if (i == 1) neon else line.copy(alpha = 0.55f),
            start = Offset(lx, y),
            end = Offset(lx + r * (if (i == 2) 0.6f else 1.0f), y),
            strokeWidth = w * 0.016f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
    drawSparkle(Offset(w * 0.74f, h * 0.30f), w * 0.06f, star)
}

/** 出错：一个倾斜的警示角 + 一粒红点，不画大红叉 */
private fun DrawScope.drawError(
    w: Float, h: Float,
    accent: Color, neon: Color, star: Color, pink: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.52f
    val r = w * 0.21f
    val tri = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.98f, cy + r * 0.72f)
        quadraticBezierTo(cx, cy + r * 0.92f, cx - r * 0.98f, cy + r * 0.72f)
        close()
    }
    drawPath(
        tri,
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.85f), neon.copy(alpha = 0.75f)),
            startY = cy - r,
            endY = cy + r
        )
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(cx - w * 0.010f, cy - r * 0.30f),
        size = Size(w * 0.020f, r * 0.62f),
        cornerRadius = CornerRadius(w * 0.01f)
    )
    drawCircle(Color.White.copy(alpha = 0.92f), w * 0.014f, Offset(cx, cy + r * 0.50f))
    drawSparkle(Offset(w * 0.78f, h * 0.26f), w * 0.05f, star)
    drawCircle(pink.copy(alpha = 0.6f), w * 0.012f, Offset(w * 0.24f, h * 0.32f))
}

/** 四角星：内半径压到 0.26，尖角才利落，不会糊成圆点 */
private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val inner = r * 0.26f
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticBezierTo(center.x + inner * 0.4f, center.y - inner * 0.4f, center.x + r, center.y)
        quadraticBezierTo(center.x + inner * 0.4f, center.y + inner * 0.4f, center.x, center.y + r)
        quadraticBezierTo(center.x - inner * 0.4f, center.y + inner * 0.4f, center.x - r, center.y)
        quadraticBezierTo(center.x - inner * 0.4f, center.y - inner * 0.4f, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}
