package com.moneymatters.app.core.ledger

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

private const val DB_NAME = "money_matters.db"
private const val PREFS_NAME = "ledger_key_store"
private const val PREFS_PASSPHRASE_KEY = "db_passphrase"
private const val PASSPHRASE_BYTES = 32

/**
 * Builds the real, on-device [MoneyMattersDatabase]: encrypted at rest with SQLCipher, keyed by a
 * random passphrase that itself lives only inside an Android Keystore-backed encrypted file. There's
 * no user passcode yet (that's P01/P02); this key is device-bound, not user-derived.
 */
object LedgerDatabaseProvider {
    @Volatile private var instance: MoneyMattersDatabase? = null

    fun get(context: Context): MoneyMattersDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): MoneyMattersDatabase {
        val factory = SupportOpenHelperFactory(passphrase(context))
        return Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .build()
    }

    private fun passphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.getString(PREFS_PASSPHRASE_KEY, null)?.let { return it.hexToBytes() }
        val generated = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit().putString(PREFS_PASSPHRASE_KEY, generated.toHex()).apply()
        return generated
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
