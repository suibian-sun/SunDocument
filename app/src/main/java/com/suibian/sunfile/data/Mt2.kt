package com.suibian.sunfile.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 仿 MT2 目录：应用数据集中放在内部存储根目录，卸载后仍保留。
 * 内部存储不可写（未授权 / 分区只读）时退到应用专属外部目录，功能不变。
 */
object Mt2 {

    const val DIR_NAME = "SunMT2"

    private var cachedRoot: File? = null

    fun root(context: Context): File {
        cachedRoot?.let { if (it.exists() || it.mkdirs()) return it }
        val preferred = File(Environment.getExternalStorageDirectory(), DIR_NAME)
        val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)
        val dir = if (runCatching { preferred.mkdirs() || preferred.isDirectory && preferred.canWrite() }.getOrDefault(false)) {
            preferred
        } else {
            fallback
        }
        runCatching { dir.mkdirs() }
        cachedRoot = dir
        return dir
    }

    fun sub(context: Context, name: String): File {
        val d = File(root(context), name)
        runCatching { if (!d.exists()) d.mkdirs() }
        return d
    }

    fun openMethodDir(context: Context): File = sub(context, "openMethod")
    fun apksDir(context: Context): File = sub(context, "apks")
    fun keystoreDir(context: Context): File = sub(context, "keystore")
    fun logsDir(context: Context): File = sub(context, "logs")

    fun ensure(context: Context) {
        runCatching {
            root(context)
            openMethodDir(context)
            apksDir(context)
            keystoreDir(context)
        }
    }

    // ---------------- 默认打开方式镜像 ----------------

    private fun omFile(context: Context, ext: String): File =
        File(openMethodDir(context), ".${ext.ifEmpty { "none" }}.om")

    /** 与偏好设置里的映射互为镜像，方便直接查看 / 手工清理 */
    fun writeOpenMethod(context: Context, ext: String, mode: String) {
        runCatching { omFile(context, ext).writeText("mode=$mode\n") }
    }

    fun clearOpenMethod(context: Context, ext: String) {
        runCatching { omFile(context, ext).delete() }
    }

    fun readOpenMethods(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        runCatching {
            openMethodDir(context).listFiles { f -> f.name.endsWith(".om") }?.forEach { f ->
                val mode = f.readText().lineSequence()
                    .firstOrNull { it.startsWith("mode=") }
                    ?.removePrefix("mode=")?.trim()
                if (!mode.isNullOrEmpty()) {
                    out[f.name.removePrefix(".").removeSuffix(".om")] = mode
                }
            }
        }
        return out
    }

    // ---------------- 崩溃日志 ----------------

    fun log(context: Context, tag: String, message: String?) {
        runCatching {
            val dir = logsDir(context)
            val file = File(dir, "DEBUG.log")
            if (file.length() > 512 * 1024) file.writeText("")
            val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$ts [$tag] ${message.orEmpty()}\n")
        }
    }

    fun installCrashHandler(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = logsDir(app)
                val file = File(dir, "DEBUG.log")
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.appendText("\n===== $ts ${thread.name} =====\n")
                file.appendText(throwable.stackTraceToString())
                file.appendText("\n")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
