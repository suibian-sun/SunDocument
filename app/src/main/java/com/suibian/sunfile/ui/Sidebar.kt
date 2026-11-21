package com.suibian.sunfile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.suibian.sunfile.data.LocalMount
import com.suibian.sunfile.data.NetworkMount
import com.suibian.sunfile.data.StorageInfo
import com.suibian.sunfile.data.StorageStats
import com.suibian.sunfile.ui.components.pressScale
import com.suibian.sunfile.ui.theme.Motion
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.ui.theme.ThemeMode
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 侧边栏「工具」区的入口 */
enum class SideTool(val label: String, val icon: ImageVector, val hint: String = "") {
    PLUGINS("插件管理", Icons.Outlined.Extension, "扩展能力"),
    REMOTE("远程管理", Icons.Outlined.Router, "局域网访问"),
    APPS("已安装应用", Icons.Outlined.Apps),
    EDITOR("文本编辑器", Icons.Outlined.Description),
    TERMINAL("终端模拟器", Icons.Outlined.Terminal),
    MCP("Mcp 服务", Icons.Outlined.Dns, "本地接口"),
    MORE("更多工具", Icons.Outlined.Tune)
}

/** 「更多工具」弹窗里的条目 */
enum class MoreTool(val label: String, val icon: ImageVector, val desc: String) {
    COLOR("屏幕取色", Icons.Outlined.Palette, "吸取屏幕任意位置的色值"),
    SIGN("签名密钥", Icons.Outlined.Fingerprint, "生成密钥并为 APK 重签名"),
    SMALI("Smali 指令", Icons.Outlined.Code, "指令速查与语法说明"),
    PASSWORD("常用密码", Icons.Outlined.Key, "本地加密保管常用口令"),
    MUSIC("音乐播放器", Icons.Outlined.MusicNote, "后台播放本地音频"),
    STREAM("流媒体播放", Icons.Outlined.PlayCircleOutline, "播放网络地址的音视频"),
    PAYLOAD("远程 Payload", Icons.Outlined.Send, "向远端接口发送请求并查看响应")
}

/** 默认显示的本地入口：根目录与内部存储 */
private val BUILTIN_LOCALS = listOf(
    LocalMount("根目录", "/"),
    LocalMount("内部存储", "/storage/emulated/0")
)

