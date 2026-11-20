package com.suibian.sunfile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.DocBlock
import com.suibian.sunfile.data.DocContent
import com.suibian.sunfile.data.DocParser
import com.suibian.sunfile.data.DocStyle
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.SheetTable
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置文档查看器：Word / Excel / PPT / ODF / CSV 直接在这里读，
 * 不再跳转第三方应用。文档按段落排版，表格按行列显示。
 */
@Composable
fun DocumentViewerOverlay(file: File, onClose: () -> Unit, onOpenExternal: (File) -> Unit) {
    var content by remember(file) { mutableStateOf<DocContent?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var sheetIndex by remember(file) { mutableIntStateOf(0) }

    BackHandler { onClose() }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) { runCatching { DocParser.parse(file) } }
        result.onSuccess { content = it }.onFailure { error = it.message ?: "无法读取这个文档" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(com.suibian.sunfile.ui.theme.Mt.BarHeight).background(SunColors.Surface1).padding(horizontal = 4.dp),
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
                    letterSpacing = 0.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    content?.meta ?: Fmt.size(file.length()),
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = { onOpenExternal(file) }, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    "用其他应用打开",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val data = content
        when {
            error != null -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("无法预览", color = SunColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    error ?: "",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "用其他应用打开",
                    color = SunColors.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(SunShapes.Small)
                        .clickable { onOpenExternal(file) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }

            data.sheets.isNotEmpty() -> {
                if (data.sheets.size > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data.sheets.forEachIndexed { i, sheet ->
                            val active = i == sheetIndex.coerceIn(0, data.sheets.lastIndex)
                            Text(
                                sheet.name,
                                color = if (active) SunColors.Accent else SunColors.TextSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (active) SunColors.AccentSoft else SunColors.Surface2)
                                    .clickable { sheetIndex = i }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                SheetView(data.sheets.getOrNull(sheetIndex.coerceIn(0, data.sheets.lastIndex)))
            }

            data.blocks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个文档没有可显示的文字", color = SunColors.TextTertiary, fontSize = 12.sp)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp)
            ) {
                items(data.blocks) { block -> DocBlockView(block) }
            }
        }
    }
}

@Composable
private fun DocBlockView(block: DocBlock) {
    when (block.style) {
        DocStyle.TITLE -> {
            Text(
                block.text,
                color = SunColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
        }

        DocStyle.HEADING -> {
            Text(
                block.text,
                color = SunColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        DocStyle.SUBHEADING -> {
            Text(
                block.text,
                color = SunColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
        }

        DocStyle.BULLET -> {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("·", color = SunColors.Accent, fontSize = 13.sp, modifier = Modifier.width(14.dp))
                Text(
                    block.text,
                    color = SunColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
            }
        }

        DocStyle.QUOTE -> {
            Row(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(18.dp)
                        .background(SunColors.AccentLine)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    block.text,
                    color = SunColors.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp
                )
            }
        }

        DocStyle.CODE -> {
            Text(
                block.text,
                color = SunColors.TextPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(SunShapes.Small)
                    .background(SunColors.Surface2)
                    .padding(10.dp)
            )
        }

        DocStyle.PARAGRAPH -> {
            Text(
                block.text,
                color = SunColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 21.5.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SheetView(sheet: SheetTable?) {
    if (sheet == null) return
    val h = remember(sheet) { androidx.compose.foundation.ScrollState(0) }
    val rows = sheet.rows
    val columns = rows.maxOfOrNull { it.size } ?: 0
    val cellWidth = when {
        columns <= 3 -> 136.dp
        columns <= 6 -> 114.dp
        else -> 94.dp
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        itemsIndexed(rows) { r, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(h)
            ) {
                repeat(columns) { c ->
                    val text = row.getOrNull(c).orEmpty()
                    Box(
                        Modifier
                            .width(cellWidth)
                            .height(34.dp)
                            .background(
                                when {
                                    r == 0 -> SunColors.Surface3
                                    r % 2 == 0 -> SunColors.Surface1
                                    else -> SunColors.Bg
                                }
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text,
                            color = if (r == 0) SunColors.TextPrimary else SunColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (r == 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
