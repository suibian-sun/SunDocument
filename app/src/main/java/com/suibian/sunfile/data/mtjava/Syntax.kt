package com.suibian.sunfile.data.mtjava

/**
 * Java 源码的词法 / 语法分析。
 *
 * 只覆盖 MT 插件用得到的那部分语法：类、字段、方法、构造器、局部变量、
 * if / for / foreach / while / do / switch / try、以及常规表达式。
 * 注解、泛型参数、lambda、内部类只做「跳过」，不影响执行。
 */

internal enum class T { IDENT, INT, LONG, FLOAT, DOUBLE, STR, CHR, OP, EOF }

internal class Tok(val t: T, val s: String, val line: Int)

class MtSyntaxException(message: String) : Exception(message)

internal object Lexer {

    private val ops = arrayOf(
        ">>>=", "<<=", ">>=", ">>>", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "->", "::"
    )
    private const val SINGLE = "+-*/%&|^!~=<>?:;,.()[]{}@"

    fun lex(src: String): List<Tok> {
        val out = ArrayList<Tok>(src.length / 4 + 8)
        var i = 0
        var line = 1
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == '\n') { line++; i++; continue }
            if (c.isWhitespace()) { i++; continue }
            if (c == '/' && i + 1 < n && src[i + 1] == '/') {
                while (i < n && src[i] != '\n') i++
                continue
            }
            if (c == '/' && i + 1 < n && src[i + 1] == '*') {
                i += 2
                while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) {
                    if (src[i] == '\n') line++
                    i++
                }
                i = minOf(n, i + 2)
                continue
            }
            if (c == '"') {
                val startLine = line
                if (i + 2 < n && src[i + 1] == '"' && src[i + 2] == '"') {
                    i += 3
                    val sb = StringBuilder()
                    while (i + 2 < n && !(src[i] == '"' && src[i + 1] == '"' && src[i + 2] == '"')) {
                        if (src[i] == '\n') line++
                        sb.append(src[i]); i++
                    }
                    i = minOf(n, i + 3)
                    out.add(Tok(T.STR, sb.toString(), startLine))
                    continue
                }
                i++
                val sb = StringBuilder()
                while (i < n && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < n) {
                        sb.append(unescape(src, i).first)
                        i = unescape(src, i).second
                        continue
                    }
                    if (src[i] == '\n') line++
                    sb.append(src[i]); i++
                }
                i++
                out.add(Tok(T.STR, sb.toString(), startLine))
                continue
            }
            if (c == '\'') {
                val lineNo = line
                i++
                var v = ' '
                if (i < n && src[i] == '\\') {
                    val u = unescape(src, i)
                    v = u.first
                    i = u.second
                } else if (i < n) {
                    v = src[i]; i++
                }
                if (i < n && src[i] == '\'') i++
                out.add(Tok(T.CHR, v.toString(), lineNo))
                continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit())) {
                val start = i
                var type = T.INT
                if (c == '0' && i + 1 < n && (src[i + 1] == 'x' || src[i + 1] == 'X')) {
                    i += 2
                    while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                } else if (c == '0' && i + 1 < n && (src[i + 1] == 'b' || src[i + 1] == 'B')) {
                    i += 2
                    while (i < n && (src[i] == '0' || src[i] == '1' || src[i] == '_')) i++
                } else {
                    while (i < n && (src[i].isDigit() || src[i] == '_')) i++
                    if (i < n && src[i] == '.') {
                        type = T.DOUBLE
                        i++
                        while (i < n && (src[i].isDigit() || src[i] == '_')) i++
                    }
                    if (i < n && (src[i] == 'e' || src[i] == 'E')) {
                        type = T.DOUBLE
                        i++
                        if (i < n && (src[i] == '+' || src[i] == '-')) i++
                        while (i < n && src[i].isDigit()) i++
                    }
                }
                val text = src.substring(start, i).replace("_", "")
                if (i < n) {
                    when (src[i]) {
                        'l', 'L' -> { type = T.LONG; i++ }
                        'f', 'F' -> { type = T.FLOAT; i++ }
                        'd', 'D' -> { type = T.DOUBLE; i++ }
                    }
                }
                out.add(Tok(type, text, line))
                continue
            }
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i
                while (i < n && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '$')) i++
                out.add(Tok(T.IDENT, src.substring(start, i), line))
                continue
            }
            var matched = false
            for (op in ops) {
                if (src.startsWith(op, i)) {
                    out.add(Tok(T.OP, op, line))
                    i += op.length
                    matched = true
                    break
                }
            }
            if (matched) continue
            if (SINGLE.indexOf(c) >= 0) {
                out.add(Tok(T.OP, c.toString(), line))
                i++
                continue
            }
            // 不认识的字符直接跳过，避免整份源码解析失败
            i++
        }
        out.add(Tok(T.EOF, "", line))
        return out
    }

    private fun unescape(src: String, at: Int): Pair<Char, Int> {
        val c = src[at + 1]
        return when (c) {
            'n' -> '\n' to at + 2
            'r' -> '\r' to at + 2
            't' -> '\t' to at + 2
            'b' -> '\b' to at + 2
            'f' -> '\u000C' to at + 2
            '0' -> '\u0000' to at + 2
            'u' -> {
                val hex = src.substring(minOf(at + 2, src.length), minOf(at + 6, src.length))
                val v = hex.toIntOrNull(16) ?: 63
                v.toChar() to minOf(src.length, at + 6)
            }
            else -> c to at + 2
        }
    }
}

