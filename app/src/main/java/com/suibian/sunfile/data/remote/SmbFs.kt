package com.suibian.sunfile.data.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/**
 * SMB2 / SMB3 客户端。
 *
 * 链接形式 `smb://user:pass@host/share/sub`：第一段路径是共享名，
 * 根路径列出该账号能看到的共享，和 Windows 的「网络」视图一致。
 */
class SmbFs(private val t: RemoteTarget, timeoutSec: Int) : RemoteFs {

    override val label: String = "SMB · ${t.displayHost}"

    private val client = SMBClient()
    private val connection: Connection
    private val session: Session
    private val shares = HashMap<String, DiskShare>()

    init {
        try {
            connection = client.connect(t.host, if (t.port > 0) t.port else 445)
            val rawUser = t.user
            val domain = if (rawUser.contains('\\')) rawUser.substringBefore('\\') else ""
            val user = if (rawUser.contains('\\')) rawUser.substringAfter('\\') else rawUser
            val ac = if (user.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(user, t.password.toCharArray(), domain)
            }
            session = connection.authenticate(ac)
        } catch (e: Exception) {
            runCatching { client.close() }
            throw RemoteException("SMB 连接失败：${e.message}", e)
        }
    }

    private fun connect(name: String): DiskShare =
        session.connectShare(name) as? DiskShare ?: throw RemoteException("$name 不是磁盘共享")

    private fun share(name: String): DiskShare {
        shares[name]?.let { return it }
        val s = try {
            connect(name)
        } catch (e: RemoteException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException("无法打开共享 $name：${e.message}", e)
        }
        shares[name] = s
        return s
    }

    /** "/share/a/b" -> ("share", "a/b")；"/" -> (null, "") */
    private fun split(path: String): Pair<String?, String> {
        val p = normalizePath(path).trim('/')
        if (p.isEmpty()) return null to ""
        val idx = p.indexOf('/')
        return if (idx < 0) p to "" else p.substring(0, idx) to p.substring(idx + 1)
    }

    override fun list(path: String): List<RemoteEntry> {
        val (shareName, rest) = split(path)
        if (shareName == null) {
            // smbj 不提供 NetShareEnum，只能按常见共享名探测。
            // 链接里直接写共享名（smb://user:pass@host/share）就不会走这里。
            val out = ArrayList<RemoteEntry>()
            for (name in COMMON_SHARES) {
                val ok = runCatching { share(name) }.isSuccess
                if (ok) out.add(RemoteEntry(name, "/$name", true))
            }
            if (out.isEmpty()) {
                throw RemoteException("没有探测到可访问的共享，请在链接里写明共享名，例如 smb://user:pass@host/share")
            }
            out.sortBy { it.name.lowercase() }
            return out
        }
        val share = share(shareName)
        val dir = rest
        val items = try {
            share.list(dir)
        } catch (e: Exception) {
            throw RemoteException("读取目录失败：${e.message}", e)
        }
        val out = ArrayList<RemoteEntry>(items.size)
        for (f in items) {
            val name = f.fileName
            if (name == "." || name == "..") continue
            val isDir = (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            out.add(
                RemoteEntry(
                    name = name,
                    path = if (dir.isEmpty()) "/$shareName/$name" else "/$shareName/$dir/$name",
                    isDir = isDir,
                    size = if (isDir) 0 else f.endOfFile,
                    modified = runCatching { f.lastWriteTime.toEpochMillis() }.getOrDefault(0L)
                )
            )
        }
        out.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        return out
    }

    override fun stat(path: String): RemoteEntry? {
        val (shareName, rest) = split(path)
        if (shareName == null) return RemoteEntry("/", "/", true)
        val s = share(shareName)
        return runCatching {
            if (rest.isEmpty() || s.folderExists(rest)) {
                RemoteEntry(shareName, normalizePath(path), true)
            } else if (s.fileExists(rest)) {
                RemoteEntry(baseName(path), normalizePath(path), false)
            } else null
        }.getOrNull()
    }

    override fun openInput(path: String): InputStream {
        val (shareName, rest) = split(path)
        if (shareName == null) throw RemoteException("不能读取共享列表")
        val f = share(shareName).openFile(
            rest,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return f.inputStream
    }

    override fun openOutput(path: String, append: Boolean): OutputStream {
        val (shareName, rest) = split(path)
        if (shareName == null) throw RemoteException("不能写入共享列表")
        val dir = rest.substringBeforeLast('/', "")
        if (dir.isNotEmpty()) mkdir("/$shareName/$dir")
        val f = share(shareName).openFile(
            rest,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            if (append) SMB2CreateDisposition.FILE_OPEN_IF else SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        return f.outputStream
    }

    override fun mkdir(path: String) {
        val (shareName, rest) = split(path)
        if (shareName == null) return
        val s = share(shareName)
        if (rest.isEmpty()) return
        var cur = ""
        for (seg in rest.split('/')) {
            if (seg.isEmpty()) continue
            cur = if (cur.isEmpty()) seg else "$cur/$seg"
            runCatching { if (!s.folderExists(cur)) s.mkdir(cur) }
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val (shareName, rest) = split(path)
        if (shareName == null || rest.isEmpty()) throw RemoteException("不能删除共享根")
        val s = share(shareName)
        try {
            if (isDir) s.rmdir(rest, true) else s.rm(rest)
        } catch (e: Exception) {
            throw RemoteException("删除失败：${e.message}", e)
        }
    }

    override fun rename(from: String, to: String) {
        val (sf, rf) = split(from)
        val (st, rt) = split(to)
        if (sf == null || st == null || sf != st) throw RemoteException("只能在同一共享内重命名")
        try {
            val entry = share(sf).openFile(
                rf,
                EnumSet.of(AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            entry.use { it.rename(rt) }
        } catch (e: Exception) {
            throw RemoteException("重命名失败：${e.message}", e)
        }
    }

    companion object {
        private val COMMON_SHARES = listOf(
            "C$", "D$", "E$", "Users", "Share", "Public", "shared", "share",
            "home", "homes", "data", "media", "downloads", "backup", "nas", "public"
        )
    }

    override fun close() {
        shares.values.forEach { runCatching { it.close() } }
        shares.clear()
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }
}
