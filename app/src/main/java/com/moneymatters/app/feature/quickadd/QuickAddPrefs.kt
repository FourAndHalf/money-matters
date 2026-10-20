package com.moneymatters.app.feature.quickadd

import android.content.Context

/**
 * How many times each [EntryKind] has been saved, so the journal preview can show itself
 * automatically the first few times for a new entry type (plan.html → P04's preview toggle),
 * then default to hidden once the postings for that type are familiar. Not a secret, so plain
 * `SharedPreferences` rather than the encrypted store `ProfileStore`/`PasscodeStore` use.
 */
class QuickAddPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("quick_add_prefs", Context.MODE_PRIVATE)

    fun shouldAutoShowPreview(kind: EntryKind): Boolean = prefs.getInt(key(kind), 0) < AUTO_SHOW_LIMIT

    fun recordSave(kind: EntryKind) {
        prefs.edit().putInt(key(kind), prefs.getInt(key(kind), 0) + 1).apply()
    }

    private fun key(kind: EntryKind) = "preview_shown_${kind.name}"

    companion object {
        private const val AUTO_SHOW_LIMIT = 3
    }
}
