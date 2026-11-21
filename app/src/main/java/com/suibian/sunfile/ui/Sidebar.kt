package com.suibian.sunfile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.LocalMount
import com.suibian.sunfile.data.NetworkMount
import com.suibian.sunfile.data.StorageInfo
import com.suibian.sunfile.data.StorageStats
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.ThemeMode
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用内全部工具的目录。
 *
 * 侧边栏显示哪些、以什么顺序显示，都由设置里的「管理工具」决定；
 * 这里只负责定义「有哪些」，新增工具时只改这一处。
 */
enum class AppTool(
    val id: String,
    val label: String,
    val icon: ImageVector
) {
    PLUGINS("plugins", "插件", Icons.Outlined.Extension),
    TERMINAL("terminal", "终端", Icons.Outlined.Terminal),
    REMOTE("remote", "远程管理", Icons.Outlined.Router),
    APPS("apps", "已安装应用", Icons.Outlined.Apps),
    EDITOR("editor", "文本编辑器", Icons.Outlined.Description),
    MCP("mcp", "MCP 服务", Icons.Outlined.Dns),
    COLOR("color", "屏幕取色", Icons.Outlined.Palette),
    SIGN("sign", "签名密钥", Icons.Outlined.Fingerprint),
    SMALI("smali", "Smali 指令", Icons.Outlined.Code),
    PASSWORD("password", "常用密码", Icons.Outlined.Key),
    MUSIC("music", "音乐播放", Icons.Outlined.MusicNote),
    STREAM("stream", "流媒体", Icons.Outlined.PlayCircleOutline),
    PAYLOAD("payload", "远程 Payload", Icons.Outlined.Send),
    SYSINFO("sysinfo", "系统信息", Icons.Outlined.Memory),
    TRASH("trash", "回收站", Icons.Outlined.Delete);

    companion object {
        /** 首次安装默认露出的几个，其余在「管理工具」里开 */
        val DEFAULTS = listOf(PLUGINS, TERMINAL, REMOTE, APPS, EDITOR, MCP, SYSINFO)

        fun byId(id: String): AppTool? = entries.firstOrNull { it.id == id }

        /** 把偏好里存的 id 列表还原成工具，空列表按默认 */
        fun resolve(ids: List<String>): List<AppTool> {
            if (ids.isEmpty()) return DEFAULTS
            return ids.mapNotNull { byId(it) }
        }
    }
}

/** 默认显示的本地入口：根目录与内部存储 */
private val BUILTIN_LOCALS = listOf(
    LocalMount("根目录", "/"),
    LocalMount("内部存储", "/storage/emulated/0")
)

/**
 * 侧边栏。
 *
 * 整块与顶部导航栏同色，靠明度差与内容区拉开层次；
 * 三个区块各自可折叠，区块之间不再画分隔线 —— 标题本身就是分界。
 */
