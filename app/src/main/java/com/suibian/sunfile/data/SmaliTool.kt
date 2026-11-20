package com.suibian.sunfile.data

import android.content.Context
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Smali 反汇编与汇编。
 *
 * 用 dexlib2 / baksmali / smali 三个库：dex 是容器，baksmali 把每个
 * `classesN.dex` 摊成 `包路径/类名.smali`，smali 再把目录编译回 dex。
 * 这里只做「摊开 → 编辑 → 编译 → 塞回 APK → 重签」的编排，语法细节交给库。
 *
 * 中间产物放在缓存目录，缓存键由 APK 路径 + 大小 + 修改时间组成；再次打开直接命中，
 * 只有真的改过（.smali 的 mtime 晚于目录创建时间）才会重新编译。
 */
object SmaliTool {

    /** 一个 dex 反汇编后的目录 */
    data class Part(
        val entry: String,
        val dir: File,
        val api: Int,
        val born: Long
    ) {
        val display: String get() = entry.removeSuffix(".dex")
    }

    data class Session(val apk: File, val root: File, val parts: List<Part>)

    private val DEX_NAME = Regex("^classes(\\d*)\\.dex$")

    fun cacheKey(apk: File): String =
        "${apk.name}-${apk.length()}-${apk.lastModified()}".hashCode().toUInt().toString(16)

    fun cacheDir(context: Context, apk: File): File =
        File(File(context.cacheDir, "smali"), cacheKey(apk))

    /**
     * 摊开 APK 里所有 dex。已经有缓存的话只补缺的 part，不重做。
     * 回调参数：(已完成, 总数, 当前 dex 名)
     */
    fun open(apk: File, root: File, onStep: (Int, Int, String) -> Unit = { _, _, _ -> }): Session {
        root.mkdirs()
        val payload = LinkedHashMap<String, ByteArray>()
        ZipFile(apk).use { zip ->
            zip.entries().toList()
                .filter { !it.isDirectory && DEX_NAME.matches(it.name) }
                .sortedBy { dexIndex(it.name) }
                .forEach { e -> payload[e.name] = zip.getInputStream(e).readBytes() }
        }
        if (payload.isEmpty()) error("包里没有 classes.dex")

        val parts = ArrayList<Part>()
        payload.entries.forEachIndexed { i, (name, bytes) ->
            onStep(i, payload.size, name)
            val dir = File(root, name.removeSuffix(".dex"))
            val api = apiOf(dexVersion(bytes))
            val done = dir.isDirectory && File(dir, ".ok").isFile && dir.walkTopDown().any { it.extension == "smali" }
            val born = if (done) File(dir, ".ok").lastModified() else 0L
            if (!done) {
                dir.deleteRecursively()
                dir.mkdirs()
                val tmp = File(root, "$name.tmp")
                tmp.writeBytes(bytes)
                val dex = DexFileFactory.loadDexFile(tmp, Opcodes.forDexVersion(dexVersion(bytes)))
                val options = BaksmaliOptions().apply {
                    this.apiLevel = api
                    parameterRegisters = true
                    localsDirective = false
                    sequentialLabels = false
                    debugInfo = true
                    codeOffsets = false
                    accessorComments = false
                }
                val ok = Baksmali.disassembleDexFile(dex, dir, Runtime.getRuntime().availableProcessors(), options)
                tmp.delete()
                if (!ok) error("反汇编失败：$name")
                File(dir, ".ok").writeText(name)
            }
            parts.add(Part(name, dir, api, if (done) born else File(dir, ".ok").lastModified()))
        }
        onStep(payload.size, payload.size, "")
        return Session(apk, root, parts)
    }

    private fun dexIndex(name: String): Int {
        val g = DEX_NAME.matchEntire(name)?.groupValues?.get(1).orEmpty()
        return if (g.isEmpty()) 1 else g.toIntOrNull() ?: 1
    }

    /** dex 头第 4~6 字节是版本号，035 / 037 / 038 / 039 */
    private fun dexVersion(bytes: ByteArray): Int {
        if (bytes.size < 8) return 35
        return String(bytes, 4, 3, Charsets.US_ASCII).toIntOrNull() ?: 35
    }

    /** dex 版本对应到 api：039 → 28，038 → 26，037 → 24，035 → 23 */
    private fun apiOf(version: Int): Int = when {
        version >= 39 -> 28
        version == 38 -> 26
        version == 37 -> 24
        else -> 23
    }

    /** 目录里所有 .smali，按路径排序 */
    fun classesOf(part: Part): List<File> =
        part.dir.walkTopDown()
            .filter { it.isFile && it.extension == "smali" }
            .sortedBy { it.relativeTo(part.dir).invariantSeparatorsPath }
            .toList()

