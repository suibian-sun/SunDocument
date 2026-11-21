package com.suibian.sunfile.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Shizuku 桥接。
 *
 * MT 管理器的做法是：装一个常驻的 Shizuku 服务（用 adb 或 root 启动，跑在 shell/root 身份），
 * 应用拿到它的 binder 后，把「执行一条 shell 命令」这个请求代理过去。
 * 系统看到的是 shell 在操作文件，而不是本应用，SELinux 与 scoped storage 的限制自然绕过。
 *
 * 关键点是 [newProcess]：它向 Shizuku 申请一个以 shell 身份运行的子进程，
 * 之后所有的 ls / cat / stat / cp 都是这个进程在做，结果通过管道回传给我们。
 */
object ShizukuBridge {

    /** Shizuku 官方包名，用于判断是否安装 */
    const val PACKAGE = "moe.shizuku.privileged.api"

    /** 授权请求码，回调里用它区分是不是自己发的请求 */
    const val REQUEST_CODE = 0x5A1D

    /** Shizuku 从 12 版（API 11）起才提供 newProcess，低版本只能做 binder 代理 */
    private const val MIN_VERSION_FOR_PROCESS = 11

    @Volatile
    private var installedCache: Boolean? = null

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val hookInstalled = AtomicBoolean(false)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code != REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = result == PackageManager.PERMISSION_GRANTED
        listeners.forEach { runCatching { it(granted) } }
    }

    /** 是否装了 Shizuku（不关心有没有启动） */
    fun installed(context: Context): Boolean {
        installedCache?.let { return it }
        val ok = runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        }.getOrDefault(false)
        installedCache = ok
        return ok
    }

    /** 服务是否活着：Shizuku 进程没启动时 binder 为 null，ping 会失败 */
    fun running(): Boolean = runCatching {
        Shizuku.getBinder() != null && Shizuku.pingBinder()
    }.getOrDefault(false)

    /** 是否已经授过权 */
    fun permitted(): Boolean = runCatching {
        if (!running()) return false
        if (Shizuku.isPreV11()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 能不能真的起进程（决定能否当文件后端用） */
    fun usable(): Boolean = runCatching {
        permitted() && Shizuku.getVersion() >= MIN_VERSION_FOR_PROCESS
    }.getOrDefault(false)

    fun version(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(0)

    /** 弹出 Shizuku 的授权框；真正的结果走 [addResultListener] */
    fun requestPermission(): Boolean = runCatching {
        if (!running()) return false
        if (permitted()) return true
        installHook()
        Shizuku.requestPermission(REQUEST_CODE)
        true
    }.getOrDefault(false)

    fun addResultListener(listener: (Boolean) -> Unit) {
        installHook()
        listeners.add(listener)
    }

    fun removeResultListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun installHook() {
        if (!hookInstalled.compareAndSet(false, true)) return
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
    }

    /**
     * 以 shell 身份创建一个子进程。
     *
     * 这是整个代理方案的核心：命令不是本应用执行的，而是 Shizuku 服务 fork 出来的
     * shell 进程执行的，所以 uid 是 2000（shell），能读 /、/system、/vendor、
     * /data/local/tmp 这些普通应用碰不到的路径。
     *
     * 官方把 `Shizuku.newProcess` 设为 private，所以这里照它内部的做法：
     * 用 [ShizukuBinderWrapper] 包一层 binder，拿到 IShizukuService 后直接调 newProcess。
     */
    fun newProcess(command: Array<String>): Process? = runCatching {
        if (!usable()) return null
        val binder = Shizuku.getBinder() ?: return null
        val service = IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
        val remote = service.newProcess(command, null, null) ?: return null
        RemoteProcess(remote)
    }.getOrNull()

    /** 给界面用的一句话状态 */
    fun statusText(context: Context): String = when {
        !installed(context) -> "未安装 Shizuku · 点此查看安装方式"
        !running() -> "服务未运行 · 点此唤起"
        !permitted() -> "待授权 · 点此申请"
        !usable() -> "版本过低（需 Shizuku 12.0+）"
        else -> "已授权 · shell 身份"
    }

    /**
     * 把 Shizuku 返回的远程进程包装成标准 [Process]。
     *
     * 上层（Shell）只认 Process 的输入输出流和退出码，所以这层适配做完之后，
     * 原来的 su 通道代码一行都不用改。
     */
    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {

        private val stdin: OutputStream by lazy {
            ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
        }
        private val stdout: InputStream by lazy {
            ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
        }
        private val stderr: InputStream by lazy {
            ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
        }

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr

        override fun waitFor(): Int = runCatching { remote.waitFor() }.getOrDefault(-1)

        override fun exitValue(): Int = runCatching { remote.exitValue() }.getOrDefault(-1)

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        /**
         * 带超时的等待。服务端支持按毫秒等待，直接用；
         * 万一某个版本不认这个参数，就退回轮询，绝不把线程永久挂住。
         */
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            return runCatching { remote.waitForTimeout(timeout, unit.name) }.getOrElse {
                val deadline = System.currentTimeMillis() + unit.toMillis(timeout)
                while (System.currentTimeMillis() < deadline) {
                    if (runCatching { remote.exitValue() }.isSuccess) return true
                    runCatching { Thread.sleep(20) }
                }
                false
            }
        }
    }
}
