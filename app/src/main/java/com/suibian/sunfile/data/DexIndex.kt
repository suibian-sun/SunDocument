package com.suibian.sunfile.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 只读的 dex 索引：类、方法、字段、字符串。
 *
 * 用于「反编译前先看清结构」——不做 Smali 生成，也不回写 dex，
 * 所以不会出现改坏安装包的风险。
 */
object DexIndex {

    data class DexMethod(val name: String, val descriptor: String, val access: Int)

    data class DexField(val name: String, val type: String, val access: Int)

    data class DexClass(
        val descriptor: String,
        val superDescriptor: String?,
        val access: Int,
        val sourceFile: String?,
        val methods: List<DexMethod>,
        val fields: List<DexField>
    ) {
        val name: String get() = pretty(descriptor)
        val superName: String? get() = superDescriptor?.let { pretty(it) }
        val isInterface: Boolean get() = access and 0x0200 != 0
    }

    data class DexFile(
        val classes: List<DexClass>,
        val strings: List<String>,
        val methodCount: Int,
        val fieldCount: Int
    )

    class DexException(message: String) : Exception(message)

    fun pretty(descriptor: String): String = when {
        descriptor.startsWith("L") && descriptor.endsWith(";") ->
            descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        descriptor.startsWith("[") -> descriptor
        else -> descriptor
    }

    fun read(bytes: ByteArray, maxClasses: Int = 40_000): DexFile {
        if (bytes.size < 0x70) throw DexException("dex 过小")
        if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() || bytes[2] != 'x'.code.toByte()) {
            throw DexException("不是 dex 文件")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val u32: (Int) -> Int = { off -> if (off >= 0 && off + 4 <= bytes.size) buf.getInt(off) else 0 }

        val stringCount = u32(0x38)
        val stringOff = u32(0x3C)
        val typeCount = u32(0x40)
        val typeOff = u32(0x44)
        val fieldCount = u32(0x50)
        val fieldOff = u32(0x54)
        val methodCount = u32(0x58)
        val methodOff = u32(0x5C)
        val classCount = u32(0x60)
        val classOff = u32(0x64)

        if (stringCount <= 0 || stringCount > 2_000_000) throw DexException("字符串表异常")
        if (classOff + classCount * 32L > bytes.size) throw DexException("class_defs 越界")

        val strings = ArrayList<String>(minOf(stringCount, 200_000))
        for (i in 0 until minOf(stringCount, 200_000)) {
            val off = u32(stringOff + i * 4)
            strings.add(if (off in 0 until bytes.size) readMutf8(bytes, off) else "")
        }

        fun typeName(idx: Int): String {
            if (idx < 0 || idx >= typeCount) return ""
            val sidx = u32(typeOff + idx * 4)
            return if (sidx in strings.indices) strings[sidx] else ""
        }

        var totalMethods = 0
        var totalFields = 0
        val classes = ArrayList<DexClass>(minOf(classCount, maxClasses))
        for (ci in 0 until minOf(classCount, maxClasses)) {
            val base = classOff + ci * 32
            if (base + 32 > bytes.size) break
            val classIdx = u32(base)
            val access = u32(base + 4)
            val superIdx = u32(base + 8)
            val sourceIdx = u32(base + 16)
            val classDataOff = u32(base + 24)
            val descriptor = typeName(classIdx)
            if (descriptor.isEmpty()) continue
            val superDesc = if (superIdx >= 0) typeName(superIdx).ifEmpty { null } else null
            val source = if (sourceIdx in strings.indices) strings[sourceIdx] else null

            val methods = ArrayList<DexMethod>()
            val fields = ArrayList<DexField>()
            if (classDataOff > 0 && classDataOff < bytes.size) {
                var p = classDataOff
                val staticFields = uleb(bytes, p)
                p = staticFields.next
                val instanceFields = uleb(bytes, p)
                p = instanceFields.next
                val directMethods = uleb(bytes, p)
                p = directMethods.next
                val virtualMethods = uleb(bytes, p)
                p = virtualMethods.next

                val fieldTotal = (staticFields.value + instanceFields.value).coerceAtMost(20_000)
                var fieldIdx = 0
                for (k in 0 until fieldTotal) {
                    val diff = uleb(bytes, p)
                    p = diff.next
                    val acc = uleb(bytes, p)
                    p = acc.next
                    fieldIdx += diff.value
                    val fidx = fieldIdx
                    if (fidx in 0 until fieldCount) {
                        val fbase = fieldOff + fidx * 8
                        if (fbase + 8 <= bytes.size) {
                            val nameIdx = u32(fbase + 4)
                            val typeIdx = buf.getShort(fbase + 2).toInt() and 0xFFFF
                            fields.add(
                                DexField(
                                    name = strings.getOrElse(nameIdx) { "" },
                                    type = typeName(typeIdx),
                                    access = acc.value
                                )
                            )
                        }
                    }
                }

                val methodTotal = (directMethods.value + virtualMethods.value).coerceAtMost(50_000)
                var methodIdx = 0
                for (k in 0 until methodTotal) {
                    val diff = uleb(bytes, p)
                    p = diff.next
                    val acc = uleb(bytes, p)
                    p = acc.next
                    val code = uleb(bytes, p)
                    p = code.next
                    methodIdx += diff.value
                    if (methodIdx in 0 until methodCount) {
                        val mbase = methodOff + methodIdx * 8
                        if (mbase + 8 <= bytes.size) {
                            val protoIdx = buf.getShort(mbase + 2).toInt() and 0xFFFF
                            val nameIdx = u32(mbase + 4)
                            methods.add(
                                DexMethod(
                                    name = strings.getOrElse(nameIdx) { "" },
                                    descriptor = protoOf(protoIdx, buf, u32(0x4C), typeCount, typeOff, strings, u32),
                                    access = acc.value
                                )
                            )
                        }
                    }
                }
            }
            totalMethods += methods.size
            totalFields += fields.size
            classes.add(DexClass(descriptor, superDesc, access, source, methods, fields))
        }
        return DexFile(classes, strings, totalMethods, totalFields)
    }