    /** 包名形式：com/example/Foo.smali → com.example.Foo */
    fun className(part: Part, file: File): String =
        file.relativeTo(part.dir).invariantSeparatorsPath.removeSuffix(".smali").replace('/', '.')

    /** 有没有改过：任何一个 .smali 的修改时间晚于目录标记 */
    fun isDirty(part: Part): Boolean {
        val born = File(part.dir, ".ok").lastModified()
        return part.dir.walkTopDown().any { it.isFile && it.extension == "smali" && it.lastModified() > born }
    }

    fun dirtyCount(part: Part): Int {
        val born = File(part.dir, ".ok").lastModified()
        return part.dir.walkTopDown().count { it.isFile && it.extension == "smali" && it.lastModified() > born }
    }

    /** 把目录里的 smali 编译成 dex，失败时抛出带行号的异常 */
    fun assemblePart(part: Part, out: File): File {
        val files = classesOf(part)
        if (files.isEmpty()) error("${part.entry} 里没有 smali 文件")
        out.parentFile?.mkdirs()
        val options = SmaliOptions().apply {
            apiLevel = part.api
            outputDexFile = out.absolutePath
            jobs = Runtime.getRuntime().availableProcessors()
            verboseErrors = true
        }
        val ok = try {
            Smali.assemble(options, files.map { it.absolutePath })
        } catch (e: Exception) {
            throw IllegalStateException(cleanMessage(e), e)
        }
        if (!ok || !out.isFile || out.length() == 0L) error("编译失败：${part.entry}")
        return out
    }

    private fun cleanMessage(e: Throwable): String {
        val raw = e.message ?: e.toString()
        // smali 的报错带一长串 [文件,行] 前缀，保留有用部分
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(6)
            .joinToString("\n")
            .ifEmpty { e.toString() }
    }

    /**
     * 编译所有改动过的 dex 并写回：产物落在原包同目录的 `<名字>-smali.apk`，
     * 再用运行时密钥重签成 `<名字>-smali-signed.apk`（改过 dex 的包原签名必然失效）。
     */
    fun writeBack(
        context: Context,
        session: Session,
        onStep: (String) -> Unit = {}
    ): Result<File> = runCatching {
        val changed = session.parts.filter { isDirty(it) }
        if (changed.isEmpty()) error("没有改动")

        val built = HashMap<String, File>()
        changed.forEachIndexed { i, part ->
            onStep("编译 ${part.entry}（${i + 1}/${changed.size}）")
            val out = File(session.root, "${part.entry}.new")
            assemblePart(part, out)
            built[part.entry] = out
        }

        onStep("写回安装包")
        val plain = File(session.apk.parentFile, session.apk.name.removeSuffix(".apk") + "-smali.apk")
        repack(session.apk, plain, built)

        onStep("重新签名")
        val r = ApkSign.signToSibling(context, plain)
        val signed = r.output ?: run {
            plain.delete()
            error("重签失败：${r.message}")
        }
        plain.delete()
        // 新包已改过 dex，旧缓存不再有效
        session.root.deleteRecursively()
        signed
    }

    /** 重写 zip，替换指定条目，其余原样复制（含压缩方式与时间） */
    private fun repack(source: File, target: File, replaced: Map<String, File>) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        ZipFile(source).use { zip ->
            ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                val entries = zip.entries().toList()
                for (e in entries) {
                    if (e.isDirectory) {
                        out.putNextEntry(ZipEntry(e.name).apply { time = e.time })
                        out.closeEntry()
                        continue
                    }
                    val next = ZipEntry(e.name).apply { time = e.time }
                    val swap = replaced[e.name]
                    if (swap != null) {
                        next.size = swap.length()
                        out.putNextEntry(next)
                        swap.inputStream().use { it.copyTo(out) }
                    } else {
                        out.putNextEntry(next)
                        zip.getInputStream(e).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    /** 把单个 .smali 文本编译成 dex（给「新建类」之类的场景留的口子） */
    fun assembleText(text: String, api: Int, workDir: File): File {
        val dir = File(workDir, "one").apply { deleteRecursively(); mkdirs() }
        val pkg = Regex("""(?m)^\s*\.class\s+.*?(L[^\s;]+;)""").find(text)
            ?.groupValues?.get(1)
            ?.removePrefix("L")?.removeSuffix(";")
            ?.substringBeforeLast('/', "")
            .orEmpty()
        val target = File(dir, if (pkg.isEmpty()) "Anon.smali" else "$pkg/Anon.smali")
        target.parentFile?.mkdirs()
        target.writeText(text)
        val part = Part("tmp.dex", dir, api, System.currentTimeMillis())
        return assemblePart(part, File(workDir, "one.dex"))
    }
}
