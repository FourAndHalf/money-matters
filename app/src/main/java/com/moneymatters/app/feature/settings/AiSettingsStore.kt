package com.moneymatters.app.feature.settings

import android.content.Context
import android.content.SharedPreferences
import com.moneymatters.app.core.security.encryptedPrefs

/**
 * AI weekly-review settings (plan.html → P21's "AI: on/off, API key, payload preview"). Not
 * consumed until the AI weekly summary (P19, Phase 4) exists, but the API key is a real secret
 * the moment it's typed in, so the real store ([encrypted]) uses the same Keystore-backed
 * encrypted prefs as the passcode and profile — matching their shape so tests can hand this a
 * plain `SharedPreferences` instead (the Keystore isn't available under Robolectric).
 */
class AiSettingsStore(private val prefs: SharedPreferences) {
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) {
            val editor = prefs.edit()
            if (value.isNullOrBlank()) editor.remove(KEY_API_KEY) else editor.putString(KEY_API_KEY, value)
            editor.apply()
        }

    /** Show the exact payload sent before each call, once there is a call to make. */
    var payloadPreview: Boolean
        get() = prefs.getBoolean(KEY_PAYLOAD_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_PAYLOAD_PREVIEW, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PAYLOAD_PREVIEW = "payload_preview"

        /** The real store: an Android Keystore-backed encrypted prefs file. */
        fun encrypted(context: Context) = AiSettingsStore(encryptedPrefs(context, "ai_settings"))
    }
}
