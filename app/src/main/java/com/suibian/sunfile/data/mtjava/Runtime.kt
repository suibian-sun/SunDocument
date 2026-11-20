package com.suibian.sunfile.data.mtjava

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 运行时：解释执行 AST。
 *
 * 值模型直接用宿主 JVM 的对象——字符串就是 String，列表就是 ArrayList，
 * 插件 new 出来的对象用 [JObj] 表示。凡是解释器不认识的类（JDK、Android、
 * MT 的 API 实现类），一律走反射调用，这样不用为每个类手写桥接。
 */

class MtRuntimeException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class ClassRef(val cls: JClass?, val name: String?)

internal class JObj(val cls: JClass) {
    val fields = HashMap<String, Any?>()
    /** 宿主对象（File、SharedPreferences、API 实现等） */
    var native: Any? = null

    override fun toString(): String = native?.toString() ?: cls.name
}

internal class JMethod(
    val name: String,
    val params: List<Param>,
    val body: Block?,
    val isStatic: Boolean = false,
    val native: ((Interp, JObj?, List<Any?>) -> Any?)? = null
)

internal class JClass(val name: String) {
    var superClass: JClass? = null
    var superName: String? = null
    val methods = HashMap<String, ArrayList<JMethod>>()
    val staticFields = HashMap<String, Any?>()
    val ctors = ArrayList<JMethod>()
    /** 实例字段的初始值表达式，构造时按父类到子类依次求值 */
    val fieldInits = ArrayList<Pair<String, Expr>>()
    /** 构造完成后挂宿主对象 */
    var hostFactory: ((JObj) -> Unit)? = null
    var initialized = false

    fun add(m: JMethod) {
        methods.getOrPut(m.name) { ArrayList() }.add(m)
    }

    fun find(name: String, argc: Int): JMethod? {
        var c: JClass? = this
        while (c != null) {
            val list = c.methods[name]
            if (list != null) {
                val hit = list.firstOrNull { it.params.size == argc } ?: list.lastOrNull()
                if (hit != null) return hit
            }
            c = c.superClass
        }
        return null
    }
}

internal class Frame(val self: JObj?, val cls: JClass) {
    val vars = HashMap<String, Any?>()
}

private class ReturnSignal(val v: Any?) : RuntimeException(null, null, false, false)
private object BreakSignal : RuntimeException(null, null, false, false)
private object ContinueSignal : RuntimeException(null, null, false, false)

internal class Interp {

    val classes = LinkedHashMap<String, JClass>()
    var log: (String) -> Unit = {}
    /** 宿主上下文等，交给 MtApi 注入 */
    var host: Any? = null
    /** 宿主类工厂：new 一个不在源码里的类时用 */
    val factories = HashMap<String, (List<Any?>) -> Any?>()

    fun load(decls: List<ClassDecl>) {
        for (d in decls) classes[d.name] = JClass(d.name)
        for (d in decls) {
            val c = classes[d.name] ?: continue
            val parent = d.ext ?: d.impls.firstOrNull()
            if (parent != null) {
                c.superName = parent
                val simple = parent.substringAfterLast('.')
                c.superClass = classes[parent] ?: classes[simple]
            }
            for (f in d.fields) {
                if (f.isStatic) {
                    c.staticFields[f.name] = if (f.init != null) eval(f.init, Frame(null, c)) else V.defaultOf(f.type)
                } else {
                    c.fieldInits.add(f.name to (f.init ?: Lit(V.defaultOf(f.type))))
                }
            }
            for (m in d.methods) {
                if (m.name == "<init>") {
                    c.ctors.add(JMethod("<init>", m.params, m.body, false))
                } else {
                    c.add(JMethod(m.name, m.params, m.body, m.isStatic))
                }
            }
        }
    }

    fun classOf(name: String): JClass? = classes[name] ?: classes[name.substringAfterLast('.')]

    /** 重新挂父类：宿主 API 可能在源码之后才注册 */
    fun relink() {
        for (c in classes.values) {
            val n = c.superName ?: continue
            if (c.superClass == null) c.superClass = classes[n] ?: classes[n.substringAfterLast('.')]
        }
    }

    fun instantiate(name: String, args: List<Any?> = emptyList()): JObj {
        val cls = classOf(name.substringAfterLast('.')) ?: classOf(name)
            ?: throw MtRuntimeException("找不到类 $name")
        return construct(cls, args)
    }

