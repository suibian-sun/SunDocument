package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.service.Inspect
import com.suibian.sunfile.service.InspectService
import com.suibian.sunfile.service.UiNode
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 界面分析：抓当前窗口的控件树。
 * 默认给 3 秒倒计时，够切到目标应用再回来，不用来回点。
 */
@Composable
fun InspectPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf<List<UiNode>?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<UiNode?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(InspectService.enabled(context)) }

    val filtered = remember(nodes, query) {
        nodes.orEmpty().filter {
            query.isBlank() || it.text.contains(query, true) || it.id.contains(query, true) ||
                it.cls.contains(query, true) || it.desc.contains(query, true)
        }
    }

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ui", text))
        message = "已复制控件树"
        scope.launch {
            delay(1600)
            message = null
        }
    }

    fun capture(delaySeconds: Int) {
        scope.launch {
            for (i in delaySeconds downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = 0
            enabled = InspectService.enabled(context)
            val result = Inspect.capture()
            if (result == null) {
                message = if (enabled) "没有拿到窗口，确认服务已连接" else "无障碍服务未开启"
            } else {
                nodes = result
                message = "${result.size} 个控件"
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        enabled = InspectService.enabled(context)
        if (enabled) capture(0)
    }

    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            delay(1800)
            message = null
        }
    }

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(SunColors.TopBar).statusBarsPadding().height(Mt.BarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = SunColors.OnTopBar, modifier = Modifier.size(21.dp))
            }
            Text("界面分析", color = SunColors.OnTopBar, fontSize = 16.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (nodes != null) {
                Text(
                    "复制",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { copy(Inspect.toXml(filtered)) }.padding(10.dp)
                )
            }
            Text(
                if (countdown > 0) "$countdown s" else "抓取",
                color = if (countdown > 0) SunColors.TextTertiary else SunColors.Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(enabled = countdown == 0) { capture(3) }
                    .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            )
        }

        if (!enabled) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text("需要无障碍权限", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "开启后，应用才能读取当前界面的控件层级。只在你点抓取时读取。",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "去开启",
                    color = SunColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(SunColors.Accent, RoundedCornerShape(9.dp))
                        .clickable { InspectService.openSettings(context) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(34.dp)
                .background(SunColors.Surface2, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 12.5.sp),
                cursorBrush = SolidColor(SunColors.Accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("过滤文本、id 或类名", color = SunColors.TextTertiary, fontSize = 12.5.sp)
                    inner()
                }
            )
        }

        val list = nodes
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (countdown > 0) "切到目标界面…" else "点右上角抓取当前界面",
                    color = SunColors.TextTertiary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(filtered) { _, node ->
                    NodeRow(node) { detail = node }
                }
            }
        }
    }

    detail?.let { node ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = {
                Text(
                    node.cls.substringAfterLast('.').ifEmpty { "View" },
                    color = SunColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Column {
                    DetailLine("id", node.id.ifEmpty { "—" })
                    DetailLine("text", node.text.ifEmpty { "—" })
                    DetailLine("desc", node.desc.ifEmpty { "—" })
                    DetailLine("bounds", node.bounds)
                    DetailLine(
                        "flags",
                        buildList {
                            if (node.clickable) add("clickable")
                            if (node.scrollable) add("scrollable")
                            if (!node.enabled) add("disabled")
                            add("children ${node.children}")
                        }.joinToString(" · ")
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { copy(Inspect.toXml(listOf(node))) }) {
                    Text("复制节点", color = SunColors.Accent, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp) }
            }
        )
    }

    message?.let { msg ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 40.dp)
                    .background(SunColors.Surface4, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun NodeRow(node: UiNode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Spacer(Modifier.width((10 + node.depth * 11).dp))
        Text(
            node.cls.substringAfterLast('.').ifEmpty { "View" },
            color = SunColors.TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Column(Modifier.weight(1f)) {
            Text(
                node.label,
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.id.isNotEmpty() || node.bounds.isNotEmpty()) {
                Text(
                    node.id.substringAfterLast('/').ifEmpty { node.bounds },
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (node.clickable) {
            Text("●", color = SunColors.Accent, fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
        }
    }
}

@Composable
private fun DetailLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, color = SunColors.TextTertiary, fontSize = 10.5.sp, modifier = Modifier.width(52.dp))
        Text(value, color = SunColors.TextSecondary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
    }
}