// ---------------------------------------------------------------- AST

internal sealed class Expr

internal class Lit(val v: Any?) : Expr()
internal class Name(val id: String) : Expr()
internal class Field(val target: Expr, val name: String) : Expr()
internal class Call(val target: Expr?, val name: String, val args: List<Expr>) : Expr()
internal class New(
    val type: String,
    val args: List<Expr>,
    val dims: List<Expr> = emptyList(),
    val init: List<Expr>? = null
) : Expr()

internal class Bin(val op: String, val l: Expr, val r: Expr) : Expr()
internal class Un(val op: String, val e: Expr, val postfix: Boolean) : Expr()
internal class Assign(val op: String, val target: Expr, val value: Expr) : Expr()
internal class Cond(val c: Expr, val a: Expr, val b: Expr) : Expr()
internal class Cast(val type: String, val e: Expr) : Expr()
internal class Index(val target: Expr, val idx: Expr) : Expr()
internal class InstanceOf(val e: Expr, val type: String) : Expr()
internal object ThisExpr : Expr()
internal object SuperExpr : Expr()

internal sealed class Stmt

internal class Block(val stmts: List<Stmt>) : Stmt()
internal class VarStmt(val type: String?, val name: String, val init: Expr?) : Stmt()
internal class ExprStmt(val e: Expr) : Stmt()
internal class IfStmt(val c: Expr, val t: Stmt, val f: Stmt?) : Stmt()
internal class WhileStmt(val c: Expr, val body: Stmt, val doWhile: Boolean) : Stmt()
internal class ForStmt(val init: List<Stmt>, val cond: Expr?, val update: List<Expr>, val body: Stmt) : Stmt()
internal class ForEachStmt(val type: String?, val name: String, val iter: Expr, val body: Stmt) : Stmt()
internal class ReturnStmt(val e: Expr?) : Stmt()
internal object BreakStmt : Stmt()
internal object ContinueStmt : Stmt()
internal class SwitchStmt(val sel: Expr, val cases: List<Pair<Expr?, List<Stmt>>>) : Stmt()
internal class TryStmt(
    val body: Block,
    val catches: List<Triple<String, String, Block>>,
    val fin: Block?
) : Stmt()

