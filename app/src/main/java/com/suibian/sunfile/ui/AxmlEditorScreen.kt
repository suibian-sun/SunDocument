package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.data.ArchiveEdit
import com.suibian.sunfile.data.ApkSign
import com.suibian.sunfile.data.Axml
import com.suibian.sunfile.data.AxmlEncoder
import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.ui.theme.SunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 二进制 XML 编辑器。解成文本改，保存时编译回 AXML 写进 APK，
 * 顺带给一个「重签名」按钮——改过清单的包签名一定失效，不签装不上。
 */
@Composable
internal fun AxmlEditorOverlay(
    file: File,
    entry: String = "AndroidManifest.xml",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val inApk = runCatching { java.util.zip.ZipFile(file).use { it.getEntry(entry) != null } }.getOrDefault(false)

    LaunchedEffect(file, entry) {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (inApk) FileRepo.readZipEntry(file, entry, 4L * 1024 * 1024) else file.readBytes()
                Axml.decode(bytes)
            }
        }
        r.onSuccess { text = it }
            .onFailure { loadError = it.message ?: "解析失败" }
    }

    fun save() {
        val body = text ?: return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = AxmlEncoder.encode(body)
                    if (inApk) {
                        if (!ArchiveEdit.zipPut(file, entry, bytes)) error("写回安装包失败")
                    } else {
                        file.writeBytes(bytes)
                    }
                    bytes.size
                }
            }
            busy = false
            status = r.fold(
                { "已保存 ${it} 字节${if (inApk) "，签名已失效，需要重签" else ""}" },
                { "保存失败：${it.message}" }
            )
        }
    }

    fun sign() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { ApkSign.signToSibling(context, file) }
            busy = false
            status = if (r.ok) "已生成 ${r.output?.name}" else "重签失败：${r.message}"
        }
    }

    OverlayShell(
        title = if (inApk) "AndroidManifest.xml" else file.name,
        subtitle = if (inApk) file.name else "二进制 XML",
        onClose = onClose,
        actions = {
            Text(
                if (busy) "…" else "保存",
                color = Color.White,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .background(SunColors.Accent, RoundedCornerShape(7.dp))
                    .clickable(enabled = !busy && text != null) { save() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (inApk) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "重签名",
                        color = SunColors.TextPrimary,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .background(SunColors.Surface2, RoundedCornerShape(7.dp))
                            .clickable(enabled = !busy) { sign() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Text(
                        "改完清单导出可用安装包",
                        color = SunColors.TextTertiary,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = if (status.contains("失败")) SunColors.Danger else SunColors.TextSecondary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loadError != null -> Text(
                        loadError!!,
                        color = SunColors.Danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                    text == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            Modifier.height(22.dp),
                            color = SunColors.Accent,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> BasicTextField(
                        value = text!!,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            color = SunColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

}