    fun construct(cls: JClass, args: List<Any?>): JObj {
        val obj = JObj(cls)
        val chain = ArrayList<JClass>()
        var k: JClass? = cls
        while (k != null) { chain.add(k); k = k.superClass }
        chain.reverse()
        for (c in chain) {
            for ((n, e) in c.fieldInits) obj.fields[n] = eval(e, Frame(obj, c))
        }
        val ctor = cls.ctors.firstOrNull { it.params.size == args.size }
            ?: cls.ctors.firstOrNull()
        if (ctor != null) {
            execMethod(ctor, obj, cls, args)
        } else {
            // 没有显式构造器时，沿父类找无参构造
            var c: JClass? = cls.superClass
            while (c != null) {
                val p = c.ctors.firstOrNull { it.params.size == args.size }
                if (p != null) { execMethod(p, obj, c, args); break }
                c = c.superClass
            }
        }
        var c: JClass? = cls
        while (c != null) {
            c.hostFactory?.invoke(obj)
            c = c.superClass
        }
        return obj
    }

    fun callMethod(target: JObj, name: String, args: List<Any?>): Any? {
        val m = target.cls.find(name, args.size)
        if (m != null) return execMethod(m, target, target.cls, args)
        val nat = target.native
        if (nat != null) return Native.call(nat, name, args)
        throw MtRuntimeException("${target.cls.name} 没有方法 $name/${args.size}")
    }

    fun callStatic(cls: JClass, name: String, args: List<Any?>): Any? {
        var c: JClass? = cls
        while (c != null) {
            val m = c.methods[name]?.firstOrNull { it.params.size == args.size }
            if (m != null) return execMethod(m, null, c, args)
            c = c.superClass
        }
        throw MtRuntimeException("${cls.name} 没有静态方法 $name")
    }

    private fun execMethod(
        m: JMethod,
        self: JObj?,
        owner: JClass,
        args: List<Any?>
    ): Any? {
        m.native?.let { return it(this, self, args) }
        val body = m.body ?: return null
        val f = Frame(self, owner)
        for (i in m.params.indices) {
            f.vars[m.params[i].name] = V.coerce(m.params[i].type, args.getOrNull(i))
        }
        return try {
            exec(body, f)
            null
        } catch (r: ReturnSignal) {
            r.v
        }
    }

    // ---------------------------------------------------------------- 语句

    private fun exec(s: Stmt, f: Frame) {
        when (s) {
            is Block -> for (x in s.stmts) exec(x, f)
            is EmptyStmt -> {}
            is VarStmt -> f.vars[s.name] = if (s.init != null) eval(s.init, f) else V.defaultOf(s.type)
            is ExprStmt -> eval(s.e, f)
            is IfStmt -> if (V.truth(eval(s.c, f))) exec(s.t, f) else s.f?.let { exec(it, f) }
            is WhileStmt -> {
                if (s.doWhile) {
                    do {
                        try { exec(s.body, f) } catch (c: ContinueSignal) { /* 继续 */ }
                        if (!V.truth(eval(s.c, f))) break
                    } while (true)
                } else {
                    while (V.truth(eval(s.c, f))) {
                        try { exec(s.body, f) } catch (c: ContinueSignal) { continue }
                    }
                }
            }
            is ForStmt -> {
                for (x in s.init) exec(x, f)
                while (s.cond == null || V.truth(eval(s.cond, f))) {
                    try { exec(s.body, f) } catch (c: ContinueSignal) { /* next */ }
                    for (u in s.update) eval(u, f)
                }
            }
            is ForEachStmt -> {
                val it = eval(s.iter, f)
                val list = V.iterable(it)
                for (v in list) {
                    f.vars[s.name] = V.coerce(s.type ?: "", v)
                    try { exec(s.body, f) } catch (c: ContinueSignal) { continue }
                }
            }
            is ReturnStmt -> throw ReturnSignal(if (s.e == null) null else eval(s.e, f))
            is BreakStmt -> throw BreakSignal
            is ContinueStmt -> throw ContinueSignal
            is SwitchStmt -> {
                val sel = eval(s.sel, f)
                var start = -1
                for (i in s.cases.indices) {
                    val key = s.cases[i].first
                    if (key == null || V.eq(sel, eval(key, f))) { start = i; break }
                }
                if (start >= 0) {
                    try {
                        for (i in start until s.cases.size) {
                            for (x in s.cases[i].second) exec(x, f)
                        }
                    } catch (b: BreakSignal) { /* 跳出 */ }
                }
            }
            is TryStmt -> {
                var handled = false
                try {
                    exec(s.body, f)
                } catch (r: ReturnSignal) {
                    throw r
                } catch (e: Throwable) {
                    if (e is BreakSignal || e is ContinueSignal) throw e
                    for ((type, name, block) in s.catches) {
                        f.vars[name] = e
                        exec(block, f)
                        handled = true
                        break
                    }
                    if (!handled) throw e
                } finally {
                    s.fin?.let { exec(it, f) }
                }
            }
            is ThrowStmt -> {
                val v = eval(s.e, f)
                throw MtRuntimeException(V.str(v))
            }
            is SyncStmt -> exec(s.body, f)
        }
    }

