package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
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
import com.suibian.sunfile.data.ShellEngine
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内置终端。
 *
 * 命令不经过应用自己的 API，而是交给提权通道执行：拿到 Shizuku 或 Root 后，
 * `ls /data`、`cat /system/build.prop` 看到的都是真实内容。
 * 这也是「用终端去读根目录」的落点 —— 终端与文件管理器共用同一个 Shell 引擎。
 *
 * 每条命令单独开一个进程：有超时、有退出码、不会把 UI 卡死，
 * 也不会因为一个后台进程残留而污染下一条命令的环境。
 */
@Composable
fun TerminalPage(
    onBack: () -> Unit,
    onOpenPath: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val lines = remember { mutableStateListOf<String>() }
    var cwd by remember { mutableStateOf("/sdcard") }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    val engine by Shell.engineState.collectAsState()
    val privileged = engine != ShellEngine.NONE

    fun banner() {
        lines.add("SunDocument · ${engine.label} · $cwd")
        lines.add("help 查看命令")
        lines.add("")
    }

    LaunchedEffect(Unit) {
        if (lines.isEmpty()) banner()
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    fun elevate() {
        if (pending || privileged) return
        pending = true
        lines.add("正在申请权限…")
        scope.launch {
            val e = withContext(Dispatchers.IO) { Shell.requestAuto() }
            pending = false
            lines.add(
                if (e != ShellEngine.NONE) "已接管 · ${e.label}"
                else "未获得权限，继续以普通身份执行"
            )
            lines.add("")
        }
    }

    fun run(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || running) return
        history.add(text)
        historyIndex = -1
        lines.add("${if (privileged) "#" else "$"} $text")

        when {
            text == "clear" -> {
                lines.clear()
                return
            }
            text == "help" -> {
                lines.add("cd <dir>     切换目录（可用根目录）")
                lines.add("open <dir>   用文件管理器打开该目录")
                lines.add("su           申请 Shizuku / Root")
                lines.add("ll / la      ls -al / ls -a")
                lines.add("其余按 /system/bin/sh 语法执行，支持管道与重定向")
                lines.add("")
                return
            }
            text == "su" || text == "shizuku" -> {
                elevate()
                return
            }
            text.startsWith("open ") -> {
                val arg = text.removePrefix("open ").trim().trim('"', '\'')
                val target = if (arg.startsWith("/")) arg else "$cwd/$arg"
                running = true
                scope.launch {
                    val real = withContext(Dispatchers.IO) { Shell.resolveDir(target) }
                    running = false
                    if (real != null) onOpenPath(real) else {
                        lines.add("open: $target: 不是目录")
                        lines.add("")
                    }
                }
                return
            }
            text.startsWith("cd") -> {
                val arg = text.removePrefix("cd").trim().trim('"', '\'')
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
        }

        val script = when (text) {
            "ll" -> "cd ${Shell.q(cwd)} 2>/dev/null; ls -al"
            "la" -> "cd ${Shell.q(cwd)} 2>/dev/null; ls -a"
            "pwd" -> "echo ${Shell.q(cwd)}"
            else -> "cd ${Shell.q(cwd)} 2>/dev/null; $text"
        }
        running = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                Shell.runScript(script, asRoot = privileged, timeoutMs = 25_000)
            }
            val out = result.out.trimEnd()
            if (out.isNotEmpty()) out.lineSequence().take(400).forEach { lines.add(it) }
            val err = result.err.trim()
            if (err.isNotEmpty()) {
                err.lineSequence().take(60).forEach { lines.add(it) }
                if (!privileged && err.contains("Permission denied", true)) {
                    lines.add("该目录需要权限，输入 su")
                }
            }
            if (out.isEmpty() && err.isEmpty()) lines.add("（无输出）")
            lines.add("")
            while (lines.size > 2000) lines.removeAt(0)
            running = false
        }
    }

    // 常用命令一条就够，横向排开，不占列表空间
    val shortcuts = listOf("ls", "ll", "pwd", "cd ..", "cd /", "clear", "help", "su")

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.TopBar)
                .statusBarsPadding()
                .height(Mt.BarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "终端",
                    color = SunColors.OnTopBar,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    cwd,
                    color = SunColors.OnTopBarMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Text(
                when {
                    pending -> "…"
                    privileged -> engine.label.uppercase()
                    else -> "SHELL"
                },
                color = if (privileged) SunColors.OnTopBar else SunColors.OnTopBarMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .background(Color.White.copy(alpha = if (privileged) 0.12f else 0.06f), SunShapes.Small)
                    .clickable(enabled = !privileged && !pending) { elevate() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            items(lines) { line ->
                Text(
                    line.ifEmpty { " " },
                    color = when {
                        line.startsWith("#") || line.startsWith("$") -> SunColors.Accent
                        line.startsWith("cd:") || line.startsWith("open:") ||
                            line.contains("Permission denied") || line.startsWith("该目录") -> SunColors.Danger
                        line.startsWith("已接管") -> SunColors.Success
                        else -> SunColors.TextSecondary
                    },
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 快捷命令
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            shortcuts.forEach { cmd ->
                Text(
                    cmd,
                    color = SunColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SunColors.Surface2)
                        .clickable(enabled = !running) { run(cmd) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
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
                if (privileged) "#" else "$",
                color = if (privileged) SunColors.Success else SunColors.Accent,
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
                if (running) "…" else "运行",
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
