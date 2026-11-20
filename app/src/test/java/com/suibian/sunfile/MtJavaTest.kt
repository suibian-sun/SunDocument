package com.suibian.sunfile

import com.suibian.sunfile.data.mtapi.MtApi
import com.suibian.sunfile.data.mtjava.Interp
import com.suibian.sunfile.data.mtjava.Lexer
import com.suibian.sunfile.data.mtjava.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MT 插件解释器：语法、对象、继承、JDK 调用、MT API 基类 */
class MtJavaTest {

    private fun interp(vararg sources: String): Interp {
        val i = Interp()
        for (s in sources) i.load(Parser(Lexer.lex(s)).parseUnit())
        return i
    }

    @Test
    fun basicMethod() {
        val i = interp(
            """
            public class Demo {
                public int add(int a, int b) { return a + b; }
                public String hi(String n) { return "hi " + n; }
                public long big(long x) { return x * 2; }
            }
            """
        )
        val o = i.instantiate("Demo")
        assertEquals(5, i.callMethod(o, "add", listOf(2, 3)))
        assertEquals("hi sun", i.callMethod(o, "hi", listOf("sun")))
        assertEquals(20L, i.callMethod(o, "big", listOf(10L)))
    }

    @Test
    fun loopsAndLists() {
        val i = interp(
            """
            import java.util.ArrayList;
            import java.util.List;
            public class Loop {
                public int sum(int n) {
                    int s = 0;
                    for (int i = 0; i < n; i++) { s += i; }
                    return s;
                }
                public String join() {
                    List<String> list = new ArrayList<>();
                    list.add("a");
                    list.add("b");
                    StringBuilder sb = new StringBuilder();
                    for (String s : list) { sb.append(s).append("-"); }
                    return sb.toString();
                }
                public int count(String text) {
                    int n = 0;
                    for (char c : text.toCharArray()) { if (c == 'a') n++; }
                    return n;
                }
            }
            """
        )
        val o = i.instantiate("Loop")
        assertEquals(10, i.callMethod(o, "sum", listOf(5)))
        assertEquals("a-b-", i.callMethod(o, "join", emptyList<String>()))
        assertEquals(3, i.callMethod(o, "count", listOf("banana")))
    }

    @Test
    fun switchAndTry() {
        val i = interp(
            """
            public class Flow {
                public String pick(int n) {
                    switch (n) {
                        case 1: return "one";
                        case 2: return "two";
                        default: return "other";
                    }
                }
                public int safe(String s) {
                    try {
                        return Integer.parseInt(s);
                    } catch (Exception e) {
                        return -1;
                    }
                }
                public int cond(int a) { return a > 3 ? a * 2 : a; }
            }
            """
        )
        val o = i.instantiate("Flow")
        assertEquals("one", i.callMethod(o, "pick", listOf(1)))
        assertEquals("other", i.callMethod(o, "pick", listOf(9)))
        assertEquals(42, i.callMethod(o, "safe", listOf("42")))
        assertEquals(-1, i.callMethod(o, "safe", listOf("x")))
        assertEquals(10, i.callMethod(o, "cond", listOf(5)))
    }

    @Test
    fun fieldsAndStatic() {
        val i = interp(
            """
            public class Box {
                private int value = 3;
                private static final String TAG = "box";
                public int get() { return value; }
                public void set(int v) { this.value = v; }
                public String tag() { return TAG; }
                public String fmt(double d) { return String.format("%.1f", d); }
                public int abs(int v) { return Math.abs(v); }
            }
            """
        )
        val o = i.instantiate("Box")
        assertEquals(3, i.callMethod(o, "get", emptyList<String>()))
        i.callMethod(o, "set", listOf(7))
        assertEquals(7, i.callMethod(o, "get", emptyList<String>()))
        assertEquals("box", i.callMethod(o, "tag", emptyList<String>()))
        assertEquals("3.5", i.callMethod(o, "fmt", listOf(3.5)))
        assertEquals(4, i.callMethod(o, "abs", listOf(-4)))
    }

    @Test
    fun translationEngineSubclass() {
        val src = """
            import bin.mt.plugin.api.translation.BaseTranslationEngine;
            import java.util.Arrays;
            import java.util.List;

            public class CaseEngine extends BaseTranslationEngine {
                public CaseEngine() {
                    super(new ConfigurationBuilder().setForceNotToSkipTranslated(true).build());
                }
                @Override
                public String name() { return "Case"; }
                @Override
                public List<String> loadSourceLanguages() { return Arrays.asList("src"); }
                @Override
                public List<String> loadTargetLanguages(String s) {
                    return Arrays.asList("upper", "lower");
                }
                @Override
                public String translate(String text, String sourceLanguage, String targetLanguage) {
                    if (targetLanguage.equals("upper")) return text.toUpperCase();
                    return text.toLowerCase();
                }
            }
        """
        val i = interp(src)
        MtApi.install(i, null)
        val o = i.instantiate("CaseEngine")
        assertEquals("Case", i.callMethod(o, "name", emptyList<String>()))
        assertEquals("HELLO", i.callMethod(o, "translate", listOf("Hello", "src", "upper")))
        assertEquals("hello", i.callMethod(o, "translate", listOf("Hello", "src", "lower")))
        val langs = com.suibian.sunfile.data.mtjava.V.iterable(
            i.callMethod(o, "loadTargetLanguages", listOf("src"))
        )
        assertEquals(listOf("upper", "lower"), langs)
    }

    @Test
    fun regexAndFiles() {
        val i = interp(
            """
            import java.util.regex.Matcher;
            import java.util.regex.Pattern;

            public class Re {
                public String digits(String s) {
                    Pattern p = Pattern.compile("[0-9]+");
                    Matcher m = p.matcher(s);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) { sb.append(m.group()).append(","); }
                    return sb.toString();
                }
                public String upperList(String s) {
                    String[] parts = s.split(",");
                    String out = "";
                    for (int i = 0; i < parts.length; i++) {
                        out = out + parts[i].toUpperCase();
                    }
                    return out;
                }
            }
            """
        )
        val o = i.instantiate("Re")
        assertEquals("12,34,", i.callMethod(o, "digits", listOf("a12b34")))
        assertEquals("ABC", i.callMethod(o, "upperList", listOf("a,b,c")))
    }

    @Test
    fun parserErrors() {
        var ok = false
        try {
            Parser(Lexer.lex("public class A { void f() { int x = ; } }")).parseUnit()
        } catch (e: Exception) {
            ok = true
        }
        assertTrue(ok)
        assertFalse(Lexer.lex("class A {}").isEmpty())
    }
}
