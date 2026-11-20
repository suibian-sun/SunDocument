package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Shell
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内置终端：/system/bin/sh，可选 su。
 *
 * 与文件列表的 Root 开关共用同一个会话；开启后用 su -c 执行，
 * 因此 `cd /data/data && ls` 能看到真实目录，而不是被 SELinux 挡回。
 */
@Composable
fun TerminalPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val lines = remember { mutableStateListOf<String>() }
    var cwd by remember { mutableStateOf("/sdcard") }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(Shell.rootAvailable) }
    var pendingRoot by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    fun banner() {
        lines.add("SunDocument shell · ${if (root) "root" else "sh"}")
        lines.add("help 查看用法，clear 清屏，cd 切换目录")
        lines.add("")
    }

    LaunchedEffect(Unit) {
        if (lines.isEmpty()) banner()
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    fun enableRoot() {
        if (pendingRoot || root) return
        pendingRoot = true
        lines.add("正在请求 Root…")
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Shell.requestRoot() }
            pendingRoot = false
            root = ok
            lines.add(if (ok) "已获得 Root，后续命令以 uid=0 执行" else "未获得 Root，继续以普通用户执行")
            lines.add("")
        }
    }

    fun run(cmd: String) {
        val text = cmd.trim()
        if (text.isEmpty() || running) return
        history.add(text)
        historyIndex = -1
        lines.add("${if (root) "root@" else ""}$cwd ${if (root) "#" else "$"} $text")
        when {
            text == "clear" -> {
                lines.clear()
                return
            }
            text == "help" -> {
                lines.add("cd <目录>        切换工作目录（支持 Root 目录）")
                lines.add("ls / cat / cp / mv / rm / chmod / stat 等系统命令直接可用")
                lines.add("su               在终端内申请 Root")
                lines.add("clear            清屏")
                lines.add("")
                return
            }
            text == "su" -> {
                enableRoot()
                return
            }
            text.startsWith("cd ") -> {
                val arg = text.removePrefix("cd ").trim().trim('"', '\'')
                val target = when {
                    arg.isEmpty() || arg == "~" -> "/sdcard"
                    arg.startsWith("/") -> arg
                    else -> "$cwd/$arg"
                }
                running = true
                scope.launch {
                    val real = withContext(Dispatchers.IO) { Shell.resolveDir(target) }
                    running = false
                    if (real != null) cwd = real else {
                        lines.add("cd: $target: 不是目录")
                        lines.add("")
                    }
                }
                return
            }
            text == "pwd" -> {
                lines.add(cwd)
                lines.add("")
                return
            }
        }
        if (text.startsWith("su ") || text.contains("| su")) {
            if (!root) enableRoot()
        }
        running = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val script = "cd ${Shell.q(cwd)} 2>/dev/null; $text"
                Shell.runScript(script, root, 25_000)
            }
            val out = result.out.trimEnd()
            if (out.isNotEmpty()) {
                out.lineSequence().take(400).forEach { lines.add(it) }
            }
            val err = result.err.trim()
            if (err.isNotEmpty()) err.lineSequence().take(60).forEach { lines.add(it) }
            if (out.isEmpty() && err.isEmpty()) lines.add("（无输出）")
            lines.add("")
            while (lines.size > 2000) lines.removeAt(0)
            running = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Mt.BarHeight)
                .background(SunColors.Surface1)
                .padding(horizontal = 4.dp),
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
            Column(Modifier.weight(1f)) {
                Text("终端", color = SunColors.TextPrimary, fontSize = 16.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    cwd,
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                if (pendingRoot) "授权中…" else if (root) "ROOT" else "SHELL",
                color = if (root) SunColors.Success else SunColors.TextTertiary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .background(if (root) SunColors.AccentSoft else SunColors.Surface3)
                    .clickable(enabled = !root && !pendingRoot) { enableRoot() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(lines) { line ->
                Text(
                    line.ifEmpty { " " },
                    color = when {
                        line.startsWith("root@") || line.startsWith("/") && line.contains(" $ ") -> SunColors.Accent
                        line.startsWith("执行失败") || line.contains("不是目录") || line.startsWith("cd:") -> SunColors.Danger
                        line.startsWith("已获得") -> SunColors.Success
                        else -> SunColors.TextSecondary
                    },
                    fontSize = 9.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.Surface1)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (root) "#" else "$",
                color = if (root) SunColors.Success else SunColors.Accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = SunColors.TextPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    run(input)
                    input = ""
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .background(SunColors.Surface3, SunShapes.Small)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> {
                                if (history.isNotEmpty()) {
                                    historyIndex = if (historyIndex < 0) history.lastIndex
                                    else (historyIndex - 1).coerceAtLeast(0)
                                    input = history[historyIndex]
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (historyIndex >= 0) {
                                    historyIndex += 1
                                    if (historyIndex > history.lastIndex) {
                                        historyIndex = -1
                                        input = ""
                                    } else input = history[historyIndex]
                                }
                                true
                            }
                            else -> false
                        }
                    }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    running -> "…"
                    pendingRoot -> "…"
                    else -> "运行"
                },
                color = if (running) SunColors.TextTertiary else SunColors.Accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .background(SunColors.AccentSoft)
                    .clickable(enabled = !running) {
                        run(input)
                        input = ""
                    }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}