@Composable
fun SidebarContent(
    currentPath: String,
    version: String,
    dark: Boolean,
    themeMode: ThemeMode,
    locals: List<LocalMount>,
    networks: List<NetworkMount>,
    groups: List<String>,
    onNavigate: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onFollowSystem: () -> Unit,
    onAddLocal: () -> Unit,
    onAddNetwork: () -> Unit,
    onAddGroup: () -> Unit,
    onManageGroups: () -> Unit,
    onSettings: () -> Unit,
    onOpenTool: (SideTool) -> Unit,
    onOpenMore: (MoreTool) -> Unit,
    onOpenNetwork: (NetworkMount) -> Unit,
    onRemoveLocal: (String) -> Unit,
    onRemoveNetwork: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    // 容量信息按路径缓存，侧边栏每次打开只重新读一次，不随重组反复 statfs
    val stats = remember { mutableStateMapOf<String, StorageInfo?>() }
    LaunchedEffect(locals) {
        val paths = (BUILTIN_LOCALS + locals).map { it.path }.distinct()
        val loaded = withContext(Dispatchers.IO) { paths.associateWith { StorageStats.of(it) } }
        stats.putAll(loaded)
    }

    Column(Modifier.fillMaxHeight().background(SunColors.Bg)) {
        // ---------- 顶部固定导航栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.TopBar)
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SunColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "S",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SunDocument",
                    color = SunColors.OnTopBar,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "v$version · 双栏文件管理",
                    color = SunColors.OnTopBarMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            // 白天 / 黑夜切换
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (dark) "切换到白天" else "切换到黑夜",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(19.dp)
                )
            }
            // 竖向三点 → 工具栏
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "工具栏",
                        tint = SunColors.OnTopBar,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(SunColors.Surface4, SunShapes.Medium)
                ) {
                    MenuRow(
                        Icons.Outlined.BrightnessAuto,
                        "主题跟随系统",
                        checked = themeMode == ThemeMode.SYSTEM
                    ) {
                        menuOpen = false
                        onFollowSystem()
                    }
                    MenuRow(Icons.Outlined.Cloud, "添加网络存储") {
                        menuOpen = false
                        onAddNetwork()
                    }
                    MenuRow(Icons.Outlined.CreateNewFolder, "添加本地存储") {
                        menuOpen = false
                        onAddLocal()
                    }
                    MenuRow(Icons.Outlined.Add, "添加网络分组") {
                        menuOpen = false
                        onAddGroup()
                    }
                    MenuRow(Icons.Outlined.Tune, "管理工具分组") {
                        menuOpen = false
                        onManageGroups()
                    }
                    MenuRow(Icons.Outlined.Settings, "设置") {
                        menuOpen = false
                        onSettings()
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // ---------- 本地存储 ----------
            SectionTitle("本地存储")
            (BUILTIN_LOCALS + locals).forEach { mount ->
                val builtIn = locals.none { it.path == mount.path }
                StorageRow(
                    icon = if (mount.path == "/") Icons.Outlined.Folder else Icons.Outlined.Smartphone,
                    name = mount.name,
                    path = mount.path,
                    info = stats[mount.path],
                    active = currentPath == mount.path,
                    onOpen = { onNavigate(mount.path) },
                    onRemove = if (builtIn) null else ({ onRemoveLocal(mount.path) })
                )
            }

            // ---------- 网络存储 ----------
            SectionTitle(
                "网络存储",
                action = if (networks.isEmpty()) null else "添加",
                onAction = onAddNetwork
            )
            if (networks.isEmpty()) {
                EmptyHint("还没有网络存储，点右上角三点添加")
            } else {
                val order = groups.ifEmpty { listOf(com.suibian.sunfile.data.DEFAULT_GROUP) }
                val byGroup = networks.groupBy { it.group }
                order.forEach { group ->
                    val list = byGroup[group] ?: return@forEach
                    if (order.size > 1) {
                        Text(
                            group,
                            color = SunColors.TextTertiary,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    list.forEach { net ->
                        NetworkRow(net, onOpen = { onOpenNetwork(net) }, onRemove = { onRemoveNetwork(net.uri) })
                    }
                }
                networks.filter { !order.contains(it.group) }.forEach { net ->
                    NetworkRow(net, onOpen = { onOpenNetwork(net) }, onRemove = { onRemoveNetwork(net.uri) })
                }
            }

            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))

            // ---------- 工具 ----------
            SectionTitle("工具")
            SideTool.entries.forEach { tool ->
                ToolRow(tool) {
                    if (tool == SideTool.MORE) moreOpen = true else onOpenTool(tool)
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(SunColors.Hairline))
        Text(
            "github.com/suibian-sun/SunDocument",
            color = SunColors.TextTertiary,
            fontSize = 9.sp,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }

    if (moreOpen) {
        MoreToolsDialog(
            onDismiss = { moreOpen = false },
            onPick = {
                moreOpen = false
                onOpenMore(it)
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = SunColors.TextTertiary,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            Text(
                action,
                color = SunColors.Accent,
                fontSize = 10.5.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { onAction() }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = SunColors.TextTertiary,
        fontSize = 10.sp,
        modifier = Modifier.padding(start = 22.dp, end = 14.dp, top = 2.dp, bottom = 6.dp)
    )
}

/** 一条存储：名称 / 路径 / 进度 / 已用与剩余 */
@Composable
private fun StorageRow(
    icon: ImageVector,
    name: String,
    path: String,
    info: StorageInfo?,
    active: Boolean,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val interaction = remember { MutableInteractionSource() }
    val ratio = info?.ratio ?: 0f
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(Motion.Slow, easing = Motion.EaseOut),
        label = "storageRatio"
    )
    val exists = remember(path) { File(path).isDirectory }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .clip(SunShapes.Small)
            .background(if (active) SunColors.AccentSoft else Color.Transparent)
            .pressScale(interaction, 0.985f)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(start = 9.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(
            icon,
            null,
            tint = if (!exists) SunColors.TextTertiary else if (active) SunColors.Accent else SunColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp).size(17.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = if (active) SunColors.Accent else SunColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (info != null && info.total > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${(info.ratio * 100).toInt()}%",
                        color = SunColors.TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                path,
                color = SunColors.TextTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = if (ratio > 0.9f) SunColors.Danger else SunColors.Accent,
                trackColor = SunColors.Surface3
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "已用 ${info?.used?.let { Fmt.size(it) } ?: "—"}",
                    color = SunColors.TextSecondary,
                    fontSize = 9.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "剩余 ${info?.available?.let { Fmt.size(it) } ?: "—"}",
                    color = SunColors.TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "移除",
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** 网络存储：只显示名称与链接 */
@Composable
private fun NetworkRow(net: NetworkMount, onOpen: () -> Unit, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .clip(SunShapes.Small)
            .pressScale(interaction, 0.985f)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(start = 22.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Cloud,
            null,
            tint = SunColors.TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                net.name,
                color = SunColors.TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                net.uri,
                color = SunColors.TextTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                "移除",
                tint = SunColors.TextTertiary,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

@Composable
private fun ToolRow(tool: SideTool, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 0.5.dp)
            .clip(SunShapes.Small)
            .pressScale(interaction, 0.985f)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 9.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            tool.icon,
            null,
            tint = SunColors.TextSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            tool.label,
            color = SunColors.TextPrimary,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        if (tool.hint.isNotBlank()) {
            Text(tool.hint, color = SunColors.TextTertiary, fontSize = 9.sp)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint = SunColors.TextTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    checked: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = SunColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (checked) Text("✓", color = SunColors.Accent, fontSize = 13.sp)
            }
        },
        leadingIcon = {
            Icon(icon, null, tint = SunColors.TextSecondary, modifier = Modifier.size(17.dp))
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp)
    )
}

/** 更多工具：一屏列完七个入口，不再多一层跳转 */
@Composable
private fun MoreToolsDialog(onDismiss: () -> Unit, onPick: (MoreTool) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SunShapes.Medium)
                .background(SunColors.Surface2)
                .padding(vertical = 6.dp)
        ) {
            Text(
                "更多工具",
                color = SunColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 6.dp)
            )
            MoreTool.entries.forEach { tool ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = interaction, indication = null) { onPick(tool) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(tool.icon, null, tint = SunColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tool.label, color = SunColors.TextPrimary, fontSize = 13.sp)
                        Text(tool.desc, color = SunColors.TextTertiary, fontSize = 9.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