internal class ThrowStmt(val e: Expr) : Stmt()
internal object EmptyStmt : Stmt()
internal class SyncStmt(val e: Expr, val body: Block) : Stmt()

internal class FieldDecl(val type: String, val name: String, val init: Expr?, val isStatic: Boolean)

internal class Param(val type: String, val name: String)

internal class MethodDecl(
    val name: String,
    val ret: String,
    val params: List<Param>,
    val body: Block?,
    val isStatic: Boolean
)

internal class ClassDecl(
    val name: String,
    val ext: String?,
    val impls: List<String>,
    val fields: List<FieldDecl>,
    val methods: List<MethodDecl>
)

// ---------------------------------------------------------------- parser

internal class Parser(private val toks: List<Tok>) {

    private var p = 0

    private val mods = hashSetOf(
        "public", "private", "protected", "static", "final", "abstract", "native",
        "synchronized", "transient", "volatile", "strictfp", "default", "sealed"
    )

    private fun tk(k: Int = 0): Tok = toks[minOf(p + k, toks.size - 1)]

    private fun isOp(s: String): Boolean = tk().t == T.OP && tk().s == s
    private fun isWord(s: String): Boolean = tk().t == T.IDENT && tk().s == s

    private fun eat(s: String): Boolean {
        if (isOp(s) || isWord(s)) { p++; return true }
        return false
    }

    private fun need(s: String) {
        if (!eat(s)) err("需要 '$s'")
    }

    private fun err(msg: String): Nothing =
        throw MtSyntaxException("第 ${tk().line} 行：$msg（遇到 '${tk().s}'）")

    fun parseUnit(): List<ClassDecl> {
        val out = ArrayList<ClassDecl>()
        while (tk().t != T.EOF) {
            when {
                eat("package") || eat("import") -> skipToSemicolon()
                isWord("class") || isWord("interface") || isWord("enum") -> {
                    p++
                    out.add(parseClassBody())
                }
                isOp("@") -> skipAnnotation()
                isOp("}") -> p++
                isOp(";") -> p++
                else -> p++
            }
        }
        return out
    }

    private fun skipToSemicolon() {
        while (tk().t != T.EOF && !isOp(";")) p++
        if (isOp(";")) p++
    }

    private fun skipAnnotation() {
        need("@")
        if (tk().t == T.IDENT) p++
        while (isOp(".")) { p++; if (tk().t == T.IDENT) p++ }
        if (isOp("(")) skipBalanced("(", ")")
    }

    private fun skipBalanced(open: String, close: String) {
        var depth = 0
        while (tk().t != T.EOF) {
            if (isOp(open)) depth++
            if (isOp(close)) {
                depth--
                p++
                if (depth <= 0) return
                continue
            }
            p++
        }
    }

    private fun parseClassBody(): ClassDecl {
        val name = tk().s
        if (tk().t != T.IDENT) err("类名缺失")
        p++
        if (isOp("<")) skipBalanced("<", ">")
        var ext: String? = null
        val impls = ArrayList<String>()
        while (true) {
            if (eat("extends")) ext = parseType()
            else if (eat("implements")) {
                while (true) {
                    impls.add(parseType())
                    if (!eat(",")) break
                }
            } else break
        }
        need("{")
        val fields = ArrayList<FieldDecl>()
        val methods = ArrayList<MethodDecl>()
        while (tk().t != T.EOF && !isOp("}")) {
            parseMember(name, fields, methods)
        }
        need("}")
        return ClassDecl(name, ext, impls, fields, methods)
    }

