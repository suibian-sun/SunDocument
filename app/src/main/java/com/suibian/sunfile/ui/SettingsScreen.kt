package com.suibian.sunfile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
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
    apply: ((AppPrefs) -> AppPrefs) -> Unit,
    onBack: () -> Unit,
    onRequestRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
    onCheckUpdate: () -> Unit,
    onChangelog: () -> Unit,
    onAbout: () -> Unit,
    onOpenSource: () -> Unit,
    onThanks: () -> Unit,
    onAppInfo: () -> Unit,
    onTrash: () -> Unit,
    dataDir: String = ""
) {
    val context = LocalContext.current
    val engine by Shell.engineState.collectAsState()
    var dialog by remember { mutableStateOf<TextSetting?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.TopBar)
                .statusBarsPadding()
                .height(com.suibian.sunfile.ui.theme.Mt.BarHeight)
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
            Text(
                "设置",
                color = SunColors.OnTopBar,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ---------------- 启动 ----------------
            Category("启动")
            ActionRow(
                "请求 Root 权限",
                if (engine == ShellEngine.SU) "已授权" else "未授权",
                onRequestRoot
            )
            ActionRow(
                "请求 Shell 权限",
                when (engine) {
                    ShellEngine.SHIZUKU -> "已授权"
                    else -> ShizukuBridge.statusText(context)
                },
                onRequestShizuku
            )
            ValueRow("自定义 su 命令", prefs.customSuCommand) { dialog = TextSetting.Su }
            ActionRow(
                "安装 Shizuku",
                if (ShizukuBridge.installed(context)) "已安装" else "未安装",
                onInstallShizuku
            )
            ValueRow("启动路径 · 左窗口", prefs.startPathLeft.ifBlank { "上次位置" }) {
                dialog = TextSetting.StartLeft
            }
            ValueRow("启动路径 · 右窗口", prefs.startPathRight.ifBlank { "上次位置" }) {
                dialog = TextSetting.StartRight
            }
            SwitchRow("自动启动服务", prefs.autoStartService) {
                apply { it.copy(autoStartService = !it.autoStartService) }
            }

            Divider()
            // ---------------- 外观 ----------------
            Category("外观")
            ThemeSegmented(prefs.themeMode) { mode -> apply { it.copy(themeMode = mode) } }
            AccentRow(prefs.accentColor) { color -> apply { it.copy(accentColor = color) } }
            ValueRow(
                "文件列表大小",
                when (prefs.fileListSize) {
                    -1 -> "紧凑"
                    1 -> "宽松"
                    else -> "标准"
                }
            ) {
                apply {
                    it.copy(fileListSize = when (it.fileListSize) {
                        -1 -> 0; 0 -> 1; else -> -1
                    })
                }
            }
            ValueRow("文件名最大行数", "${prefs.fileNameMaxLines} 行") {
                apply { it.copy(fileNameMaxLines = if (it.fileNameMaxLines >= 3) 1 else it.fileNameMaxLines + 1) }
            }
            ValueRow("文件列表时间偏好", if (prefs.timeByCreated) "创建时间" else "修改时间") {
                apply { it.copy(timeByCreated = !it.timeByCreated) }
            }
            SwitchRow("文件列表不显示权限", prefs.hidePermissions) {
                apply { it.copy(hidePermissions = !it.hidePermissions) }
            }
            ValueRow("时间日期格式", prefs.dateFormat) { dialog = TextSetting.DateFormat }
            ActionRow("桌面图标", "由系统桌面决定", onAppInfo)

            Divider()
            // ---------------- 常规 ----------------
            Category("常规")
            SwitchRow("生成备份文件", prefs.backupBeforeSave) {
                apply { it.copy(backupBeforeSave = !it.backupBeforeSave) }
            }
            SwitchRow("保留文件时间", prefs.keepFileTime) {
                apply { it.copy(keepFileTime = !it.keepFileTime) }
            }
            SwitchRow("启用输入框收藏", prefs.inputFavorites) {
                apply { it.copy(inputFavorites = !it.inputFavorites) }
            }
            SwitchRow("文件列表显示隐藏文件", prefs.showHidden) {
                apply { it.copy(showHidden = !it.showHidden) }
            }
            ValueRow("排序", "${prefs.sort.mode.label}${if (prefs.sort.ascending) " · 升序" else " · 降序"}") {
                apply { it.copy(sort = it.sort.nextFor(it.sort.mode)) }
            }
            ValueRow("自定义 MT2 目录", prefs.customMt2Dir.ifBlank { dataDir.ifBlank { "默认" } }) {
                dialog = TextSetting.Mt2Dir
            }
            ValueRow("编辑器字号", "${prefs.editorFontSize} sp") {
                apply {
                    it.copy(editorFontSize = when {
                        it.editorFontSize < 14 -> 14
                        it.editorFontSize < 16 -> 16
                        it.editorFontSize < 18 -> 18
                        else -> 12
                    })
                }
            }
            SwitchRow("编辑器自动换行", prefs.editorWrap) {
                apply { it.copy(editorWrap = !it.editorWrap) }
            }
            SwitchRow("编辑器行号", prefs.editorLineNumbers) {
                apply { it.copy(editorLineNumbers = !it.editorLineNumbers) }
            }
            SwitchRow("编辑器代码补全", prefs.editorCompletion) {
                apply { it.copy(editorCompletion = !it.editorCompletion) }
            }

            Divider()
            // ---------------- 回收站 ----------------
            Category("回收站")
            SwitchRow("启用回收站", prefs.trashEnabled) {
                apply { it.copy(trashEnabled = !it.trashEnabled) }
            }
            SwitchRow("默认移动到回收站", prefs.defaultMoveToTrash) {
                apply { it.copy(defaultMoveToTrash = !it.defaultMoveToTrash) }
            }
            ValueRow(
                "自动清理",
                if (prefs.trashAutoCleanDays <= 0) "不清理" else "${prefs.trashAutoCleanDays} 天前"
            ) {
                apply {
                    it.copy(trashAutoCleanDays = when (it.trashAutoCleanDays) {
                        0 -> 7; 7 -> 30; 30 -> 90; else -> 0
                    })
                }
            }
            SwitchRow("显示删除警告", prefs.showDeleteWarning) {
                apply { it.copy(showDeleteWarning = !it.showDeleteWarning) }
            }
            ActionRow("打开回收站", "", onTrash)

            Divider()
            // ---------------- 安装 ----------------
            Category("安装")
            SwitchRow("APK 安装验证", prefs.verifyApk) {
                apply { it.copy(verifyApk = !it.verifyApk) }
            }
            SwitchRow("安装后删除安装包", prefs.deleteApkAfterInstall) {
                apply { it.copy(deleteApkAfterInstall = !it.deleteApkAfterInstall) }
            }
            SwitchRow("安装前二次确认", prefs.confirmInstall) {
                apply { it.copy(confirmInstall = !it.confirmInstall) }
            }
            SwitchRow("使用 Shizuku 安装", prefs.installViaShizuku) {
                apply { it.copy(installViaShizuku = !it.installViaShizuku) }
            }
            SwitchRow("使用 Dhizuku 安装", prefs.installViaDhizuku) {
                apply { it.copy(installViaDhizuku = !it.installViaDhizuku) }
            }
            SwitchRow("使用 Root 安装", prefs.installViaRoot) {
                apply { it.copy(installViaRoot = !it.installViaRoot) }
            }
            ValueRow("自定义系统安装器", prefs.customInstaller.ifBlank { "系统默认" }) {
                dialog = TextSetting.Installer
            }
            InfoRow(
                "安装优先级",
                "按 Shizuku → Root → 系统安装器依次尝试，前一个不可用才交给下一个。"
            )

            Divider()
            // ---------------- 书签与底栏 ----------------
            Category("书签与底栏")
            SwitchRow("侧边栏显示书签", prefs.sidebarBookmarks) {
                apply { it.copy(sidebarBookmarks = !it.sidebarBookmarks) }
            }
            SwitchRow("新书签置顶", prefs.bookmarkToTop) {
                apply { it.copy(bookmarkToTop = !it.bookmarkToTop) }
            }
            SwitchRow("书签上滑分左右", prefs.bookmarkSwipeSplit) {
                apply { it.copy(bookmarkSwipeSplit = !it.bookmarkSwipeSplit) }
            }
            SwitchRow("底栏增加下边距", prefs.bottomBarExtra) {
                apply { it.copy(bottomBarExtra = !it.bottomBarExtra) }
            }
            ValueRow("下边距大小", "${prefs.bottomBarPadding} dp") {
                apply {
                    it.copy(
                        bottomBarPadding = when (it.bottomBarPadding) {
                            0 -> 4; 4 -> 8; 8 -> 12; 12 -> 16; else -> 0
                        }
                    )
                }
            }

            Divider()
            // ---------------- 外置存储 ----------------
            Category("外置存储")
            SwitchRow("加载缩略图", prefs.externalThumbs) {
                apply { it.copy(externalThumbs = !it.externalThumbs) }
            }
            SwitchRow("优化数据传输", prefs.optimizeExternal) {
                apply { it.copy(optimizeExternal = !it.optimizeExternal) }
            }

            Divider()
            // ---------------- 网络存储 ----------------
            Category("网络存储")
            SwitchRow("加载缩略图", prefs.networkThumbs) {
                apply { it.copy(networkThumbs = !it.networkThumbs) }
            }
            SwitchRow("仅在 WLAN 下加载", prefs.networkWifiOnly) {
                apply { it.copy(networkWifiOnly = !it.networkWifiOnly) }
            }
            ValueRow("图片大小限制", "${prefs.networkImageLimitKb} KB") {
                apply {
                    it.copy(
                        networkImageLimitKb = when (it.networkImageLimitKb) {
                            256 -> 512; 512 -> 1024; 1024 -> 2048; else -> 256
                        }
                    )
                }
            }
            ValueRow("加载时间限制", "${prefs.networkTimeoutSec} 秒") {
                apply {
                    it.copy(
                        networkTimeoutSec = when (it.networkTimeoutSec) {
                            5 -> 10; 10 -> 15; 15 -> 30; else -> 5
                        }
                    )
                }
            }

            Divider()
            // ---------------- 其他 ----------------
            Category("其他")
            ValueRow("语言", if (prefs.language == "zh") "简体中文" else "English") {
                apply { it.copy(language = if (it.language == "zh") "en" else "zh") }
            }
            SwitchRow("启动时检查更新", prefs.autoCheckUpdate) {
                apply { it.copy(autoCheckUpdate = !it.autoCheckUpdate) }
            }
            SwitchRow("下载加速", prefs.useAccelerator) {
                apply { it.copy(useAccelerator = !it.useAccelerator) }
            }
            ActionRow("检测更新", "v${BuildConfig.VERSION_NAME}", onCheckUpdate)
            ActionRow("历史更新", "", onChangelog)
            ActionRow("特别鸣谢", "", onThanks)
            ActionRow("开源地址", "", onOpenSource)

            Spacer(Modifier.height(32.dp))
            Text(
                "SunDocument · Kotlin + Jetpack Compose",
                color = SunColors.TextTertiary,
                fontSize = 9.5.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    dialog?.let { kind ->
        TextSettingDialog(
            kind = kind,
            initial = when (kind) {
                TextSetting.Su -> prefs.customSuCommand
                TextSetting.StartLeft -> prefs.startPathLeft
                TextSetting.StartRight -> prefs.startPathRight
                TextSetting.DateFormat -> prefs.dateFormat
                TextSetting.Mt2Dir -> prefs.customMt2Dir
                TextSetting.Installer -> prefs.customInstaller
            },
            onDismiss = { dialog = null },
            onConfirm = { value ->
                apply {
                    when (kind) {
                        TextSetting.Su -> it.copy(customSuCommand = value)
                        TextSetting.StartLeft -> it.copy(startPathLeft = value)
                        TextSetting.StartRight -> it.copy(startPathRight = value)
                        TextSetting.DateFormat -> it.copy(dateFormat = value)
                        TextSetting.Mt2Dir -> it.copy(customMt2Dir = value)
                        TextSetting.Installer -> it.copy(customInstaller = value)
                    }
                }
                dialog = null
            }
        )
    }
}

/** 需要弹出文本输入的设置项 */
private enum class TextSetting { Su, StartLeft, StartRight, DateFormat, Mt2Dir, Installer }

@Composable
private fun TextSettingDialog(
    kind: TextSetting,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(kind) { mutableStateOf(initial) }
    val title = when (kind) {
        TextSetting.Su -> "自定义 su 命令"
        TextSetting.StartLeft -> "左窗口启动路径"
        TextSetting.StartRight -> "右窗口启动路径"
        TextSetting.DateFormat -> "时间日期格式"
        TextSetting.Mt2Dir -> "自定义 MT2 目录"
        TextSetting.Installer -> "自定义系统安装器"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface2,
        shape = SunShapes.Medium,
        title = { Text(title, color = SunColors.TextPrimary, fontSize = 15.sp) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = SunColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SunColors.Surface3, SunShapes.Small)
                    .padding(10.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("确定", color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = SunColors.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun Category(text: String) {
    Text(
        text,
        color = SunColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp)
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(1.dp)
            .background(SunColors.Hairline)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = SunColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
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
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SunColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(
                value,
                color = SunColors.TextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Icon(
            Icons.Outlined.ArrowForward,
            null,
            tint = SunColors.TextTertiary.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SunColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = SunColors.TextTertiary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SunColors.TextSecondary, fontSize = 12.sp)
            Text(
                subtitle,
                color = SunColors.TextTertiary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 主题色：几个克制的预设，避免用户调出对比度灾难 */
@Composable
private fun AccentRow(current: Long, onPick: (Long) -> Unit) {
    val colors = listOf(
        0L to "默认",
        0xFF4A90F7 to "蓝",
        0xFF3FB950 to "绿",
        0xFFD9A93F to "琥珀",
        0xFFE5534B to "红",
        0xFFC792EA to "紫",
        0xFF3FC1C9 to "青"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("主题颜色", color = SunColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { (value, _) ->
                val on = current == value
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (value == 0L) SunColors.Accent else Color(value),
                            CircleShape
                        )
                        .clickable { onPick(value) },
                    contentAlignment = Alignment.Center
                ) {
                    if (on) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
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
