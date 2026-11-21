package com.suibian.sunfile.data.remote

import android.net.Uri
import com.suibian.sunfile.data.NetKind
import com.suibian.sunfile.data.NetworkMount
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** 远程目录里的一项 */
data class RemoteEntry(
    val name: String,
    /** 以 / 开头的绝对路径；SMB 的第一段是共享名 */
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val modified: Long = 0L,
    val link: Boolean = false
)

class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 远程文件系统的统一接口。
 *
 * 六种协议各有各的脾气（SMB 的共享、S3 的前缀、FTP 的被动模式），
 * 界面上只认这一层：列目录、读、写、建目录、改名、删除。
 */
interface RemoteFs : Closeable {
    val label: String

    /** 起始路径，通常是 "/"（SMB 是共享列表） */
    val rootPath: String get() = "/"

    fun list(path: String): List<RemoteEntry>

    fun stat(path: String): RemoteEntry? = null

    fun openInput(path: String): InputStream

    fun openOutput(path: String, append: Boolean = false): OutputStream

    fun mkdir(path: String)

    fun delete(path: String, isDir: Boolean)

    fun rename(from: String, to: String)

    override fun close()
}

/** 从链接里拆出来的连接参数 */
data class RemoteTarget(
    val kind: NetKind,
    val scheme: String,
    val user: String,
    val password: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: Map<String, String>
) {
    val displayHost: String get() = if (port > 0) "$host:$port" else host

    companion object {
        fun parse(mount: NetworkMount): RemoteTarget {
            val uri = runCatching { Uri.parse(mount.uri) }.getOrNull()
                ?: throw RemoteException("链接格式不正确：${mount.uri}")
            val scheme = (uri.scheme ?: mount.kind.scheme).lowercase()
            val userInfo = uri.userInfo ?: ""
            val user = Uri.decode(userInfo.substringBefore(':'))
            val pass = Uri.decode(userInfo.substringAfter(':', ""))
            val host = uri.host ?: uri.authority?.substringAfter('@')?.substringBefore(':')
                ?: throw RemoteException("链接缺少主机名")
            val port = if (uri.port > 0) uri.port else when (scheme) {
                "ftps" -> 990
                "sftp" -> 22
                "smb" -> 445
                "https", "webdavs" -> 443
                else -> when (mount.kind) {
                    NetKind.FTP, NetKind.FTPS -> 21
                    NetKind.SFTP -> 22
                    NetKind.SMB -> 445
                    NetKind.WEBDAV -> 80
                    NetKind.OBJECT -> 443
                }
            }
            val q = HashMap<String, String>()
            uri.queryParameterNames?.forEach { k ->
                uri.getQueryParameter(k)?.let { q[k] = it }
            }
            val p = uri.path?.takeIf { it.isNotBlank() } ?: "/"
            return RemoteTarget(mount.kind, scheme, user, pass, host, port, p, q)
        }
    }
}

object RemoteFsFactory {
    fun open(mount: NetworkMount, timeoutSec: Int = 15): RemoteFs {
        val t = RemoteTarget.parse(mount)
        return when (mount.kind) {
            NetKind.FTP, NetKind.FTPS -> FtpFs(t, timeoutSec)
            NetKind.SFTP -> SftpFs(t, timeoutSec)
            NetKind.SMB -> SmbFs(t, timeoutSec)
            NetKind.WEBDAV -> WebDavFs(t, timeoutSec)
            NetKind.OBJECT -> S3Fs(t, timeoutSec)
        }
    }
}

/** 路径工具：统一以 / 开头，去掉重复斜杠与结尾斜杠 */
internal fun normalizePath(path: String): String {
    val p = path.trim().replace('\\', '/')
    val lead = if (p.startsWith("/")) p else "/$p"
    val collapsed = lead.replace(Regex("/{2,}"), "/")
    return if (collapsed.length > 1) collapsed.trimEnd('/') else "/"
}

internal fun joinPath(dir: String, name: String): String =
    if (dir == "/" || dir.isEmpty()) "/$name" else "$dir/$name"

internal fun parentOf(path: String): String {
    val p = normalizePath(path)
    if (p == "/") return "/"
    val cut = p.lastIndexOf('/')
    return if (cut <= 0) "/" else p.substring(0, cut)
}

internal fun baseName(path: String): String {
    val p = normalizePath(path)
    return if (p == "/") "/" else p.substringAfterLast('/')
}
