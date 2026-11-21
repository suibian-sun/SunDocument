package com.suibian.sunfile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.suibian.sunfile.BuildConfig
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.data.Shell
import com.suibian.sunfile.data.ShellEngine
import com.suibian.sunfile.data.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.suibian.sunfile.update.ReleaseInfo
import com.suibian.sunfile.update.UpdateState
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.ui.theme.ThemeMode
import com.suibian.sunfile.util.Fmt
import java.io.File

@Composable
fun SettingsPage(
    prefs: AppPrefs,
    showHidden: Boolean,
    onBack: () -> Unit,
    onToggleHidden: () -> Unit,
    onSort: () -> Unit,
    onToggleAutoCheck: () -> Unit,
    onToggleAccelerator: () -> Unit,
    onCheckUpdate: () -> Unit,
    onEditorPrefs: (Int?, Boolean?, Boolean?, Boolean?) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onClearOpenWith: () -> Unit,
    onClearRecent: () -> Unit,
    onRequestRoot: () -> Unit,
    onAbout: () -> Unit,
    dataDir: String = "",
    onOpenWithManager: () -> Unit = {},
    onChangelog: () -> Unit = {},
    onTrash: () -> Unit = {},
    onHtmlMode: (String) -> Unit = {},
    onPlugins: () -> Unit = {},
    onMcp: () -> Unit = {},
    onInspect: () -> Unit = {},
    onMtPlugins: () -> Unit = {}
) {
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
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text("设置", color = SunColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("外观")
            ThemeSegmented(prefs.themeMode, onThemeChange)

            SectionTitle("浏览")
            SwitchRow("显示隐藏文件", "以 . 开头的文件和目录", showHidden, onToggleHidden)
            ValueRow("排序方式", "${prefs.sort.mode.label}${if (prefs.sort.ascending) " · 升序" else " · 降序"}", onSort)

            SectionTitle("更新")
            SwitchRow("启动时检查更新", "打开应用后自动读取 GitHub Release", prefs.autoCheckUpdate, onToggleAutoCheck)
            SwitchRow("下载加速", "优先使用公共镜像线路，失败自动回退", prefs.useAccelerator, onToggleAccelerator)
            ValueRow("检查更新", "v${BuildConfig.VERSION_NAME}", onCheckUpdate)
            ValueRow("历史更新", "", onChangelog)
            ValueRow("回收站", "", onTrash)
            ValueRow("插件", "", onPlugins)
            ValueRow("MT 插件", "", onMtPlugins)

            SectionTitle("开发者")
            ValueRow("界面分析", "", onInspect)
            ValueRow("MCP 服务", "", onMcp)

            ValueRow(
                "网页与 Markdown",
                when (prefs.htmlMode) {
                    "PREVIEW" -> "点击直接预览"
                    "EDIT" -> "点击直接编辑"
                    else -> "每次询问"
                }
            ) {
                onHtmlMode(
                    when (prefs.htmlMode) {
                        "ASK" -> "PREVIEW"
                        "PREVIEW" -> "EDIT"
                        else -> "ASK"
                    }
                )
            }

            SectionTitle("编辑器")
            ValueRow(
                "字号",
                "${prefs.editorFontSize} sp · 点击切换",
                onClick = {
                    val next = when {
                        prefs.editorFontSize < 14 -> 14
                        prefs.editorFontSize < 16 -> 16
                        prefs.editorFontSize < 18 -> 18
                        else -> 12
                    }
                    onEditorPrefs(next, null, null, null)
                }
            )
            SwitchRow("自动换行", "长行自动折行显示", prefs.editorWrap) {
                onEditorPrefs(null, !prefs.editorWrap, null, null)
            }
            SwitchRow("显示行号", "关闭后更适合阅读长段落", prefs.editorLineNumbers) {
                onEditorPrefs(null, null, !prefs.editorLineNumbers, null)
            }
            SwitchRow("代码补全", "按上下文给出关键字、符号与片段", prefs.editorCompletion) {
                onEditorPrefs(null, null, null, !prefs.editorCompletion)
            }
            InfoRow("修改历史", "保存前自动留档，单个文件保留最近 30 个版本")

            SectionTitle("存储访问")
            StorageAccessRows(onGranted = onRequestRoot)

            SectionTitle("打开方式")
            ValueRow("默认打开方式", "${prefs.openWith.size} 个", onOpenWithManager)
            ValueRow("清除记住的打开方式", "", onClearOpenWith)
            ValueRow("清除最近访问", "${prefs.recent.size} 条记录", onClearRecent)

            SectionTitle("数据目录")
            InfoRow("SunMT2", dataDir.ifEmpty { "初始化中…" })

            SectionTitle("关于")
            ValueRow("版本", "v${BuildConfig.VERSION_NAME}", onAbout)
            ValueRow("开源地址", "github.com/suibian-sun/SunDocument", onAbout)

            Spacer(Modifier.height(28.dp))
            Text(
                "SunDocument · Kotlin + Jetpack Compose",
                color = SunColors.TextTertiary,
                fontSize = 9.5.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * 存储访问：把「怎么读受限目录」讲清楚，并给出两条通道。
 *
 * Shizuku 是主角——它不需要设备 root，只要装一个用 adb 启动过的 Shizuku 服务，
 * 我们的文件操作就会被代理到 shell 进程执行。普通应用读不到 / 和 /system，
 * 而 shell 身份可以。
 */
@Composable
private fun StorageAccessRows(onGranted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by Shell.engineState.collectAsState()
    var shizukuText by remember { mutableStateOf(ShizukuBridge.statusText(context)) }

    // 授权框是 Shizuku 自己弹的，结果只能从它的回调里拿。
    // 真正的接管与刷新由主界面统一处理，这里只负责把文案改过来。
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = {
            shizukuText = ShizukuBridge.statusText(context)
        }
        ShizukuBridge.addResultListener(listener)
        onDispose { ShizukuBridge.removeResultListener(listener) }
    }

    ValueRow("Shizuku 通道", shizukuText) {
        when {
            // 没装：直接送到官方发布页，避免用户下到仿冒包
            !ShizukuBridge.installed(context) -> {
                val ok = runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.isSuccess
                toast(context, if (ok) "请先安装 Shizuku 并启动服务" else "未安装 Shizuku")
            }
            // 装了但服务没起：唤起 Shizuku 让它自己提示启动方式
            !ShizukuBridge.running() -> {
                val ok = runCatching {
                    val i = context.packageManager.getLaunchIntentForPackage(ShizukuBridge.PACKAGE)
                        ?: error("no launcher")
                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                toast(context, if (ok) "在 Shizuku 里点「启动」后再回来" else "Shizuku 服务未运行")
            }
            !ShizukuBridge.permitted() -> {
                if (!ShizukuBridge.requestPermission()) toast(context, "无法请求授权")
            }
            else -> scope.launch {
                val ok = withContext(Dispatchers.IO) { Shell.requestShizuku() }
                shizukuText = ShizukuBridge.statusText(context)
                onGranted()
                toast(context, if (ok) "Shizuku 已接管文件操作" else "Shizuku 通道不可用")
            }
        }
    }

    ValueRow(
        "Root 通道",
        if (engine == ShellEngine.SU) "已授权 · 可读全部目录" else "未授权 · 点击申请",
        onGranted
    )

    if (engine != ShellEngine.NONE) {
        InfoRow("当前引擎", Shell.statusText)
    } else {
        InfoRow(
            "说明",
            "Shizuku 以 shell 身份运行，能读根目录与系统分区；/data/data 下的应用私有目录仍需 Root。"
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = SunColors.TextTertiary,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 7.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SunColors.TextPrimary, fontSize = 14.5.sp)
            Text(subtitle, color = SunColors.TextTertiary, fontSize = 10.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SunColors.Accent,
                uncheckedThumbColor = SunColors.TextSecondary,
                uncheckedTrackColor = SunColors.Surface4,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SunColors.TextPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(
                value,
                color = SunColors.TextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        Icon(
            androidx.compose.material.icons.Icons.Outlined.ArrowForward,
            null,
            tint = SunColors.TextTertiary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SunColors.TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = SunColors.TextTertiary, fontSize = 9.5.sp, lineHeight = 14.sp)
        }
    }
}

// ---------------- 更新弹窗 ----------------

@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onDownload: (ReleaseInfo) -> Unit,
    onInstall: (File) -> Unit,
    onOpenPermission: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onHistory: () -> Unit = {}
) {
    if (state is UpdateState.Idle) return
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        containerColor = SunColors.Surface4,
        shape = SunShapes.Medium,
        title = {
            Text(
                when (state) {
                    is UpdateState.Checking -> "检查更新"
                    is UpdateState.UpToDate -> "已是最新版本"
                    is UpdateState.Available -> "发现新版本 v${state.info.version}"
                    is UpdateState.Downloading -> "正在下载更新"
                    is UpdateState.Ready -> "下载完成"
                    is UpdateState.Error -> "更新失败"
                    else -> "检查更新"
                },
                color = SunColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when (state) {
                    is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(15.dp),
                            color = SunColors.Accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在连接 GitHub…", color = SunColors.TextSecondary, fontSize = 12.sp)
                    }

                    is UpdateState.UpToDate -> Text(
                        "当前 v${state.current} 已是最新版本。",
                        color = SunColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    is UpdateState.Available -> {
                        Text(
                            "v$currentVersion  →  v${state.info.version}",
                            color = SunColors.TextPrimary,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${Fmt.size(state.info.size)} · ${state.info.publishedAt.take(10)}",
                            color = SunColors.TextTertiary,
                            fontSize = 9.5.sp,
                            style = NumberStyle,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("更新内容", color = SunColors.TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "历史记录",
                                color = SunColors.Accent,
                                fontSize = 10.5.sp,
                                modifier = Modifier
                                    .clip(SunShapes.Small)
                                    .clickable { onHistory() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val notes = state.info.notes.ifBlank {
                            com.suibian.sunfile.data.Changelog.of(state.info.version)
                                ?.items?.joinToString("\n") { "- $it" } ?: ""
                        }
                        NotesBlock(notes)
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            "线路 ${state.attempt}/${state.routeCount}：${state.route}",
                            color = SunColors.TextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = SunColors.Accent,
                            trackColor = SunColors.Surface2
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${(state.progress * 100).toInt()}%  ${Fmt.size(state.downloaded)} / ${Fmt.size(state.total)}",
                                color = SunColors.TextPrimary,
                                style = NumberStyle
                            )
                            Text(
                                if (state.speed > 0) Fmt.speed(state.speed) else "连接中…",
                                color = SunColors.TextTertiary,
                                style = NumberStyle
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "下载完成后会自动调起系统安装程序",
                            color = SunColors.TextTertiary,
                            fontSize = 9.5.sp
                        )
                    }

                    is UpdateState.Ready -> {
                        Text(
                            if (state.needPermission) "安装包已就绪，需要先允许「安装未知来源应用」。"
                            else "安装包已就绪，正在调起系统安装程序…",
                            color = SunColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${state.apk.name}\n${Fmt.size(state.apk.length())} · v${state.info.version}",
                            color = SunColors.TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    is UpdateState.Error -> Text(
                        state.message,
                        color = SunColors.Danger,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onDownload(state.info) }) {
                    Text("立即更新", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Ready -> TextButton(
                    onClick = { if (state.needPermission) onOpenPermission() else onInstall(state.apk) }
                ) {
                    Text(if (state.needPermission) "去授权" else "立即安装", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Error -> TextButton(onClick = onRetry) {
                    Text("重试", color = SunColors.Accent, fontSize = 13.sp)
                }

                is UpdateState.Downloading -> TextButton(onClick = {}, enabled = false) {
                    Text("下载中…", color = SunColors.TextTertiary, fontSize = 13.sp)
                }

                else -> TextButton(onClick = onDismiss) {
                    Text("好的", color = SunColors.Accent, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            when {
                state is UpdateState.Downloading -> Text(
                    "后台下载中",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp
                )
                state is UpdateState.Ready && state.needPermission -> TextButton(onClick = { onInstall(state.apk) }) {
                    Text("重新安装", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
                else -> TextButton(onClick = onDismiss) {
                    Text("关闭", color = SunColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
private fun ThemeSegmented(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(SunShapes.Medium)
            .background(SunColors.Surface2)
            .padding(3.dp)
    ) {
        ThemeMode.values().forEach { mode ->
            val active = mode == current
            val bg by animateColorAsState(
                targetValue = if (active) SunColors.Surface4 else Color.Transparent,
                animationSpec = tween(200),
                label = "themeSeg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(SunShapes.Small)
                    .background(bg)
                    .clickable { onPick(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (active) SunColors.TextPrimary else SunColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}


/** 更新说明按行渲染：以 # 开头当小标题，- / * 当列表项，其余为正文 */
@Composable
private fun NotesBlock(notes: String) {
    val lines = notes.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.take(200)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SunShapes.Small)
            .background(SunColors.Bg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (lines.isEmpty()) {
            Text("本次更新未提供说明。", color = SunColors.TextTertiary, fontSize = 10.5.sp)
            return@Column
        }
        lines.forEach { raw ->
            val head = raw.startsWith("#")
            val bullet = raw.startsWith("- ") || raw.startsWith("* ") || raw.startsWith("• ")
            val text = raw.removePrefix("#").removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
            Row(Modifier.padding(vertical = if (head) 4.dp else 2.dp)) {
                if (bullet) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(SunColors.Accent)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text,
                    color = if (head) SunColors.TextPrimary else SunColors.TextSecondary,
                    fontSize = if (head) 11.5.sp else 10.5.sp,
                    fontWeight = if (head) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