    // ---------------------------------------------------------------- 表达式

    fun eval(e: Expr, f: Frame): Any? = when (e) {
        is Lit -> e.v
        is ThisExpr -> f.self
        is SuperExpr -> f.self
        is Name -> readName(e.id, f)
        is Field -> readField(eval(e.target, f), e.name)
        is Index -> readIndex(eval(e.target, f), eval(e.idx, f))
        is Cast -> V.cast(e.type, eval(e.e, f))
        is Cond -> if (V.truth(eval(e.c, f))) eval(e.a, f) else eval(e.b, f)
        is InstanceOf -> V.instanceOf(eval(e.e, f), e.type)
        is Un -> unary(e, f)
        is Bin -> binary(e, f)
        is Assign -> assign(e, f)
        is New -> newObject(e, f)
        is Call -> call(e, f)
    }

    private fun readName(id: String, f: Frame): Any? {
        if (f.vars.containsKey(id)) return f.vars[id]
        var c: JClass? = f.cls
        while (c != null) {
            if (c.staticFields.containsKey(id)) return c.staticFields[id]
            c = c.superClass
        }
        val self = f.self
        if (self != null) {
            var k: JClass? = self.cls
            while (k != null) {
                if (self.fields.containsKey(id)) return self.fields[id]
                k = k.superClass
            }
            // 宿主对象的字段 / getter
            val nat = self.native
            if (nat != null) {
                val r = runCatching { Native.get(nat, id) }.getOrNull()
                if (r != null) return r
            }
        }
        classes[id]?.let { return ClassRef(it, null) }
        if (Native.knownClass(id)) return ClassRef(null, id)
        throw MtRuntimeException("未知变量 $id（第 ? 行）")
    }

    private fun readField(target: Any?, name: String): Any? = when (target) {
        is Array<*> -> if (name == "length") target.size else null
        is CharArray -> if (name == "length") target.size else null
        is IntArray -> if (name == "length") target.size else null
        is LongArray -> if (name == "length") target.size else null
        is ByteArray -> if (name == "length") target.size else null
        is DoubleArray -> if (name == "length") target.size else null
        is FloatArray -> if (name == "length") target.size else null
        is BooleanArray -> if (name == "length") target.size else null
        is ClassRef -> {
            val c = target.cls
            if (c != null && c.staticFields.containsKey(name)) c.staticFields[name]
            else Native.staticField(target.name ?: c?.name ?: "", name)
        }
        is JObj -> target.fields[name] ?: target.native?.let { Native.get(it, name) }
        is List<*> -> if (name == "size") target.size else Native.get(target, name)
        else -> if (target == null) throw MtRuntimeException("空引用读取字段 $name") else Native.get(target, name)
    }

    private fun readIndex(target: Any?, idx: Any?): Any? = when (target) {
        is JArr -> target.items.getOrNull(V.toInt(idx))
        is Array<*> -> target.getOrNull(V.toInt(idx))
        is CharArray -> target.getOrNull(V.toInt(idx))
        is IntArray -> target.getOrNull(V.toInt(idx))
        is LongArray -> target.getOrNull(V.toInt(idx))
        is ByteArray -> target.getOrNull(V.toInt(idx))
        is BooleanArray -> target.getOrNull(V.toInt(idx))
        is DoubleArray -> target.getOrNull(V.toInt(idx))
        is FloatArray -> target.getOrNull(V.toInt(idx))
        is List<*> -> target.getOrNull(V.toInt(idx))
        is Map<*, *> -> target[idx]
        is String -> target.getOrNull(V.toInt(idx))?.toString()
        else -> null
    }

    private fun unary(e: Un, f: Frame): Any? {
        if (e.op == "++" || e.op == "--") {
            val old = eval(e.e, f)
            val nv = V.arith(if (e.op == "++") "+" else "-", old, 1)
            writeBack(e.e, nv, f)
            return if (e.postfix) old else nv
        }
        val v = eval(e.e, f)
        return when (e.op) {
            "!" -> !V.truth(v)
            "-" -> when (v) {
                is Int -> -v
                is Long -> -v
                is Float -> -v
                is Double -> -v
                else -> -V.toDouble(v)
            }
            "+" -> v
            "~" -> V.toInt(v).inv()
            else -> v
        }
    }

