package com.suibian.sunfile.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.MtPlugins
import com.suibian.sunfile.data.mtapi.PrefItem
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MT 插件管理。装 .mtp、看接口、跑设置界面、测翻译、看日志。
 * 插件是 Java 源码，由内置解释器执行，不走 DexClassLoader。
 */
@Composable
fun MtPluginPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<MtPlugins.Plugin>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        plugins = withContext(Dispatchers.IO) { MtPlugins.list(context) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(context.cacheDir, "install-${System.currentTimeMillis()}.mtp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    val p = MtPlugins.install(context, tmp)
                    tmp.delete()
                    p
                }
            }
            r.onSuccess {
                message = "已安装 ${it.name}"
                reload++
            }.onFailure {
                message = it.message ?: "安装失败"
            }
        }
    }

    val plugin = plugins.firstOrNull { it.id == selected }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (plugin != null) selected = null else onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = SunColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    plugin?.name ?: "MT 插件",
                    color = SunColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (plugin != null) {
                    Text(
                        "${plugin.id} · ${plugin.versionName}",
                        color = SunColors.TextTertiary,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
            }
            if (plugin == null) {
                IconButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Outlined.Add, contentDescription = "安装", tint = SunColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (plugin == null) {
            if (plugins.isEmpty()) {
                EmptyPlugins(
                    onInstall = { picker.launch("*/*") },
                    onDemo = {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { MtPlugins.installDemo(context) } }
                                .onSuccess { reload++ }
                                .onFailure { message = it.message }
                        }
                    }
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(plugins, key = { it.id }) { p ->
                        PluginRow(p) { selected = p.id }
                    }
                }
            }
        } else {
            PluginDetail(
                plugin = plugin,
                onChanged = { reload++ },
                onRemoved = {
                    selected = null
                    reload++
                },
                onMessage = { message = it }
            )
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示", fontSize = 15.sp) },
            text = { Text(text, fontSize = 13.sp, color = SunColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("知道了", color = SunColors.Accent) }
            },
            containerColor = SunColors.Surface2
        )
    }
}

