package com.suibian.sunfile.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

data class VaultEntry(
    val id: String,
    val title: String,
    val account: String,
    val password: String,
    val note: String = ""
)

/**
 * 常用密码的本地保管。
 *
 * 主口令不落盘，只存 PBKDF2 派生的校验值；条目用同一口令派生的
 * AES-256-GCM 密钥加密后整体写入文件。改口令时用旧口令解开、新口令重新加密。
 * 忘记口令即无法恢复——这是设计目标，不是缺陷。
 */
object Vault {

    private const val FILE = "vault.dat"
    private const val ITER = 120_000
    private const val KEY_BITS = 256

    private fun file(context: Context): File = File(context.filesDir, FILE)

    private fun pbkdf2(pass: String, salt: ByteArray, iter: Int = ITER): ByteArray {
        val spec = PBEKeySpec(pass.toCharArray(), salt, iter, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encrypt(key: ByteArray, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv to cipher.doFinal(plain)
    }

    private fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    fun exists(context: Context): Boolean = file(context).isFile

    /** 首次创建：写入 salt、校验值与空列表 */
    fun create(context: Context, pin: String): Result<Unit> = runCatching {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = pbkdf2(pin, salt)
        val (iv, data) = encrypt(key, JSONArray().toString().toByteArray())
        val root = JSONObject()
            .put("v", 1)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(data))
        file(context).writeText(root.toString())
    }

    /** 用口令解出条目；口令错误时 AES-GCM 校验失败，抛异常 */
    fun open(context: Context, pin: String): Result<List<VaultEntry>> = runCatching {
        val root = JSONObject(file(context).readText())
        val salt = unb64(root.getString("salt"))
        val key = pbkdf2(pin, salt)
        val raw = decrypt(key, unb64(root.getString("iv")), unb64(root.getString("data")))
        parse(String(raw))
    }

    fun save(context: Context, pin: String, entries: List<VaultEntry>): Result<Unit> = runCatching {
        val root = JSONObject(file(context).readText())
        val salt = unb64(root.getString("salt"))
        val key = pbkdf2(pin, salt)
        val (iv, data) = encrypt(key, dump(entries).toByteArray())
        val next = JSONObject()
            .put("v", 1)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(data))
        file(context).writeText(next.toString())
    }

    private fun parse(raw: String): List<VaultEntry> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            VaultEntry(
                id = o.optString("id", i.toString()),
                title = o.optString("t"),
                account = o.optString("a"),
                password = o.optString("p"),
                note = o.optString("n")
            )
        }
    }

    private fun dump(entries: List<VaultEntry>): String {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("t", it.title)
                    .put("a", it.account)
                    .put("p", it.password)
                    .put("n", it.note)
            )
        }
        return arr.toString()
    }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun newId(): String = System.currentTimeMillis().toString(36) + SecureRandom().nextInt(9999)
}