    private fun binary(e: Bin, f: Frame): Any? {
        if (e.op == "&&") {
            val l = eval(e.l, f)
            return V.truth(l) && V.truth(eval(e.r, f))
        }
        if (e.op == "||") {
            val l = eval(e.l, f)
            return V.truth(l) || V.truth(eval(e.r, f))
        }
        val l = eval(e.l, f)
        val r = eval(e.r, f)
        return when (e.op) {
            "+" -> if (l is String || r is String) V.str(l) + V.str(r) else V.arith("+", l, r)
            "-", "*", "/", "%" -> V.arith(e.op, l, r)
            "==" -> V.eq(l, r)
            "!=" -> !V.eq(l, r)
            "<" -> V.cmp(l, r) < 0
            ">" -> V.cmp(l, r) > 0
            "<=" -> V.cmp(l, r) <= 0
            ">=" -> V.cmp(l, r) >= 0
            "&" -> if (l is Boolean && r is Boolean) l and r else V.toInt(l) and V.toInt(r)
            "|" -> if (l is Boolean && r is Boolean) l or r else V.toInt(l) or V.toInt(r)
            "^" -> if (l is Boolean && r is Boolean) l xor r else V.toInt(l) xor V.toInt(r)
            "<<" -> V.toInt(l) shl V.toInt(r)
            ">>" -> V.toInt(l) shr V.toInt(r)
            ">>>" -> V.toInt(l) ushr V.toInt(r)
            "instanceof" -> V.instanceOf(l, (e.r as? Name)?.id ?: "")
            else -> throw MtRuntimeException("不支持的运算符 ${e.op}")
        }
    }

    private fun assign(e: Assign, f: Frame): Any? {
        val nv = if (e.op == "=") eval(e.value, f) else {
            val old = eval(e.target, f)
            V.arith(e.op.dropLast(1), old, eval(e.value, f))
        }
        writeBack(e.target, nv, f)
        return nv
    }

    private fun writeBack(target: Expr, v: Any?, f: Frame) {
        when (target) {
            is Name -> {
                if (f.vars.containsKey(target.id)) {
                    f.vars[target.id] = v
                    return
                }
                var c: JClass? = f.cls
                while (c != null) {
                    if (c.staticFields.containsKey(target.id)) { c.staticFields[target.id] = v; return }
                    c = c.superClass
                }
                val self = f.self
                if (self != null) {
                    var k: JClass? = self.cls
                    while (k != null) {
                        if (self.fields.containsKey(target.id)) { self.fields[target.id] = v; return }
                        k = k.superClass
                    }
                }
                f.vars[target.id] = v
            }
            is Field -> {
                val t = eval(target.target, f)
                if (t is JObj) t.fields[target.name] = v
                else if (t is ClassRef) t.cls?.staticFields?.put(target.name, v)
                else if (t != null) Native.set(t, target.name, v)
            }
            is Index -> {
                val t = eval(target.target, f)
                @Suppress("UNCHECKED_CAST")
                when (t) {
                    is JArr -> t.items[V.toInt(eval(target.idx, f))] = v
                    is MutableList<*> -> (t as MutableList<Any?>)[V.toInt(eval(target.idx, f))] = v
                    is MutableMap<*, *> -> (t as MutableMap<Any?, Any?>)[eval(target.idx, f)] = v
                    else -> {}
                }
            }
            else -> {}
        }
    }

    private val STATIC_IMPORTS = listOf(
        "java.lang.Math", "java.lang.String", "java.lang.Integer", "java.lang.Long",
        "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character",
        "java.util.Arrays", "java.util.Collections", "java.lang.System"
    )

    private fun staticImport(name: String, args: List<Any?>): Any? {
        for (c in STATIC_IMPORTS) {
            val r = runCatching { Native.staticCall(c, name, args) }
            if (r.isSuccess) return r.getOrNull()
        }
        throw MtRuntimeException("找不到方法 $name/${args.size}")
    }

    private fun newObject(e: New, f: Frame): Any? {
        val type = e.type
        if (e.init != null) {
            val list = e.init.map { eval(it, f) }
            return JArr(ArrayList(list))
        }
        if (e.dims.isNotEmpty()) {
            val n = V.toInt(eval(e.dims[0], f)).coerceAtLeast(0)
            return JArr(MutableList<Any?>(n) { V.defaultOf(type) })
        }
        val args = e.args.map { eval(it, f) }
        val simple = type.substringAfterLast('.')
        classOf(simple)?.let { return construct(it, args) }
        factories[simple]?.let { return it(args) }
        factories[type]?.let { return it(args) }
        if (simple == "ArrayList" || simple == "LinkedList" || simple == "List") return ArrayList<Any?>(args)
        if (simple == "HashMap" || simple == "LinkedHashMap" || simple == "Map") return LinkedHashMap<Any?, Any?>()
        if (simple == "HashSet" || simple == "LinkedHashSet" || simple == "Set") return LinkedHashSet<Any?>()
        if (simple == "StringBuilder" || simple == "StringBuffer") {
            return if (args.isEmpty()) StringBuilder() else StringBuilder(V.str(args[0]))
        }
        return Native.construct(type, args)
    }

