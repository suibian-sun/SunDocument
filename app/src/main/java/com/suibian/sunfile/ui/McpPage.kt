package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Mcp
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MCP 服务页：开关、端口、令牌，以及一份能直接粘给 AI 客户端的配置。
 * 服务只绑 127.0.0.1，同一 Wi-Fi 下的其它设备连不上，除非用 adb forward。
 */
@Composable
fun McpPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by Mcp.state.collectAsStateWithLifecycle()
    var port by remember { mutableStateOf(status.port.toString()) }
    var token by remember { mutableStateOf(status.token) }
    var copied by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = label
    }

    val endpoint = "http://127.0.0.1:${status.port}/mcp"
    val config = """
        {
          "mcpServers": {
            "sundocument": {
              "url": "$endpoint",
              "headers": { "Authorization": "Bearer ${status.token}" }
            }
          }
        }
    """.trimIndent()

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().topBarBackground().statusBarsPadding().height(Mt.BarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = SunColors.OnTopBar, modifier = Modifier.size(21.dp))
            }
            Text("MCP 服务", color = SunColors.OnTopBar, fontSize = 16.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(
                checked = status.running,
                onCheckedChange = { on ->
                    val p = port.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8686
                    val t = token.ifBlank { Mcp.newToken() }
                    token = t
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { Mcp.setEnabled(context, on, p, t) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SunColors.Accent,
                    uncheckedThumbColor = SunColors.TextTertiary,
                    uncheckedTrackColor = SunColors.Surface3,
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.padding(end = 10.dp)
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                status.running -> "运行中"
                                status.lastError != null -> "启动失败：${status.lastError}"
                                else -> "已停止"
                            },
                            color = when {
                                status.running -> SunColors.Success
                                status.lastError != null -> SunColors.Danger
                                else -> SunColors.TextSecondary
                            },
                            fontSize = 13.sp
                        )
                        Text(endpoint, color = SunColors.TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        "复制地址",
                        color = SunColors.Accent,
                        fontSize = 11.5.sp,
                        modifier = Modifier.clickable { copy("endpoint", endpoint) }.padding(8.dp)
                    )
                }
            }

            item { McpSection("端口") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = port,
                        onValueChange = { input -> port = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(SunColors.Accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).height(40.dp)
                            .background(SunColors.Surface2, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    )
                    Text(
                        "应用",
                        color = if (status.running) SunColors.Accent else SunColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clickable(enabled = status.running) {
                                val p = port.toIntOrNull() ?: return@clickable
                                if (p in 1024..65535) {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { Mcp.start(context, p, token) }
                                }
                            }
                            .padding(6.dp)
                    )
                }
            }

            item { McpSection("令牌") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        token.ifEmpty { "—" },
                        color = SunColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "换一个",
                        color = SunColors.Accent,
                        fontSize = 11.5.sp,
                        modifier = Modifier.clickable {
                            val t = Mcp.newToken()
                            token = t
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                Mcp.setEnabled(context, status.running, status.port, t)
                            }
                        }.padding(8.dp)
                    )
                }
            }

            item { McpSection("客户端配置") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .background(SunColors.Surface2, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        config,
                        color = SunColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "复制配置",
                        color = SunColors.Accent,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable { copy("config", config) }
                    )
                }
            }

            item { McpSection("请求记录") }
            if (status.logs.isEmpty()) {
                item {
                    Text("还没有请求", color = SunColors.TextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            itemsIndexed(status.logs) { _, log ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        if (log.ok) "·" else "×",
                        color = if (log.ok) SunColors.Success else SunColors.Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.width(14.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(log.method, color = SunColors.TextPrimary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                        if (log.detail.isNotEmpty()) {
                            Text(log.detail, color = SunColors.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.time)),
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            item {
                Text(
                    "只监听本机。要在电脑上连接，先执行 adb forward tcp:${status.port} tcp:${status.port}",
                    color = SunColors.TextTertiary,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }
        }
    }

    copied?.let { label ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                "已复制$label",
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 40.dp)
                    .background(SunColors.Surface4, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        androidx.compose.runtime.LaunchedEffect(label) {
            kotlinx.coroutines.delay(1600)
            copied = null
        }
    }
}

@Composable
private fun McpSection(title: String) {
    Text(
        title,
        color = SunColors.TextTertiary,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 2.dp)
    )
}
