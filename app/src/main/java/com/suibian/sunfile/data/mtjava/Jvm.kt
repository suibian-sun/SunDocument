package com.suibian.sunfile.data.mtjava

/**
 * JVM 字节码解释器。
 *
 * 存在的理由：插件的 libs 目录里是 `.class`，Android 上没有 d8 把它变成 dex，
 * DexClassLoader 也加载不了。于是把 class 文件解析出来，按指令直接执行。
 *
 * 与 [Interp] 共用一套对象模型：解释出来的实例就是 [JObj]，静态字段挂在 [JClass]，
 * 调宿主类（String / List / File / Android API）走 [Native] 的反射桥。
 *
 * 覆盖常规编译器生成的指令；`jsr`、`ret`、`invokedynamic` 这类老式或动态指令会直接报错，
 * 带上方法名与 pc，不静默返回 null。
 */
internal object Jvm {

    private class JvmThrow(val value: Any?) : RuntimeException(null, null, false, false)
    private class Return(val v: Any?) : RuntimeException(null, null, false, false)

    /** 指令名只用于报错，按 opcode 下标摆放 */
    private val OPS = arrayOf(
        "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4",
        "iconst_5", "lconst_0", "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0", "dconst_1",
        "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload", "lload", "fload", "dload", "aload",
        "iload_0", "iload_1", "iload_2", "iload_3", "lload_0", "lload_1", "lload_2", "lload_3",
        "fload_0", "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2", "dload_3",
        "aload_0", "aload_1", "aload_2", "aload_3", "iaload", "laload", "faload", "daload", "aaload",
        "baload", "caload", "saload", "istore", "lstore", "fstore", "dstore", "astore",
        "istore_0", "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1", "lstore_2", "lstore_3",
        "fstore_0", "fstore_1", "fstore_2", "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
        "astore_0", "astore_1", "astore_2", "astore_3", "iastore", "lastore", "fastore", "dastore",
        "aastore", "bastore", "castore", "sastore", "pop", "pop2", "dup", "dup_x1", "dup_x2",
        "dup2", "dup2_x1", "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub", "fsub",
        "dsub", "imul", "lmul", "fmul", "dmul", "idiv", "ldiv", "fdiv", "ddiv", "irem", "lrem", "frem",
        "drem", "ineg", "lneg", "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr",
        "iand", "land", "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f", "i2d", "l2i", "l2f",
        "l2d", "f2i", "f2l", "f2d", "d2i", "d2l", "d2f", "i2b", "i2c", "i2s", "lcmp", "fcmpl",
        "fcmpg", "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "if_icmpeq",
        "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne",
        "goto", "jsr", "ret", "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn",
        "dreturn", "areturn", "return", "getstatic", "putstatic", "getfield", "putfield",
        "invokevirtual", "invokespecial", "invokestatic", "invokeinterface", "invokedynamic",
        "new", "newarray", "anewarray", "arraylength", "athrow", "checkcast", "instanceof",
        "monitorenter", "monitorexit", "wide", "multianewarray", "ifnull", "ifnonnull", "goto_w",
        "jsr_w"
    )

