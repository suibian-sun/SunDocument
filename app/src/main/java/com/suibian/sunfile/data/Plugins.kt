package com.suibian.sunfile.data

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.util.zip.ZipFile

/**
 * 插件体系，按 MT 的 `.mtp` 包模型实现。
 *
 * 一个插件就是一个 zip：`manifest.json` 描述自己，`classes.dex`（或 libs 目录里的 jar）放代码，
 * `assets/` 放资源，`icon.png` 放图标。装上之后宿主用 DexClassLoader 载入，
 * 按 manifest 里声明的动作出现在文件长按菜单里。
 *
 * 与 MT 的差别只在接口签名：MT 用它自己的一套 `bin.mt.plugin.api.*`，这里用
 * `run(Context, List<File>): String?`，插件仍然是「代码跑在宿主进程里」的形态。
 */
object Plugins {

    const val EXT = "mtp"

    data class Action(
        val id: String,
        val label: String,
        val pluginId: String,
        val pluginName: String,
        /** dex 里的类名 */
        val className: String? = null,
        /** 直接用 shell 执行，${files} 会替换成选中路径 */
        val script: String? = null,
        val extensions: List<String> = listOf("*"),
        val dirOnly: Boolean = false,
        val fileOnly: Boolean = false
    ) {
        fun accepts(file: File): Boolean {
            if (dirOnly && !file.isDirectory) return false
            if (fileOnly && file.isDirectory) return false
            if (extensions.isEmpty() || extensions.contains("*")) return true
            val ext = file.name.substringAfterLast('.', "").lowercase()
            return extensions.any { it.equals(ext, true) || it == "dir" && file.isDirectory }
        }
    }

    data class Plugin(
        val id: String,
        val name: String,
        val description: String,
        val versionName: String,
        val versionCode: Int,
        val dir: File,
        val interfaces: List<String>,
        val actions: List<Action>,
        val enabled: Boolean
    ) {
        val hasCode: Boolean get() = File(dir, "classes.dex").exists() || File(dir, "libs").listFiles()?.isNotEmpty() == true
    }

    fun root(context: Context): File = File(context.filesDir, "plugins").apply { mkdirs() }

