package com.suibian.sunfile.data

import java.io.ByteArrayOutputStream

/**
 * 文本 XML → 二进制 AXML。
 *
 * 与 [Axml.decode] 配对使用：解出来的文本改完再编译回去，就能写回 APK。
 * 属性名会查一张 `android.R.attr` 表写进 resource map；表里没有的自定义属性
 * 仍然会写进字符串池，但 resource id 记 0，系统按名字找不到时就忽略。
 */
object AxmlEncoder {

    private class Node(val name: String) {
        val attrs = ArrayList<Pair<String, String>>()
        val children = ArrayList<Node>()
        val text = StringBuilder()
        var parent: Node? = null
    }

    private class Ns(val prefix: String, val uri: String)

    private class Buf(cap: Int = 8192) {
        var data = ByteArray(cap)
        var size = 0

        private fun ensure(n: Int) {
            if (size + n <= data.size) return
            var cap = data.size
            while (cap < size + n) cap *= 2
            data = data.copyOf(cap)
        }

        fun u8(v: Int) { ensure(1); data[size++] = v.toByte() }
        fun u16(v: Int) { u8(v and 0xFF); u8((v ushr 8) and 0xFF) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        fun raw(b: ByteArray) { ensure(b.size); System.arraycopy(b, 0, data, size, b.size); size += b.size }

        fun patch32(pos: Int, v: Int) {
            data[pos] = v.toByte()
            data[pos + 1] = (v ushr 8).toByte()
            data[pos + 2] = (v ushr 16).toByte()
            data[pos + 3] = (v ushr 24).toByte()
        }

        fun toByteArray(): ByteArray = data.copyOf(size)
    }

    fun encode(text: String): ByteArray {
        val root = parse(text)
        val nsList = collectNs(root)

        // 1. 属性名先去重，放在字符串池最前面，resource map 与之一一对应
        val attrNames = LinkedHashSet<String>()
        walk(root) { n ->
            for ((k, _) in n.attrs) {
                if (k.startsWith("xmlns")) continue
                attrNames.add(k.substringAfter(':'))
            }
        }

        val pool = ArrayList<String>()
        val index = HashMap<String, Int>()
        fun intern(s: String): Int = index.getOrPut(s) {
            pool.add(s)
            pool.size - 1
        }

        val attrNameIdx = HashMap<String, Int>()
        for (a in attrNames) attrNameIdx[a] = intern(a)

        // 2. 收集其余字符串
        for (ns in nsList) {
            intern(ns.prefix)
            intern(ns.uri)
        }

        val nodes = ArrayList<Node>()
        walk(root) { if (it !== root) nodes.add(it) }
        for (n in nodes) {
            intern(n.name)
            for ((k, v) in n.attrs) {
                if (k.startsWith("xmlns")) continue
                if (typeOf(v) == 0x03) intern(v)
            }
            if (n.text.isNotBlank()) intern(n.text.toString().trim())
        }

        val buf = Buf()
        buf.u16(0x0003)
        buf.u16(8)
        val totalPos = buf.size
        buf.u32(0)

        writeStringPool(buf, pool)

        // resource map
        buf.u16(0x0180)
        buf.u16(8)
        buf.u32(8 + attrNames.size * 4)
        for (a in attrNames) buf.u32(ATTR_IDS[a] ?: 0)

        // 命名空间
        for (ns in nsList) {
            buf.u16(0x0100)
            buf.u16(16)
            buf.u32(24)
            buf.u32(1)
            buf.u32(-1)
            buf.u32(index[ns.prefix] ?: 0)
            buf.u32(index[ns.uri] ?: 0)
        }

        // 节点
        fun writeNode(n: Node) {
            val hasText = n.text.isNotBlank() && n.children.isEmpty()
            val attrCount = n.attrs.count { !it.first.startsWith("xmlns") }
            buf.u16(0x0102)
            buf.u16(16)
            buf.u32(16 + 20 + attrCount * 20)
            buf.u32(1)
            buf.u32(-1)
            buf.u32(-1)
            buf.u32(index[n.name] ?: 0)
            buf.u16(20)
            buf.u16(20)
            buf.u16(attrCount)
            buf.u16(0)
            buf.u16(0)
            buf.u16(0)
            for ((k, v) in n.attrs) {
                if (k.startsWith("xmlns")) continue
                val prefix = if (k.contains(':')) k.substringBefore(':') else ""
                val name = k.substringAfter(':')
                val nsIdx = if (prefix.isNotEmpty()) {
                    nsList.firstOrNull { it.prefix == prefix }?.let { index[it.uri] } ?: -1
                } else -1
                buf.u32(if (nsIdx < 0) -1 and 0xFFFFFFFF.toInt() else nsIdx)
                buf.u32(attrNameIdx[name] ?: (index[name] ?: 0))
                val t = typeOf(v)
                val data = dataOf(v, t, index)
                buf.u32(if (t == 0x03) (index[v] ?: 0) else -1)
                buf.u16(8)
                buf.u8(0)
                buf.u8(t)
                buf.u32(data)
            }
            if (hasText) {
                buf.u16(0x0104)
                buf.u16(16)
                buf.u32(20)
                buf.u32(1)
                buf.u32(-1)
                buf.u32(index[n.text.toString().trim()] ?: 0)
            }
            for (c in n.children) writeNode(c)
            buf.u16(0x0103)
            buf.u16(16)
            buf.u32(24)
            buf.u32(1)
            buf.u32(-1)
            buf.u32(-1)
            buf.u32(index[n.name] ?: 0)
        }

        for ((i, c) in root.children.withIndex()) {
            writeNode(c)
        }

        for (ns in nsList.reversed()) {
            buf.u16(0x0101)
            buf.u16(16)
            buf.u32(24)
            buf.u32(1)
            buf.u32(-1)
            buf.u32(index[ns.prefix] ?: 0)
            buf.u32(index[ns.uri] ?: 0)
        }

        buf.patch32(totalPos, buf.size)
        return buf.toByteArray()
    }

