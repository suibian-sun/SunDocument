package com.suibian.sunfile.data.mtjava

/**
 * `.class` 文件解析。
 *
 * 只解出执行需要的东西：类名、父类、字段类型、方法的 Code 属性与异常表、
 * 以及常量池里会被指令引用的那些项（字符串、整数、成员引用）。
 * 注解、泛型签名、调试信息全部跳过——它们不影响执行。
 *
 * 常量池保留原始 tag 与值，指令按索引取，取不到就报错，不做猜测。
 */
internal class ConstPool(
    private val tags: IntArray,
    private val values: Array<Any?>
) {
    fun tag(i: Int): Int = if (i in 1 until tags.size) tags[i] else 0

    fun utf8(i: Int): String = values.getOrNull(i) as? String ?: ""

    fun className(i: Int): String = utf8(i)

    fun ref(i: Int): MemberRef? = values.getOrNull(i) as? MemberRef

    fun constant(i: Int): Any? = values.getOrNull(i)

    override fun toString(): String = "ConstPool(${tags.size})"
}

internal data class MemberRef(val owner: String, val name: String, val desc: String)

internal class Bytecode(
    val maxStack: Int,
    val maxLocals: Int,
    val code: ByteArray,
    val handlers: List<CatchHandler>,
    val pool: ConstPool,
    val owner: String,
    val method: String,
    val desc: String
)

internal class CatchHandler(val start: Int, val end: Int, val target: Int, val type: String?)

internal class FieldDef(val name: String, val desc: String, val isStatic: Boolean, val access: Int)

internal class MethodDef(
    val name: String,
    val desc: String,
    val access: Int,
    val code: Bytecode?
) {
    val isStatic: Boolean get() = access and 0x0008 != 0
    val isNative: Boolean get() = access and 0x0100 != 0
    val isAbstract: Boolean get() = access and 0x0400 != 0
}

internal class ParsedClass(
    val name: String,
    val superName: String?,
    val interfaces: List<String>,
    val access: Int,
    val fields: List<FieldDef>,
    val methods: List<MethodDef>,
    val pool: ConstPool
) {
    val isInterface: Boolean get() = access and 0x0200 != 0
    val isAbstract: Boolean get() = access and 0x0400 != 0
}

internal object ClassFile {

    fun parse(bytes: ByteArray): ParsedClass {
        val r = Reader(bytes)
        val magic = r.u4()
        if (magic != 0xCAFEBABE.toInt()) error("不是 class 文件")
        r.u2() // minor
        r.u2() // major

        val count = r.u2()
        val tags = IntArray(count)
        val values = arrayOfNulls<Any?>(count)
        var i = 1
        while (i < count) {
            val tag = r.u1()
            tags[i] = tag
            when (tag) {
                1 -> values[i] = r.utf8()
                3 -> values[i] = r.u4()
                4 -> values[i] = Float.fromBits(r.u4())
                5 -> {
                    values[i] = (r.u4().toLong() shl 32) or (r.u4().toLong() and 0xFFFFFFFFL)
                    i++
                }
                6 -> {
                    values[i] = Double.fromBits((r.u4().toLong() shl 32) or (r.u4().toLong() and 0xFFFFFFFFL))
                    i++
                }
                7, 8, 16, 19, 20 -> values[i] = r.u2() // 索引到 utf8 / 描述符
                15 -> {
                    r.u1()
                    r.u2()
                }
                9, 10, 11, 12, 17, 18 -> {
                    val a = r.u2()
                    val b = r.u2()
                    values[i] = intArrayOf(a, b)
                }
                else -> error("未知常量池 tag $tag（第 $i 项）")
            }
            i++
        }
        val pool = ConstPool(tags, values)

        // 常量池里 Class / String / MethodType 都是「指向 utf8 的索引」，
        // 成员引用又是「Class 索引 + NameAndType 索引」，全解成字符串后指令才好用。
        fun resolve(idx: Int): String {
            var v = values.getOrNull(idx)
            var guard = 0
            while (v is Int && guard++ < 4) v = values.getOrNull(v as Int)
            return v as? String ?: ""
        }
        for (k in 1 until count) {
            when (tags[k]) {
                7, 8, 16 -> values[k] = resolve(k)
            }
        }
        for (k in 1 until count) {
            if (tags[k] == 9 || tags[k] == 10 || tags[k] == 11) {
                val pair = values[k] as? IntArray ?: continue
                val nt = values.getOrNull(pair[1]) as? IntArray ?: continue
                values[k] = MemberRef(resolve(pair[0]), resolve(nt[0]), resolve(nt[1]))
            }
        }

        val access = r.u2()
        val thisIdx = r.u2()
        val superIdx = r.u2()
        val name = pool.utf8(thisIdx)
        val superName = if (superIdx == 0) null else pool.utf8(superIdx)

        val ifCount = r.u2()
        val interfaces = (0 until ifCount).map { pool.utf8(r.u2()) }

        val fields = ArrayList<FieldDef>()
        repeat(r.u2()) {
            val fa = r.u2()
            val fn = pool.utf8(r.u2())
            val fd = pool.utf8(r.u2())
            skipAttributes(r, pool)
            fields.add(FieldDef(fn, fd, fa and 0x0008 != 0, fa))
        }

        val methods = ArrayList<MethodDef>()
        repeat(r.u2()) {
            val ma = r.u2()
            val mn = pool.utf8(r.u2())
            val md = pool.utf8(r.u2())
            var code: Bytecode? = null
            val ac = r.u2()
            repeat(ac) {
                val an = pool.utf8(r.u2())
                val len = r.u4()
                val start = r.pos
                if (an == "Code") code = readCode(r, pool, name, mn, md)
                r.pos = start + len
            }
            methods.add(MethodDef(mn, md, ma, code))
        }
        return ParsedClass(name, superName, interfaces, access, fields, methods, pool)
    }

