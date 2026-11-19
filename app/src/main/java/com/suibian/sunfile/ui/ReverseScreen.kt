package com.suibian.sunfile.ui

import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.Axml
import com.suibian.sunfile.data.DexIndex
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.cert.X509Certificate

private data class ApkOverview(
    val pkg: String = "",
    val versionName: String? = null,
    val versionCode: Long = 0,
    val appName: String? = null,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val certs: List<String> = emptyList(),
    val abis: List<String> = emptyList(),
    val dexes: List<Pair<String, Long>> = emptyList(),
    val size: Long = 0
)

/** APK 逆向面板：概览 / Manifest 反编译 / dex 结构浏览 */
@Composable
fun ReverseOverlay(file: File, startTab: Int = 0, onClose: () -> Unit) {
    val context = LocalContext.current
    var tab by remember(file) { mutableStateOf(startTab) }

    val overview by produceState<ApkOverview?>(null, file) {
        value = withContext(Dispatchers.IO) { readOverview(context, file) }
    }
    val manifest by produceState<String?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = FileRepo.readZipEntry(file, "AndroidManifest.xml", 8 * 1024 * 1024)
                Axml.decode(bytes)
            }.getOrElse { "反编译失败：${it.message}" }
        }
    }
    val dex by produceState<DexIndex.DexFile?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val name = FileRepo.zipEntries(file)
                    .firstOrNull { it.name.matches(Regex("classes\\d*\\.dex")) }
                    ?.name ?: "classes.dex"
                val bytes = FileRepo.readZipEntry(file, name, 24 * 1024 * 1024)
                DexIndex.read(bytes)
            }.getOrNull()
        }
    }

    OverlayShell(
        title = file.name,
        subtitle = overview?.let { "${it.pkg} · ${it.versionName ?: "?"} (${it.versionCode})" } ?: "解析中…",
        onClose = onClose,
        actions = {
            Row(Modifier.padding(end = 8.dp)) {
                listOf("概览", "Manifest", "DEX").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (tab == i) SunColors.Accent else SunColors.TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = if (tab == i) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(
                                if (tab == i) SunColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(7.dp)
                            )
                            .clickable { tab = i }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    ) {
        when (tab) {
            0 -> OverviewTab(overview, file)
            1 -> MonoTextTab(manifest)
            else -> DexTab(dex)
        }
    }
}

@Composable
private fun OverviewTab(info: ApkOverview?, file: File) {
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
        item { InfoRow("应用名称", info.appName ?: "—") }
        item { InfoRow("包名", info.pkg, mono = true) }
        item { InfoRow("版本", "${info.versionName ?: "—"}  (${info.versionCode})", mono = true) }
        item { InfoRow("SDK", "min ${info.minSdk} · target ${info.targetSdk}", mono = true) }
        item { InfoRow("大小", Fmt.size(info.size)) }
        item { InfoRow("DEX", info.dexes.joinToString("  ") { "${it.first} (${Fmt.size(it.second)})" }, mono = true) }
        item { InfoRow("ABI", info.abis.ifEmpty { listOf("未打包 so") }.joinToString("  "), mono = true) }
        item {
            Section("签名")
            info.certs.ifEmpty { listOf("未找到签名信息") }.forEach { cert ->
                Text(
                    cert,
                    color = SunColors.TextSecondary,
                    fontSize = 9.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        item { Section("权限 (${info.permissions.size})") }
        items(info.permissions) { p ->
            Text(p, color = SunColors.TextSecondary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
        }
        item { Section("组件") }
        item { ComponentBlock("Activity", info.activities) }
        item { ComponentBlock("Service", info.services) }
        item { ComponentBlock("Receiver", info.receivers) }
        item { ComponentBlock("Provider", info.providers) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = SunColors.TextTertiary, fontSize = 10.sp, modifier = Modifier.width(70.dp))
        Text(
            value,
            color = SunColors.TextPrimary,
            fontSize = 10.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = SunColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun ComponentBlock(label: String, list: List<String>) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text("$label (${list.size})", color = SunColors.TextTertiary, fontSize = 10.sp)
        if (list.isEmpty()) {
            Text("无", color = SunColors.TextTertiary, fontSize = 9.5.sp)
        } else {
            list.take(40).forEach { c ->
                Text(c, color = SunColors.TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            if (list.size > 40) {
                Text("… 其余 ${list.size - 40} 项", color = SunColors.TextTertiary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MonoTextTab(text: String?) {
    if (text == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = SunColors.Accent, strokeWidth = 2.dp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        items(text.split('\n')) { line ->
            Text(
                line.ifEmpty { " " },
                color = if (line.startsWith("<")) SunColors.TextPrimary else SunColors.TextSecondary,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DexTab(dex: DexIndex.DexFile?) {
    if (dex == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无法读取 classes.dex", color = SunColors.TextSecondary, fontSize = 12.sp)
        }
        return
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DexIndex.DexClass?>(null) }
    val list = remember(query, dex) {
        if (query.isBlank()) dex.classes
        else dex.classes.filter { it.name.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = SunColors.TextPrimary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .background(SunColors.Surface3, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${list.size}/${dex.classes.size} 类",
                color = SunColors.TextTertiary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            "方法 ${dex.methodCount} · 字段 ${dex.fieldCount} · 字符串 ${dex.strings.size}",
            color = SunColors.TextTertiary,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(list) { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = c }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(c.name, color = SunColors.TextPrimary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    Text(
                        (c.superName ?: "—") + " · 方法 ${c.methods.size} · 字段 ${c.fields.size}",
                        color = SunColors.TextTertiary,
                        fontSize = 8.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }

    selected?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = SunColors.Surface4,
            shape = com.suibian.sunfile.ui.theme.SunShapes.Medium,
            title = {
                Text(c.name, color = SunColors.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    Text(
                        DexIndex.accessLabel(c.access, true) + (c.superName?.let { "  extends $it" } ?: ""),
                        color = SunColors.TextTertiary,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn {
                        items(c.fields) { f ->
                            Text(
                                "${DexIndex.accessLabel(f.access, false)} ${f.name}: ${f.type}",
                                color = SunColors.TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        items(c.methods) { m ->
                            Text(
                                "${DexIndex.accessLabel(m.access, false)} ${m.name}${m.descriptor}",
                                color = SunColors.TextPrimary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selected = null }) {
                    Text("关闭", color = SunColors.Accent, fontSize = 12.sp)
                }
            }
        )
    }
}

private fun readOverview(context: android.content.Context, file: File): ApkOverview {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS
    @Suppress("DEPRECATION")
    val info = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return ApkOverview(size = file.length())
    val app = info.applicationInfo
    val label = runCatching {
        app?.sourceDir = file.absolutePath
        app?.publicSourceDir = file.absolutePath
        app?.let { pm.getApplicationLabel(it).toString() }
    }.getOrNull()

    val certs = runCatching {
        val sigs: Array<Signature> = if (android.os.Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            val pi = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            pi?.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        sigs.map { certInfo(it) }
    }.getOrDefault(emptyList())

    val abis = runCatching {
        FileRepo.zipEntries(file)
            .mapNotNull { e -> Regex("^lib/([^/]+)/").find(e.name)?.groupValues?.get(1) }
            .distinct()
    }.getOrDefault(emptyList())

    val dexes = runCatching {
        FileRepo.zipEntries(file)
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .map { it.name to it.size }
    }.getOrDefault(emptyList())

    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    return ApkOverview(
        pkg = info.packageName,
        versionName = info.versionName,
        versionCode = versionCode,
        appName = label,
        minSdk = app?.minSdkVersion ?: 0,
        targetSdk = app?.targetSdkVersion ?: 0,
        permissions = info.requestedPermissions?.toList() ?: emptyList(),
        activities = info.activities?.map { it.name } ?: emptyList(),
        services = info.services?.map { it.name } ?: emptyList(),
        receivers = info.receivers?.map { it.name } ?: emptyList(),
        providers = info.providers?.map { it.name } ?: emptyList(),
        certs = certs,
        abis = abis,
        dexes = dexes,
        size = file.length()
    )
}

private fun certInfo(sig: Signature): String = runCatching {
    val factory = java.security.cert.CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(sig.toByteArray().inputStream()) as X509Certificate
    val sha256 = HexDigest.sha256(sig.toByteArray())
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    "${cert.subjectDN.name}\n有效期 ${fmt.format(cert.notBefore)} → ${fmt.format(cert.notAfter)}\nSHA-256 $sha256"
}.getOrElse { "签名解析失败：${it.message}" }

private object HexDigest {
    fun sha256(bytes: ByteArray): String = digest(bytes, "SHA-256")

    private fun digest(bytes: ByteArray, algo: String): String = runCatching {
        java.security.MessageDigest.getInstance(algo).digest(bytes).joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}