    private fun writeStringPool(buf: Buf, pool: List<String>) {
        val offsets = IntArray(pool.size)
        val data = ByteArrayOutputStream()
        for ((i, s) in pool.withIndex()) {
            offsets[i] = data.size()
            val chars = s.toCharArray()
            if (chars.size > 0x7FFF) {
                data.write(0xFF)
                data.write(0xFF)
                data.write(0x80)
                data.write(0x00)
                data.write(chars.size and 0xFF)
                data.write((chars.size ushr 8) and 0xFF)
                data.write((chars.size ushr 16) and 0xFF)
                data.write((chars.size ushr 24) and 0xFF)
            } else {
                data.write(chars.size and 0xFF)
                data.write((chars.size ushr 8) and 0xFF)
            }
            for (c in chars) {
                data.write(c.code and 0xFF)
                data.write((c.code ushr 8) and 0xFF)
            }
            data.write(0)
            data.write(0)
        }
        val dataBytes = data.toByteArray()
        val headerSize = 28
        val size = headerSize + pool.size * 4 + dataBytes.size
        buf.u16(0x0001)
        buf.u16(headerSize)
        buf.u32(size)
        buf.u32(pool.size)
        buf.u32(0)
        buf.u32(0x00000000)
        buf.u32(headerSize + pool.size * 4)
        buf.u32(0)
        for (o in offsets) buf.u32(o)
        buf.raw(dataBytes)
    }

    // ---------------------------------------------------------------- 解析

