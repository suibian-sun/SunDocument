package com.suibian.sunfile.ui

import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Shell
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 系统信息：把没有 Root 时还能拿到的东西一次列清楚。
 *
 * 存储、内存、内核、挂载点全部来自系统接口与 /proc；
 * 后面单独标出哪些目录应用本身可读、哪些要 SAF 授权、哪些只有 Root 才能进。
 */
@Composable
fun SystemInfoPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections by produceState(initialValue = emptyList<Pair<String, List<Pair<String, String>>>>()) {
        value = withContext(Dispatchers.IO) { collect(context) }
    }

    Column(Modifier.fillMaxSize().background(SunColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(SunColors.TopBar).statusBarsPadding().height(Mt.BarHeight).padding(horizontal = 4.dp),
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
                "系统信息",
                color = SunColors.OnTopBar,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
            items(sections) { (title, rows) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(
                        title,
                        color = SunColors.TextTertiary,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                k,
                                color = SunColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(96.dp)
                            )
                            Text(
                                v,
                                color = SunColors.TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(SunColors.Hairline))
            }
        }
    }
}

private fun collect(context: android.content.Context): List<Pair<String, List<Pair<String, String>>>> {
    val out = ArrayList<Pair<String, List<Pair<String, String>>>>()

    out.add(
        "设备" to listOf(
            "型号" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "系统" to "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            "架构" to Build.SUPPORTED_ABIS.joinToString(", "),
            "内核" to readFirstLine("/proc/version").take(90)
        )
    )

    val cpu = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())
    out.add(
        "处理器" to listOf(
            "核心" to Runtime.getRuntime().availableProcessors().toString(),
            "型号" to (cpu.firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
                ?.substringAfter(':')?.trim() ?: "未知")
        )
    )

    val mem = runCatching { File("/proc/meminfo").readLines() }.getOrDefault(emptyList())
    fun memOf(key: String): Long = mem.firstOrNull { it.startsWith(key) }
        ?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
        ?.times(1024) ?: 0L
    val totalMem = memOf("MemTotal")
    val availMem = memOf("MemAvailable")
    if (totalMem > 0) {
        out.add(
            "内存" to listOf(
                "总量" to Fmt.size(totalMem),
                "可用" to Fmt.size(availMem),
                "已用" to Fmt.size((totalMem - availMem).coerceAtLeast(0))
            )
        )
    }

    out.add(
        "存储" to listOf(
            "/system" to spaceOf("/system"),
            "/data" to spaceOf("/data"),
            "内部存储" to spaceOf("/storage/emulated/0")
        ).filter { it.second.isNotEmpty() }
    )

    // 这些目录能不能进，直接决定「文件详情」能不能读
    val probes = listOf("/", "/system", "/vendor", "/data", "/data/local/tmp", "/proc", "/sdcard", "/storage")
    out.add(
        "目录权限" to probes.map { path ->
            val f = File(path)
            path to when {
                f.canRead() && runCatching { f.list()?.isNotEmpty() == true }.getOrDefault(false) ->
                    "可读"
                f.canRead() -> "可列目录，内容受 SELinux 限制"
                Shell.rootAvailable -> "需要在该目录内申请 Root"
                else -> "需要 Root"
            }
        }
    )

    val mounts = runCatching { File("/proc/mounts").readLines() }.getOrDefault(emptyList())
    if (mounts.isNotEmpty()) {
        out.add("挂载点" to mounts.take(14).map { line ->
            val parts = line.split(' ')
            (parts.getOrNull(1) ?: "?") to (parts.getOrNull(2) ?: "")
        })
    }
    return out
}

private fun spaceOf(path: String): String = runCatching {
    val stat = StatFs(path)
    val total = stat.blockCountLong * stat.blockSizeLong
    val free = stat.availableBlocksLong * stat.blockSizeLong
    if (total <= 0) "" else "${Fmt.size(total - free)} / ${Fmt.size(total)}"
}.getOrDefault("")

private fun readFirstLine(path: String): String =
    runCatching { File(path).readLines().firstOrNull().orEmpty().trim() }.getOrDefault("")