    fun run(interp: Interp, m: JMethod, self: JObj?, args: List<Any?>): Any? {
        val bc = m.code ?: return null
        val code = bc.code
        val cp = bc.pool
        val locals = arrayOfNulls<Any?>(maxOf(bc.maxLocals, 1))
        var slot = 0
        if (!m.isStatic) {
            locals[0] = self
            slot = 1
        }
        for (i in m.params.indices) {
            val t = m.params[i].type
            locals[slot] = V.coerce(t, args.getOrNull(i))
            slot += if (t == "long" || t == "double") 2 else 1
        }
        val stack = ArrayList<Any?>()
        var pc = 0

        fun u1(o: Int): Int = code[o].toInt() and 0xFF
        fun s1(o: Int): Int = code[o].toInt()
        fun u2(o: Int): Int = (u1(o) shl 8) or u1(o + 1)
        fun s2(o: Int): Int = u2(o).toShort().toInt()
        fun s4(o: Int): Int = (u1(o) shl 24) or (u1(o + 1) shl 16) or (u1(o + 2) shl 8) or u1(o + 3)

        fun push(v: Any?) {
            if (stack.size > bc.maxStack + 16) {
                throw MtRuntimeException("${bc.owner}.${bc.method} 栈溢出（max_stack=${bc.maxStack}）")
            }
            stack.add(v)
        }
        fun pop(): Any? = if (stack.isEmpty()) null else stack.removeAt(stack.size - 1)
        fun popInt(): Int = V.toInt(pop())
        fun popLong(): Long = V.toLong(pop())
        fun popFloat(): Float = V.toDouble(pop()).toFloat()
        fun popDouble(): Double = V.toDouble(pop())
        fun wide(v: Any?): Boolean = v is Long || v is Double
        fun peek(): Any? = stack.lastOrNull()
        fun popArgs(n: Int): List<Any?> = if (n == 0) emptyList() else {
            val out = ArrayList<Any?>(n)
            for (i in 0 until n) out.add(null)
            for (i in n - 1 downTo 0) out[i] = pop()
            out
        }

        var steps = 0L
        try {
            while (true) {
                if (pc < 0 || pc >= code.size) throw MtRuntimeException("${bc.owner}.${bc.method} 越界 pc=$pc")
                if (++steps > 20_000_000L) throw MtRuntimeException("${bc.owner}.${bc.method} 执行步数过多，疑似死循环")
                val start = pc
                val op = u1(pc++)
                try {
                    when (op) {
                        0 -> {}
                        1 -> push(null)
                        in 2..8 -> push(op - 3)
                        9 -> push(0L)
                        10 -> push(1L)
                        11 -> push(0f)
                        12 -> push(1f)
                        13 -> push(2f)
                        14 -> push(0.0)
                        15 -> push(1.0)
                        16 -> { push(s1(pc)); pc += 1 }
                        17 -> { push(s2(pc)); pc += 2 }
                        18 -> { push(cp.constant(u1(pc))); pc += 1 }
                        19, 20 -> { push(cp.constant(u2(pc))); pc += 2 }
                        in 21..25 -> {
                            val i = u1(pc)
                            pc += 1
                            val v = loadTyped(op, locals[i])
                            locals[i] = v
                            push(v)
                        }
                        in 26..45 -> {
                            val kind = 21 + (op - 26) / 4
                            val i = (op - 26) % 4
                            val v = loadTyped(kind, locals[i])
                            locals[i] = v
                            push(v)
                        }
                        in 46..53 -> {
                            val idx = popInt()
                            val arr = pop()
                            push(arrayLoad(op, arr, idx))
                        }
                        in 54..58 -> {
                            val i = u1(pc)
                            pc += 1
                            locals[i] = storeTyped(op, pop())
                        }
                        in 59..78 -> {
                            val kind = 54 + (op - 59) / 4
                            val i = (op - 59) % 4
                            locals[i] = storeTyped(kind, pop())
                        }
                        in 79..86 -> {
                            val v = pop()
                            val idx = popInt()
                            arrayStore(op, pop(), idx, v)
                        }
                        87 -> pop()
                        88 -> { pop(); pop() }
                        89 -> push(peek())
                        90 -> {
                            val v1 = pop(); val v2 = pop()
                            push(v1); push(v2); push(v1)
                        }
                        91 -> {
                            val v1 = pop(); val v2 = pop(); val v3 = pop()
                            push(v1); push(v3); push(v2); push(v1)
                        }
                        92 -> {
                            val v1 = pop()
                            if (wide(v1)) push(v1) else {
                                val v2 = pop()
                                push(v2); push(v1); push(v2); push(v1)
                            }
                        }
                        93 -> {
                            val v1 = pop()
                            if (wide(v1)) {
                                val v2 = pop()
                                push(v1); push(v2); push(v1)
                            } else {
                                val v2 = pop(); val v3 = pop()
                                push(v2); push(v1); push(v3); push(v2); push(v1)
                            }
                        }
                        94 -> {
                            val v1 = pop()
                            if (wide(v1)) {
                                val v2 = pop()
                                if (wide(v2)) {
                                    push(v1); push(v2); push(v1)
                                } else {
                                    val v3 = pop()
                                    push(v1); push(v3); push(v2); push(v1)
                                }
                            } else {
                                val v2 = pop(); val v3 = pop()
                                if (wide(v3)) {
                                    push(v2); push(v1); push(v3); push(v2); push(v1)
                                } else {
                                    val v4 = pop()
                                    push(v2); push(v1); push(v4); push(v3); push(v2); push(v1)
                                }
                            }
                        }
                        95 -> {
                            val v1 = pop(); val v2 = pop()
                            push(v1); push(v2)
                        }
                        in 96..115, in 120..131 -> {
                            val b = pop()
                            val a = pop()
                            push(arith(op, a, b))
                        }
                        in 116..119 -> push(negate(op, pop()))
                        132 -> {
                            val i = u1(pc)
                            locals[i] = V.toInt(locals[i]) + s1(pc + 1)
                            pc += 2
                        }
                        in 133..147 -> push(convert(op, pop()))
                        148 -> {
                            val b = popLong(); val a = popLong()
                            push(if (a > b) 1 else if (a == b) 0 else -1)
                        }
                        149 -> {
                            val b = popFloat(); val a = popFloat()
                            push(if (a > b) 1 else if (a == b) 0 else -1)
                        }
                        150 -> {
                            val b = popFloat(); val a = popFloat()
                            push(if (a < b) -1 else if (a == b) 0 else 1)
                        }
                        151 -> {
                            val b = popDouble(); val a = popDouble()
                            push(if (a > b) 1 else if (a == b) 0 else -1)
                        }
                        152 -> {
                            val b = popDouble(); val a = popDouble()
                            push(if (a < b) -1 else if (a == b) 0 else 1)
                        }
                        in 153..158 -> {
                            val v = popInt()
                            val take = when (op) {
                                153 -> v == 0; 154 -> v != 0; 155 -> v < 0; 156 -> v >= 0; 157 -> v > 0; else -> v <= 0
                            }
                            val off = s2(pc)
                            pc += 2
                            if (take) pc = start + off
                        }
                        in 159..164 -> {
                            val b = popInt(); val a = popInt()
                            val take = when (op) {
                                159 -> a == b; 160 -> a != b; 161 -> a < b; 162 -> a >= b; 163 -> a > b; else -> a <= b
                            }
                            val off = s2(pc)
                            pc += 2
                            if (take) pc = start + off
                        }
                        165, 166 -> {
                            val b = pop(); val a = pop()
                            val same = a === b
                            val take = if (op == 165) same else !same
                            val off = s2(pc)
                            pc += 2
                            if (take) pc = start + off
                        }
                        167 -> { val off = s2(pc); pc += 2; pc = start + off }
                        168 -> throw MtRuntimeException("不支持 jsr（${bc.owner}.${bc.method}）")
                        169 -> throw MtRuntimeException("不支持 ret（${bc.owner}.${bc.method}）")
                        170 -> {
                            while (pc % 4 != 0) pc++
                            val def = s4(pc)
                            val low = s4(pc + 4)
                            val high = s4(pc + 8)
                            pc += 12
                            val v = popInt()
                            pc = start + if (v in low..high) s4(pc + (v - low) * 4) else def
                        }
                        171 -> {
                            while (pc % 4 != 0) pc++
                            val def = s4(pc)
                            val n = s4(pc + 4)
                            pc += 8
                            val v = popInt()
                            var off = def
                            for (i in 0 until n) {
                                if (s4(pc + i * 8) == v) {
                                    off = s4(pc + i * 8 + 4)
                                    break
                                }
                            }
                            pc += n * 8
                            pc = start + off
                        }
                        172 -> throw Return(V.toInt(pop()))
                        173 -> throw Return(popLong())
                        174 -> throw Return(popFloat())
                        175 -> throw Return(popDouble())
                        176 -> throw Return(pop())
                        177 -> throw Return(null)
                        178 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            push(interp.getStatic(ref.owner, ref.name, ref.desc))
                        }
                        179 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            interp.putStatic(ref.owner, ref.name, ref.desc, pop())
                        }
                        180 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            push(interp.getField(pop(), ref.owner, ref.name, ref.desc))
                        }
                        181 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            val v = pop()
                            interp.putField(pop(), ref.owner, ref.name, ref.desc, v)
                        }
                        182, 185 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            if (op == 185) pc += 2
                            val args2 = popArgs(descParams(ref.desc).size)
                            val target = pop()
                            push(interp.invokeVirtual(target, ref.owner, ref.name, ref.desc, args2))
                        }
                        183 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            val args2 = popArgs(descParams(ref.desc).size)
                            val target = pop()
                            val r = interp.invokeSpecial(target, ref.owner, ref.name, ref.desc, args2)
                            // 构造器不产生返回值，栈上不留东西
                            if (ref.name != "<init>") push(r)
                        }
                        184 -> {
                            val ref = cp.ref(u2(pc)) ?: throw MtRuntimeException("常量 ${u2(pc)} 不是成员引用")
                            pc += 2
                            val args2 = popArgs(descParams(ref.desc).size)
                            push(interp.invokeStatic(ref.owner, ref.name, ref.desc, args2))
                        }
                        186 -> throw MtRuntimeException(
                            "不支持 invokedynamic（${bc.owner}.${bc.method}，多为 lambda，改用匿名类写法）"
                        )
                        187 -> {
                            val idx = u2(pc)
                            pc += 2
                            push(interp.newObject(cp.utf8(idx)))
                        }
                        188 -> {
                            val atype = u1(pc)
                            pc += 1
                            val n = popInt().coerceAtLeast(0)
                            val t = arrayType(atype)
                            push(JArr(MutableList<Any?>(n) { V.defaultOf(t) }))
                        }
                        189 -> {
                            pc += 2
                            val n = popInt().coerceAtLeast(0)
                            push(JArr(MutableList<Any?>(n) { null }))
                        }
                        190 -> {
                            val arr = pop()
                            push(
                                when (arr) {
                                    is JArr -> arr.items.size
                                    is Array<*> -> arr.size
                                    is IntArray -> arr.size
                                    is LongArray -> arr.size
                                    is ByteArray -> arr.size
                                    is CharArray -> arr.size
                                    is ShortArray -> arr.size
                                    is BooleanArray -> arr.size
                                    is FloatArray -> arr.size
                                    is DoubleArray -> arr.size
                                    else -> throw MtRuntimeException("arraylength 用在非数组上：$arr")
                                }
                            )
                        }
                        191 -> throw JvmThrow(pop())
                        192 -> {
                            val idx = u2(pc)
                            pc += 2
                            val v = pop()
                            if (v != null && !interp.isInstance(v, cp.utf8(idx))) {
                                throw MtRuntimeException("无法把 ${v.javaClass.simpleName} 转成 ${descToType(cp.utf8(idx))}")
                            }
                            push(v)
                        }
                        193 -> {
                            val idx = u2(pc)
                            pc += 2
                            push(if (interp.isInstance(pop(), cp.utf8(idx))) 1 else 0)
                        }
                        194, 195 -> pop()
                        196 -> {
                            val sub = u1(pc)
                            val idx = u2(pc + 1)
                            when (sub) {
                                21 -> { locals[idx] = loadTyped(21, locals[idx]); pc += 4 }
                                22 -> { locals[idx] = loadTyped(22, locals[idx]); pc += 4 }
                                23 -> { locals[idx] = loadTyped(23, locals[idx]); pc += 4 }
                                24 -> { locals[idx] = loadTyped(24, locals[idx]); pc += 4 }
                                25 -> { pc += 4 }
                                54 -> { locals[idx] = storeTyped(54, pop()); pc += 4 }
                                55 -> { locals[idx] = storeTyped(55, pop()); pc += 4 }
                                56 -> { locals[idx] = storeTyped(56, pop()); pc += 4 }
                                57 -> { locals[idx] = storeTyped(57, pop()); pc += 4 }
                                58 -> { locals[idx] = pop(); pc += 4 }
                                132 -> { locals[idx] = V.toInt(locals[idx]) + s2(pc + 3); pc += 6 }
                                else -> throw MtRuntimeException("不支持 wide $sub")
                            }
                        }
                        197 -> {
                            val idx = u2(pc)
                            pc += 3
                            val dims = u1(pc - 1)
                            val sizes = popArgs(dims).map { V.toInt(it) }
                            push(interp.multiArray(cp.utf8(idx), sizes))
                        }
                        198 -> {
                            val off = s2(pc)
                            pc += 2
                            if (pop() == null) pc = start + off
                        }
                        199 -> {
                            val off = s2(pc)
                            pc += 2
                            if (pop() != null) pc = start + off
                        }
                        200 -> { val off = s4(pc); pc += 4; pc = start + off }
                        else -> throw MtRuntimeException(
                            "不支持的指令 ${OPS.getOrElse(op) { "0x%02x".format(op) }}（${bc.owner}.${bc.method}，pc=$start）"
                        )
                    }
                } catch (t: JvmThrow) {
                    val h = bc.handlers.firstOrNull {
                        start >= it.start && start < it.end && (it.type == null || interp.isInstance(t.value, it.type))
                    } ?: throw t
                    stack.clear()
                    stack.add(t.value)
                    pc = h.target
                }
            }
        } catch (r: Return) {
            return r.v
        }
    }

    private fun arrayType(atype: Int): String = when (atype) {
        4 -> "boolean"
        5 -> "char"
        6 -> "float"
        7 -> "double"
        8 -> "byte"
        9 -> "short"
        10 -> "int"
        else -> "long"
    }

    private fun loadTyped(kind: Int, v: Any?): Any? = when (kind) {
        22 -> V.toLong(v)
        23 -> V.toDouble(v).toFloat()
        24 -> V.toDouble(v)
        else -> v
    }

    private fun storeTyped(kind: Int, v: Any?): Any? = when (kind) {
        55 -> V.toLong(v)
        56 -> V.toDouble(v).toFloat()
        57 -> V.toDouble(v)
        else -> v
    }

    private fun arrayLoad(op: Int, arr: Any?, idx: Int): Any? {
        val v = when (arr) {
            is JArr -> arr.items.getOrNull(idx)
            is IntArray -> arr.getOrNull(idx)
            is LongArray -> arr.getOrNull(idx)
            is ShortArray -> arr.getOrNull(idx)
            is ByteArray -> arr.getOrNull(idx)
            is CharArray -> arr.getOrNull(idx)
            is BooleanArray -> arr.getOrNull(idx)
            is FloatArray -> arr.getOrNull(idx)
            is DoubleArray -> arr.getOrNull(idx)
            is Array<*> -> arr.getOrNull(idx)
            else -> throw MtRuntimeException("不是数组：$arr")
        }
        return when (op) {
            46, 47, 49, 53 -> V.toInt(v)
            48 -> V.toDouble(v).toFloat()
            51 -> V.toInt(v).toByte()
            52 -> if (v is Char) v else V.toInt(v).toChar()
            else -> v
        }
    }

    private fun arrayStore(op: Int, arr: Any?, idx: Int, v: Any?) {
        val slot: Any? = when (op) {
            79, 84, 85, 86 -> V.toInt(v)
            80 -> V.toLong(v)
            81 -> V.toDouble(v).toFloat()
            82 -> V.toDouble(v)
            else -> v
        }
        when (arr) {
            is JArr -> if (idx in arr.items.indices) arr.items[idx] = slot
            is IntArray -> if (idx in arr.indices) arr[idx] = V.toInt(v)
            is LongArray -> if (idx in arr.indices) arr[idx] = V.toLong(v)
            is ShortArray -> if (idx in arr.indices) arr[idx] = V.toInt(v).toShort()
            is ByteArray -> if (idx in arr.indices) arr[idx] = V.toInt(v).toByte()
            is CharArray -> if (idx in arr.indices) arr[idx] = (slot as? Char ?: ' ')
            is BooleanArray -> if (idx in arr.indices) arr[idx] = V.truth(v)
            is FloatArray -> if (idx in arr.indices) arr[idx] = V.toDouble(v).toFloat()
            is DoubleArray -> if (idx in arr.indices) arr[idx] = V.toDouble(v)
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                (arr as Array<Any?>)[idx] = slot
            }
            else -> throw MtRuntimeException("不是数组：$arr")
        }
    }

    private fun arith(op: Int, a: Any?, b: Any?): Any? = when (op) {
        96 -> V.toInt(a) + V.toInt(b)
        97 -> V.toLong(a) + V.toLong(b)
        98 -> V.toDouble(a).toFloat() + V.toDouble(b).toFloat()
        99 -> V.toDouble(a) + V.toDouble(b)
        100 -> V.toInt(a) - V.toInt(b)
        101 -> V.toLong(a) - V.toLong(b)
        102 -> V.toDouble(a).toFloat() - V.toDouble(b).toFloat()
        103 -> V.toDouble(a) - V.toDouble(b)
        104 -> V.toInt(a) * V.toInt(b)
        105 -> V.toLong(a) * V.toLong(b)
        106 -> V.toDouble(a).toFloat() * V.toDouble(b).toFloat()
        107 -> V.toDouble(a) * V.toDouble(b)
        108 -> {
            val d = V.toInt(b)
            if (d == 0) throw ArithmeticException("/ by zero")
            V.toInt(a) / d
        }
        109 -> {
            val d = V.toLong(b)
            if (d == 0L) throw ArithmeticException("/ by zero")
            V.toLong(a) / d
        }
        110 -> V.toDouble(a).toFloat() / V.toDouble(b).toFloat()
        111 -> V.toDouble(a) / V.toDouble(b)
        112 -> {
            val d = V.toInt(b)
            if (d == 0) throw ArithmeticException("/ by zero")
            V.toInt(a) % d
        }
        113 -> {
            val d = V.toLong(b)
            if (d == 0L) throw ArithmeticException("/ by zero")
            V.toLong(a) % d
        }
        114 -> V.toDouble(a).toFloat() % V.toDouble(b).toFloat()
        115 -> V.toDouble(a) % V.toDouble(b)
        120 -> V.toInt(a) shl (V.toInt(b) and 31)
        121 -> V.toLong(a) shl (V.toInt(b) and 63)
        122 -> V.toInt(a) shr (V.toInt(b) and 31)
        123 -> V.toLong(a) shr (V.toInt(b) and 63)
        124 -> V.toInt(a) ushr (V.toInt(b) and 31)
        125 -> V.toLong(a) ushr (V.toInt(b) and 63)
        126 -> V.toInt(a) and V.toInt(b)
        127 -> V.toLong(a) and V.toLong(b)
        128 -> V.toInt(a) or V.toInt(b)
        129 -> V.toLong(a) or V.toLong(b)
        130 -> V.toInt(a) xor V.toInt(b)
        131 -> V.toLong(a) xor V.toLong(b)
        else -> throw MtRuntimeException("算术指令 $op 未实现")
    }

    private fun negate(op: Int, v: Any?): Any? = when (op) {
        116 -> -V.toInt(v)
        117 -> -V.toLong(v)
        118 -> -V.toDouble(v).toFloat()
        else -> -V.toDouble(v)
    }

    private fun convert(op: Int, v: Any?): Any? = when (op) {
        133 -> V.toLong(v)
        134 -> V.toDouble(v).toFloat()
        135 -> V.toDouble(v)
        136 -> V.toInt(v)
        137 -> V.toDouble(v).toFloat()
        138 -> V.toDouble(v)
        139 -> V.toDouble(v).toInt()
        140 -> V.toDouble(v).toLong()
        141 -> V.toDouble(v)
        142 -> V.toDouble(v).toInt()
        143 -> V.toDouble(v).toLong()
        144 -> V.toDouble(v).toFloat()
        145 -> V.toInt(v).toByte()
        146 -> V.toInt(v).toChar()
        147 -> V.toInt(v).toShort()
        else -> v
    }
}
