package com.suibian.sunfile.data

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * APK 重签名。
 *
 * 改过 `resources.arsc` 或 AndroidManifest 的包签名会失效，装不上；这里用
 * 运行时生成的一对密钥重新签（v1 + v2 + v3），产物可以直接安装。
 *
 * 密钥只在第一次用时生成，存在 files/transpile 下，之后一直复用——
 * 这样同一个应用多次重签出来的签名一致，可以覆盖安装。
 */
object ApkSign {

    private const val DIR = "transpile"

    data class Result(val ok: Boolean, val message: String, val output: File? = null)

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun keyPair(context: Context): Pair<PrivateKey, X509Certificate>? {
        val d = dir(context)
        val keyFile = File(d, "key.der")
        val certFile = File(d, "cert.der")
        if (keyFile.isFile && certFile.isFile) {
            runCatching {
                val key = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
                return key to cert
            }
        }
        return runCatching {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val cert = SelfCert.generate(kp, "SunDocument Transpile")
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
            kp.private to cert
        }.getOrNull()
    }

    fun sign(context: Context, input: File, output: File): Result {
        val kp = keyPair(context) ?: return Result(false, "生成签名密钥失败")
        return sign(kp.first, kp.second, input, output)
    }

    fun sign(key: PrivateKey, cert: X509Certificate, input: File, output: File): Result {
        if (!input.isFile) return Result(false, "源文件不存在")
        return try {
            output.parentFile?.mkdirs()
            if (output.exists()) output.delete()
            val config = ApkSigner.SignerConfig.Builder("transpile", key, listOf(cert)).build()
            val signer = ApkSigner.Builder(listOf(config))
                .setInputApk(input)
                .setOutputApk(output)
                .setMinSdkVersion(26)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
            signer.sign()
            Result(true, "已重签名", output)
        } catch (e: Throwable) {
            runCatching { output.delete() }
            Result(false, e.message ?: e.javaClass.simpleName)
        }
    }

    /** 同目录下生成 xxx-signed.apk */
    fun signToSibling(context: Context, apk: File): Result {
        val out = File(apk.parentFile, apk.nameWithoutExtension + "-signed.apk")
        val r = sign(context, apk, out)
        return if (r.ok) r else Result(false, r.message, null)
    }
}

/** 极简 DER 编码，只为生成一张自签名的 X.509 证书 */
object SelfCert {

    fun generate(kp: KeyPair, commonName: String): X509Certificate {
        val alg = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.nullValue())
        val name = Der.seq(Der.set(Der.seq(Der.oid("2.5.4.3"), Der.utf8(commonName))))
        val now = System.currentTimeMillis()
        val validity = Der.seq(
            Der.utc(Date(now - 86_400_000L)),
            Der.generalized(Date(now + 30L * 365 * 86_400_000L))
        )
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val tbs = Der.seq(
            Der.tlv(0xA0, Der.integer(BigInteger.valueOf(2))),
            Der.integer(serial),
            alg,
            name,
            validity,
            name,
            kp.public.encoded
        )
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(kp.private)
            update(tbs)
            sign()
        }
        val cert = Der.seq(tbs, alg, Der.bitString(sig))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private object Der {

        fun length(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }

        fun tlv(tag: Int, body: ByteArray): ByteArray =
            byteArrayOf(tag.toByte()) + length(body.size) + body

        fun seq(vararg parts: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            for (p in parts) out.write(p)
            return tlv(0x30, out.toByteArray())
        }

        fun set(vararg parts: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            for (p in parts) out.write(p)
            return tlv(0x31, out.toByteArray())
        }

        fun integer(v: BigInteger): ByteArray = tlv(0x02, v.toByteArray())

        fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

        fun bitString(b: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0) + b)

        fun utf8(s: String): ByteArray = tlv(0x0C, s.toByteArray(Charsets.UTF_8))

        fun generalized(d: Date): ByteArray {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return tlv(0x18, fmt.format(d).toByteArray(Charsets.US_ASCII))
        }

        fun utc(d: Date): ByteArray {
            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return tlv(0x17, fmt.format(d).toByteArray(Charsets.US_ASCII))
        }

        fun oid(s: String): ByteArray {
            val parts = s.split('.').map { it.toInt() }
            val out = ByteArrayOutputStream()
            out.write(parts[0] * 40 + parts[1])
            for (i in 2 until parts.size) {
                var v = parts[i]
                val buf = ArrayList<Int>()
                buf.add(v and 0x7F)
                v = v shr 7
                while (v > 0) {
                    buf.add(v and 0x7F)
                    v = v shr 7
                }
                for (j in buf.indices.reversed()) {
                    out.write(if (j == 0) buf[j] else buf[j] or 0x80)
                }
            }
            return tlv(0x06, out.toByteArray())
        }
    }
}
