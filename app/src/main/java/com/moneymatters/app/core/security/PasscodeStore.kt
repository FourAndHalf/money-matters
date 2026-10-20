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
private const val KEY_PASSCODE_LENGTH = "passcode_length"
private const val KEY_LOCK_TIMEOUT = "lock_timeout"
private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
private const val KEY_LOCKED_OUT_UNTIL = "locked_out_until"
private const val SALT_BYTES = 16
private const val HASH_BITS = 256
private const val ITERATIONS = 120_000

/** How long the app may sit in the background before it locks again (plan.html → P02). */
enum class LockTimeout(val millis: Long) { Immediately(0), OneMinute(60_000), FiveMinutes(300_000) }

/**
 * Stores the unlock passcode as a salted PBKDF2 hash, never in the clear. Set during onboarding
 * (P01); checked by the lock screen (P02), which also owns the actual BiometricPrompt call.
 * Hashing is deliberately slow, so call [save] and [verify] off the main thread.
 */
class PasscodeStore(private val prefs: SharedPreferences) {
    fun isSet(): Boolean = prefs.contains(KEY_PASSCODE_HASH)

    fun save(passcode: String, biometricEnabled: Boolean, timeout: LockTimeout = LockTimeout.OneMinute) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit()
            .putString(KEY_PASSCODE_SALT, salt.toHex())
            .putString(KEY_PASSCODE_HASH, hash(passcode, salt).toHex())
            .putInt(KEY_PASSCODE_LENGTH, passcode.length)
            .putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled)
            .putString(KEY_LOCK_TIMEOUT, timeout.name)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_OUT_UNTIL)
            .commit()
    }

    fun verify(passcode: String): Boolean {
        val salt = prefs.getString(KEY_PASSCODE_SALT, null)?.hexToBytes() ?: return false
        val stored = prefs.getString(KEY_PASSCODE_HASH, null)?.hexToBytes() ?: return false
        return MessageDigest.isEqual(stored, hash(passcode, salt))
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    /** Digits in the saved passcode, so the keypad can submit on the last one. */
    fun passcodeLength(): Int = prefs.getInt(KEY_PASSCODE_LENGTH, 0)

    fun lockTimeout(): LockTimeout =
        prefs.getString(KEY_LOCK_TIMEOUT, null)?.let { name -> LockTimeout.entries.firstOrNull { it.name == name } } ?: LockTimeout.OneMinute

    fun setLockTimeout(timeout: LockTimeout) {
        prefs.edit().putString(KEY_LOCK_TIMEOUT, timeout.name).apply()
    }

    /** Wrong attempts since the last unlock. Persisted, so killing the app doesn't reset the backoff. */
    fun failedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    /** Wall-clock millis before which no passcode attempt is accepted; 0 when not locked out. */
    fun lockedOutUntil(): Long = prefs.getLong(KEY_LOCKED_OUT_UNTIL, 0)

    /** Counts a wrong attempt and starts any lockout [lockoutMillisAfter] calls for. */
    fun recordFailedAttempt(nowMillis: Long) {
        val failures = failedAttempts() + 1
        val lockout = lockoutMillisAfter(failures)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failures)
            .putLong(KEY_LOCKED_OUT_UNTIL, if (lockout > 0) nowMillis + lockout else 0)
            .commit()
    }

    fun clearFailedAttempts() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKED_OUT_UNTIL).commit()
    }

    companion object {
        /** The real store: an Android Keystore-backed encrypted prefs file. */
        fun encrypted(context: Context) = PasscodeStore(encryptedPrefs(context, PREFS_NAME))
    }
}

internal const val FREE_ATTEMPTS = 5
private const val FIRST_LOCKOUT_MILLIS = 30_000L
private const val MAX_LOCKOUT_MILLIS = 60 * 60_000L

/**
 * No wait for the first [FREE_ATTEMPTS] − 1 wrong attempts; then 30s, doubling with each further
 * wrong attempt up to an hour. A 4-digit passcode can't be brute-forced through the keypad.
 */
fun lockoutMillisAfter(failures: Int): Long {
    if (failures < FREE_ATTEMPTS) return 0
    val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(7)
    return (FIRST_LOCKOUT_MILLIS shl doublings).coerceAtMost(MAX_LOCKOUT_MILLIS)
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
