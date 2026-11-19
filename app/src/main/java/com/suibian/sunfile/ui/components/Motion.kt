package com.suibian.sunfile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.SunColors
import kotlin.math.hypot
import kotlin.math.min

/** 按压回弹：按下缩小、松手弹回，带一点过冲但不晃 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.9f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 汉堡 ↔ 叉：三条线平滑旋转收拢，不硬切 */
@Composable
fun HamburgerIcon(open: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(Motion.Slow, easing = Motion.EaseInOut),
        label = "hamburger"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val len = w * 0.52f
        val gap = h * 0.17f
        val stroke = 1.7.dp.toPx()
        // 上 / 下两条线：向中心收拢并旋转成叉
        val offsetY = gap * (1f - p)
        val angle = 45f * p
        rotate(angle, Offset(cx, cy - offsetY)) {
            drawLine(tint, Offset(cx - len / 2, cy - offsetY), Offset(cx + len / 2, cy - offsetY), stroke, StrokeCap.Round)
        }
        rotate(-angle, Offset(cx, cy + offsetY)) {
            drawLine(tint, Offset(cx - len / 2, cy + offsetY), Offset(cx + len / 2, cy + offsetY), stroke, StrokeCap.Round)
        }
        // 中间线淡出
        drawLine(
            tint.copy(alpha = (1f - p).coerceIn(0f, 1f) * 0.85f),
            Offset(cx - len / 2, cy),
            Offset(cx + len / 2, cy),
            stroke,
            StrokeCap.Round
        )
    }
}

/** 数字滚动：变化时上下滚动淡入淡出，等宽字体不抖宽 */
@Composable
fun RollingNumber(
    value: Int,
    style: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
    color: Color = SunColors.TextSecondary
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInVertically(tween(Motion.Base, easing = Motion.EaseOut)) { it / 2 } + fadeIn(tween(Motion.Base))) togetherWith
                    (slideOutVertically(tween(Motion.Base, easing = Motion.EaseIn)) { -it / 2 } + fadeOut(tween(Motion.Fast)))
            } else {
                (slideInVertically(tween(Motion.Base, easing = Motion.EaseOut)) { -it / 2 } + fadeIn(tween(Motion.Base))) togetherWith
                    (slideOutVertically(tween(Motion.Base, easing = Motion.EaseIn)) { it / 2 } + fadeOut(tween(Motion.Fast)))
            }
        },
        label = "roll"
    ) { v ->
        androidx.compose.material3.Text(text = v.toString(), style = style, color = color, maxLines = 1)
    }
}

/** 勾选一笔画出 */
@Composable
fun DrawCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    background: Color = SunColors.Accent
) {
    val p by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(if (checked) 220 else 120, easing = Motion.EaseOut),
        label = "check"
    )
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = background.copy(alpha = 0.18f + 0.82f * p), radius = r, center = c)
        if (p <= 0.01f) return@Canvas
        val path = Path().apply {
            moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
            lineTo(c.x - r * 0.08f, c.y + r * 0.34f)
            lineTo(c.x + r * 0.44f, c.y - r * 0.34f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val dst = Path()
        measure.getSegment(0f, measure.length * p, dst, true)
        drawPath(dst, color, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = r * 0.26f,
            cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
    }
}

/** 骨架屏：避免加载时页面高度跳动 */
@Composable
fun ShimmerList(rows: Int = 9) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )
    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { i ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(SunColors.Surface3)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBar(widthFactor = 0.66f, height = 9.dp, shift = shift)
                    Spacer(Modifier.height(7.dp))
                    ShimmerBar(widthFactor = 0.32f, height = 7.dp, shift = shift, delay = i * 0.03f)
                }
            }
        }
    }
}

@Composable
private fun ShimmerBar(widthFactor: Float, height: Dp, shift: Float, delay: Float = 0f) {
    val shimmerColor = SunColors.Surface4
    Box(
        Modifier
            .fillMaxWidth(widthFactor)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(SunColors.Surface2)
            .drawWithContent {
                drawContent()
                val w = size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        start = Offset(w * (shift - 0.4f + delay), 0f),
                        end = Offset(w * (shift + 0.4f + delay), size.height)
                    )
                )
            }
    )
}

/** 圆形扩散转场：新页面从点击位置向外铺满 */
@Composable
fun CircularReveal(
    origin: Offset,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(Motion.Reveal, easing = if (visible) Motion.EaseOut else Motion.EaseIn)
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                val scope = this
                val p = progress.value
                if (p >= 0.999f) {
                    scope.drawContent()
                } else if (p > 0.001f) {
                    val maxR = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 1.05f
                    val r = maxR * p
                    val path = Path().apply {
                        addOval(Rect(origin.x - r, origin.y - r, origin.x + r, origin.y + r))
                    }
                    clipPath(path) { scope.drawContent() }
                }
            }
    ) {
        content()
    }
}