@Composable
fun SidebarContent(
    currentPath: String,
    dark: Boolean,
    themeMode: ThemeMode,
    locals: List<LocalMount>,
    networks: List<NetworkMount>,
    bookmarks: List<String>,
    showBookmarks: Boolean,
    tools: List<AppTool>,
    onNavigate: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onFollowSystem: () -> Unit,
    onAddLocal: () -> Unit,
    onAddNetwork: () -> Unit,
    onManageGroups: () -> Unit,
    onManageTools: () -> Unit,
    onSettings: () -> Unit,
    onOpenTool: (AppTool) -> Unit,
    onOpenNetwork: (NetworkMount) -> Unit,
    onRemoveLocal: (String) -> Unit,
    onRemoveNetwork: (NetworkMount) -> Unit,
    onOpenBookmark: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var localOpen by remember { mutableStateOf(true) }
    var netOpen by remember { mutableStateOf(true) }
    var toolOpen by remember { mutableStateOf(true) }

    // 容量信息按路径缓存：一次打开只 statfs 一遍
    val stats = remember { mutableStateMapOf<String, StorageInfo?>() }
    LaunchedEffect(locals) {
        val paths = (BUILTIN_LOCALS + locals).map { it.path }.distinct()
        val loaded = withContext(Dispatchers.IO) { paths.associateWith { StorageStats.of(it) } }
        stats.putAll(loaded)
    }

    Column(
        Modifier
            .fillMaxHeight()
            .background(SunColors.TopBar)
            .verticalScroll(rememberScrollState())
    ) {
        // ---------- 顶部固定区 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(SunColors.OnTopBar.copy(alpha = 0.10f), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "S",
                    color = SunColors.OnTopBar,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "SunDocument",
                color = SunColors.OnTopBar,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            ThemePulseButton(dark = dark, onClick = onToggleTheme)

            Box {
                SideIconButton(Icons.Outlined.MoreVert) { menuOpen = true }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(SunColors.Surface3)
                ) {
                    MenuCheckItem(
                        label = "主题跟随系统",
                        checked = themeMode == ThemeMode.SYSTEM
                    ) {
                        onFollowSystem()
                        menuOpen = false
                    }
                    MenuAction("添加网络存储") { menuOpen = false; onAddNetwork() }
                    MenuAction("添加本地存储") { menuOpen = false; onAddLocal() }
                    MenuAction("管理网络分组") { menuOpen = false; onManageGroups() }
                    MenuAction("管理工具") { menuOpen = false; onManageTools() }
                    MenuAction("设置") { menuOpen = false; onSettings() }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---------- 本地存储 ----------
        SectionHeader("本地存储", localOpen) { localOpen = !localOpen }
        AnimatedVisibility(localOpen, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                (BUILTIN_LOCALS + locals).forEach { mount ->
                    val removable = locals.any { it.path == mount.path && it.name == mount.name }
                    LocalRow(
                        mount = mount,
                        info = stats[mount.path],
                        active = currentPath == mount.path,
                        showUsage = !removable,
                        onClick = { onNavigate(mount.path) },
                        onRemove = if (removable) ({ onRemoveLocal(mount.path) }) else null
                    )
                }
            }
        }

        // ---------- 网络存储 ----------
        SectionHeader("网络存储", netOpen) { netOpen = !netOpen }
        AnimatedVisibility(netOpen, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                if (networks.isEmpty()) {
                    EmptyHint("长按顶部三点可添加")
                } else {
                    networks.forEach { net ->
                        NetworkRow(
                            net = net,
                            onClick = { onOpenNetwork(net) },
                            onRemove = { onRemoveNetwork(net) }
                        )
                    }
                }
            }
        }

        // ---------- 书签 ----------
        if (showBookmarks && bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(start = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Star,
                    null,
                    tint = SunColors.OnTopBarMuted,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("书签", color = SunColors.OnTopBarMuted, fontSize = 10.sp, letterSpacing = 0.6.sp)
            }
            bookmarks.take(6).forEach { path ->
                BookmarkRow(path, currentPath == path) { onOpenBookmark(path) }
            }
        }

        // ---------- 工具 ----------
        SectionHeader("工具", toolOpen) { toolOpen = !toolOpen }
        AnimatedVisibility(toolOpen, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                tools.forEach { tool ->
                    ToolRow(tool) { onOpenTool(tool) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onManageTools() }
                        .padding(start = 8.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        null,
                        tint = SunColors.OnTopBarMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("管理工具", color = SunColors.OnTopBarMuted, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

// ---------------- 组件 ----------------

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val angle by animateFloatAsState(if (expanded) 0f else -90f, tween(180), label = "secArrow")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = SunColors.OnTopBarMuted,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            null,
            tint = SunColors.OnTopBarMuted,
            modifier = Modifier.size(15.dp).rotate(angle)
        )
    }
}

@Composable
private fun LocalRow(
    mount: LocalMount,
    info: StorageInfo?,
    active: Boolean,
    showUsage: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val tint = if (active) SunColors.OnTopBar else SunColors.OnTopBar.copy(alpha = 0.86f)
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (active) Modifier.background(SunColors.OnTopBar.copy(alpha = 0.07f))
                else Modifier
            )
            .clickable { onClick() }
            .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (mount.path == "/") Icons.Outlined.Storage else Icons.Outlined.Folder,
                null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                mount.name,
                color = tint,
                fontSize = 12.5.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showUsage && info != null && info.total > 0) {
                Text(
                    "${(info.total - info.available) * 100 / info.total}%",
                    color = SunColors.OnTopBarMuted,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else if (onRemove != null) {
                Box {
                    Icon(
                        Icons.Outlined.MoreVert,
                        null,
                        tint = SunColors.OnTopBarMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { menu = true }
                    )
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        modifier = Modifier.background(SunColors.Surface3)
                    ) {
                        MenuAction("移除") { menu = false; onRemove() }
                    }
                }
            }
        }

        if (showUsage && info != null && info.total > 0) {
            val used = info.total - info.available
            val ratio = (used.toFloat() / info.total).coerceIn(0f, 1f)
            val track = Color.White.copy(alpha = 0.10f)
            val bar = SunColors.OnTopBar.copy(alpha = 0.55f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp, start = 23.dp)
                    .height(2.dp)
            ) {
                drawRoundRect(
                    color = track,
                    size = size,
                    cornerRadius = CornerRadius(size.height / 2)
                )
                drawRoundRect(
                    color = bar,
                    size = Size(size.width * ratio, size.height),
                    cornerRadius = CornerRadius(size.height / 2)
                )
            }
            Text(
                "${Fmt.sizeCompact(used)} 已用 · ${Fmt.sizeCompact(info.available)} 可用",
                color = SunColors.OnTopBarMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 4.dp, start = 23.dp)
            )
        } else if (!showUsage) {
            Text(
                mount.path,
                color = SunColors.OnTopBarMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp, start = 23.dp)
            )
        }
    }
}

