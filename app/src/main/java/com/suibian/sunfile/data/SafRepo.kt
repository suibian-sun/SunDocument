package com.suibian.sunfile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 免 Root 读受限目录。
 *
 * Android 11 起 `Android/data`、`Android/obb` 对文件 API 关闭，唯一合法入口是 SAF：
 * 让用户把这两个目录授权给应用，拿到可持久化的 tree URI，之后就能像普通目录一样列目录、读文件。
 * 系统分区（/system、/data）属于内核沙箱，没有 Root 只能读 /proc 之类的伪文件，这里不做假装。
 */
object SafRepo {

    const val AUTHORITY = "com.android.externalstorage.documents"

    /** 预先授权时用：把选择器直接定位到目标目录 */
    fun initialTree(path: String): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, path)

    fun intentFor(path: String): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialTree(path))
    }

    fun take(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun open(context: Context, tree: Uri): DocumentFile? = runCatching {
        val docId = runCatching { DocumentsContract.getDocumentId(tree) }
            .getOrElse { DocumentsContract.getTreeDocumentId(tree) }
        DocumentFile.fromTreeUri(context, DocumentsContract.buildTreeDocumentUri(AUTHORITY, docId))
    }.getOrNull()

    /** 已持久化的授权里，找属于 path 的那一个（系统重启后仍然有效） */
    fun persisted(context: Context, path: String): Uri? = runCatching {
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && DocumentsContract.getTreeDocumentId(it.uri) == path }
            ?.uri
    }.getOrNull()

    data class Node(
        val name: String,
        val uri: Uri,
        val isDir: Boolean,
        val size: Long,
        val modified: Long
    )

    fun list(context: Context, dir: DocumentFile?): List<Node> {
        if (dir == null) return emptyList()
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return children.mapNotNull { f ->
            val name = f.name ?: return@mapNotNull null
            Node(
                name = name,
                uri = f.uri,
                isDir = f.isDirectory,
                size = f.length(),
                modified = f.lastModified()
            )
        }.sortedWith(compareByDescending<Node> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun childDir(context: Context, node: Node): DocumentFile? = runCatching {
        DocumentFile.fromTreeUri(context, DocumentsContract.buildTreeDocumentUri(AUTHORITY, DocumentsContract.getDocumentId(node.uri)))
    }.getOrNull()

    /** 把 SAF 里的文件复制到真实目录，供预览/编辑使用 */
    fun copyTo(context: Context, node: Node, destDir: File): File? = try {
        destDir.mkdirs()
        var target = File(destDir, node.name)
        var n = 1
        while (target.exists()) {
            val base = node.name.substringBeforeLast('.', node.name)
            val ext = node.name.substringAfterLast('.', "")
            target = File(destDir, if (ext.isEmpty()) "$base($n)" else "$base($n).$ext")
            n++
        }
        val input = context.contentResolver.openInputStream(node.uri)
        if (input == null) null else {
            input.use { i -> target.outputStream().use { o -> i.copyTo(o, 128 * 1024) } }
            target
        }
    } catch (e: Exception) {
        null
    }

    /** 直接读一段文本，用于在编辑器里打开且不必先落盘 */
    fun readBytes(context: Context, node: Node, max: Long = 16L * 1024 * 1024): ByteArray? = runCatching {
        context.contentResolver.openInputStream(node.uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (total < max) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            out.toByteArray()
        }
    }.getOrNull()

    fun delete(context: Context, node: Node): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, node.uri)?.delete() == true }.getOrDefault(false)

    fun rename(context: Context, node: Node, newName: String): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, node.uri)?.renameTo(newName) == true }.getOrDefault(false)

    /** 当前权限能覆盖到的最上层目录名，用于面包屑 */
    fun treeLabel(path: String): String = path.substringAfter(':')
}
