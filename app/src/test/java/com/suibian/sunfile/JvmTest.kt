package com.suibian.sunfile

import com.suibian.sunfile.data.mtjava.ClassFile
import com.suibian.sunfile.data.mtjava.Interp
import com.suibian.sunfile.data.mtjava.JArr
import com.suibian.sunfile.data.mtjava.JObj
import com.suibian.sunfile.data.mtjava.JarLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 用真正的 javac 产物做样本：编译一个类、打成一个 jar，
 * 再让解释器跑里面的静态方法、构造器、字符串拼接、数组与 long 运算。
 */
private val MATH_CLASS =
    "yv66vgAAADQANAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEA" +
    "CE1hdGhVdGlsAQAEYmFzZQEAAUkHAA4BABdqYXZhL2xhbmcvU3RyaW5nQnVpbGRlcgoADQADCAARAQADaGkgCgANABMMABQA" +
    "FQEABmFwcGVuZAEALShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9TdHJpbmdCdWlsZGVyOwoADQAXDAAYABkBAAh0" +
    "b1N0cmluZwEAFCgpTGphdmEvbGFuZy9TdHJpbmc7CAAbAQADb25lCAAdAQADdHdvCAAfAQAEbWFueQEABENvZGUBAA9MaW5l" +
    "TnVtYmVyVGFibGUBAAQoSSlWAQADZmliAQAEKEkpSQEADVN0YWNrTWFwVGFibGUBAANhZGQBAAMoKUkBAAVoZWxsbwEAJihM" +
    "amF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9TdHJpbmc7AQAFcmFuZ2UBAAUoSSlbSQcALQEAAltJAQADc3VtAQAEKEkp" +
    "SgEABHBpY2sBABUoSSlMamF2YS9sYW5nL1N0cmluZzsBAApTb3VyY2VGaWxlAQANTWF0aFV0aWwuamF2YQAhAAgAAgAAAAEA" +
    "EgALAAwAAAAIAAEABQAGAAEAIAAAACIAAgABAAAACiq3AAEqBLUAB7EAAAABACEAAAAGAAEAAAADAAEABQAiAAEAIAAAACIA" +
    "AgACAAAACiq3AAEqG7UAB7EAAAABACEAAAAGAAEAAAAEAAkAIwAkAAEAIAAAAGoAAgAFAAAAJBoEowAFGqwDPAQ9BT4dGqMA" +
    "ExscYDYEHDwVBD2EAwGn/+4crAAAAAIAIQAAACIACAAAAAcABwAIAAsACQASAAoAFwALABkADAAcAAkAIgAOACUAAAAMAAMH" +
    "/gAFAQEB+gAUAAEAJgAnAAEAIAAAACAAAgABAAAACCq0AAcQCmCsAAAAAQAhAAAABgABAAAAEQABACgAKQABACAAAAAsAAIA" +
    "AgAAABS7AA1ZtwAPEhC2ABIrtgAStgAWsAAAAAEAIQAAAAYAAQAAABMAAQAqACsAAQAgAAAASwAEAAQAAAAZG7wKTQM+HRui" +
    "AA8sHR0FaE+EAwGn//IssAAAAAIAIQAAAA4AAwAAABYABAAXABcAGAAlAAAADAAC/QAGBwAsAfoAEAAJAC4ALwABACAAAABG" +
    "AAQABAAAABYJQAQ+HRqjAA4fHYVhQIQDAaf/8x+tAAAAAgAhAAAADgADAAAAHAACAB0AFAAeACUAAAAKAAL9AAQEAfoADwAJ" +
    "ADAAMQABACAAAABUAAEAAQAAACUaqwAAAAAAIQAAAAIAAAABAAAAGwAAAAIAAAAeEhqwEhywEh6wAAAAAgAhAAAAEgAEAAAA" +
    "IgAcACMAHwAkACIAJQAlAAAABQADHAICAAEAMgAAAAIAMw=="


class JvmTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * 样本是 javac 编译好的 MathUtil.class（内联成 base64，免得依赖资源打包），
     * 每次测试重新打包成 jar，顺便验证 JarLoader 的解压与解析。
     */
    private fun buildJar(): File {
        val bytes = java.util.Base64.getDecoder().decode(MATH_CLASS)
        val jar = File(tmp.root, "libs/math.jar")
        jar.parentFile.mkdirs()
        ZipOutputStream(jar.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("MathUtil.class"))
            z.write(bytes)
            z.closeEntry()
        }
        return jar
    }

    @Test
    fun parseOnly() {
        val bytes = java.util.Base64.getDecoder().decode(MATH_CLASS)
        val p = ClassFile.parse(bytes)
        assertEquals("MathUtil", p.name)
        assertTrue(p.methods.isNotEmpty())
        assertTrue(p.methods.all { it.code != null })
    }

    private fun interp(): Interp {
        val jar = buildJar()
        val i = Interp()
        i.loadClasses(JarLoader.load(jar.parentFile))
        return i
    }

    @Test
    fun staticMethodLoop() {
        val i = interp()
        assertEquals(55, i.invokeStatic("MathUtil", "fib", "(I)I", listOf(10)))
        assertEquals(0, i.invokeStatic("MathUtil", "fib", "(I)I", listOf(0)))
    }

    @Test
    fun longArithmetic() {
        val i = interp()
        assertEquals(5050L, i.invokeStatic("MathUtil", "sum", "(I)J", listOf(100)))
    }

    @Test
    fun constructorAndInstanceMethod() {
        val i = interp()
        val obj = i.newObject("LMathUtil;") as JObj
        i.invokeSpecial(obj, "MathUtil", "<init>", "(I)V", listOf(5))
        assertEquals(15, i.invokeVirtual(obj, "MathUtil", "add", "()I", emptyList()))
    }

    @Test
    fun stringConcatUsesStringBuilder() {
        val i = interp()
        val obj = i.newObject("LMathUtil;") as JObj
        i.invokeSpecial(obj, "MathUtil", "<init>", "()V", emptyList())
        assertEquals("hi 世界", i.invokeVirtual(obj, "MathUtil", "hello", "(Ljava/lang/String;)Ljava/lang/String;", listOf("世界")))
    }

    @Test
    fun arrayAllocationAndWrite() {
        val i = interp()
        val obj = i.newObject("LMathUtil;") as JObj
        i.invokeSpecial(obj, "MathUtil", "<init>", "()V", emptyList())
        val arr = i.invokeVirtual(obj, "MathUtil", "range", "(I)[I", listOf(3))
        assertTrue(arr is JArr)
        assertEquals(listOf(0, 2, 4), (arr as JArr).items.map { (it as Number).toInt() })
    }

    @Test
    fun switchOnInt() {
        val i = interp()
        assertEquals("one", i.invokeStatic("MathUtil", "pick", "(I)Ljava/lang/String;", listOf(1)))
        assertEquals("two", i.invokeStatic("MathUtil", "pick", "(I)Ljava/lang/String;", listOf(2)))
        assertEquals("many", i.invokeStatic("MathUtil", "pick", "(I)Ljava/lang/String;", listOf(9)))
    }
}
