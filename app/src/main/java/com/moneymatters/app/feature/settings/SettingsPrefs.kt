package com.moneymatters.app.feature.settings

import android.content.Context

enum class NumberFormat { Full, Compact }

/**
 * Display, Accountability and Notifications preferences (plan.html → P21). Nothing here is a
 * secret, so plain `SharedPreferences` rather than the encrypted store `ProfileStore` uses.
 * [impactThresholdPercent] and [decisionTagPrompts] aren't consumed by anything yet — the impact
 * prompt (P05) is Phase 3 — but the preference is real and persists ahead of that landing.
 */
class SettingsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    var accountantMode: Boolean
        get() = prefs.getBoolean(KEY_ACCOUNTANT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCOUNTANT_MODE, value).apply()

    var numberFormat: NumberFormat
        get() = NumberFormat.entries.firstOrNull { it.name == prefs.getString(KEY_NUMBER_FORMAT, null) } ?: NumberFormat.Full
        set(value) = prefs.edit().putString(KEY_NUMBER_FORMAT, value.name).apply()

    var impactThresholdPercent: Int
        get() = prefs.getInt(KEY_IMPACT_THRESHOLD, 50)
        set(value) = prefs.edit().putInt(KEY_IMPACT_THRESHOLD, value.coerceIn(1, 100)).apply()

    var decisionTagPrompts: Boolean
        get() = prefs.getBoolean(KEY_DECISION_PROMPTS, true)
        set(value) = prefs.edit().putBoolean(KEY_DECISION_PROMPTS, value).apply()

    /** Not consumed until bill/EMI/SIP reminders (Recurring, P11, Phase 2) exist. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    private companion object {
        const val KEY_ACCOUNTANT_MODE = "accountant_mode"
        const val KEY_NUMBER_FORMAT = "number_format"
        const val KEY_IMPACT_THRESHOLD = "impact_threshold_percent"
        const val KEY_DECISION_PROMPTS = "decision_tag_prompts"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
