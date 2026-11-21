package com.suibian.sunfile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.suibian.sunfile.data.ApkSign
import com.suibian.sunfile.data.Vault
import com.suibian.sunfile.data.VaultEntry
import com.suibian.sunfile.ui.theme.SunColors
import com.suibian.sunfile.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

// ==================== 签名密钥 ====================

@Composable
fun SigningPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apkPath by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }

    val info by produceState<Pair<String, String>?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) {
            ApkSign.keyPair(context)?.let { (_, cert) ->
                val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                val fp = digest.joinToString(":") { "%02X".format(it) }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                (cert.subjectDN.name to "$fp\n有效期至 ${date.format(cert.notAfter)}")
            }
        }
    }

    ToolScaffold(
        title = "签名密钥",
        subtitle = "v1 + v2 + v3 重签名",
        onBack = onBack,
        actions = {
            BarIcon(Icons.Outlined.Refresh, "重新生成密钥") { confirmReset = true }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            InfoCard {
                Text("当前密钥", color = SunColors.TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                if (info == null) {
                    Text("尚未生成", color = SunColors.TextSecondary, fontSize = 12.sp)
                } else {
                    val (subject, extra) = info!!
                    Text(subject, color = SunColors.TextPrimary, fontSize = 12.5.sp)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            extra,
                            color = SunColors.TextSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "改过 AndroidManifest 或 resources.arsc 的 APK 原签名会失效，用这里的密钥重签后即可安装。" +
                        "密钥保存在应用私有目录，卸载应用会一并删除。",
                    color = SunColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }

            SectionCaption("为 APK 重签名")
            SunField(apkPath, { apkPath = it }, "/storage/emulated/0/Download/app.apk", mono = true)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                if (busy) "签名中…" else "开始签名",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && apkPath.isNotBlank()
            ) {
                val src = File(apkPath.trim())
                if (!src.isFile) {
                    result = "文件不存在：${src.absolutePath}"
                } else {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { ApkSign.signToSibling(context, src) }
                        busy = false
                        result = if (r.ok) "已生成 ${r.output?.absolutePath}" else "签名失败：${r.message}"
                    }
                }
            }
            result?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = SunColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = { Text("重新生成密钥？", color = SunColors.TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "生成新密钥后，之前签过名的 APK 将无法覆盖安装（签名不一致）。",
                    color = SunColors.TextSecondary,
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Text(
                    "生成",
                    color = SunColors.Danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val dir = File(context.filesDir, "transpile")
                            File(dir, "key.der").delete()
                            File(dir, "cert.der").delete()
                            confirmReset = false
                            version++
                        }
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
                        .clickable { confirmReset = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }
}

// ==================== Smali 指令 ====================

private data class Op(val code: String, val syntax: String, val desc: String, val group: String)

private val OPCODES = listOf(
    Op("move", "move vA, vB", "把一个寄存器的值搬到另一个寄存器", "数据移动"),
    Op("move/from16", "move/from16 vAA, vBBBB", "源寄存器为 16 位时的 move", "数据移动"),
    Op("move-wide", "move-wide vA, vB", "搬运 64 位值（long / double），占用两个寄存器", "数据移动"),
    Op("move-object", "move-object vA, vB", "搬运对象引用", "数据移动"),
    Op("move-result", "move-result vAA", "取上一条 invoke 的 32 位返回值", "数据移动"),
    Op("move-result-wide", "move-result-wide vAA", "取 64 位返回值", "数据移动"),
    Op("move-result-object", "move-result-object vAA", "取对象返回值", "数据移动"),
    Op("move-exception", "move-exception vAA", "把捕获到的异常存进寄存器，必须是 catch 的第一条指令", "异常"),
    Op("return-void", "return-void", "返回空", "返回"),
    Op("return", "return vAA", "返回 32 位值", "返回"),
    Op("return-wide", "return-wide vAA", "返回 64 位值", "返回"),
    Op("return-object", "return-object vAA", "返回对象引用", "返回"),
    Op("const/4", "const/4 vA, #+B", "把 4 位有符号立即数放入寄存器", "常量"),
    Op("const/16", "const/16 vAA, #+BBBB", "16 位立即数", "常量"),
    Op("const", "const vAA, #+BBBBBBBB", "32 位立即数", "常量"),
    Op("const-wide/16", "const-wide/16 vAA, #+BBBB", "64 位立即数", "常量"),
    Op("const-string", "const-string vAA, \"text\"", "把字符串常量引用放入寄存器", "常量"),
    Op("const-class", "const-class vAA, Landroid/app/Activity;", "把类对象引用放入寄存器", "常量"),
    Op("monitor-enter", "monitor-enter vAA", "进入同步块（synchronized）", "同步"),
    Op("monitor-exit", "monitor-exit vAA", "退出同步块", "同步"),
    Op("check-cast", "check-cast vAA, Landroid/view/View;", "类型检查并转换，失败抛 ClassCastException", "类型"),
    Op("instance-of", "instance-of vA, vB, Landroid/view/View;", "判断对象是否为指定类型", "类型"),
    Op("array-length", "array-length vA, vB", "取数组长度", "数组"),
    Op("new-instance", "new-instance vAA, Ljava/util/ArrayList;", "创建对象实例（未调用构造器）", "对象"),
    Op("new-array", "new-array vA, vB, [I", "创建数组", "数组"),
    Op("filled-new-array", "filled-new-array {vC, vD}, [I", "用给定值创建数组", "数组"),
    Op("throw", "throw vAA", "抛出异常", "异常"),
    Op("goto", "goto :label", "无条件跳转", "跳转"),
    Op("goto/16", "goto/16 :label", "16 位偏移的无条件跳转", "跳转"),
    Op("packed-switch", "packed-switch vAA, :switch_data", "密集 switch 跳转表", "跳转"),
    Op("sparse-switch", "sparse-switch vAA, :switch_data", "稀疏 switch 跳转表", "跳转"),
    Op("cmp-long", "cmp-long vAA, vBB, vCC", "比较 long，返回 -1 / 0 / 1", "比较"),
    Op("cmpl-float", "cmpl-float vAA, vBB, vCC", "比较 float，NaN 时返回 -1", "比较"),
    Op("cmpg-float", "cmpg-float vAA, vBB, vCC", "比较 float，NaN 时返回 1", "比较"),
    Op("if-eq", "if-eq vA, vB, :label", "相等则跳转", "跳转"),
    Op("if-ne", "if-ne vA, vB, :label", "不等则跳转", "跳转"),
    Op("if-lt", "if-lt vA, vB, :label", "小于则跳转", "跳转"),
    Op("if-ge", "if-ge vA, vB, :label", "大于等于则跳转", "跳转"),
    Op("if-gt", "if-gt vA, vB, :label", "大于则跳转", "跳转"),
    Op("if-le", "if-le vA, vB, :label", "小于等于则跳转", "跳转"),
    Op("if-eqz", "if-eqz vAA, :label", "与 0 相等则跳转（常用于判空）", "跳转"),
    Op("if-nez", "if-nez vAA, :label", "与 0 不等则跳转（常用于非空判断）", "跳转"),
    Op("aget", "aget vAA, vBB, vCC", "读 int 数组元素", "数组"),
    Op("aput", "aput vAA, vBB, vCC", "写 int 数组元素", "数组"),
    Op("aget-object", "aget-object vAA, vBB, vCC", "读对象数组元素", "数组"),
    Op("aput-object", "aput-object vAA, vBB, vCC", "写对象数组元素", "数组"),
    Op("iget", "iget vA, vB, Lcls;->field:I", "读实例字段", "字段"),
    Op("iput", "iput vA, vB, Lcls;->field:I", "写实例字段", "字段"),
    Op("iget-object", "iget-object vA, vB, Lcls;->field:Ljava/lang/String;", "读对象类型实例字段", "字段"),
    Op("iput-object", "iput-object vA, vB, Lcls;->field:Ljava/lang/String;", "写对象类型实例字段", "字段"),
    Op("iget-boolean", "iget-boolean vA, vB, Lcls;->flag:Z", "读 boolean 字段", "字段"),
    Op("sget", "sget vAA, Lcls;->FIELD:I", "读静态字段", "字段"),
    Op("sput", "sput vAA, Lcls;->FIELD:I", "写静态字段", "字段"),
    Op("sget-object", "sget-object vAA, Lcls;->INSTANCE:Lcls;", "读静态对象字段（单例常见）", "字段"),
    Op("sput-object", "sput-object vAA, Lcls;->INSTANCE:Lcls;", "写静态对象字段", "字段"),
    Op("invoke-virtual", "invoke-virtual {vA, vB}, Lcls;->m(I)V", "调用实例方法（可被重写）", "方法调用"),
    Op("invoke-super", "invoke-super {vA}, Lcls;->m()V", "调用父类实现", "方法调用"),
    Op("invoke-direct", "invoke-direct {vA}, Lcls;-><init>()V", "调用私有方法或构造器", "方法调用"),
    Op("invoke-static", "invoke-static {vA}, Lcls;->m()V", "调用静态方法", "方法调用"),
    Op("invoke-interface", "invoke-interface {vA}, Lintf;->m()V", "调用接口方法", "方法调用"),
    Op("invoke-virtual/range", "invoke-virtual/range {v0 .. v5}, Lcls;->m()V", "寄存器连续时的调用写法", "方法调用"),
    Op("add-int", "add-int vAA, vBB, vCC", "整数加", "运算"),
    Op("sub-int", "sub-int vAA, vBB, vCC", "整数减", "运算"),
    Op("mul-int", "mul-int vAA, vBB, vCC", "整数乘", "运算"),
    Op("div-int", "div-int vAA, vBB, vCC", "整数除", "运算"),
    Op("rem-int", "rem-int vAA, vBB, vCC", "整数取余", "运算"),
    Op("and-int", "and-int vAA, vBB, vCC", "按位与", "运算"),
    Op("or-int", "or-int vAA, vBB, vCC", "按位或", "运算"),
    Op("xor-int", "xor-int vAA, vBB, vCC", "按位异或", "运算"),
    Op("shl-int", "shl-int vAA, vBB, vCC", "左移", "运算"),
    Op("shr-int", "shr-int vAA, vBB, vCC", "算术右移", "运算"),
    Op("neg-int", "neg-int vAA, vBB", "取负", "运算"),
    Op("int-to-long", "int-to-long vAA, vBB", "int 转 long", "类型转换"),
    Op("long-to-int", "long-to-int vAA, vBB", "long 转 int", "类型转换"),
    Op("int-to-string", "—", "Smali 无此指令，需 invoke-static Ljava/lang/String;->valueOf(I)Ljava/lang/String;", "类型转换"),
    Op("nop", "nop", "空操作，常用于占位或对齐", "其他")
)

@Composable
fun SmaliRefPage(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("全部") }
    val groups = remember { listOf("全部") + OPCODES.map { it.group }.distinct() }
    val shown = remember(query, group) {
        OPCODES.filter { o ->
            (group == "全部" || o.group == group) &&
                (query.isBlank() || o.code.contains(query, true) || o.desc.contains(query, true) || o.syntax.contains(query, true))
        }
    }

    ToolScaffold(
        title = "Smali 指令",
        subtitle = "${OPCODES.size} 条常用指令",
        onBack = onBack
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SunField(query, { query = it }, "搜索指令、语法或说明")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEach { g ->
                val on = g == group
                Text(
                    g,
                    color = if (on) Color.White else SunColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) SunColors.Accent else SunColors.Surface2)
                        .clickable { group = g }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.code + it.syntax }) { op ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            op.code,
                            color = SunColors.Accent,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(op.group, color = SunColors.TextTertiary, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    SelectionContainer {
                        Text(
                            op.syntax,
                            color = SunColors.TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(op.desc, color = SunColors.TextSecondary, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Hairline(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

// ==================== 常用密码 ====================

@Composable
fun PasswordPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = remember { Vault.exists(context) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf<String?>(null) }

    fun persist(list: List<VaultEntry>) {
        entries = list
        scope.launch {
            val r = withContext(Dispatchers.IO) { Vault.save(context, pin, list) }
            if (r.isFailure) error = "保存失败：${r.exceptionOrNull()?.message}"
        }
    }

    ToolScaffold(
        title = "常用密码",
        subtitle = if (ready) "AES-256-GCM 本地加密" else "首次使用需设置主口令",
        onBack = onBack,
        actions = {
            if (unlocked) {
                BarIcon(Icons.Outlined.Add, "新增") { adding = true }
            }
        }
    ) {
        if (!unlocked) {
            Column(Modifier.padding(18.dp)) {
                Icon(
                    Icons.Outlined.Lock,
                    null,
                    tint = SunColors.TextTertiary,
                    modifier = Modifier.padding(vertical = 16.dp).size(30.dp)
                )
                Text(
                    if (ready) "输入主口令解锁" else "设置主口令",
                    color = SunColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ready) "口令不保存，只用于派生解密密钥。忘记后无法找回。"
                    else "口令至少 4 位。请牢记：忘记后已保存的条目无法恢复。",
                    color = SunColors.TextTertiary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(16.dp))
                SunField(pin, { pin = it }, "主口令", mono = true)
                if (!ready) {
                    Spacer(Modifier.height(10.dp))
                    SunField(pin2, { pin2 = it }, "再输入一次")
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = SunColors.Danger, fontSize = 11.sp)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    if (ready) "解锁" else "创建密码箱",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pin.length >= 4 && (ready || pin == pin2)
                ) {
                    scope.launch {
                        error = null
                        if (!ready) {
                            val r = withContext(Dispatchers.IO) { Vault.create(context, pin) }
                            if (r.isSuccess) {
                                entries = emptyList(); unlocked = true
                            } else {
                                error = "创建失败：${r.exceptionOrNull()?.message}"
                            }
                        } else {
                            val r = withContext(Dispatchers.IO) { Vault.open(context, pin) }
                            if (r.isSuccess) {
                                entries = r.getOrDefault(emptyList()); unlocked = true
                            } else {
                                error = "口令错误或数据损坏"
                            }
                        }
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                EmptyState("还没有保存任何口令", "右上角 + 添加一条")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(entries, key = { it.id }) { e ->
                        val shownPlain = revealing == e.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editing = e }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Key,
                                    null,
                                    tint = SunColors.TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    e.title.ifBlank { "未命名" },
                                    color = SunColors.TextPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    if (shownPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    "显示/隐藏",
                                    tint = SunColors.TextTertiary,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { revealing = if (shownPlain) null else e.id }
                                        .padding(5.dp)
                                )
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    "复制",
                                    tint = SunColors.TextTertiary,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable {
                                            copyToClipboard(context, e.password)
                                            toast(context, "口令已复制")
                                        }
                                        .padding(5.dp)
                                )
                                Icon(
                                    Icons.Outlined.Delete,
                                    "删除",
                                    tint = SunColors.TextTertiary,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { persist(entries.filterNot { it.id == e.id }) }
                                        .padding(5.dp)
                                )
                            }
                            if (e.account.isNotBlank()) {
                                Text(
                                    e.account,
                                    color = SunColors.TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(start = 25.dp, top = 2.dp)
                                )
                            }
                            Text(
                                if (shownPlain) e.password else "•".repeat(e.password.length.coerceIn(6, 16)),
                                color = SunColors.TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 25.dp, top = 3.dp)
                            )
                            if (e.note.isNotBlank()) {
                                Text(
                                    e.note,
                                    color = SunColors.TextTertiary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 25.dp, top = 3.dp)
                                )
                            }
                        }
                        Hairline(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val target = editing
        var title by remember(target) { mutableStateOf(target?.title ?: "") }
        var account by remember(target) { mutableStateOf(target?.account ?: "") }
        var password by remember(target) { mutableStateOf(target?.password ?: "") }
        var note by remember(target) { mutableStateOf(target?.note ?: "") }

        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            containerColor = SunColors.Surface2,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(if (target == null) "新增条目" else "编辑条目", color = SunColors.TextPrimary, fontSize = 15.sp)
            },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    SunField(title, { title = it }, "名称，如「公司邮箱」")
                    Spacer(Modifier.height(8.dp))
                    SunField(account, { account = it }, "账号")
                    Spacer(Modifier.height(8.dp))
                    SunField(password, { password = it }, "口令", mono = true)
                    Spacer(Modifier.height(8.dp))
                    SunField(note, { note = it }, "备注")
                }
            },
            confirmButton = {
                Text(
                    "保存",
                    color = SunColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (title.isNotBlank() && password.isNotBlank()) {
                                val item = VaultEntry(
                                    id = target?.id ?: Vault.newId(),
                                    title = title.trim(),
                                    account = account.trim(),
                                    password = password,
                                    note = note.trim()
                                )
                                val next = if (target == null) entries + item
                                else entries.map { if (it.id == target.id) item else it }
                                persist(next)
                                adding = false
                                editing = null
                            }
                        }
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
                        .clickable { adding = false; editing = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("password", text))
}
