package com.moneymatters.app.feature.accounts

import com.moneymatters.app.core.money.Paise

/**
 * Balance after each entry, in chronological (oldest-first) order, starting from [opening].
 * The account's current balance is always the last value returned.
 */
fun runningBalances(opening: Paise, amounts: List<Paise>): List<Paise> {
    var balance = opening
    return amounts.map { balance += it; balance }
}
