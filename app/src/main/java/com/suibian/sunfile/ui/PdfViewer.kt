package com.suibian.sunfile.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 用系统自带的 PdfRenderer 渲染，不引第三方库，也不跳浏览器。
 * 页面按需渲染：滑到哪页才渲哪页，长文档也不会一次吃掉几百 MB。
 */
@Composable
fun PdfViewerOverlay(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    var error by remember(file) { mutableStateOf<String?>(null) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    val lock = remember(file) { Mutex() }

    DisposableEffect(file) {
        var descriptor: ParcelFileDescriptor? = null
        val opened = runCatching {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor!!)
        }
        opened.onSuccess {
            renderer = it
            pageCount = it.pageCount
        }.onFailure {
            error = when {
                it is java.io.IOException && (it.message ?: "").contains("password", true) -> "这个 PDF 有密码"
                else -> it.message ?: "无法打开这个 PDF"
            }
        }
        onDispose {
            runCatching { renderer?.close() }
            renderer = null
            runCatching { descriptor?.close() }
        }
    }

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .height(Mt.BarHeight)
                .background(SunColors.Surface1)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = SunColors.TextPrimary,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        error != null -> "打不开"
                        pageCount == 0 -> "读取中…"
                        else -> "$pageCount 页 · ${com.suibian.sunfile.util.Fmt.size(file.length())}"
                    },
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }

        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = SunColors.TextSecondary, fontSize = 12.5.sp)
            }

            renderer == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }

            else -> {
                val screenWidth = LocalConfiguration.current.screenWidthDp
                val widthPx = (screenWidth * context.resources.displayMetrics.density * 1.2f).toInt()
                    .coerceIn(720, 2000)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pageCount) { index ->
                        PdfPage(renderer, lock, index, widthPx)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer?, lock: Mutex, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, renderer, index, widthPx) {
        if (renderer == null) return@produceState
        value = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat()
                        val w = widthPx
                        val h = (w * ratio).toInt().coerceAtLeast(1)
                        // PDF 页面是透明底，先填白再画，否则暗色模式下会变成一片黑
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()
            }
        }
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = SunColors.TextTertiary, strokeWidth = 1.5.dp)
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "第 ${index + 1} 页",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.White)
        )
        Spacer(Modifier.height(2.dp))
    }
}
