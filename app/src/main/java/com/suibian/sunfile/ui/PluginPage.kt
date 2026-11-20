package com.suibian.sunfile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Plugins
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 插件管理：装、卸、启停，以及每个插件带了哪些菜单动作。
 * 插件包格式与 MT 一致（zip 改后缀 .mtp，内含 manifest.json）。
 */
@Composable
fun PluginPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 装/卸之后 tick 变化，重新扫描；扫描在 IO 线程，不挡首帧
    val plugins by androidx.compose.runtime.produceState(
        initialValue = emptyList<Plugins.Plugin>(),
        tick
    ) {
        value = withContext(Dispatchers.IO) { Plugins.list(context) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "plugin_install.mtp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    Plugins.install(context, temp).getOrThrow()
                }
            }
            busy = false
            result.onSuccess {
                message = "已安装 ${it.name} ${it.versionName}"
                tick++
            }.onFailure {
                message = it.message ?: "安装失败"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(Mt.BarHeight).background(SunColors.Surface1).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text(
                "插件",
                color = SunColors.TextPrimary,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "安装",
                color = SunColors.Accent,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { picker.launch(arrayOf("*/*")) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        if (busy) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = SunColors.Accent, strokeWidth = 1.6.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在安装…", color = SunColors.TextTertiary, fontSize = 11.5.sp)
            }
        }

        if (plugins.isEmpty() && !busy) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Extension,
                    null,
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("还没有安装插件", color = SunColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "插件是一个 .mtp 文件，装上之后会出现在文件的长按菜单里。",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "选择 .mtp",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SunColors.Accent)
                        .clickable { picker.launch(arrayOf("*/*")) }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "装一个示例看看",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Plugins.installDemo(context) }
                                r.onSuccess { tick++; message = "已安装示例插件" }
                                    .onFailure { message = it.message ?: "安装失败" }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(plugins, key = { it.id }) { plugin ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(SunShapes.Small)
                                    .background(SunColors.Surface3),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Extension,
                                    null,
                                    tint = SunColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        plugin.name,
                                        color = SunColors.TextPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (plugin.versionName.isNotEmpty()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(plugin.versionName, color = SunColors.TextTertiary, fontSize = 10.sp)
                                    }
                                }
                                if (plugin.description.isNotEmpty()) {
                                    Text(
                                        plugin.description,
                                        color = SunColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Switch(
                                checked = plugin.enabled,
                                onCheckedChange = { on ->
                                    Plugins.setEnabled(context, plugin.id, on)
                                    tick++
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SunColors.Accent,
                                    uncheckedThumbColor = SunColors.TextTertiary,
                                    uncheckedTrackColor = SunColors.Surface3
                                ),
                                modifier = Modifier.size(width = 40.dp, height = 24.dp)
                            )
                        }

                        if (plugin.actions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                plugin.actions.joinToString(" · ") { it.label },
                                color = SunColors.TextTertiary,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(start = 50.dp)
                            )
                        }

                        Row(Modifier.padding(start = 50.dp, top = 6.dp)) {
                            Text(
                                "卸载",
                                color = SunColors.Danger,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .clip(SunShapes.Small)
                                    .clickable {
                                        Plugins.uninstall(context, plugin.id)
                                        tick++
                                        message = "已卸载 ${plugin.name}"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                plugin.id,
                                color = SunColors.TextTertiary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))
                }
            }
        }
    }

    message?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { message = null },
            containerColor = SunColors.Surface4,
            shape = SunShapes.Medium,
            title = { Text("插件", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = { Text(text, color = SunColors.TextSecondary, fontSize = 12.5.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { message = null }) {
                    Text("好", color = SunColors.Accent, fontSize = 13.sp)
                }
            }
        )
    }
}
