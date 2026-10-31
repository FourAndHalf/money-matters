package com.moneymatters.app.feature.reconcile

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.core.reconcile.ReconcileView

/** A statement balance as typed. A leading minus is allowed because an account can be overdrawn, or a card overpaid. */
fun parseSignedRupees(text: String): Paise? {
    val t = text.trim()
    val negative = t.startsWith("-") || t.startsWith("−")
    val amount = parseRupees(if (negative) t.drop(1).trim() else t) ?: return null
    return if (negative) -amount else amount
}

/** What the Reconcile screen shows for the typed statement balance and the ticked entries. */
data class ReconcileScreenState(
    val view: ReconcileView,
    val statementText: String,
    val ticked: Set<String>,
) {
    val statement: Paise? get() = parseSignedRupees(statementText)
    val difference: Paise? get() = statement?.let { view.difference(it, ticked) }
    val matches: Boolean get() = difference == Paise.Zero
    val canFinish: Boolean get() = matches && ticked.isNotEmpty()
    val tickedTotal: Paise get() = Paise(view.lines.filter { it.transactionId in ticked }.sumOf { it.amount.value })
}