    private fun parseMember(owner: String, fields: MutableList<FieldDecl>, methods: MutableList<MethodDecl>) {
        if (isOp(";")) { p++; return }
        if (isOp("@")) { skipAnnotation(); return }
        if (isWord("class") || isWord("interface") || isWord("enum")) {
            // 内部类暂不支持，整段跳过
            p++
            skipBalanced("{", "}")
            return
        }
        var isStatic = false
        while (tk().t == T.IDENT && mods.contains(tk().s)) {
            if (tk().s == "static") isStatic = true
            p++
        }
        if (isOp("<")) skipBalanced("<", ">")

        // 构造器：名字与类名相同
        if (tk().t == T.IDENT && tk().s == owner && tk(1).let { it.t == T.OP && it.s == "(" }) {
            p++
            val params = parseParams()
            skipThrows()
            val body = if (isOp("{")) parseBlock() else { need(";"); null }
            methods.add(MethodDecl("<init>", "void", params, body, false))
            return
        }
        if (isOp("{")) { skipBalanced("{", "}"); return }

        val type = parseType()
        val name = tk().s
        if (tk().t != T.IDENT) err("成员名缺失")
        p++
        if (isOp("(")) {
            val params = parseParams()
            skipThrows()
            val body = if (isOp("{")) parseBlock() else { need(";"); null }
            methods.add(MethodDecl(name, type, params, body, isStatic))
            return
        }
        // 字段，可能是逗号分隔的多个
        var curName = name
        while (true) {
            var init: Expr? = null
            while (isOp("[")) { p++; need("]") }
            if (eat("=")) init = parseArrayOrExpr()
            fields.add(FieldDecl(type, curName, init, isStatic))
            if (eat(",")) {
                curName = tk().s
                if (tk().t != T.IDENT) err("字段名缺失")
                p++
                continue
            }
            break
        }
        while (isOp("[")) { p++; need("]") }
        need(";")
    }

    private fun skipThrows() {
        if (!eat("throws")) return
        while (true) {
            parseType()
            if (!eat(",")) break
        }
    }

    private fun parseType(): String {
        val sb = StringBuilder()
        if (tk().t != T.IDENT) err("类型缺失")
        sb.append(tk().s)
        p++
        while (true) {
            if (isOp("<")) {
                skipBalanced("<", ">")
                continue
            }
            if (isOp(".") && tk(1).t == T.IDENT) {
                sb.append('.').append(tk(1).s)
                p += 2
                continue
            }
            break
        }
        while (isOp("[") && tk(1).t == T.OP && tk(1).s == "]") {
            sb.append("[]")
            p += 2
        }
        if (isOp("...")) { p++ }
        return sb.toString()
    }

    private fun parseParams(): List<Param> {
        need("(")
        val out = ArrayList<Param>()
        if (eat(")")) return out
        while (true) {
            if (isOp("@")) skipAnnotation()
            while (tk().t == T.IDENT && (mods.contains(tk().s) || tk().s == "final")) p++
            val type = parseType()
            if (isOp("...")) p++
            val name = tk().s
            if (tk().t == T.IDENT) p++
            out.add(Param(type, name))
            if (eat(",")) continue
            break
        }
        need(")")
        return out
    }

    private fun parseArrayOrExpr(): Expr {
        if (isOp("{")) {
            p++
            val items = ArrayList<Expr>()
            while (tk().t != T.EOF && !isOp("}")) {
                items.add(parseArrayOrExpr())
                if (!eat(",")) break
            }
            need("}")
            return New("", emptyList(), emptyList(), items)
        }
        return parseExpr()
    }

    fun parseBlock(): Block {
        need("{")
        val list = ArrayList<Stmt>()
        while (tk().t != T.EOF && !isOp("}")) list.add(parseStmt())
        need("}")
        return Block(list)
    }