    private fun call(e: Call, f: Frame): Any? {
        val args = e.args.map { eval(it, f) }
        val target = e.target
        if (target == null) {
            // 本类实例方法 / 静态方法 / 静态导入
            f.self?.let { self ->
                val m = self.cls.find(e.name, args.size)
                if (m != null) return execMethod(m, self, self.cls, args)
            }
            var c: JClass? = f.cls
            while (c != null) {
                val m = c.methods[e.name]?.firstOrNull { it.params.size == args.size }
                if (m != null) return execMethod(m, null, c, args)
                c = c.superClass
            }
            val nat = f.self?.native
            if (nat != null) {
                val r = runCatching { Native.call(nat, e.name, args) }
                if (r.isSuccess) return r.getOrNull()
            }
            return staticImport(e.name, args)
        }
        if (target is SuperExpr) {
            val sup = f.cls.superClass
            if (sup != null && e.name == "<init>") {
                val ctor = sup.ctors.firstOrNull { it.params.size == args.size }
                if (ctor != null) return execMethod(ctor, f.self, sup, args)
                return null
            }
            val self = f.self ?: throw MtRuntimeException("super 只能在实例里用")
            return callMethod(self, e.name, args)
        }
        val t = eval(target, f)
        return when (t) {
            is ClassRef -> {
                val c = t.cls
                if (c != null && c.methods.containsKey(e.name)) callStatic(c, e.name, args)
                else Native.staticCall(t.name ?: c?.name ?: "", e.name, args)
            }
            is JObj -> callMethod(t, e.name, args)
            null -> throw MtRuntimeException("空引用调用 ${e.name}")
            else -> Native.call(t, e.name, args)
        }
    }
}

internal class JArr(val items: MutableList<Any?>) {
    override fun toString(): String = items.joinToString(", ", "[", "]") { V.str(it) }
}

internal object V {

    fun str(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is Char -> v.toString()
        else -> v.toString()
    }

    fun truth(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Int -> v != 0
        is Long -> v != 0L
        is Double -> v != 0.0
        is Float -> v != 0f
        is Char -> v.code != 0
        is Number -> v.toDouble() != 0.0
        else -> true
    }

