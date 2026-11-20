package com.suibian.sunfile

import com.suibian.sunfile.data.Axml
import com.suibian.sunfile.data.AxmlEncoder
import org.junit.Assert.assertTrue
import org.junit.Test

class AxmlTest {

    @Test
    fun encodeThenDecode() {
        val text = """
            xmlns:android="http://schemas.android.com/apk/res/android"
            <manifest android:versionCode="3" android:versionName="1.0" package="cc.sun.demo">
                <uses-permission android:name="android.permission.INTERNET"/>
                <application android:label="Demo" android:debuggable="false" android:allowBackup="true">
                    <activity android:name=".Main" android:exported="true"/>
                </application>
            </manifest>
        """.trimIndent()
        val bytes = AxmlEncoder.encode(text)
        assertTrue(bytes.size > 100)
        java.io.File("/tmp/out.axml").writeBytes(bytes)
        val out = Axml.decode(bytes)
        println("=== SIZE ${bytes.size} ===")
        println(out)
        assertTrue(out.contains("cc.sun.demo"))
        assertTrue(out.contains("android:name=\".Main\""))
        assertTrue(out.contains("android:versionCode=\"3\""))
        assertTrue(out.contains("android:exported=\"true\""))
        assertTrue(out.contains("<application"))
    }
}
