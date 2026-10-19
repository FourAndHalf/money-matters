package com.moneymatters.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val PREFS_NAME = "app_security"
private const val KEY_PASSCODE_HASH = "passcode_hash"
private const val KEY_PASSCODE_SALT = "passcode_salt"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
private const val SALT_BYTES = 16
private const val HASH_BITS = 256
private const val ITERATIONS = 120_000

/**
 * Stores the unlock passcode as a salted PBKDF2 hash, never in the clear. Set during onboarding
 * (P01); checked by the lock screen (P02), which also owns the actual BiometricPrompt call.
 * Hashing is deliberately slow, so call [save] and [verify] off the main thread.
 */
class PasscodeStore(private val prefs: SharedPreferences) {
    fun isSet(): Boolean = prefs.contains(KEY_PASSCODE_HASH)

    fun save(passcode: String, biometricEnabled: Boolean) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit()
            .putString(KEY_PASSCODE_SALT, salt.toHex())
            .putString(KEY_PASSCODE_HASH, hash(passcode, salt).toHex())
            .putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled)
            .commit()
    }

    fun verify(passcode: String): Boolean {
        val salt = prefs.getString(KEY_PASSCODE_SALT, null)?.hexToBytes() ?: return false
        val stored = prefs.getString(KEY_PASSCODE_HASH, null)?.hexToBytes() ?: return false
        return MessageDigest.isEqual(stored, hash(passcode, salt))
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    companion object {
        /** The real store: an Android Keystore-backed encrypted prefs file. */
        fun encrypted(context: Context) = PasscodeStore(encryptedPrefs(context, PREFS_NAME))
    }
}

private fun hash(passcode: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, HASH_BITS)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

/** Opens (or creates) an encrypted prefs file keyed by the Android Keystore. Shared by the app's small stores. */
internal fun encryptedPrefs(context: Context, name: String): SharedPreferences = EncryptedSharedPreferences.create(
    context,
    name,
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