    fun toInt(v: Any?): Int = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Float -> v.toInt()
        is Char -> v.code
        is Boolean -> if (v) 1 else 0
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull() ?: 0
        else -> 0
    }

    fun toLong(v: Any?): Long = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> v.toLong()
        is Float -> v.toLong()
        is Char -> v.code.toLong()
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: 0L
        else -> 0L
    }

    fun toDouble(v: Any?): Double = when (v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is Float -> v.toDouble()
        is Char -> v.code.toDouble()
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    fun defaultOf(type: String?): Any? = when (type) {
        "int" -> 0
        "long" -> 0L
        "short" -> 0
        "byte" -> 0
        "char" -> ' '
        "boolean" -> false
        "float" -> 0f
        "double" -> 0.0
        else -> null
    }

    fun coerce(type: String?, v: Any?): Any? {
        if (type == null) return v
        return when (type) {
            "int", "Integer" -> toInt(v)
            "long", "Long" -> toLong(v)
            "short", "Short" -> toInt(v).toShort()
            "byte", "Byte" -> toInt(v).toByte()
            "char", "Character" -> when (v) {
                is Char -> v
                is Number -> v.toInt().toChar()
                else -> ' '
            }
            "boolean", "Boolean" -> truth(v)
            "float", "Float" -> toDouble(v).toFloat()
            "double", "Double" -> toDouble(v)
            "String", "java.lang.String" -> if (v == null) null else str(v)
            else -> v
        }
    }

    fun arith(op: String, a: Any?, b: Any?): Any? {
        if (a is String || b is String) return if (op == "+") str(a) + str(b) else 0
        if (a is Double || b is Double || a is Float || b is Float) {
            val x = toDouble(a)
            val y = toDouble(b)
            return when (op) {
                "+" -> x + y
                "-" -> x - y
                "*" -> x * y
                "/" -> x / y
                "%" -> x % y
                else -> x
            }
        }
        if (a is Long || b is Long) {
            val x = toLong(a)
            val y = toLong(b)
            return when (op) {
                "+" -> x + y
                "-" -> x - y
                "*" -> x * y
                "/" -> if (y == 0L) 0L else x / y
                "%" -> if (y == 0L) 0L else x % y
                else -> x
            }
        }
        val x = toInt(a)
        val y = toInt(b)
        return when (op) {
            "+" -> x + y
            "-" -> x - y
            "*" -> x * y
            "/" -> if (y == 0) 0 else x / y
            "%" -> if (y == 0) 0 else x % y
            else -> x
        }
    }

    fun eq(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a is Number && b is Number) return toDouble(a) == toDouble(b)
        if (a is Char && b is Number) return a.code == toInt(b)
        if (b is Char && a is Number) return b.code == toInt(a)
        return a == b
    }

    fun cmp(a: Any?, b: Any?): Int {
        if (a is String || b is String) return str(a).compareTo(str(b))
        val x = toDouble(a)
        val y = toDouble(b)
        return x.compareTo(y)
    }

    fun iterable(v: Any?): List<Any?> = when (v) {
        null -> emptyList()
        is JArr -> v.items
        is List<*> -> v
        is Map<*, *> -> v.values.toList()
        is String -> v.map { it }
        is Array<*> -> v.toList()
        is CharArray -> v.toList()
        is IntArray -> v.toList()
        is LongArray -> v.toList()
        is ByteArray -> v.toList()
        is DoubleArray -> v.toList()
        is FloatArray -> v.toList()
        is BooleanArray -> v.toList()
        else -> emptyList()
    }

    fun cast(type: String, v: Any?): Any? = when (type) {
        "int" -> toInt(v)
        "long" -> toLong(v)
        "double" -> toDouble(v)
        "float" -> toDouble(v).toFloat()
        "short" -> toInt(v).toShort()
        "byte" -> toInt(v).toByte()
        "char" -> when (v) {
            is Char -> v
            is Number -> v.toInt().toChar()
            else -> ' '
        }
        "boolean" -> truth(v)
        "String" -> if (v == null) null else str(v)
        else -> v
    }

    fun instanceOf(v: Any?, type: String): Boolean {
        if (v == null) return false
        if (type in setOf("int", "long", "double", "float", "boolean", "char", "short", "byte")) {
            return v is Number || v is Char || v is Boolean
        }
        return when (v) {
            is JObj -> {
                var c: JClass? = v.cls
                var hit = false
                while (c != null) {
                    if (c.name == type || c.name.substringAfterLast('.') == type) { hit = true; break }
                    c = c.superClass
                }
                hit || v.native?.javaClass?.let { Native.matches(it, type) } == true
            }
            is String -> type == "String" || type == "Object" || type == "CharSequence"
            is List<*> -> type == "List" || type == "Collection" || type == "Object"
            is Map<*, *> -> type == "Map" || type == "Object"
            else -> Native.matches(v.javaClass, type)
        }
    }
}

internal object Native {

    private val SHORT: Map<String, String> = mapOf(
        "String" to "java.lang.String",
        "StringBuilder" to "java.lang.StringBuilder",
        "StringBuffer" to "java.lang.StringBuffer",
        "Object" to "java.lang.Object",
        "Math" to "java.lang.Math",
        "System" to "java.lang.System",
        "Integer" to "java.lang.Integer",
        "Long" to "java.lang.Long",
        "Double" to "java.lang.Double",
        "Float" to "java.lang.Float",
        "Boolean" to "java.lang.Boolean",
        "Character" to "java.lang.Character",
        "Byte" to "java.lang.Byte",
        "Short" to "java.lang.Short",
        "Number" to "java.lang.Number",
        "Thread" to "java.lang.Thread",
        "Throwable" to "java.lang.Throwable",
        "Exception" to "java.lang.Exception",
        "RuntimeException" to "java.lang.RuntimeException",
        "IllegalArgumentException" to "java.lang.IllegalArgumentException",
        "IllegalStateException" to "java.lang.IllegalStateException",
        "NullPointerException" to "java.lang.NullPointerException",
        "UnsupportedOperationException" to "java.lang.UnsupportedOperationException",
        "IOException" to "java.io.IOException",
        "File" to "java.io.File",
        "Arrays" to "java.util.Arrays",
        "Collections" to "java.util.Collections",
        "ArrayList" to "java.util.ArrayList",
        "HashMap" to "java.util.HashMap",
        "LinkedHashMap" to "java.util.LinkedHashMap",
        "HashSet" to "java.util.HashSet",
        "LinkedList" to "java.util.LinkedList",
        "Pattern" to "java.util.regex.Pattern",
        "Matcher" to "java.util.regex.Matcher",
        "StandardCharsets" to "java.nio.charset.StandardCharsets",
        "Charset" to "java.nio.charset.Charset",
        "JSONObject" to "org.json.JSONObject",
        "JSONArray" to "org.json.JSONArray",
        "JSONTokener" to "org.json.JSONTokener",
        "Base64" to "android.util.Base64",
        "TextUtils" to "android.text.TextUtils",
        "Uri" to "android.net.Uri",
        "Environment" to "android.os.Environment",
        "Log" to "android.util.Log",
        "Character2" to "java.lang.Character"
    )