    private fun readCode(r: Reader, pool: ConstPool, owner: String, method: String, desc: String): Bytecode {
        val maxStack = r.u2()
        val maxLocals = r.u2()
        val len = r.u4()
        val code = r.bytes(len)
        val handlers = ArrayList<CatchHandler>()
        repeat(r.u2()) {
            val start = r.u2()
            val end = r.u2()
            val target = r.u2()
            val typeIdx = r.u2()
            handlers.add(CatchHandler(start, end, target, if (typeIdx == 0) null else pool.utf8(typeIdx)))
        }
        // 属性（LineNumberTable / LocalVariableTable）对执行没用，整体跳过
        val attrCount = r.u2()
        repeat(attrCount) {
            r.u2()
            val alen = r.u4()
            r.skip(alen)
        }
        return Bytecode(maxStack, maxLocals, code, handlers, pool, owner, method, desc)
    }

    private fun skipAttributes(r: Reader, pool: ConstPool) {
        val n = r.u2()
        repeat(n) {
            r.u2()
            val len = r.u4()
            r.skip(len)
        }
    }

    /** 大端读取；class 文件里所有多字节都是大端 */
    private class Reader(private val b: ByteArray) {
        var pos = 0

        fun u1(): Int = b[pos++].toInt() and 0xFF

        fun u2(): Int = (u1() shl 8) or u1()

        fun u4(): Int = (u2() shl 16) or u2()

        fun bytes(n: Int): ByteArray {
            val out = b.copyOfRange(pos, pos + n)
            pos += n
            return out
        }

        fun skip(n: Int) {
            pos += n
        }

        fun utf8(): String {
            val n = u2()
            val s = String(b, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }
    }
}

/** 描述符转 Java 源码里的类型名，交给统一的类型转换用 */
internal fun descToType(desc: String): String = when {
    desc.startsWith("[") -> descToType(desc.substring(1)) + "[]"
    desc == "I" -> "int"
    desc == "J" -> "long"
    desc == "S" -> "short"
    desc == "B" -> "byte"
    desc == "C" -> "char"
    desc == "Z" -> "boolean"
    desc == "F" -> "float"
    desc == "D" -> "double"
    desc == "V" -> "void"
    desc.startsWith("L") -> desc.removePrefix("L").removeSuffix(";").replace('/', '.')
    else -> "java.lang.Object"
}

/** 参数列表：([ILjava/lang/String;)V → [int, java.lang.String] */
internal fun descParams(desc: String): List<String> {
    val out = ArrayList<String>()
    var i = desc.indexOf('(') + 1
    while (i < desc.length && desc[i] != ')') {
        val start = i
        while (desc[i] == '[') i++
        if (desc[i] == 'L') i = desc.indexOf(';', i)
        i++
        out.add(descToType(desc.substring(start, i)))
    }
    return out
}

internal fun descReturn(desc: String): String = descToType(desc.substring(desc.indexOf(')') + 1))
