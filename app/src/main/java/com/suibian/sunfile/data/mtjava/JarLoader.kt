package com.suibian.sunfile.data.mtjava

import java.io.File
import java.util.zip.ZipFile

/**
 * 把插件 `libs/` 下的 jar 读成 [ParsedClass]。
 *
 * 只认 `.class`，跳过 `module-info`、`META-INF/` 和多版本目录 META-INF/versions——
 * 后者是给高版本 JVM 用的，取基础版本即可。同名类先到先得，和 classpath 的语义一致。
 */
internal object JarLoader {

    fun load(libsDir: File): List<ParsedClass> {
        if (!libsDir.isDirectory) return emptyList()
        val jars = libsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", true) }
            ?.sortedBy { it.name }
            ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<ParsedClass>()
        for (jar in jars) {
            loadJar(jar, seen, out)
        }
        return out
    }

    private fun loadJar(jar: File, seen: MutableSet<String>, out: MutableList<ParsedClass>) {
        runCatching {
            ZipFile(jar).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .sortedBy { it.name }
                for (e in entries) {
                    val name = e.name
                    if (name.startsWith("META-INF/") || name == "module-info.class") continue
                    if (!seen.add(name)) continue
                    val bytes = zip.getInputStream(e).readBytes()
                    if (bytes.size < 8) continue
                    val parsed = runCatching { ClassFile.parse(bytes) }.getOrNull() ?: continue
                    out.add(parsed)
                }
            }
        }
    }

    /** 从任意目录读散落的 .class（插件里直接放 class 文件的情况） */
    fun loadDir(dir: File): List<ParsedClass> {
        if (!dir.isDirectory) return emptyList()
        val out = ArrayList<ParsedClass>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }
            .forEach { f ->
                runCatching { ClassFile.parse(f.readBytes()) }.getOrNull()?.let { out.add(it) }
            }
        return out
    }
}