    fun knownClass(name: String): Boolean =
        SHORT.containsKey(name) || name.contains('.') && runCatching { Class.forName(name) }.isSuccess

    private fun resolve(name: String): Class<*>? {
        SHORT[name]?.let { n ->
            return runCatching { Class.forName(n) }.getOrNull()
        }
        return runCatching { Class.forName(name) }.getOrNull()
    }

    fun matches(cls: Class<*>, type: String): Boolean {
        val target = resolve(type) ?: return false
        return target.isAssignableFrom(cls)
    }

    private fun convert(v: Any?, type: Class<*>): Any? {
        if (type == Void.TYPE) return null
        if (v == null) return if (type.isPrimitive) V.defaultOf(type.name) else null
        if (type.isArray) {
            val list = when (v) {
                is JArr -> v.items
                is List<*> -> v
                else -> listOf(v)
            }
            val comp = type.componentType
            val arr = java.lang.reflect.Array.newInstance(comp, list.size)
            for (i in list.indices) java.lang.reflect.Array.set(arr, i, convert(list[i], comp))
            return arr
        }
        if (v is JObj) {
            val nat = v.native
            if (nat != null && type.isInstance(nat)) return nat
            return null
        }
        if (type.isInstance(v)) return v
        return when (type.name) {
            "int" -> V.toInt(v)
            "long" -> V.toLong(v)
            "short" -> V.toInt(v).toShort()
            "byte" -> V.toInt(v).toByte()
            "char" -> when (v) {
                is Char -> v
                is Number -> v.toInt().toChar()
                else -> ' '
            }
            "boolean" -> V.truth(v)
            "float" -> V.toDouble(v).toFloat()
            "double" -> V.toDouble(v)
            "java.lang.String" -> V.str(v)
            "java.lang.CharSequence" -> V.str(v)
            "java.lang.Integer" -> V.toInt(v)
            "java.lang.Long" -> V.toLong(v)
            "java.lang.Double" -> V.toDouble(v)
            "java.lang.Float" -> V.toDouble(v).toFloat()
            "java.lang.Boolean" -> V.truth(v)
            "java.lang.Object" -> if (v is JObj) v.native else v
            else -> null
        }
    }

    private fun rank(v: Any?, type: Class<*>): Int {
        if (v == null) return if (type.isPrimitive) -1 else 1
        val n = type.name
        return when {
            v is Int && (n == "int" || n == "java.lang.Integer") -> 6
            v is Long && (n == "long" || n == "java.lang.Long") -> 6
            v is Double && (n == "double" || n == "java.lang.Double") -> 6
            v is Float && (n == "float" || n == "java.lang.Float") -> 6
            v is Boolean && (n == "boolean" || n == "java.lang.Boolean") -> 6
            v is Char && (n == "char" || n == "java.lang.Character") -> 6
            v is String && (n == "java.lang.String" || n == "java.lang.CharSequence") -> 6
            type.isInstance(v) -> 5
            v is Int && n == "long" -> 3
            v is Int && (n == "double" || n == "java.lang.Double") -> 2
            v is Int && (n == "float" || n == "java.lang.Float") -> 2
            v is Long && (n == "double" || n == "java.lang.Double") -> 2
            v is Number && (n == "java.lang.Number" || n == "java.lang.Object") -> 2
            n == "java.lang.Object" -> 1
            type.isInterface -> 1
            !type.isPrimitive && convert(v, type) != null -> 1
            else -> -1
        }
    }

