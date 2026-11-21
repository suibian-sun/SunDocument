package com.suibian.sunfile.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suibian.sunfile.data.FileKind
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunSkin
import com.suibian.sunfile.ui.theme.IconTintMode
import java.io.File

/** 其它地方仍按类型取线性图标 */
fun iconOf(file: File, isDir: Boolean = file.isDirectory): ImageVector {
    if (isDir) return Icons.Outlined.Folder
    val ext = FileKinds.extensionOf(file)
    return when (FileKinds.kindOf(file)) {
        FileKind.IMAGE -> Icons.Outlined.Image
        FileKind.AUDIO -> Icons.Outlined.Audiotrack
        FileKind.VIDEO -> Icons.Outlined.Movie
        FileKind.ARCHIVE -> Icons.Outlined.Archive
        FileKind.APK -> Icons.Outlined.Android
        FileKind.PDF -> Icons.Outlined.PictureAsPdf
        FileKind.OFFICE -> Icons.Outlined.Description
        FileKind.TEXT -> if (ext in CODE_EXT) Icons.Outlined.Code else Icons.Outlined.Description
        FileKind.BINARY -> Icons.Outlined.InsertDriveFile
        FileKind.DIR -> Icons.Outlined.Folder
    }
}

private val CODE_EXT = setOf(
    "kt", "kts", "java", "js", "mjs", "ts", "jsx", "tsx", "py", "c", "h", "cpp", "hpp", "cs",
    "go", "rs", "php", "rb", "swift", "sh", "bash", "smali", "lua", "dart", "sql", "json",
    "xml", "html", "css", "yml", "yaml", "gradle", "vue", "svelte"
)

@Composable
fun tintOf(file: File, isDir: Boolean = file.isDirectory): Color {
    // 单色模板：图标统一成灰度，只靠字形区分类型，列表更安静
    if (SunSkin.iconTint == IconTintMode.MONO) {
        return if (isDir) SunColors.TextSecondary else SunColors.File
    }
    if (isDir) return folderColor()
    return extColor(FileKinds.extensionOf(file), FileKinds.kindOf(file))
}

/** 文件夹主色：蓝系里唯一带一点紫的色，用来和图片、文档拉开距离 */
@Composable
@ReadOnlyComposable
fun folderColor(): Color = if (SunColors.dark) Color(0xFF6FB0F5) else Color(0xFF4A90E2)

/** 文件夹渐变的另一端：只做轻微偏移，不做发光 */
@Composable
@ReadOnlyComposable
fun folderColorEnd(): Color = if (SunColors.dark) Color(0xFF9B8CFF) else Color(0xFF7C7CFF)

/**
 * 文件类型色。
 *
 * 全套蓝系、低饱和、只上图标与小标签。刻意不给每个扩展名一个颜色 ——
 * 一屏几十行不同颜色撞在一起，文件管理器立刻变成廉价游戏。
 */
@Composable
@ReadOnlyComposable
fun extColor(ext: String, kind: FileKind): Color {
    val dark = SunColors.dark
    return when (kind) {
        FileKind.DIR -> folderColor()
        FileKind.IMAGE -> if (dark) Color(0xFF5CCBFB) else Color(0xFF38BDF8)
        FileKind.VIDEO -> if (dark) Color(0xFFA396FF) else Color(0xFF8B7CFF)
        FileKind.AUDIO -> if (dark) Color(0xFFFF9CC6) else Color(0xFFFF7EB6)
        FileKind.ARCHIVE -> if (dark) Color(0xFFFFBC73) else Color(0xFFFFA94D)
        // 安装包与未知类型共用中性蓝灰：它俩本来就不该抢注意力
        FileKind.APK, FileKind.BINARY -> if (dark) Color(0xFF8FA3BE) else Color(0xFF7B8CA6)
        // 代码走青色，普通文本走文档蓝：同一栏里一眼分得开，又不跳出蓝系
        FileKind.TEXT -> if (ext in CODE_EXT) {
            if (dark) Color(0xFF4FE0CE) else Color(0xFF2DD4BF)
        } else {
            if (dark) Color(0xFF6FA8F0) else Color(0xFF4C8FE0)
        }
        FileKind.OFFICE, FileKind.PDF ->
            if (dark) Color(0xFF5D93FF) else Color(0xFF2F6FED)
    }
}

