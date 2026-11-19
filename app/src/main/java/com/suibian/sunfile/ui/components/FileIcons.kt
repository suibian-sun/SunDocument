package com.suibian.sunfile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.suibian.sunfile.data.FileKind
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.ui.theme.SunColors
import java.io.File

/** 文件类型图标：全部使用线性（outlined）图标，不绘制任何底框 */
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

fun tintOf(file: File, isDir: Boolean = file.isDirectory): Color {
    if (isDir) return SunColors.Folder
    return when (FileKinds.kindOf(file)) {
        FileKind.IMAGE -> Color(0xFF6FB7E8)
        FileKind.AUDIO -> Color(0xFFB78BE8)
        FileKind.VIDEO -> Color(0xFFE08A5F)
        FileKind.ARCHIVE -> Color(0xFFD9A93F)
        FileKind.APK -> SunColors.Success
        FileKind.PDF -> Color(0xFFE5675E)
        FileKind.OFFICE -> Color(0xFF5B9BD5)
        FileKind.TEXT -> SunColors.TextSecondary
        else -> Color(0xFF8A939E)
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
fun FileTypeIcon(file: File, isDir: Boolean = file.isDirectory, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Icon(
        imageVector = iconOf(file, isDir),
        contentDescription = null,
        tint = tintOf(file, isDir),
        modifier = Modifier.size(size)
    )
}
