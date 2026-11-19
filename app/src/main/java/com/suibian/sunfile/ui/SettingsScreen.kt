package com.suibian.sunfile.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.BuildConfig
import com.suibian.sunfile.data.AppPrefs
import com.suibian.sunfile.update.ReleaseInfo
import com.suibian.sunfile.update.UpdateState
import com.suibian.sunfile.ui.theme.NumberStyle
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
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
    onAbout: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).background(SunColors.Surface1).padding(horizontal = 4.dp),
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
            Text("设置", color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("浏览")
            SwitchRow("显示隐藏文件", "以 . 开头的文件和目录", showHidden, onToggleHidden)
            ValueRow("排序方式", "${prefs.sort.mode.label}${if (prefs.sort.ascending) " · 升序" else " · 降序"}", onSort)

            SectionTitle("更新")
            SwitchRow("启动时检查更新", "打开应用后自动读取 GitHub Release", prefs.autoCheckUpdate, onToggleAutoCheck)
            SwitchRow("下载加速", "优先使用公共镜像线路，失败自动回退", prefs.useAccelerator, onToggleAccelerator)
            ValueRow("立即检查更新", "当前 v${BuildConfig.VERSION_NAME}", onCheckUpdate)

            SectionTitle("编辑")
            InfoRow("修改历史", "保存前自动留档，单个文件保留最近 30 个版本")

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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = SunColors.TextTertiary,
        fontSize = 9.5.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SunColors.TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = SunColors.TextTertiary, fontSize = 9.5.sp)
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
        Text(title, color = SunColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = SunColors.TextTertiary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = SunColors.TextTertiary,
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
    onDismiss: () -> Unit
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
                fontSize = 15.sp,
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
                        Spacer(Modifier.height(10.dp))
                        Text("更新说明", color = SunColors.TextSecondary, fontSize = 10.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.info.notes.take(1200),
                            color = SunColors.TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SunShapes.Small)
                                .background(SunColors.Bg)
                                .padding(9.dp)
                        )
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