    private fun parseStmt(): Stmt {
        if (isOp("{")) return parseBlock()
        if (isOp(";")) { p++; return EmptyStmt }
        if (isOp("@")) { skipAnnotation(); return parseStmt() }
        if (eat("if")) {
            need("(")
            val c = parseExpr()
            need(")")
            val t = parseStmt()
            val f = if (eat("else")) parseStmt() else null
            return IfStmt(c, t, f)
        }
        if (eat("while")) {
            need("(")
            val c = parseExpr()
            need(")")
            return WhileStmt(c, parseStmt(), false)
        }
        if (eat("do")) {
            val body = parseStmt()
            need("while")
            need("(")
            val c = parseExpr()
            need(")")
            need(";")
            return WhileStmt(c, body, true)
        }
        if (isWord("for")) return parseFor()
        if (isWord("switch")) return parseSwitch()
        if (isWord("try")) return parseTry()
        if (eat("return")) {
            val e = if (isOp(";")) null else parseExpr()
            need(";")
            return ReturnStmt(e)
        }
        if (eat("break")) { need(";"); return BreakStmt }
        if (eat("continue")) { need(";"); return ContinueStmt }
        if (eat("throw")) {
            val e = parseExpr()
            need(";")
            return ThrowStmt(e)
        }
        if (eat("synchronized")) {
            need("(")
            val e = parseExpr()
            need(")")
            return SyncStmt(e, parseBlock())
        }
        tryVarDecl()?.let { return it }
        val e = parseExpr()
        need(";")
        return ExprStmt(e)
    }

    private fun tryVarDecl(): Stmt? {
        val save = p
        if (isOp("@")) { skipAnnotation() }
        val type = try {
            parseType()
        } catch (e: Exception) {
            p = save
            return null
        }
        if (tk().t != T.IDENT) { p = save; return null }
        val name = tk().s
        if (!(tk(1).t == T.OP && (tk(1).s == "=" || tk(1).s == ";" || tk(1).s == "," || tk(1).s == "["))) {
            p = save
            return null
        }
        p++
        while (isOp("[")) { p++; need("]") }
        var init: Expr? = null
        if (eat("=")) init = parseArrayOrExpr()
        need(";")
        return VarStmt(type, name, init)
    }

    private fun parseFor(): Stmt {
        need("for")
        need("(")
        // foreach
        val save = p
        run {
            while (tk().t == T.IDENT && (mods.contains(tk().s) || tk().s == "final")) p++
            val type = try { parseType() } catch (e: Exception) { null }
            if (type != null && tk().t == T.IDENT && tk(1).t == T.OP && tk(1).s == ":") {
                val name = tk().s
                p += 2
                val iter = parseExpr()
                need(")")
                return ForEachStmt(type, name, iter, parseStmt())
            }
            p = save
        }
        val init = ArrayList<Stmt>()
        if (!isOp(";")) {
            val v = tryVarDeclNoSemi()
            if (v != null) init.add(v)
            else {
                while (true) {
                    init.add(ExprStmt(parseExpr()))
                    if (!eat(",")) break
                }
                need(";")
            }
        } else need(";")
        val cond = if (isOp(";")) null else parseExpr()
        need(";")
        val update = ArrayList<Expr>()
        if (!isOp(")")) {
            while (true) {
                update.add(parseExpr())
                if (!eat(",")) break
            }
        }
        need(")")
        return ForStmt(init, cond, update, parseStmt())
    }

    private fun tryVarDeclNoSemi(): Stmt? {
        val save = p
        val type = try { parseType() } catch (e: Exception) { p = save; return null }
        if (tk().t != T.IDENT) { p = save; return null }
        val name = tk().s
        if (!(tk(1).t == T.OP && (tk(1).s == "=" || tk(1).s == ";" || tk(1).s == ","))) { p = save; return null }
        p++
        var init: Expr? = null
        if (eat("=")) init = parseArrayOrExpr()
        if (isOp(",")) { p = save; return null }
        if (isOp(";")) p++
        return VarStmt(type, name, init)
    }

    private fun parseSwitch(): Stmt {
        need("switch")
        need("(")
        val sel = parseExpr()
        need(")")
        need("{")
        val cases = ArrayList<Pair<Expr?, List<Stmt>>>()
        var cur: Expr? = null
        var body = ArrayList<Stmt>()
        var started = false
        while (tk().t != T.EOF && !isOp("}")) {
            if (eat("case")) {
                if (started) { cases.add(cur to body); body = ArrayList() }
                cur = parseExpr()
                need(":")
                started = true
                continue
            }
            if (eat("default")) {
                if (started) { cases.add(cur to body); body = ArrayList() }
                cur = null
                need(":")
                started = true
                continue
            }
            body.add(parseStmt())
        }
        if (started) cases.add(cur to body)
        need("}")
        return SwitchStmt(sel, cases)
    }

