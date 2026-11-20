package com.suibian.sunfile

import com.suibian.sunfile.data.SelfCert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class SelfCertTest {

    @Test
    fun generateAndVerify() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = SelfCert.generate(kp, "SunDocument Transpile")
        assertEquals("CN=SunDocument Transpile", cert.subjectDN.name)
        assertEquals("CN=SunDocument Transpile", cert.issuerDN.name)
        cert.checkValidity()
        val v = Signature.getInstance("SHA256withRSA").run {
            initVerify(kp.public)
            update(cert.tbsCertificate)
            verify(cert.signature)
        }
        assertTrue(v)
    }
}
