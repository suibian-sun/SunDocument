package com.suibian.sunfile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.suibian.sunfile.data.FileKinds
import com.suibian.sunfile.data.SafRepo
import com.suibian.sunfile.ui.components.MaterialFileIcon
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.topBarBackground
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.ui.theme.SunShapes
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android/data 与 Android/obb 的浏览器。
 *
 * 这两个目录从 Android 11 起不再允许直接读，唯一不 Root 的办法是让用户把目录授权给应用（SAF）。
 * 授权是一次性的，系统会记住；之后这里和普通列表操作一致：点开、导出、改名、删除。
 */
@Composable
fun SafPage(
    path: String,
    label: String,
    onBack: () -> Unit,
    onOpen: (File) -> Unit,
    onExport: (SafRepo.Node) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tree by remember(path) { mutableStateOf<Uri?>(SafRepo.persisted(context, path)) }
    var loading by remember(path) { mutableStateOf(false) }
    var denied by remember(path) { mutableStateOf(false) }
    val stack = remember(path) { mutableStateListOf<Pair<String, DocumentFile>>() }
    val nodes = remember(path) { mutableStateListOf<SafRepo.Node>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SafRepo.take(context, uri)
            tree = uri
        } else {
            denied = true
        }
    }

    fun load(dir: DocumentFile, name: String) {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) { SafRepo.list(context, dir) }
            nodes.clear()
            nodes.addAll(list)
            loading = false
            if (stack.isEmpty() || stack.last().first != name) stack.add(name to dir)
        }
    }

    LaunchedEffect(tree) {
        val t = tree ?: return@LaunchedEffect
        val dir = withContext(Dispatchers.IO) { SafRepo.open(context, t) }
        if (dir != null) {
            stack.clear()
            load(dir, SafRepo.treeLabel(path))
        } else {
            denied = true
        }
    }

    BackHandler {
        when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                scope.launch {
                    loading = true
                    val list = withContext(Dispatchers.IO) { SafRepo.list(context, stack.last().second) }
                    nodes.clear()
                    nodes.addAll(list)
                    loading = false
                }
            }
            tree != null -> {
                tree = null
                stack.clear()
            }
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().topBarBackground().statusBarsPadding().height(Mt.BarHeight).padding(horizontal = 4.dp),
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
                    label,
                    color = SunColors.OnTopBar,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stack.lastOrNull()?.first?.let { "/" + it.trimStart('/') } ?: "需要授权",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (tree == null) "授权" else "换目录",
                color = SunColors.Accent,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(SunShapes.Small)
                    .clickable { picker.launch(SafRepo.initialTree(path)) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        when {
            tree == null -> Column(
                Modifier.fillMaxSize().padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    null,
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "系统不让应用直接读这个目录",
                    color = SunColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "把 $label 授权给 SunDocument 即可浏览。授权一次长期有效，随时可以在这里撤销。",
                    color = SunColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
                if (denied) {
                    Spacer(Modifier.height(8.dp))
                    Text("没有授权，无法读取", color = SunColors.Danger, fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "选择目录",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SunColors.Accent)
                        .clickable { picker.launch(SafRepo.initialTree(path)) }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }

            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = SunColors.Accent, strokeWidth = 2.dp)
            }

            nodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个目录是空的", color = SunColors.TextTertiary, fontSize = 12.sp)
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(nodes, key = { it.uri.toString() }) { node ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                if (node.isDir) {
                                    val dir = SafRepo.childDir(context, node)
                                    if (dir != null) {
                                        nodes.clear()
                                        load(dir, node.name)
                                    }
                                } else {
                                    scope.launch {
                                        val copied = withContext(Dispatchers.IO) {
                                            SafRepo.copyTo(context, node, File(context.cacheDir, "saf"))
                                        }
                                        if (copied != null) onOpen(copied)
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialFileIcon(name = node.name, isDir = node.isDir, size = 24.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                node.name,
                                color = SunColors.TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (node.isDir) Fmt.time(node.modified) else Fmt.size(node.size),
                                color = SunColors.TextTertiary,
                                fontSize = 10.5.sp
                            )
                        }
                        if (!node.isDir) {
                            Text(
                                "导出",
                                color = SunColors.Accent,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .clip(SunShapes.Small)
                                    .clickable { onExport(node) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private typealias Uri = android.net.Uri