    private fun parseTry(): Stmt {
        need("try")
        val body = parseBlock()
        val catches = ArrayList<Triple<String, String, Block>>()
        while (eat("catch")) {
            need("(")
            while (tk().t == T.IDENT && (mods.contains(tk().s) || tk().s == "final")) p++
            val type = parseType()
            val name = tk().s
            if (tk().t == T.IDENT) p++
            need(")")
            catches.add(Triple(type, name, parseBlock()))
        }
        val fin = if (eat("finally")) parseBlock() else null
        return TryStmt(body, catches, fin)
    }

    // ------------------------------------------------------------ 表达式

    fun parseExpr(): Expr = parseAssign()

    private fun parseAssign(): Expr {
        val left = parseTernary()
        if (tk().t == T.OP && tk().s in ASSIGN_OPS) {
            val op = tk().s
            p++
            val value = parseAssign()
            return Assign(op, left, value)
        }
        return left
    }

    private fun parseTernary(): Expr {
        val c = parseBinary(0)
        if (eat("?")) {
            val a = parseAssign()
            need(":")
            val b = parseAssign()
            return Cond(c, a, b)
        }
        return c
    }

    private fun parseBinary(level: Int): Expr {
        if (level >= PREC.size) return parseUnary()
        var left = parseBinary(level + 1)
        while (true) {
            val t = tk()
            if (t.t != T.OP) break
            val idx = PREC[level].indexOf(t.s)
            if (idx < 0) break
            p++
            val right = parseBinary(level + 1)
            left = Bin(t.s, left, right)
        }
        return left
    }

    private fun parseUnary(): Expr {
        val t = tk()
        if (t.t == T.OP) {
            when (t.s) {
                "!", "~", "-", "+", "++", "--" -> {
                    p++
                    return Un(t.s, parseUnary(), false)
                }
                "(" -> {
                    // 强制转换
                    val cast = tryCast()
                    if (cast != null) return cast
                }
            }
        }
        return parsePostfix()
    }

    private fun tryCast(): Expr? {
        val save = p
        need("(")
        val type = try { parseType() } catch (e: Exception) { p = save; return null }
        if (!isOp(")")) { p = save; return null }
        val basic = type in BASIC_TYPES
        if (!basic) {
            val next = tk(1)
            val startsExpr = next.t == T.IDENT || next.t == T.INT || next.t == T.LONG ||
                next.t == T.FLOAT || next.t == T.DOUBLE || next.t == T.STR || next.t == T.CHR ||
                (next.t == T.OP && (next.s == "(" || next.s == "!" || next.s == "~"))
            if (!startsExpr) { p = save; return null }
        }
        p++
        return Cast(type, parseUnary())
    }

    private fun parsePostfix(): Expr {
        var e = parsePrimary()
        while (true) {
            when {
                isOp(".") -> {
                    p++
                    if (isWord("class")) { p++; e = Lit(e.toString()); continue }
                    if (isWord("this")) { p++; continue }
                    if (isWord("new")) { p++; parseType(); parseArgs(); e = Lit(null); continue }
                    val name = tk().s
                    if (tk().t != T.IDENT) err("成员名缺失")
                    p++
                    if (isOp("(")) e = Call(e, name, parseArgs())
                    else e = Field(e, name)
                }
                isOp("[") -> {
                    p++
                    val idx = parseExpr()
                    need("]")
                    e = Index(e, idx)
                }
                isOp("++") || isOp("--") -> {
                    val op = tk().s
                    p++
                    e = Un(op, e, true)
                }
                else -> return e
            }
        }
    }

