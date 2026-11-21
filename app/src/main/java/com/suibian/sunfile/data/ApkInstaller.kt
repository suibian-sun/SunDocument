package com.suibian.sunfile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * APK 安装通道。
 *
 * 四条路各有边界：
 * - **Shizuku**：以 shell 身份把 APK 从 stdin 喂给 `pm install -S`，可静默安装；
 * - **Root**：`su -c pm install`，同样走 stdin，最稳；
 * - **Dhizuku**：它共享的是 DeviceOwner 策略，并没有开放 APK 安装接口，
 *   这里只做可用性检测，检测通过也会明确告知「需配合系统安装器」，不假装静默；
 * - **系统安装器**：FileProvider + ACTION_VIEW，任何设备都能用，但要用户点确认。
 *
 * 四个开关都真的参与选路，选不到就按优先级往下退，最后一定落到系统安装器。
 */
object ApkInstaller {

    enum class Channel(val label: String) {
        SHIZUKU("Shizuku"),
        DHIZUKU("Dhizuku"),
        ROOT("Root"),
        SYSTEM("系统安装器")
    }

    data class ApkMeta(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val label: String
    )

    sealed interface Outcome {
        data class Installed(val channel: Channel, val message: String) : Outcome
        data class Launched(val channel: Channel, val note: String = "") : Outcome
        data class Failed(val channel: Channel, val message: String) : Outcome
    }

    /** 解析 APK 元信息；返回 null 表示不是合法 APK */
    fun meta(context: Context, file: File): ApkMeta? {
        val pm = context.packageManager
        val info = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return null
        val app = info.applicationInfo
        app?.sourceDir = file.absolutePath
        app?.publicSourceDir = file.absolutePath
        val label = runCatching { app?.let { pm.getApplicationLabel(it).toString() } }
            .getOrNull() ?: info.packageName
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return ApkMeta(info.packageName, info.versionName ?: "?", code, label)
    }

    /** 校验结果：null 表示通过，否则是原因 */
    fun verify(context: Context, file: File): String? {
        if (!file.exists()) return "文件不存在"
        if (file.length() <= 0) return "文件为空"
        if (!file.name.lowercase().endsWith(".apk")) return "不是 .apk 文件"
        val m = meta(context, file) ?: return "APK 解析失败，文件可能损坏或不是安装包"
        if (m.packageName.isBlank()) return "APK 缺少包名"
        return null
    }