/** 短标签：太长的扩展名取前 4 位，几个常见别名做归一 */
private fun labelOf(ext: String): String = when (ext) {
    "", "?" -> "?"
    "markdown" -> "MD"
    "jpeg", "jpe", "jfif" -> "JPG"
    "tiff" -> "TIF"
    "tar.gz" -> "TGZ"
    "tar.bz2" -> "TBZ"
    "gradle" -> "GRD"
    "properties" -> "PRP"
    "docx", "docm" -> "DOC"
    "xlsx", "xlsm" -> "XLS"
    "pptx", "pptm" -> "PPT"
    "yaml" -> "YML"
    "kotlin" -> "KT"
    else -> ext.uppercase().take(4)
}

/**
 * 文件图标（对齐 MT 管理器）：
 * 统一的「纸张」轮廓 + 折角，颜色与标签随扩展名变化；
 * 图片与视频直接显示首帧，不再只是类型色块。
 */
@Composable
fun FileTypeBadge(
    file: File,
    isDir: Boolean = file.isDirectory,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    if (isDir) {
        MaterialFileIcon(file.name, isDir = true, size = size, modifier = modifier)
        return
    }
    val kind = FileKinds.kindOfName(file.name)
    // 图片与视频仍优先给首帧，缩略图拿不到时回落到主题图标
    if (kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.AUDIO) {
        ThumbBadge(file, kind, size, modifier)
        return
    }
    MaterialFileIcon(file.name, isDir = false, size = size, modifier = modifier)
}

@Composable
private fun ThumbBadge(file: File, kind: FileKind, size: Dp, modifier: Modifier) {
    val thumbKind = when (kind) {
        FileKind.VIDEO -> ThumbKind.VIDEO
        FileKind.AUDIO -> ThumbKind.AUDIO
        else -> ThumbKind.IMAGE
    }
    val bmp = rememberThumb(file, thumbKind)
    if (bmp == null) {
        MaterialFileIcon(file.name, isDir = false, size = size, modifier = modifier)
        return
    }
    val shape = RoundedCornerShape(size * 0.18f)
    val fallback = extColor(FileKinds.extensionOf(file), FileKinds.kindOfName(file.name))
    Box(
        modifier.size(size).clip(shape).background(fallback.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (kind == FileKind.VIDEO) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(size * 0.06f)
                    .size(size * 0.34f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(size * 0.17f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val p = Path().apply {
                        moveTo(w * 0.2f, 0f)
                        lineTo(w * 0.95f, h * 0.5f)
                        lineTo(w * 0.2f, h)
                        close()
                    }
                    drawPath(p, Color.White)
                }
            }
        }
    }
}

/**
 * 文件图标：淡色圆角方块打底，上面压一张纸，扩展名用类型色写在纸上。
 *
 * 底色只给 13%（深色 22%）的透明度，方块的存在感来自形状而不是颜色 ——
 * 一屏几十个高饱和色块会把文件名彻底压住。
 */
