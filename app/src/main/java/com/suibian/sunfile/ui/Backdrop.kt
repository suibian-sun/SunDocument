package com.suibian.sunfile.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.Backdrop
import com.suibian.sunfile.ui.theme.SunColors

/**
 * 全局壁纸层，铺在所有页面的最底下。
 *
 * 上面的页面本身是半透明的（二次元模板的面颜色带 alpha），
 * 所以这里只负责三件事：画壁纸、按设置模糊、叠一层遮罩保证文字对比度。
 */
@Composable
fun AppBackdrop(
    source: String,
    bust: Long,
    blurDp: Int,
    dimPercent: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dark = SunColors.dark
    val state by Backdrop.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(source, bust) {
        if (source.isBlank()) Backdrop.clear() else Backdrop.load(context, source, bust)
    }

    Box(modifier.fillMaxSize()) {
        // 没有图、或图还没到时用粉紫渐变兜底：二次元模板不能退化成一片灰
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (dark) {
                        Brush.linearGradient(
                            listOf(Color(0xFF2A1B3D), Color(0xFF15203A), Color(0xFF3A1B33))
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color(0xFFFFE7F3), Color(0xFFE9E4FF), Color(0xFFE3F6FF))
                        )
                    }
                )
        )

        val bmp = state.bitmap
        if (bmp != null) {
            val image = bmp.asImageBitmap()
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // Android 12 起高斯模糊走 RenderEffect，成本可接受；
                        // 更早的版本没有这个能力，改成压低不透明度，让壁纸退成雾面
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(blurDp.dp)
                        } else {
                            Modifier.alpha(0.4f)
                        }
                    )
            )
        }

        // 遮罩：深色压暗、浅色提亮，两边都是为了让面板上的文字保持在 AA 对比度以上
        val veil = if (dark) {
            Color.Black.copy(alpha = (dimPercent / 100f).coerceIn(0f, 0.85f))
        } else {
            Color.White.copy(alpha = (dimPercent / 100f * 0.75f).coerceIn(0f, 0.8f))
        }
        Box(Modifier.fillMaxSize().background(veil))
    }
}

/** 模糊圆角为 0 时完全不碰绘制层，避免无谓的离屏渲染 */
@Suppress("unused")
private fun Modifier.softBlur(radius: Dp): Modifier =
    if (radius.value <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) this
    else this.blur(radius)