    private fun parse(text: String): Node {
        val root = Node("#root")
        var cur = root
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '<') {
                if (text.startsWith("<!--", i)) {
                    val e = text.indexOf("-->", i)
                    i = if (e < 0) n else e + 3
                    continue
                }
                if (text.startsWith("<?", i)) {
                    val e = text.indexOf("?>", i)
                    i = if (e < 0) n else e + 2
                    continue
                }
                if (text.startsWith("</", i)) {
                    val e = text.indexOf('>', i)
                    i = if (e < 0) n else e + 1
                    cur = cur.parent ?: root
                    continue
                }
                // 开始标签
                i++
                val nameStart = i
                while (i < n && !text[i].isWhitespace() && text[i] != '>' && text[i] != '/') i++
                val name = text.substring(nameStart, i)
                val node = Node(name)
                node.parent = cur
                var selfClose = false
                while (i < n) {
                    while (i < n && text[i].isWhitespace()) i++
                    if (i < n && text[i] == '/') { selfClose = true; i++; continue }
                    if (i < n && text[i] == '>') { i++; break }
                    val aStart = i
                    while (i < n && text[i] != '=' && !text[i].isWhitespace() && text[i] != '>') i++
                    val key = text.substring(aStart, i).trim()
                    while (i < n && text[i].isWhitespace()) i++
                    if (i < n && text[i] == '=') {
                        i++
                        while (i < n && text[i].isWhitespace()) i++
                        val quote = if (i < n) text[i] else '"'
                        if (quote == '"' || quote == '\'') {
                            i++
                            val vStart = i
                            while (i < n && text[i] != quote) i++
                            val v = unescape(text.substring(vStart, i))
                            i++
                            if (key.isNotEmpty()) node.attrs.add(key to v)
                        }
                    } else if (key.isNotEmpty()) {
                        node.attrs.add(key to "")
                    }
                }
                cur.children.add(node)
                if (!selfClose) cur = node
                continue
            }
            val next = text.indexOf('<', i)
            val end = if (next < 0) n else next
            var chunk = text.substring(i, end)
            i = end
            if (chunk.isBlank()) continue
            // 顶层 xmlns:android="..." 这类声明
            val trimmed = chunk.trim()
            if (trimmed.startsWith("xmlns")) {
                for (line in trimmed.lines()) {
                    val l = line.trim()
                    if (!l.startsWith("xmlns")) {
                        if (l.isNotBlank()) cur.text.append(unescape(l))
                        continue
                    }
                    val eq = l.indexOf('=')
                    if (eq < 0) continue
                    val key = l.substring(0, eq).trim()
                    val v = l.substring(eq + 1).trim().trim('"', '\'')
                    val prefix = key.removePrefix("xmlns:").let { if (key == "xmlns") "" else it }
                    root.attrs.add("xmlns:$prefix" to v)
                }
                continue
            }
            cur.text.append(unescape(trimmed))
        }
        return root
    }

    private fun collectNs(root: Node): List<Ns> {
        val out = ArrayList<Ns>()
        val seen = HashSet<String>()
        walk(root) { n ->
            for ((k, v) in n.attrs) {
                if (!k.startsWith("xmlns")) continue
                val prefix = if (k == "xmlns") "" else k.substringAfter(':')
                if (v.isEmpty() || !seen.add(prefix + "\u0000" + v)) continue
                out.add(Ns(prefix, v))
            }
        }
        return out
    }

    private fun walk(n: Node, visit: (Node) -> Unit) {
        visit(n)
        for (c in n.children) walk(c, visit)
    }

    // ---------------------------------------------------------------- 值处理

    private fun typeOf(v: String): Int {
        if (v.startsWith("@0x") || v.matches(Regex("@0x[0-9a-fA-F]+"))) return 0x01
        if (v.startsWith("?0x")) return 0x02
        if (v == "true" || v == "false") return 0x12
        if (v.matches(Regex("-?\\d+"))) return 0x10
        if (v.matches(Regex("0x[0-9a-fA-F]+"))) return 0x11
        if (v.matches(Regex("#[0-9a-fA-F]{6,8}"))) return 0x1c
        return 0x03
    }

    private fun dataOf(v: String, type: Int, index: Map<String, Int>): Int = when (type) {
        0x01, 0x02 -> v.substring(3).toLongOrNull(16)?.toInt() ?: 0
        0x12 -> if (v == "true") -1 else 0
        0x10 -> v.toIntOrNull() ?: 0
        0x11 -> v.substring(2).toLongOrNull(16)?.toInt() ?: 0
        0x1c -> {
            val hex = v.substring(1)
            val rgb = hex.toLongOrNull(16)?.toInt() ?: 0
            when (hex.length) {
                6 -> (0xFF000000.toInt()) or rgb
                8 -> rgb
                else -> rgb
            }
        }
        else -> index[v] ?: 0
    }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** android.R.attr 的常用 id，写回时系统靠它识别属性 */
    private val ATTR_IDS: Map<String, Int> = mapOf(
        "theme" to 0x01010000, "label" to 0x01010001, "icon" to 0x01010002,
        "name" to 0x01010003, "permission" to 0x01010006, "protectionLevel" to 0x01010009,
        "priority" to 0x0101000c, "enabled" to 0x0101000e, "debuggable" to 0x0101000f,
        "exported" to 0x01010010, "process" to 0x01010011, "taskAffinity" to 0x01010012,
        "authorities" to 0x01010018, "grantUriPermissions" to 0x0101001b,
        "launchMode" to 0x0101001d, "screenOrientation" to 0x0101001e,
        "configChanges" to 0x0101001f, "value" to 0x01010024, "resource" to 0x01010025,
        "mimeType" to 0x01010026, "scheme" to 0x01010027, "host" to 0x01010028,
        "port" to 0x01010029, "path" to 0x0101002a, "pathPrefix" to 0x0101002b,
        "pathPattern" to 0x0101002c, "targetActivity" to 0x01010022,
        "minSdkVersion" to 0x0101020c, "versionCode" to 0x0101021b, "versionName" to 0x0101021c,
        "targetSdkVersion" to 0x01010270, "maxSdkVersion" to 0x01010271,
        "largeHeap" to 0x01010271, "allowBackup" to 0x01010280,
        "hardwareAccelerated" to 0x010102d3, "extractNativeLibs" to 0x010104ea,
        "usesCleartextTraffic" to 0x010104ec, "roundIcon" to 0x0101052c,
        "requestLegacyExternalStorage" to 0x01010603, "directBootAware" to 0x01010505,
        "appComponentFactory" to 0x0101057a, "usesCleartextTraffic2" to 0x010104ec
    )
}
