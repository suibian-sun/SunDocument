package com.suibian.sunfile.data

import java.io.File

/**
 * 文件对比：文本按行做 LCS diff，文件夹 / 压缩包按条目清单做差异报告。
 * 只读、无副作用，可直接在 IO 线程调用。
 */
object Diff {

    enum class Op { SAME, ADD, DEL }

    data class Row(val op: Op, val oldLine: Int, val newLine: Int, val text: String)

    /** 单侧行数乘积超过这个量级时退化为整段替换，避免 DP 表撑爆内存 */
    private const val DP_LIMIT = 1_500_000L

    fun lines(old: String, new: String): List<Row> =
        lines(old.split('\n'), new.split('\n'))

    fun lines(a: List<String>, b: List<String>): List<Row> {
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--
            endB--
        }

        val rows = ArrayList<Row>(a.size + b.size)
        for (i in 0 until start) rows += Row(Op.SAME, i + 1, i + 1, a[i])

        val n = endA - start
        val m = endB - start
        if (n == 0 && m == 0) {
            // 整段都相同，只补尾部
        } else if (n.toLong() * m.toLong() > DP_LIMIT) {
            // 太大：不做逐行对齐，按整段替换展示
            for (i in 0 until n) rows += Row(Op.DEL, start + i + 1, 0, a[start + i])
            for (j in 0 until m) rows += Row(Op.ADD, 0, start + j + 1, b[start + j])
        } else {
            rows += lcs(a, b, start, endA, endB)
        }

        val tail = a.size - endA
        for (k in 0 until tail) {
            rows += Row(Op.SAME, endA + k + 1, endB + k + 1, a[endA + k])
        }
        return rows
    }

    private fun lcs(a: List<String>, b: List<String>, start: Int, endA: Int, endB: Int): List<Row> {
        val n = endA - start
        val m = endB - start
        val w = m + 1
        val dp = IntArray((n + 1) * w)
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i * w + j] = if (a[start + i] == b[start + j]) {
                    dp[(i + 1) * w + j + 1] + 1
                } else {
                    maxOf(dp[(i + 1) * w + j], dp[i * w + j + 1])
                }
            }
        }
        val out = ArrayList<Row>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[start + i] == b[start + j] -> {
                    out += Row(Op.SAME, start + i + 1, start + j + 1, a[start + i])
                    i++
                    j++
                }
                dp[(i + 1) * w + j] >= dp[i * w + j + 1] -> {
                    out += Row(Op.DEL, start + i + 1, 0, a[start + i])
                    i++
                }
                else -> {
                    out += Row(Op.ADD, 0, start + j + 1, b[start + j])
                    j++
                }
            }
        }
        while (i < n) {
            out += Row(Op.DEL, start + i + 1, 0, a[start + i])
            i++
        }
        while (j < m) {
            out += Row(Op.ADD, 0, start + j + 1, b[start + j])
            j++
        }
        return out
    }

    fun stats(rows: List<Row>): Triple<Int, Int, Int> {
        var add = 0
        var del = 0
        var same = 0
        for (r in rows) when (r.op) {
            Op.ADD -> add++
            Op.DEL -> del++
            Op.SAME -> same++
        }
        return Triple(add, del, same)
    }

    // ---------------- 目录 / 压缩包对比 ----------------

    data class Node(val name: String, val size: Long, val isDir: Boolean, val time: Long = 0L)

    fun dirNodes(root: File, onFile: (String) -> Unit = {}): Map<String, Node> {
        val map = LinkedHashMap<String, Node>()
        val base = root.absolutePath
        fun walk(dir: File, depth: Int) {
            if (depth > 24) return
            val list = dir.listFiles() ?: return
            for (f in list) {
                val rel = f.absolutePath.removePrefix(base).trimStart('/')
                if (rel.isEmpty()) continue
                val isDir = f.isDirectory
                map[rel] = Node(f.name, if (isDir) 0L else f.length(), isDir, f.lastModified())
                onFile(rel)
                if (isDir) walk(f, depth + 1)
            }
        }
        if (root.isDirectory) walk(root, 0)
        return map
    }

    fun zipNodes(file: File): Map<String, Node> {
        val map = LinkedHashMap<String, Node>()
        runCatching { FileRepo.archiveEntries(file) }.getOrNull()?.forEach { item ->
            val name = item.name.trimEnd('/')
            map[name] = Node(name, item.size, item.isDir, item.time)
        }
        return map
    }

    /** 两棵树逐条对比，输出 MT 风格的文本报告 */
    fun treeReport(left: String, right: String, a: Map<String, Node>, b: Map<String, Node>): String {
        val sb = StringBuilder()
        sb.append("对比 $left\n与　 $right\n\n")
        val onlyA = a.keys - b.keys
        val onlyB = b.keys - a.keys
        val both = a.keys.intersect(b.keys)
        var changed = 0
        var onlyCount = 0
        for (k in onlyA.sorted()) {
            sb.append("仅左侧  ").append(if (a[k]?.isDir == true) "$k/" else k).append('\n')
            onlyCount++
        }
        for (k in onlyB.sorted()) {
            sb.append("仅右侧  ").append(if (b[k]?.isDir == true) "$k/" else k).append('\n')
            onlyCount++
        }
        for (k in both.sorted()) {
            val x = a.getValue(k)
            val y = b.getValue(k)
            if (x.isDir != y.isDir) {
                sb.append("类型不同  ").append(k).append('\n')
                changed++
            } else if (!x.isDir && x.size != y.size) {
                sb.append("大小不同  ").append(k)
                    .append("  ").append(x.size).append(" → ").append(y.size).append('\n')
                changed++
            }
        }
        sb.append("\n共 ").append(a.size).append(" / ").append(b.size).append(" 项，仅一侧 ")
            .append(onlyCount).append(" 项，内容不同 ").append(changed).append(" 项")
        return sb.toString()
    }
}