@Composable
private fun NetworkRow(net: NetworkMount, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            net.kind.label,
            color = SunColors.OnTopBarMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                net.name,
                color = SunColors.OnTopBar.copy(alpha = 0.9f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                net.uri,
                color = SunColors.OnTopBarMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
        Box {
            Icon(
                Icons.Outlined.MoreVert,
                null,
                tint = SunColors.OnTopBarMuted,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { menu = true }
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.background(SunColors.Surface3)
            ) {
                MenuAction("删除") { menu = false; onRemove() }
            }
        }
    }
}

@Composable
private fun BookmarkRow(path: String, active: Boolean, onClick: () -> Unit) {
    Text(
        path.substringAfterLast('/').ifBlank { path },
        color = if (active) SunColors.OnTopBar else SunColors.OnTopBar.copy(alpha = 0.78f),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(start = 32.dp, end = 12.dp, top = 7.dp, bottom = 7.dp)
    )
}

@Composable
private fun ToolRow(tool: AppTool, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            tool.icon,
            null,
            tint = SunColors.OnTopBar.copy(alpha = 0.82f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            tool.label,
            color = SunColors.OnTopBar.copy(alpha = 0.9f),
            fontSize = 12.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = SunColors.OnTopBarMuted.copy(alpha = 0.65f),
        fontSize = 10.sp,
        modifier = Modifier.padding(start = 30.dp, top = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun SideIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = SunColors.OnTopBar, modifier = Modifier.size(18.dp))
    }
}

/**
 * 主题切换按钮：点击时以图标为中心向外扩散一圈光晕，
 * 点下去有个明确落点，而不是换个图标就完了。
 */
@Composable
private fun ThemePulseButton(dark: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pulse = remember { Animatable(0f) }

    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                scope.launch {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, tween(460))
                    pulse.snapTo(0f)
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(38.dp)) {
            val r = pulse.value
            if (r > 0f && r < 1f) {
                val radius = 6.dp.toPx() + r * 15.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = (1f - r) * 0.30f),
                    radius = radius,
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - r) * 0.10f),
                    radius = radius * 0.72f
                )
            }
        }
        Icon(
            Icons.Outlined.LightMode,
            if (dark) "切换到白天" else "切换到黑夜",
            tint = SunColors.OnTopBar,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = SunColors.TextPrimary, fontSize = 12.5.sp) },
        onClick = onClick
    )
}

@Composable
private fun MenuCheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = SunColors.TextPrimary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (checked) SunColors.Accent else SunColors.TextTertiary.copy(alpha = 0.30f),
                            RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            Icons.Outlined.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        },
        onClick = onClick
    )
}