@Composable
private fun PageBadge(label: String, color: Color, dotted: Boolean, modifier: Modifier) {
    val dark = SunColors.dark
    // 深色下不能用白纸：白卡片夹在藏蓝列表里像一块没关的灯
    val paper = if (dark) Color(0xFF101A2C) else Color.White
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color.copy(alpha = if (dark) 0.22f else 0.13f),
            cornerRadius = CornerRadius(w * 0.23f, w * 0.23f)
        )

        val left = w * 0.255f
        val right = w * 0.745f
        val top = h * 0.145f
        val bottom = h * 0.855f
        val fold = (right - left) * 0.36f

        val page = Path().apply {
            moveTo(left, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(page, paper)
        drawPath(
            page,
            color = color.copy(alpha = 0.22f),
            style = Stroke(width = w * 0.014f, join = StrokeJoin.Round)
        )
        // 折角：用类型色透出来，像纸被翻起
        drawPath(
            Path().apply {
                moveTo(right - fold, top)
                lineTo(right - fold, top + fold)
                lineTo(right, top + fold)
                close()
            },
            color.copy(alpha = 0.30f)
        )

        if (dotted) {
            val stroke = h * 0.048f
            for (i in 0..2) {
                val y = top + h * (0.235f + i * 0.125f)
                drawLine(
                    color = color.copy(alpha = 0.78f),
                    start = Offset(left + w * 0.05f, y),
                    end = Offset(right - w * (if (i == 2) 0.10f else 0.05f), y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            return@Canvas
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            this.color = color.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = when (label.length) {
                1 -> w * 0.30f
                2 -> w * 0.26f
                3 -> w * 0.205f
                else -> w * 0.165f
            }
        }
        val baseline = bottom - h * 0.115f
        drawContext.canvas.nativeCanvas.drawText(label, w * 0.5f, baseline, paint)
    }
}

/** 实心文件夹：蓝到紫的微渐变，比纯色更立体，但不做发光 */
@Composable
fun MtFolderIcon(modifier: Modifier = Modifier, tint: Color = folderColor()) {
    val tintEnd = folderColorEnd()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 后层：稍微向右下错开，形成厚度
        val back = Path().apply {
            moveTo(w * 0.10f, h * 0.36f)
            quadraticBezierTo(w * 0.10f, h * 0.26f, w * 0.20f, h * 0.26f)
            lineTo(w * 0.39f, h * 0.26f)
            lineTo(w * 0.47f, h * 0.36f)
            lineTo(w * 0.86f, h * 0.36f)
            quadraticBezierTo(w * 0.94f, h * 0.36f, w * 0.94f, h * 0.44f)
            lineTo(w * 0.94f, h * 0.80f)
            quadraticBezierTo(w * 0.94f, h * 0.88f, w * 0.86f, h * 0.88f)
            lineTo(w * 0.14f, h * 0.88f)
            quadraticBezierTo(w * 0.06f, h * 0.88f, w * 0.06f, h * 0.80f)
            close()
        }
        drawPath(back, color = tint.copy(alpha = 0.72f))

        val front = Path().apply {
            moveTo(w * 0.06f, h * 0.40f)
            quadraticBezierTo(w * 0.06f, h * 0.33f, w * 0.15f, h * 0.33f)
            lineTo(w * 0.38f, h * 0.33f)
            lineTo(w * 0.45f, h * 0.41f)
            lineTo(w * 0.85f, h * 0.41f)
            quadraticBezierTo(w * 0.94f, h * 0.41f, w * 0.94f, h * 0.49f)
            lineTo(w * 0.94f, h * 0.78f)
            quadraticBezierTo(w * 0.94f, h * 0.86f, w * 0.85f, h * 0.86f)
            lineTo(w * 0.14f, h * 0.86f)
            quadraticBezierTo(w * 0.06f, h * 0.86f, w * 0.06f, h * 0.78f)
            close()
        }
        drawPath(
            front,
            brush = Brush.verticalGradient(
                listOf(tint, tintEnd),
                startY = h * 0.33f,
                endY = h * 0.86f
            )
        )
        drawPath(
            front,
            color = Color.White.copy(alpha = 0.14f),
            style = Stroke(width = 0.6.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}

/** ".." 上级目录：三点文件夹线性图标（固定列表首行） */
@Composable
fun FolderDotsIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.26f)
            lineTo(w * 0.38f, h * 0.26f)
            lineTo(w * 0.47f, h * 0.37f)
            lineTo(w * 0.92f, h * 0.37f)
            lineTo(w * 0.92f, h * 0.82f)
            lineTo(w * 0.08f, h * 0.82f)
            close()
        }
        drawPath(
            path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val r = w * 0.042f
        for (i in -1..1) {
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(w * 0.5f + i * w * 0.14f, h * 0.595f)
            )
        }
    }
}

@Composable
fun FileTypeIcon(file: File, isDir: Boolean = file.isDirectory, size: Dp = 20.dp) {
    Icon(
        imageVector = iconOf(file, isDir),
        contentDescription = null,
        tint = tintOf(file, isDir),
        modifier = Modifier.size(size)
    )
}

/** 大尺寸预览用：方形圆角容器包住同一套图标，用于空状态、详情页 */
@Composable
fun FileTypePlate(file: File, isDir: Boolean = false, size: Dp = 56.dp) {
    val color = tintOf(file, isDir)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(color.copy(alpha = if (SunColors.dark) 0.14f else 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        FileTypeBadge(file, isDir, size * 0.62f)
    }
}
