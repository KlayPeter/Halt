package com.klaypeter.halt.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN 只存 PBKDF2 摘要 + 随机盐，不存明文。
 * 4 位 PIN 本身熵很低，所以靠迭代次数和错误次数限制来抬成本。
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.getString(KEY_HASH, null) != null

    fun set(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltText = prefs.getString(KEY_SALT, null) ?: return false
        val hashText = prefs.getString(KEY_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltText, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashText, Base64.NO_WRAP) }.getOrNull() ?: return false
        return hash(pin, salt).contentEquals(expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec)
            .encoded
    }

    companion object {
        /** PIN 位数。 */
        const val LENGTH = 4

        private const val PREFS = "pin_store"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 20_000
        private const val KEY_BITS = 256
    }
}
