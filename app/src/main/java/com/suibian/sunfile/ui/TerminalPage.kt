package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置终端：直接调用系统的 /system/bin/sh，不依赖扩展包。
 * cd 由界面自己维护工作目录，其它命令带绝对路径执行，输出一次性回填。
 */
@Composable
fun TerminalPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lines = remember { mutableStateListOf<String>() }
    var cwd by remember { mutableStateOf("/sdcard") }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (lines.isEmpty()) {
            lines.add("SunDocument shell · /system/bin/sh")
            lines.add("输入 help 查看可用写法，clear 清屏")
            lines.add("")
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    fun run(cmd: String) {
        val text = cmd.trim()
        if (text.isEmpty() || running) return
        lines.add("$cwd $ $text")
        when {
            text == "clear" -> {
                lines.clear()
                return
            }
            text == "help" -> {
                lines.add("cd <目录>   切换工作目录")
                lines.add("ls / cat / cp / mv / rm / chmod 等系统命令可直接使用")
                lines.add("需要 Root 的命令请先用 su 提权（su -c \"...\"）")
                return
            }
            text.startsWith("cd ") -> {
                val target = text.removePrefix("cd ").trim().trim('"')
                val next = if (target.startsWith("/")) File(target) else File(cwd, target)
                if (next.isDirectory) {
                    cwd = runCatching { next.canonicalPath }.getOrDefault(next.absolutePath)
                } else {
                    lines.add("cd: ${next.absolutePath}: 不是目录")
                }
                return
            }
        }
        running = true
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val pb = ProcessBuilder("/system/bin/sh", "-c", "cd \"$cwd\" && $text")
                    pb.redirectErrorStream(true)
                    val p = pb.start()
                    val output = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    output
                }.getOrElse { "执行失败：${it.message}\n" }
            }
            lines.addAll(out.trimEnd().split('\n').takeIf { out.isNotBlank() } ?: emptyList())
            if (out.isBlank()) lines.add("（无输出）")
            lines.add("")
            while (lines.size > 3000) lines.removeAt(0)
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
            Modifier.fillMaxWidth().height(54.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text("终端", color = SunColors.TextPrimary, fontSize = 12.5.sp)
                Text(
                    cwd,
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
            items(lines) { line ->
                Text(
                    line.ifEmpty { " " },
                    color = when {
                        line.startsWith("$cwd $") -> SunColors.Accent
                        line.startsWith("执行失败") || line.contains("不是目录") -> SunColors.Danger
                        else -> SunColors.TextSecondary
                    },
                    fontSize = 9.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(SunColors.Surface2).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$", color = SunColors.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = SunColors.TextPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier
                    .weight(1f)
                    .background(SunColors.Surface3, SunShapes.Small)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (running) "…" else "运行",
                color = if (running) SunColors.TextTertiary else SunColors.Accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(SunColors.AccentSoft, SunShapes.Small)
                    .clickable(enabled = !running) {
                        run(input)
                        input = ""
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