    private fun pick(methods: List<Method>, args: List<Any?>): Pair<Method, Array<Any?>>? {
        var best: Pair<Method, Array<Any?>>? = null
        var bestScore = -1
        for (m in methods) {
            var score = 0
            val out: Array<Any?>
            if (m.isVarArgs) {
                val fixed = m.parameterCount - 1
                if (args.size < fixed) continue
                out = arrayOfNulls(m.parameterCount)
                var ok = true
                for (i in 0 until fixed) {
                    val t = m.parameterTypes[i]
                    val r = rank(args[i], t)
                    if (r < 0) { ok = false; break }
                    score += r
                    out[i] = convert(args[i], t)
                }
                if (!ok) continue
                val rest = args.drop(fixed)
                val comp = m.parameterTypes.last().componentType
                val arr = java.lang.reflect.Array.newInstance(comp, rest.size)
                for (i in rest.indices) {
                    if (rank(rest[i], comp) < 0) { ok = false; break }
                    java.lang.reflect.Array.set(arr, i, convert(rest[i], comp))
                }
                if (!ok) continue
                out[fixed] = arr
                score += 1
            } else {
                if (m.parameterCount != args.size) continue
                out = arrayOfNulls(args.size)
                var ok = true
                for (i in args.indices) {
                    val t = m.parameterTypes[i]
                    val r = rank(args[i], t)
                    if (r < 0) { ok = false; break }
                    score += r
                    out[i] = convert(args[i], t)
                }
                if (!ok) continue
            }
            if (score > bestScore) {
                bestScore = score
                best = m to out
            }
        }
        return best
    }

    fun call(target: Any, name: String, args: List<Any?>): Any? {
        val list = target.javaClass.methods.filter { it.name == name && !Modifier.isStatic(it.modifiers) }
        val hit = pick(list, args)
            ?: target.javaClass.methods.filter { it.name == name }.let { pick(it, args) }
            ?: throw MtRuntimeException("${target.javaClass.simpleName} 不支持 $name/${args.size}")
        return invoke(hit.first, target, hit.second)
    }

    fun staticCall(clsName: String, name: String, args: List<Any?>): Any? {
        val cls = resolve(clsName)
            ?: throw MtRuntimeException("找不到类 $clsName（不支持 $name）")
        val list = cls.methods.filter { it.name == name }
        val hit = pick(list, args) ?: throw MtRuntimeException("$clsName 不支持 $name/${args.size}")
        return invoke(hit.first, null, hit.second)
    }

    fun construct(type: String, args: List<Any?>): Any? {
        val cls = resolve(type.substringAfterLast('.')) ?: resolve(type)
            ?: throw MtRuntimeException("找不到类 $type")
        for (c in cls.constructors.sortedByDescending { it.parameterCount }) {
            val hit = pickCtor(c, args) ?: continue
            return try {
                c.isAccessible = true
                c.newInstance(*hit)
            } catch (e: InvocationTargetException) {
                throw MtRuntimeException(e.targetException.message ?: "构造 $type 失败", e.targetException)
            }
        }
        throw MtRuntimeException("$type 没有匹配的构造器/${args.size}")
    }

    private fun pickCtor(c: Constructor<*>, args: List<Any?>): Array<Any?>? {
        if (c.parameterCount != args.size) return null
        val out = arrayOfNulls<Any?>(args.size)
        for (i in args.indices) {
            val t = c.parameterTypes[i]
            if (rank(args[i], t) < 0) return null
            out[i] = convert(args[i], t)
        }
        return out
    }

    fun staticField(clsName: String, name: String): Any? {
        val cls = resolve(clsName) ?: return null
        return runCatching {
            val f = cls.getField(name)
            f.isAccessible = true
            f.get(null)
        }.getOrNull()
    }

    fun get(target: Any, name: String): Any? {
        val cls = target.javaClass
        runCatching {
            val f = cls.getField(name)
            f.isAccessible = true
            return f.get(target)
        }
        val cap = name.replaceFirstChar { it.uppercase() }
        return runCatching {
            val m = cls.methods.firstOrNull { it.name == "get$cap" && it.parameterCount == 0 }
                ?: cls.methods.firstOrNull { it.name == "is$cap" && it.parameterCount == 0 }
            if (m != null) {
                m.isAccessible = true
                m.invoke(target)
            } else null
        }.getOrNull()
    }

    fun set(target: Any, name: String, v: Any?) {
        val cls = target.javaClass
        runCatching {
            val f = cls.getField(name)
            f.isAccessible = true
            f.set(target, convert(v, f.type))
            return
        }
        val cap = name.replaceFirstChar { it.uppercase() }
        runCatching {
            val m = cls.methods.firstOrNull {
                it.name == "set$cap" && it.parameterCount == 1
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(target, convert(v, m.parameterTypes[0]))
            }
        }
    }

    private fun invoke(m: Method, target: Any?, args: Array<Any?>): Any? {
        return try {
            m.isAccessible = true
            val r = m.invoke(target, *args)
            if (r is Unit) null else r
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            if (cause is MtRuntimeException) throw cause
            throw MtRuntimeException(cause.message ?: cause.javaClass.simpleName, cause)
        }
    }
}
