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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    if (isDir) return SunColors.Folder
    return extColor(FileKinds.extensionOf(file), FileKinds.kindOf(file))
}

/** 扩展名配色：尽量贴近各家语言/办公软件的识别色，扫一眼就能分清 */
fun extColor(ext: String, kind: FileKind): Color = when (ext) {
    "py", "pyw" -> Color(0xFF3E7CB1)
    "js", "mjs", "cjs", "jsx" -> Color(0xFFB49A00)
    "ts", "tsx" -> Color(0xFF2F74C0)
    "java" -> Color(0xFFB54A3C)
    "kt", "kts" -> Color(0xFF8C6BD6)
    "html", "htm", "xhtml" -> Color(0xFFD2572F)
    "css", "scss", "less" -> Color(0xFF2F6BD8)
    "json", "json5" -> Color(0xFF7C8794)
    "xml", "plist" -> Color(0xFF7C8FA0)
    "md", "markdown" -> Color(0xFF6B7680)
    "sh", "bash", "zsh", "fish" -> Color(0xFF3F8F52)
    "go" -> Color(0xFF1F93B5)
    "rs" -> Color(0xFFB07A5A)
    "c", "h" -> Color(0xFF5A6FBF)
    "cpp", "cc", "cxx", "hpp", "hxx" -> Color(0xFF4A5FA8)
    "php" -> Color(0xFF6C63A8)
    "rb" -> Color(0xFFB3423A)
    "swift" -> Color(0xFFD2572F)
    "sql" -> Color(0xFFC08420)
    "yml", "yaml" -> Color(0xFFA8443C)
    "gradle", "groovy" -> Color(0xFF3C7FA8)
    "toml", "ini", "conf", "cfg", "properties" -> Color(0xFF6E7B88)
    "smali" -> Color(0xFF8A8F98)
    "lua" -> Color(0xFF4B4B96)
    "dart" -> Color(0xFF2A8BC0)
    "vue" -> Color(0xFF3FA37A)
    "svelte" -> Color(0xFFC6502B)
    "csv", "tsv" -> Color(0xFF3F8F52)
    "doc", "docx", "docm", "rtf", "odt", "wps", "pages" -> Color(0xFF2B579A)
    "xls", "xlsx", "xlsm", "ods", "et", "numbers" -> Color(0xFF217346)
    "ppt", "pptx", "pptm", "odp", "dps", "key" -> Color(0xFFC4492B)
    "zip", "jar", "aar", "war", "ear", "whl", "epub", "xapk", "apks" -> Color(0xFFC08A24)
    "rar" -> Color(0xFF9B5BB5)
    "7z" -> Color(0xFF3D8B8B)
    "tar", "gz", "tgz", "bz2", "xz", "zst" -> Color(0xFFA87A3C)
    "apk" -> Color(0xFF3FB950)
    "pdf" -> Color(0xFFD34B43)
    else -> when (kind) {
        FileKind.IMAGE -> Color(0xFF5AA3D6)
        FileKind.AUDIO -> Color(0xFF9B78D4)
        FileKind.VIDEO -> Color(0xFFCE7B4F)
        FileKind.TEXT -> Color(0xFF7E8895)
        FileKind.ARCHIVE -> Color(0xFFC08A24)
        FileKind.OFFICE -> Color(0xFF2B579A)
        FileKind.APK -> Color(0xFF3FB950)
        FileKind.PDF -> Color(0xFFD34B43)
        else -> Color(0xFF75808C)
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
        MtFolderIcon(modifier.size(size), SunColors.Folder)
        return
    }
    val kind = FileKinds.kindOfName(file.name)
    if (kind == FileKind.IMAGE || kind == FileKind.VIDEO) {
        ThumbBadge(file, kind == FileKind.VIDEO, size, modifier)
        return
    }
    val ext = FileKinds.extensionOf(file)
    PageBadge(
        label = labelOf(ext),
        color = extColor(ext, kind),
        dotted = ext.isEmpty() || kind == FileKind.TEXT && ext in setOf("txt", "log", "nfo"),
        modifier = modifier.size(size)
    )
}

@Composable
private fun ThumbBadge(file: File, isVideo: Boolean, size: Dp, modifier: Modifier) {
    val bmp = rememberThumb(file, isVideo)
    val shape = RoundedCornerShape(size * 0.18f)
    val fallback = extColor(FileKinds.extensionOf(file), FileKinds.kindOfName(file.name))
    Box(
        modifier.size(size).clip(shape).background(fallback.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (isVideo) Icons.Outlined.Movie else Icons.Outlined.Image,
                null,
                tint = fallback.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.55f)
            )
        }
        if (isVideo) {
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
 * 文件图标：MT 的底是「整块彩色圆角方」，上面压一张白纸，
 * 扩展名用底色写在纸上。方块比纸张更容易在密集列表里被眼睛抓住，
 * 颜色又能在不看文字时区分类型。
 */
@Composable
private fun PageBadge(label: String, color: Color, dotted: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
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
        drawPath(page, Color.White.copy(alpha = 0.95f))
        // 折角：用底色透出来，像纸被翻起
        drawPath(
            Path().apply {
                moveTo(right - fold, top)
                lineTo(right - fold, top + fold)
                lineTo(right, top + fold)
                close()
            },
            color.copy(alpha = 0.24f)
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

/** 实心文件夹：带一个浅色上层，比线性图标更接近 MT */
@Composable
fun MtFolderIcon(modifier: Modifier = Modifier, tint: Color = SunColors.Folder) {
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
        drawPath(front, color = tint)
        drawPath(
            front,
            color = Color.White.copy(alpha = 0.12f),
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