@Composable
private fun EmptyPlugins(onInstall: () -> Unit, onDemo: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("没有插件", color = SunColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "MT 的 .mtp 直接装，源码由内置解释器执行",
            color = SunColors.TextTertiary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("选择 .mtp", true, onInstall)
            Pill("装示例插件", false, onDemo)
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (primary) Color.White else SunColors.TextPrimary,
        fontSize = 12.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (primary) SunColors.Accent else SunColors.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun PluginRow(p: MtPlugins.Plugin, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PluginIcon(p, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                color = SunColors.TextPrimary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(p.versionName.ifBlank { null }, p.kind).joinToString(" · "),
                color = SunColors.TextTertiary,
                fontSize = 10.5.sp
            )
        }
        Text(
            if (p.enabled) "启用" else "停用",
            color = if (p.enabled) SunColors.TextSecondary else SunColors.TextTertiary,
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun PluginIcon(p: MtPlugins.Plugin, size: androidx.compose.ui.unit.Dp) {
    val bmp = remember(p.dir) {
        p.icon?.let { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(SunColors.Surface2),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
        } else {
            Text(
                p.name.take(1).ifBlank { "P" },
                color = SunColors.TextSecondary,
                fontSize = (size.value / 2.4f).sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PluginDetail(
    plugin: MtPlugins.Plugin,
    onChanged: () -> Unit,
    onRemoved: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(plugin.id) { context.getSharedPreferences("mtplugin_${plugin.id}", android.content.Context.MODE_PRIVATE) }
    var items by remember(plugin.id) { mutableStateOf<List<PrefItem>?>(null) }
    var prefsError by remember(plugin.id) { mutableStateOf<String?>(null) }
    val logs = remember(plugin.id) { mutableStateListOf<String>() }
    var enabled by remember(plugin.id) { mutableStateOf(plugin.enabled) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(plugin.id, refresh) {
        if (!plugin.hasPreference) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { runCatching { MtPlugins.buildPreference(context, plugin) } }
        r.onSuccess {
            items = it.first
            logs.clear()
            logs.addAll(it.second)
        }.onFailure {
            prefsError = it.message ?: "设置界面加载失败"
            items = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIcon(plugin, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plugin.name, color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${plugin.versionName} (${plugin.versionCode}) · SDK ${plugin.sdk}",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    scope.launch(Dispatchers.IO) { MtPlugins.setEnabled(context, plugin.id, on) }
                    onChanged()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SunColors.Accent,
                    uncheckedThumbColor = SunColors.TextTertiary,
                    uncheckedTrackColor = SunColors.Surface2
                )
            )
        }

        if (plugin.description.isNotBlank()) {
            Text(
                plugin.description,
                color = SunColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }

        plugin.engineClass?.let { engine ->
            GroupTitle("翻译引擎")
            TranslateTest(plugin, engine) { logs.addAll(it) }
        }

        if (plugin.hasPreference) {
            GroupTitle("设置")
            when {
                prefsError != null -> Text(
                    prefsError!!,
                    color = SunColors.Danger,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                items == null -> Text(
                    "加载中…",
                    color = SunColors.TextTertiary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                else -> items!!.forEach { item ->
                    PrefRow(item, prefs) { refresh++ }
                }
            }
        }

        if (logs.isNotEmpty()) {
            GroupTitle("日志")
            Text(
                logs.takeLast(60).joinToString("\n"),
                color = SunColors.TextSecondary,
                fontSize = 10.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .background(SunColors.Surface2, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Text(
                "卸载",
                color = SunColors.Danger,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SunColors.Surface2)
                    .clickable { confirmDelete = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(30.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("卸载 ${plugin.name}？", fontSize = 15.sp) },
            text = { Text("插件的源码、资源与配置都会删除。", fontSize = 12.5.sp, color = SunColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { MtPlugins.uninstall(context, plugin.id) }
                        onMessage("已卸载")
                        onRemoved()
                    }
                }) { Text("卸载", color = SunColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", color = SunColors.TextSecondary) }
            },
            containerColor = SunColors.Surface2
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        color = SunColors.TextTertiary,
        fontSize = 10.5.sp,
        modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun TranslateTest(plugin: MtPlugins.Plugin, engineClass: String, onLog: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("Hello, world") }
    var source by remember { mutableStateOf("auto") }
    var target by remember { mutableStateOf("zh-CN") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var langs by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(plugin.id) {
        val src = withContext(Dispatchers.IO) { MtPlugins.sourceLanguages(context, plugin) }
        langs = src
        if (src.isNotEmpty() && !src.contains(source)) source = src.first()
        val t = withContext(Dispatchers.IO) { MtPlugins.targetLanguages(context, plugin, source) }
        if (t.isNotEmpty() && !t.contains(target)) target = t.first()
    }

    Column(Modifier.padding(horizontal = 14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SunColors.Surface2, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            if (input.isEmpty()) Text("要翻译的文本", color = SunColors.TextTertiary, fontSize = 12.sp)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 12.5.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (langs.isEmpty()) source else source,
                color = SunColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(SunColors.Surface2, RoundedCornerShape(6.dp))
                    .clickable {
                        val list = langs.ifEmpty { listOf("auto") }
                        source = next(list, source)
                        scope.launch(Dispatchers.IO) {
                            val t = MtPlugins.targetLanguages(context, plugin, source)
                            withContext(Dispatchers.Main) {
                                if (t.isNotEmpty() && !t.contains(target)) target = t.first()
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Text(" → ", color = SunColors.TextTertiary, fontSize = 11.sp)
            Text(
                target,
                color = SunColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(SunColors.Surface2, RoundedCornerShape(6.dp))
                    .clickable { target = next(listOf("upper", "lower", "zh-CN", "en"), target) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (running) "…" else "运行",
                color = Color.White,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (running) SunColors.TextTertiary else SunColors.Accent)
                    .clickable(enabled = !running) {
                        running = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                MtPlugins.translate(context, plugin, input, source, target)
                            }
                            running = false
                            result = if (r.ok) r.text else "失败：${r.text}"
                            onLog(r.logs)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                result,
                color = SunColors.TextPrimary,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SunColors.Surface2, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            )
        }
    }
}

private fun next(list: List<String>, cur: String): String {
    if (list.isEmpty()) return cur
    val i = list.indexOf(cur)
    return list[(i + 1).mod(list.size)]
}

@Composable
private fun PrefRow(item: PrefItem, prefs: android.content.SharedPreferences, onChange: () -> Unit) {
    when (item) {
        is PrefItem.Header -> Text(
            item.title,
            color = SunColors.TextTertiary,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp)
        )
        is PrefItem.Text -> {
            val context = LocalContext.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.url != null) {
                        item.url?.let { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(item.title, color = SunColors.TextPrimary, fontSize = 12.5.sp)
                item.summary?.let {
                    Text(it, color = SunColors.TextTertiary, fontSize = 10.5.sp)
                }
            }
        }
        is PrefItem.Switch -> {
            val value = prefs.getBoolean(item.key, item.def)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = SunColors.TextPrimary, fontSize = 12.5.sp)
                    val sub = when {
                        value && item.summaryOn != null -> item.summaryOn
                        !value && item.summaryOff != null -> item.summaryOff
                        else -> item.summary
                    }
                    sub?.let { Text(it, color = SunColors.TextTertiary, fontSize = 10.5.sp) }
                }
                Switch(
                    checked = value,
                    onCheckedChange = {
                        prefs.edit().putBoolean(item.key, it).apply()
                        onChange()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SunColors.Accent,
                        uncheckedThumbColor = SunColors.TextTertiary,
                        uncheckedTrackColor = SunColors.Surface2
                    )
                )
            }
        }
        is PrefItem.Input -> {
            var editing by remember { mutableStateOf(false) }
            var draft by remember { mutableStateOf("") }
            val value = prefs.getString(item.key, item.def) ?: ""
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        draft = value
                        editing = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = SunColors.TextPrimary, fontSize = 12.5.sp)
                    val sub = if (item.valueAsSummary) value else item.summary
                    sub?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = SunColors.TextTertiary, fontSize = 10.5.sp)
                    }
                }
                Text(
                    value.ifBlank { "未设置" },
                    color = SunColors.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp)
                )
            }
            if (editing) {
                AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text(item.title, fontSize = 15.sp) },
                    text = {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SunColors.Bg, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            prefs.edit().putString(item.key, draft).apply()
                            editing = false
                            onChange()
                        }) { Text("保存", color = SunColors.Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editing = false }) { Text("取消", color = SunColors.TextSecondary) }
                    },
                    containerColor = SunColors.Surface2
                )
            }
        }
        is PrefItem.ListPref -> {
            var open by remember { mutableStateOf(false) }
            val value = prefs.getString(item.key, item.def)
            val current = item.items.firstOrNull { it.second == value }?.first
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = SunColors.TextPrimary, fontSize = 12.5.sp)
                    val sub = value?.let { item.itemSummaries[it] } ?: item.summary
                    sub?.let { Text(it, color = SunColors.TextTertiary, fontSize = 10.5.sp) }
                }
                Text(
                    current ?: "未选择",
                    color = SunColors.TextSecondary,
                    fontSize = 11.5.sp
                )
            }
            if (open) {
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(item.title, fontSize = 15.sp) },
                    text = {
                        Column {
                            item.items.forEach { (name, v) ->
                                Text(
                                    name,
                                    color = if (v == value) SunColors.Accent else SunColors.TextPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            prefs.edit().putString(item.key, v).apply()
                                            open = false
                                            onChange()
                                        }
                                        .padding(vertical = 9.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { open = false }) { Text("取消", color = SunColors.TextSecondary) }
                    },
                    containerColor = SunColors.Surface2
                )
            }
        }
    }
}
