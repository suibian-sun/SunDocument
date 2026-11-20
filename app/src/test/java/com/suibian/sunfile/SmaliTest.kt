package com.suibian.sunfile

import com.suibian.sunfile.data.SmaliTool
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.ClassDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SmaliTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val source = """
        .class public Ldemo/Hello;
        .super Ljava/lang/Object;

        .field public static final TAG:Ljava/lang/String; = "hello"

        .method public constructor <init>()V
            .registers 1
            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method

        .method public static add(II)I
            .registers 3
            add-int v0, p0, p1
            return v0
        .end method

        .method public greet(Ljava/lang/String;)Ljava/lang/String;
            .registers 3
            new-instance v0, Ljava/lang/StringBuilder;
            invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
            invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            move-result-object v0
            const-string v1, " world"
            invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            move-result-object v0
            invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
            move-result-object v0
            return-object v0
        .end method
    """.trimIndent()

    private fun makeApk(dir: File): File {
        val work = File(dir, "work").apply { mkdirs() }
        val dex = SmaliTool.assembleText(source, 26, work)
        val apk = File(dir, "demo.apk")
        ZipOutputStream(apk.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("AndroidManifest.xml"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
            out.putNextEntry(ZipEntry("classes.dex"))
            dex.inputStream().use { it.copyTo(out) }
            out.closeEntry()
        }
        return apk
    }

    private fun methodsOf(dex: File): List<String> {
        val d = DexFileFactory.loadDexFile(dex, Opcodes.forApi(26))
        val out = ArrayList<String>()
        for (c: ClassDef in d.classes) {
            for (m in c.methods) out.add("${c.type}->${m.name}")
        }
        return out
    }

    @Test
    fun disassembleThenReassemble() {
        val apk = makeApk(tmp.root)
        val root = File(tmp.root, "cache").apply { mkdirs() }
        val session = SmaliTool.open(apk, root)
        assertEquals(1, session.parts.size)
        val part = session.parts[0]
        val classes = SmaliTool.classesOf(part)
        assertEquals(1, classes.size)
        assertEquals("demo.Hello", SmaliTool.className(part, classes[0]))
        assertTrue(classes[0].readText().contains(".method public static add(II)I"))
        assertEquals(0, SmaliTool.dirtyCount(part))

        // 改一个方法体：常量换成 42
        val text = classes[0].readText()
        classes[0].writeText(text.replace("const-string v1, \" world\"", "const-string v1, \" smali\""))
        assertTrue(SmaliTool.isDirty(part))

        val out = File(tmp.root, "classes.new.dex")
        SmaliTool.assemblePart(part, out)
        val methods = methodsOf(out)
        assertTrue(methods.contains("Ldemo/Hello;->add"))
        assertTrue(methods.contains("Ldemo/Hello;->greet"))
    }

    @Test
    fun addMethodSurvivesRoundTrip() {
        val apk = makeApk(tmp.root)
        val session = SmaliTool.open(apk, File(tmp.root, "c2").apply { mkdirs() })
        val file = SmaliTool.classesOf(session.parts[0]).first()
        val added = file.readText().trimEnd() + """

            .method public static twice(I)I
                .registers 2
                mul-int/lit8 v0, p0, 0x2
                return v0
            .end method
        """.trimIndent() + "\n"
        file.writeText(added)
        val out = File(tmp.root, "again.dex")
        SmaliTool.assemblePart(session.parts[0], out)
        assertTrue(methodsOf(out).contains("Ldemo/Hello;->twice"))
    }

    @Test
    fun cacheIsReused() {
        val apk = makeApk(tmp.root)
        val root = File(tmp.root, "cache").apply { mkdirs() }
        val a = SmaliTool.open(apk, root)
        val mark = File(a.parts[0].dir, ".ok").lastModified()
        val b = SmaliTool.open(apk, root)
        assertEquals(mark, File(b.parts[0].dir, ".ok").lastModified())
        assertEquals(1, b.parts.size)
    }
}