    fun dhizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.rosan.dhizuku", 0)
        true
    }.getOrDefault(false)

    /**
     * 按设置排出实际尝试顺序。SYSTEM 永远兜底，所以返回值一定非空。
     */
    fun plan(prefs: AppPrefs, context: Context): List<Channel> {
        val enabled = LinkedHashSet<Channel>()
        if (prefs.installViaShizuku && ShizukuBridge.usable()) enabled.add(Channel.SHIZUKU)
        if (prefs.installViaDhizuku && dhizukuInstalled(context)) enabled.add(Channel.DHIZUKU)
        if (prefs.installViaRoot) enabled.add(Channel.ROOT)
        enabled.add(Channel.SYSTEM)

        val preferred = when (prefs.installPriority.uppercase()) {
            "ROOT" -> listOf(Channel.ROOT, Channel.SHIZUKU, Channel.DHIZUKU, Channel.SYSTEM)
            "SYSTEM" -> listOf(Channel.SYSTEM, Channel.SHIZUKU, Channel.ROOT, Channel.DHIZUKU)
            "DHIZUKU" -> listOf(Channel.DHIZUKU, Channel.SHIZUKU, Channel.ROOT, Channel.SYSTEM)
            else -> listOf(Channel.SHIZUKU, Channel.DHIZUKU, Channel.ROOT, Channel.SYSTEM)
        }
        return preferred.filter { it in enabled }.ifEmpty { listOf(Channel.SYSTEM) }
    }

    /**
     * 执行安装。会按顺序尝试，成功的通道立即返回；
     * 全都失败时返回最后一次失败原因。
     */
    suspend fun install(
        context: Context,
        file: File,
        prefs: AppPrefs,
        progress: MutableStateFlow<Float> = MutableStateFlow(-1f)
    ): Outcome = withContext(Dispatchers.IO) {
        val check = verify(context, file)
        if (prefs.verifyApk && check != null) {
            return@withContext Outcome.Failed(Channel.SYSTEM, check)
        }
        var last: Outcome? = null
        for (channel in plan(prefs, context)) {
            val r = when (channel) {
                Channel.SHIZUKU -> installViaShell(file, shizuku = true, progress = progress)
                Channel.ROOT -> installViaShell(file, shizuku = false, progress = progress)
                Channel.DHIZUKU -> Outcome.Failed(
                    Channel.DHIZUKU,
                    "Dhizuku 只共享 DeviceOwner 策略，不提供 APK 安装接口"
                )
                Channel.SYSTEM -> launchSystem(context, file, prefs.customInstaller)
            }
            if (r is Outcome.Installed || r is Outcome.Launched) {
                if (prefs.deleteApkAfterInstall && r is Outcome.Installed) {
                    runCatching { file.delete() }
                }
                return@withContext r
            }
            last = r
        }
        last ?: Outcome.Failed(Channel.SYSTEM, "没有可用的安装通道")
    }

    // ---------------- 具体通道 ----------------

    private fun installViaShell(
        file: File,
        shizuku: Boolean,
        progress: MutableStateFlow<Float>
    ): Outcome {
        val channel = if (shizuku) Channel.SHIZUKU else Channel.ROOT
        if (shizuku && !ShizukuBridge.usable()) {
            return Outcome.Failed(channel, "Shizuku 未连接或未授权")
        }
        if (!shizuku && !Shell.requestSu()) {
            return Outcome.Failed(channel, "Root 未授权")
        }
        val size = file.length()
        val script = "pm install -r -d -t -S $size"
        val process = try {
            if (shizuku) {
                ShizukuBridge.newProcess(arrayOf("/system/bin/sh", "-c", script))
                    ?: return Outcome.Failed(channel, "Shizuku 无法创建进程")
            } else {
                ProcessBuilder(Shell.suCommand, "-c", script).redirectErrorStream(true).start()
            }
        } catch (e: Exception) {
            return Outcome.Failed(channel, "无法启动安装进程：${e.message}")
        }

        val sink = ByteArrayOutputStream()
        val drain = Thread {
            runCatching { process.inputStream.use { it.copyTo(sink, 8 * 1024) } }
        }.apply { isDaemon = true; start() }

        try {
            progress.value = 0f
            process.outputStream.use { out ->
                file.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        progress.value = (sent.toFloat() / size).coerceIn(0f, 1f)
                    }
                    out.flush()
                }
            }
            val ok = process.waitFor(180, TimeUnit.SECONDS)
            if (!ok) {
                runCatching { process.destroyForcibly() }
                return Outcome.Failed(channel, "安装超时")
            }
            drain.join(1200)
            val text = sink.toString(Charsets.UTF_8.name())
            progress.value = -1f
            return if (text.contains("Success", ignoreCase = true) ||
                text.contains("成功", ignoreCase = true)
            ) {
                Outcome.Installed(channel, text.trim().lines().lastOrNull { it.isNotBlank() } ?: "安装成功")
            } else {
                Outcome.Failed(channel, text.trim().ifBlank { "安装失败，退出码 ${process.exitValue()}" }.take(300))
            }
        } catch (e: Exception) {
            runCatching { process.destroy() }
            progress.value = -1f
            return Outcome.Failed(channel, e.message ?: "安装失败")
        }
    }

    private fun launchSystem(context: Context, file: File, customInstaller: String): Outcome {
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { return Outcome.Failed(Channel.SYSTEM, "无法共享安装包：${it.message}") }

        val pkg = customInstaller.trim()
        val intents = ArrayList<Intent>()
        if (pkg.isNotEmpty()) {
            intents.add(
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            intents.add(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
        intents.add(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in intents) {
            val ok = runCatching {
                if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) {
                return Outcome.Launched(
                    Channel.SYSTEM,
                    if (pkg.isNotEmpty()) "已交给 $pkg" else ""
                )
            }
        }
        return Outcome.Failed(Channel.SYSTEM, "没有可用的系统安装器")
    }
}
