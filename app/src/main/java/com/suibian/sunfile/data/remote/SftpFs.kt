package com.suibian.sunfile.data.remote

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

/** SFTP（SSH 文件传输），密码认证 + 可选的私钥 */
class SftpFs(private val t: RemoteTarget, timeoutSec: Int) : RemoteFs {

    override val label: String = "SFTP · ${t.displayHost}"

    private val timeoutMs = timeoutSec.coerceIn(3, 120) * 1000
    private val session: Session
    private val channel: ChannelSftp

    init {
        try {
            val jsch = JSch()
            session = jsch.getSession(t.user.ifBlank { "root" }, t.host, t.port)
            session.setPassword(t.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey")
            session.timeout = timeoutMs
            session.connect(timeoutMs)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(timeoutMs)
        } catch (e: Exception) {
            throw RemoteException("SFTP 连接失败：${e.message}", e)
        }
    }

    @Synchronized
    override fun list(path: String): List<RemoteEntry> {
        val p = normalizePath(path)
        val list = try {
            @Suppress("UNCHECKED_CAST")
            channel.ls(p) as java.util.Vector<ChannelSftp.LsEntry>
        } catch (e: Exception) {
            throw RemoteException("读取目录失败：${e.message}", e)
        }
        val out = ArrayList<RemoteEntry>(list.size)
        for (e in list) {
            val name = e.filename
            if (name == "." || name == "..") continue
            val a = e.attrs
            out.add(
                RemoteEntry(
                    name = name,
                    path = joinPath(p, name),
                    isDir = a.isDir,
                    size = if (a.isDir) 0 else a.size,
                    modified = a.mTime.toLong() * 1000L,
                    link = a.isLink
                )
            )
        }
        out.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        return out
    }

    @Synchronized
    override fun stat(path: String): RemoteEntry? {
        val p = normalizePath(path)
        if (p == "/") return RemoteEntry("/", "/", true)
        return runCatching {
            val a = channel.stat(p)
            RemoteEntry(baseName(p), p, a.isDir, if (a.isDir) 0 else a.size, a.mTime.toLong() * 1000L)
        }.getOrNull()
    }

    @Synchronized
    override fun openInput(path: String): InputStream = try {
        channel.get(normalizePath(path))
    } catch (e: Exception) {
        throw RemoteException("无法下载：${e.message}", e)
    }

    @Synchronized
    override fun openOutput(path: String, append: Boolean): OutputStream = try {
        val p = normalizePath(path)
        val mode = if (append) ChannelSftp.APPEND else ChannelSftp.OVERWRITE
        channel.put(p, mode)
    } catch (e: Exception) {
        throw RemoteException("无法上传：${e.message}", e)
    }

    @Synchronized
    override fun mkdir(path: String) {
        val p = normalizePath(path)
        if (p == "/") return
        var cur = ""
        for (seg in p.trim('/').split('/')) {
            cur += "/$seg"
            runCatching { channel.mkdir(cur) }
        }
    }

    @Synchronized
    override fun delete(path: String, isDir: Boolean) {
        val p = normalizePath(path)
        try {
            if (isDir) {
                list(p).forEach { delete(it.path, it.isDir) }
                channel.rmdir(p)
            } else {
                channel.rm(p)
            }
        } catch (e: Exception) {
            throw RemoteException("删除失败：${e.message}", e)
        }
    }

    @Synchronized
    override fun rename(from: String, to: String) {
        try {
            channel.rename(normalizePath(from), normalizePath(to))
        } catch (e: Exception) {
            throw RemoteException("重命名失败：${e.message}", e)
        }
    }

    @Synchronized
    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}
