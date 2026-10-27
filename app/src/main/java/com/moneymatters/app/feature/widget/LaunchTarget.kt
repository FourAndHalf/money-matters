package com.moneymatters.app.feature.widget

import android.content.Context
import android.content.Intent
import com.moneymatters.app.MainActivity

internal const val ACTION_ADD_ENTRY = "com.moneymatters.app.action.ADD_ENTRY"
internal const val ACTION_OPEN_ACCOUNTS = "com.moneymatters.app.action.OPEN_ACCOUNTS"

/** Where a widget button sends the app once it is open (and unlocked). */
enum class LaunchTarget {
    AddEntry,
    Accounts,
    ;

    companion object {
        fun from(intent: Intent?): LaunchTarget? = when (intent?.action) {
            ACTION_ADD_ENTRY -> AddEntry
            ACTION_OPEN_ACCOUNTS -> Accounts
            else -> null
        }
    }
}

/** An intent for [MainActivity] that reuses a running instance instead of stacking a second one. */
internal fun launchIntent(context: Context, action: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action ?: Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