    private fun parseArgs(): List<Expr> {
        need("(")
        val out = ArrayList<Expr>()
        if (eat(")")) return out
        while (true) {
            out.add(parseExpr())
            if (eat(",")) continue
            break
        }
        need(")")
        return out
    }

    private fun parsePrimary(): Expr {
        val t = tk()
        when (t.t) {
            T.INT -> { p++; return intLit(t.s) }
            T.LONG -> { p++; return Lit(parseLong(t.s)) }
            T.FLOAT -> { p++; return Lit(t.s.toFloatOrNull() ?: 0f) }
            T.DOUBLE -> { p++; return Lit(t.s.toDoubleOrNull() ?: 0.0) }
            T.STR -> { p++; return Lit(t.s) }
            T.CHR -> { p++; return Lit(t.s.firstOrNull() ?: ' ') }
            else -> {}
        }
        if (isOp("(")) {
            p++
            val e = parseExpr()
            need(")")
            return e
        }
        if (isOp("{")) {
            val arr = parseArrayOrExpr()
            return arr
        }
        if (isWord("new")) return parseNew()
        if (isWord("this")) {
            p++
            if (isOp("(")) return Call(ThisExpr, "<init>", parseArgs())
            return ThisExpr
        }
        if (isWord("super")) {
            p++
            if (isOp("(")) return Call(SuperExpr, "<init>", parseArgs())
            return SuperExpr
        }
        if (isWord("true")) { p++; return Lit(true) }
        if (isWord("false")) { p++; return Lit(false) }
        if (isWord("null")) { p++; return Lit(null) }
        if (t.t == T.IDENT) {
            p++
            if (isOp("(")) return Call(null, t.s, parseArgs())
            return Name(t.s)
        }
        if (isOp(".")) { p++; return ThisExpr }
        err("无法识别的表达式")
    }

    private fun parseNew(): Expr {
        need("new")
        val type = parseType()
        if (isOp("[")) {
            val dims = ArrayList<Expr>()
            while (isOp("[")) {
                p++
                if (!isOp("]")) dims.add(parseExpr())
                need("]")
            }
            return New(type, emptyList(), dims)
        }
        val args = if (isOp("(")) parseArgs() else emptyList()
        if (isOp("{")) {
            // new X() { ... } 是匿名内部类，语法上跳过，值为 null
            if (args.isNotEmpty() || type in BASIC_TYPES) {
                skipBalanced("{", "}")
                return Lit(null)
            }
            val arr = parseArrayOrExpr() as New
            return New(type, args, emptyList(), arr.init)
        }
        return New(type, args)
    }

    private fun intLit(s: String): Lit {
        val v = when {
            s.startsWith("0x", true) -> s.substring(2).toLongOrNull(16)
            s.startsWith("0b", true) -> s.substring(2).toLongOrNull(2)
            else -> s.toLongOrNull()
        } ?: 0L
        return Lit(v.toInt())
    }

    private fun parseLong(s: String): Long = when {
        s.startsWith("0x", true) -> s.substring(2).toLongOrNull(16) ?: 0L
        else -> s.toLongOrNull() ?: 0L
    }

    private companion object {
        val ASSIGN_OPS = setOf("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=")
        val BASIC_TYPES = setOf(
            "int", "long", "short", "byte", "char", "boolean", "float", "double", "void"
        )
        // 从低到高
        val PREC = listOf(
            arrayOf("||").toList(),
            arrayOf("&&").toList(),
            arrayOf("|").toList(),
            arrayOf("^").toList(),
            arrayOf("&").toList(),
            arrayOf("==", "!=").toList(),
            arrayOf("<", ">", "<=", ">=", "instanceof").toList(),
            arrayOf("<<", ">>", ">>>").toList(),
            arrayOf("+", "-").toList(),
            arrayOf("*", "/", "%").toList()
        )
    }
}