    private fun protoOf(
        protoIdx: Int,
        buf: ByteBuffer,
        protoOff: Int,
        typeCount: Int,
        typeOff: Int,
        strings: List<String>,
        u32: (Int) -> Int
    ): String {
        if (protoIdx < 0) return ""
        val base = protoOff + protoIdx * 12
        return runCatching {
            val returnIdx = u32(base + 4)
            val paramsOff = u32(base + 8)
            val ret = typeAt(returnIdx, typeCount, typeOff, strings, u32)
            if (paramsOff <= 0) return@runCatching "()$ret"
            val count = u32(paramsOff)
            val sb = StringBuilder("(")
            for (i in 0 until count.coerceAtMost(64)) {
                sb.append(typeAt(u32(paramsOff + 4 + i * 2), typeCount, typeOff, strings, u32))
            }
            sb.append(")").append(ret)
            sb.toString()
        }.getOrDefault("")
    }

    private fun typeAt(idx: Int, typeCount: Int, typeOff: Int, strings: List<String>, u32: (Int) -> Int): String {
        if (idx < 0 || idx >= typeCount) return ""
        val sidx = u32(typeOff + idx * 4)
        return strings.getOrElse(sidx) { "" }
    }

    private class Uleb(val value: Int, val next: Int)

    private fun uleb(bytes: ByteArray, start: Int): Uleb {
        var result = 0
        var shift = 0
        var p = start
        while (p < bytes.size && shift < 35) {
            val b = bytes[p].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            p++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Uleb(result, p)
    }

    /** MUTF-8：0xC0 0x80 表示 \u0000，改成普通 UTF-8 后解码即可 */
    private fun readMutf8(bytes: ByteArray, start: Int): String {
        val out = java.io.ByteArrayOutputStream(32)
        var p = start
        var guard = 0
        while (p < bytes.size && guard < 8192) {
            val b = bytes[p].toInt() and 0xFF
            if (b == 0) break
            if (b == 0xC0 && p + 1 < bytes.size && (bytes[p + 1].toInt() and 0xFF) == 0x80) {
                out.write(0)
                p += 2
            } else {
                out.write(b)
                p++
            }
            guard++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun accessLabel(flags: Int, isClass: Boolean): String {
        val parts = ArrayList<String>()
        fun on(bit: Int, text: String) {
            if (flags and bit != 0) parts.add(text)
        }
        if (isClass) {
            on(0x0001, "public"); on(0x0002, "private"); on(0x0004, "protected")
            on(0x0008, "static"); on(0x0010, "final"); on(0x0200, "interface")
            on(0x0400, "abstract"); on(0x1000, "synthetic"); on(0x2000, "annotation"); on(0x4000, "enum")
        } else {
            on(0x0001, "public"); on(0x0002, "private"); on(0x0004, "protected")
            on(0x0008, "static"); on(0x0010, "final"); on(0x0020, "synchronized")
            on(0x0100, "native"); on(0x0400, "abstract"); on(0x1000, "synthetic")
        }
        return parts.joinToString(" ")
    }
}