    fun list(context: Context): List<Plugin> {
        val root = root(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val manifest = parseManifest(File(dir, "manifest.json")) ?: return@mapNotNull null
            val id = manifest["pluginID"] as? String ?: dir.name
            val name = manifest["name"] as? String ?: id
            val interfaces = (manifest["interfaces"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val actions = parseActions(manifest, id, name)
            Plugin(
                id = id,
                name = name,
                description = manifest["description"] as? String ?: "",
                versionName = manifest["versionName"] as? String ?: "",
                versionCode = (manifest["versionCode"] as? Number)?.toInt() ?: 0,
                dir = dir,
                interfaces = interfaces,
                actions = actions,
                enabled = !File(dir, ".disabled").exists()
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** 装一个 .mtp：解压到 plugins/<id>/，同名覆盖 */
    fun install(context: Context, packageFile: File): Result<Plugin> = runCatching {
        val temp = File(root(context), ".staging")
        temp.deleteRecursively()
        temp.mkdirs()
        ZipFile(packageFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val target = File(temp, entry.name)
                // 防路径穿越
                if (!target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                }
            }
        }
        val manifest = parseManifest(File(temp, "manifest.json"))
            ?: throw IllegalArgumentException("这个包里没有 manifest.json")
        val id = manifest["pluginID"] as? String
            ?: throw IllegalArgumentException("manifest.json 缺少 pluginID")
        if (!id.matches(Regex("""[A-Za-z0-9_.]+"""))) throw IllegalArgumentException("pluginID 只能包含字母、数字、下划线和点")
        val dest = File(root(context), id)
        dest.deleteRecursively()
        if (!temp.renameTo(dest)) {
            temp.copyRecursively(dest, overwrite = true)
            temp.deleteRecursively()
        }
        list(context).firstOrNull { it.id == id } ?: throw IllegalStateException("安装后读取失败")
    }

    /** 装一个示例插件：只用 shell 脚本，装完立刻能在长按菜单里看到 */
    fun installDemo(context: Context): Result<Plugin> = runCatching {
        val packageFile = File(context.cacheDir, "demo.mtp")
        val manifest = """
            {
              // 插件 SDK 版本，与 MT 对齐
              pluginSdkVersion: 2,
              pluginID: "cc.sun.document.demo",
              versionCode: 1,
              versionName: "1.0",
              name: "示例插件",
              description: "两个脚本动作：计算 MD5、统计占用",
              interfaces: [],
              actions: [
                { id: "md5", label: "计算 MD5", script: "md5sum ${'$'}{files}", extensions: ["*"] },
                { id: "size", label: "统计占用", script: "du -sh ${'$'}{files}", extensions: ["*"] }
              ]
            }
        """.trimIndent()
        java.util.zip.ZipOutputStream(packageFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        install(context, packageFile).getOrThrow()
    }

    fun uninstall(context: Context, id: String) {
        File(root(context), id).deleteRecursively()
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val flag = File(File(root(context), id), ".disabled")
        if (enabled) {
            flag.delete()
        } else {
            flag.parentFile?.mkdirs()
            flag.writeText("")
        }
    }

    /** 当前选中的文件能用的动作 */
    fun actionsFor(context: Context, files: List<File>): List<Action> {
        if (files.isEmpty()) return emptyList()
        return list(context)
            .filter { it.enabled }
            .flatMap { it.actions }
            .filter { action -> files.all { action.accepts(it) } }
    }

    /**
     * 执行动作。shell 动作直接把路径拼进脚本；代码动作走 DexClassLoader，
     * 先找 `run(Context, List<File>)`，再退回 `run(Context, List<String>)`。
     */
    fun run(context: Context, action: Action, files: List<File>, asRoot: Boolean = false): String? {
        action.script?.let { script ->
            val quoted = files.joinToString(" ") { Shell.q(it.absolutePath) }
            val cmd = script.replace("\${files}", quoted).replace("\$FILES", quoted)
            val result = Shell.runScript(cmd, asRoot, 60_000)
            return (result.out + result.err).trim().ifEmpty { "（没有输出）" }
        }
        val className = action.className ?: return "这个插件没有可执行入口"
        val plugin = list(context).firstOrNull { it.id == action.pluginId } ?: return "插件已被卸载"

        val loader = loaderFor(context, plugin) ?: return "插件代码加载失败"
        val clazz = Class.forName(className, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance()

        val listMethod = runCatching {
            clazz.getMethod("run", Context::class.java, List::class.java)
        }.getOrNull()
        if (listMethod != null) {
            return (listMethod.invoke(instance, context, files) as? String)?.trim()
        }
        val arrayMethod = runCatching {
            clazz.getMethod("run", Context::class.java, Array<String>::class.java)
        }.getOrNull()
        if (arrayMethod != null) {
            return (arrayMethod.invoke(instance, context, files.map { it.absolutePath }.toTypedArray()) as? String)?.trim()
        }
        val noArg = runCatching { clazz.getMethod("run") }.getOrNull()
        if (noArg != null) return (noArg.invoke(instance) as? String)?.trim()
        return "插件没有 run(Context, List<File>) 方法"
    }

    private fun loaderFor(context: Context, plugin: Plugin): DexClassLoader? = runCatching {
        val dex = File(plugin.dir, "classes.dex")
        if (!dex.exists() && File(plugin.dir, "plugin.dex").exists()) {
            File(plugin.dir, "plugin.dex").renameTo(dex)
        }
        val jars = File(plugin.dir, "libs").listFiles { f -> f.name.endsWith(".jar") }.orEmpty()
        val path = (listOf(dex) + jars).filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }
        if (path.isEmpty()) return null
        val odex = File(context.codeCacheDir, "plugin_${plugin.id.hashCode()}").apply { mkdirs() }
        DexClassLoader(path, odex.absolutePath, plugin.dir.absolutePath, context.classLoader)
    }.getOrNull()

    private fun parseActions(manifest: Map<String, Any?>, id: String, name: String): List<Action> {
        val raw = manifest["actions"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val label = map["label"] as? String ?: return@mapNotNull null
            val script = map["script"] as? String
            val className = map["class"] as? String
            if (script == null && className == null) return@mapNotNull null
            Action(
                id = map["id"] as? String ?: label,
                label = label,
                pluginId = id,
                pluginName = name,
                className = className,
                script = script,
                extensions = (map["extensions"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("*"),
                dirOnly = map["dir"] == true,
                fileOnly = map["file"] == true
            )
        }
    }

    // ---------------- manifest 解析 ----------------

    /**
     * MT 的 manifest.json 允许注释和裸键名，不是严格 JSON。
     * 这里用一个够用的宽松解析器，省掉为一个小文件引一个 JSON 库。
     */
    fun parseManifest(file: File): Map<String, Any?>? = runCatching {
        if (!file.exists()) return null
        @Suppress("UNCHECKED_CAST")
        LenientJson(file.readText()).parse() as? Map<String, Any?>
    }.getOrNull()
}

/** 宽松 JSON：允许两种注释写法、裸键、单引号、尾逗号 */
internal class LenientJson(private val src: String) {

    private var i = 0

    fun parse(): Any? {
        skip()
        val v = value()
        skip()
        return v
    }

    private fun value(): Any? {
        skip()
        if (i >= src.length) return null
        return when (val c = src[i]) {
            '{' -> obj()
            '[' -> arr()
            '"', '\'' -> str(c)
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> if (c.isDigit() || c == '-' || c == '+') num() else { i++; null }
        }
    }

    private fun obj(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        i++ // {
        while (true) {
            skip()
            if (i >= src.length) break
            if (src[i] == '}') { i++; break }
            val key = if (src[i] == '"' || src[i] == '\'') str(src[i]) else bare()
            skip()
            if (i < src.length && src[i] == ':') i++
            val v = value()
            if (key.isNotEmpty()) out[key] = v
            skip()
            if (i < src.length && src[i] == ',') i++
        }
        return out
    }

    private fun arr(): List<Any?> {
        val out = ArrayList<Any?>()
        i++ // [
        while (true) {
            skip()
            if (i >= src.length) break
            if (src[i] == ']') { i++; break }
            out.add(value())
            skip()
            if (i < src.length && src[i] == ',') i++
        }
        return out
    }

    private fun str(quote: Char): String {
        i++
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' && i + 1 < src.length -> {
                    when (val n = src[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'u' -> {
                            if (i + 5 < src.length) {
                                sb.append(src.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: ' ')
                                i += 4
                            }
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                c == quote -> { i++; return sb.toString() }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private fun bare(): String {
        val start = i
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '-' || src[i] == '.')) i++
        return src.substring(start, i)
    }

    private fun num(): Any {
        val start = i
        if (src[i] == '-' || src[i] == '+') i++
        while (i < src.length && (src[i].isDigit() || src[i] == '.' || src[i] == 'e' || src[i] == 'E')) i++
        val text = src.substring(start, i)
        return text.toIntOrNull() ?: text.toDoubleOrNull() ?: 0
    }

    private fun skip() {
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    val end = src.indexOf("*/", i + 2)
                    i = if (end < 0) src.length else end + 2
                }
                else -> return
            }
        }
    }
}
