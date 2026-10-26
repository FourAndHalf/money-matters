package com.moneymatters.app.feature.widget

import android.content.Context
import android.content.Intent
import com.moneymatters.app.MainActivity

internal const val ACTION_ADD_ENTRY = "com.moneymatters.app.action.ADD_ENTRY"
internal const val ACTION_OPEN_ACCOUNTS = "com.moneymatters.app.action.OPEN_ACCOUNTS"
internal const val ACTION_DISABLE_LOCK = "com.moneymatters.app.action.DISABLE_LOCK"

/** Where a widget button sends the app once it is open (and unlocked). */
enum class LaunchTarget {
    AddEntry,
    Accounts,

    /** The widget's lock toggle switched off: only acted on once the app is open, so it takes the passcode like any other way in. */
    DisableLock,
    ;

    companion object {
        fun from(intent: Intent?): LaunchTarget? = when (intent?.action) {
            ACTION_ADD_ENTRY -> AddEntry
            ACTION_OPEN_ACCOUNTS -> Accounts
            ACTION_DISABLE_LOCK -> DisableLock
            else -> null
        }
    }
}

/** An intent for [MainActivity] that reuses a running instance instead of stacking a second one. */
internal fun launchIntent(context: Context, action: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action ?: Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
