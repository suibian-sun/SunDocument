package com.suibian.sunfile.data.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/** FTP / FTPS（显式与隐式）客户端，走被动模式 */
class FtpFs(private val t: RemoteTarget, timeoutSec: Int) : RemoteFs {

    override val label: String = "FTP · ${t.displayHost}"

    private val client: FTPClient
    private val timeoutMs = timeoutSec.coerceIn(3, 120) * 1000

    init {
        val implicit = t.scheme == "ftps" && t.port == 990
        client = if (t.kind == com.suibian.sunfile.data.NetKind.FTPS || t.scheme == "ftps") {
            FTPSClient(implicit)
        } else {
            FTPClient()
        }
        client.controlEncoding = "UTF-8"
        client.setConnectTimeout(timeoutMs)
        client.setDefaultTimeout(timeoutMs)
        client.setDataTimeout(timeoutMs)
        try {
            client.connect(t.host, t.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw RemoteException("服务器拒绝连接：${client.replyString}")
            }
            client.login(t.user.ifBlank { "anonymous" }, t.password)
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
        } catch (e: RemoteException) {
            runCatching { client.disconnect() }
            throw e
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            throw RemoteException("FTP 连接失败：${e.message}", e)
        }
    }

    @Synchronized
    override fun list(path: String): List<RemoteEntry> {
        val p = normalizePath(path)
        val files = client.listFiles(p) ?: throw RemoteException("读取目录失败：${client.replyString}")
        return files.filter { it.name != "." && it.name != ".." }
            .map { f ->
                RemoteEntry(
                    name = f.name,
                    path = joinPath(p, f.name),
                    isDir = f.isDirectory,
                    size = if (f.isDirectory) 0 else f.size,
                    modified = f.timestamp?.timeInMillis ?: 0L,
                    link = f.isSymbolicLink
                )
            }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    @Synchronized
    override fun stat(path: String): RemoteEntry? {
        val p = normalizePath(path)
        if (p == "/") return RemoteEntry("/", "/", true)
        val dir = parentOf(p)
        return runCatching { list(dir).firstOrNull { it.path == p } }.getOrNull()
    }

    @Synchronized
    override fun openInput(path: String): InputStream {
        val p = normalizePath(path)
        client.setFileType(FTP.BINARY_FILE_TYPE)
        val raw = client.retrieveFileStream(p)
            ?: throw RemoteException("无法下载：${client.replyString}")
        return object : FilterInputStream(raw) {
            override fun close() {
                runCatching { super.close() }
                runCatching { client.completePendingCommand() }
            }
        }
    }

    @Synchronized
    override fun openOutput(path: String, append: Boolean): OutputStream {
        val p = normalizePath(path)
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (append) {
            // 追加：先取回原内容再整体写回，FTP 的 APPE 在各家实现上差异太大
            val existing = runCatching { openInput(p).use { it.readBytes() } }.getOrNull()
            val out = client.storeFileStream(p) ?: throw RemoteException("无法上传：${client.replyString}")
            if (existing != null) out.write(existing)
            return wrapStore(out)
        }
        val raw = client.storeFileStream(p) ?: throw RemoteException("无法上传：${client.replyString}")
        return wrapStore(raw)
    }

    private fun wrapStore(raw: OutputStream): OutputStream = object : FilterOutputStream(raw) {
        override fun close() {
            runCatching { flush() }
            runCatching { super.close() }
            runCatching { client.completePendingCommand() }
        }
    }

    @Synchronized
    override fun mkdir(path: String) {
        val p = normalizePath(path)
        if (p == "/") return
        // 逐级创建，已存在的目录会返回失败，直接忽略
        var cur = ""
        for (seg in p.trim('/').split('/')) {
            cur += "/$seg"
            runCatching { client.makeDirectory(cur) }
        }
    }

    @Synchronized
    override fun delete(path: String, isDir: Boolean) {
        val p = normalizePath(path)
        if (isDir) {
            list(p).forEach { delete(it.path, it.isDir) }
            if (!client.removeDirectory(p)) throw RemoteException("删除目录失败：${client.replyString}")
        } else {
            if (!client.deleteFile(p)) throw RemoteException("删除失败：${client.replyString}")
        }
    }

    @Synchronized
    override fun rename(from: String, to: String) {
        if (!client.rename(normalizePath(from), normalizePath(to))) {
            throw RemoteException("重命名失败：${client.replyString}")
        }
    }

    @Synchronized
    override fun close() {
        runCatching { client.logout() }
        runCatching { client.disconnect() }
    }
}
