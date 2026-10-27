package com.moneymatters.app.feature.widget

import android.content.Context
import android.content.SharedPreferences
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.security.encryptedPrefs

private const val PREFS_NAME = "widget_snapshot"
private const val KEY_NET_WORTH = "net_worth"
private const val KEY_ASSETS = "assets"
private const val KEY_LIABILITIES = "liabilities"
private const val KEY_UPDATED_AT = "updated_at"

/** The figures the net worth widget shows, as the app last saw them. */
data class WidgetSnapshot(val netWorth: Paise, val assets: Paise, val liabilities: Paise, val updatedAtMillis: Long)

/**
 * What the home-screen widget reads instead of opening the ledger: a widget process has no business holding the database
 * key. The app rewrites it whenever the totals change or it comes to the foreground. Encrypted like the other small stores
 * (constructor takes the prefs so tests can pass plain ones).
 */
class WidgetStore(private val prefs: SharedPreferences) {
    fun save(snapshot: WidgetSnapshot) {
        prefs.edit()
            .putLong(KEY_NET_WORTH, snapshot.netWorth.value)
            .putLong(KEY_ASSETS, snapshot.assets.value)
            .putLong(KEY_LIABILITIES, snapshot.liabilities.value)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtMillis)
            .commit()
    }

    /** Null until the app has saved one. */
    fun read(): WidgetSnapshot? {
        if (!prefs.contains(KEY_NET_WORTH)) return null
        return WidgetSnapshot(
            Paise(prefs.getLong(KEY_NET_WORTH, 0)),
            Paise(prefs.getLong(KEY_ASSETS, 0)),
            Paise(prefs.getLong(KEY_LIABILITIES, 0)),
            prefs.getLong(KEY_UPDATED_AT, 0),
        )
    }

    companion object {
        fun encrypted(context: Context) = WidgetStore(encryptedPrefs(context, PREFS_NAME))
    }
}
