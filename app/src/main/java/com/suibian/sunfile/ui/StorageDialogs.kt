package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.DEFAULT_GROUP
import com.suibian.sunfile.ui.theme.SunColors

/** 侧边栏里由三点菜单触发的四个弹窗 */
enum class StorageDialog { AddLocal, AddNetwork, AddGroup, ManageGroups }

@Composable
fun StorageDialogHost(
    kind: StorageDialog,
    groups: List<String>,
    onDismiss: () -> Unit,
    onAddLocal: (String, String) -> Unit,
    onAddNetwork: (String, String, String) -> Unit,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit
) {
    when (kind) {
        StorageDialog.AddLocal -> {
            var name by remember { mutableStateOf("") }
            var path by remember { mutableStateOf("/storage/emulated/0/") }
            Shell(
                title = "添加本地存储",
                confirm = "添加",
                onDismiss = onDismiss,
                onConfirm = { if (path.isNotBlank()) onAddLocal(name, path) },
                enabled = path.isNotBlank()
            ) {
                Text("路径", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                SunField(path, { path = it }, "/storage/emulated/0/Download", mono = true)
                Spacer(Modifier.height(10.dp))
                Text("显示名称（可留空）", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                SunField(name, { name = it }, "自动取末级目录名")
            }
        }

        StorageDialog.AddNetwork -> {
            var name by remember { mutableStateOf("") }
            var kind by remember { mutableStateOf(com.suibian.sunfile.data.NetKind.SMB) }
            var uri by remember { mutableStateOf("smb://") }
            var group by remember { mutableStateOf(groups.firstOrNull() ?: DEFAULT_GROUP) }
            Shell(
                title = "添加网络存储",
                confirm = "添加",
                onDismiss = onDismiss,
                onConfirm = { if (uri.contains("://")) onAddNetwork(name, uri, group) },
                enabled = uri.contains("://")
            ) {
                // 协议决定链接前缀：选完直接把输入框的 scheme 换掉，省得用户记格式
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.suibian.sunfile.data.NetKind.entries.forEach { k ->
                        val on = k == kind
                        Text(
                            k.label,
                            color = if (on) androidx.compose.ui.graphics.Color.White else SunColors.TextSecondary,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (on) SunColors.Accent else SunColors.Surface3)
                                .clickable {
                                    kind = k
                                    val rest = uri.substringAfter("://", "")
                                    uri = "${k.scheme}://$rest"
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("链接", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                SunField(
                    uri,
                    { uri = it; kind = com.suibian.sunfile.data.NetKind.guess(it) },
                    when (kind) {
                        com.suibian.sunfile.data.NetKind.SMB -> "smb://user:pass@192.168.1.2/share"
                        com.suibian.sunfile.data.NetKind.SFTP -> "sftp://user:pass@192.168.1.2:22/home"
                        com.suibian.sunfile.data.NetKind.WEBDAV -> "webdav://user:pass@192.168.1.2:5005/dav"
                        com.suibian.sunfile.data.NetKind.OBJECT -> "s3://AK:SK@minio.local:9000/bucket?region=us-east-1"
                        com.suibian.sunfile.data.NetKind.FTPS -> "ftps://user:pass@192.168.1.2:21"
                        else -> "ftp://user:pass@192.168.1.2:21"
                    },
                    mono = true
                )
                Spacer(Modifier.height(10.dp))
                Text("名称（可留空）", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                SunField(name, { name = it }, "家里的 NAS")
                Spacer(Modifier.height(10.dp))
                Text("分组", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.forEach { g ->
                        val on = g == group
                        Text(
                            g,
                            color = if (on) androidx.compose.ui.graphics.Color.White else SunColors.TextSecondary,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (on) SunColors.Accent else SunColors.Surface3)
                                .clickable { group = g }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        StorageDialog.AddGroup -> {
            var name by remember { mutableStateOf("") }
            Shell(
                title = "添加网络分组",
                confirm = "创建",
                onDismiss = onDismiss,
                onConfirm = { onAddGroup(name) },
                enabled = name.isNotBlank()
            ) {
                Text("分组名称", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                SunField(name, { name = it }, "如「公司」「家里」")
            }
        }

        StorageDialog.ManageGroups -> {
            Shell(
                title = "管理工具分组",
                confirm = "完成",
                onDismiss = onDismiss,
                onConfirm = onDismiss,
                enabled = true
            ) {
                Text(
                    "默认分组不可删除；删除其他分组时，组内网络存储会退回默认分组。",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Column {
                    groups.forEach { g ->
                        var editing by remember(g) { mutableStateOf(false) }
                        var draft by remember(g) { mutableStateOf(g) }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editing) {
                                Box(Modifier.weight(1f)) {
                                    SunField(draft, { draft = it }, g)
                                }
                                Text(
                                    "保存",
                                    color = SunColors.Accent,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (draft.isNotBlank()) onRenameGroup(g, draft)
                                            editing = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            } else {
                                Text(
                                    g,
                                    color = SunColors.TextPrimary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "重命名",
                                    color = SunColors.TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { editing = true }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                                if (g != DEFAULT_GROUP) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        "删除",
                                        tint = SunColors.TextTertiary,
                                        modifier = Modifier
                                            .padding(start = 2.dp)
                                            .clickable { onDeleteGroup(g) }
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

/** 统一外观的对话框外壳 */
@Composable
private fun Shell(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SunColors.Surface2,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, color = SunColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                content()
            }
        },
        confirmButton = {
            Text(
                confirm,
                color = if (enabled) SunColors.Accent else SunColors.TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onConfirm() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = SunColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    )
}
