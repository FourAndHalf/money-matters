package com.moneymatters.app.core.invest

import android.content.SharedPreferences

/** The investor's equity target as a whole percent (the rest is debt); unset until they choose one. */
class AllocationPrefs(private val prefs: SharedPreferences) {
    fun targetEquityPercent(): Int? = prefs.getInt(KEY_EQUITY, -1).takeIf { it in 0..100 }

    fun save(percent: Int) {
        require(percent in 0..100) { "A target is a percentage from 0 to 100" }
        prefs.edit().putInt(KEY_EQUITY, percent).apply()
    }

    private companion object {
        const val KEY_EQUITY = "target_equity_percent"
    }
}
