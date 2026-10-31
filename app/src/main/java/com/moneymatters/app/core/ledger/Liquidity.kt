package com.moneymatters.app.core.ledger

import com.moneymatters.app.core.money.Paise

/**
 * The tier an account sits in: the one chosen, or else the one its kind implies (bank and cash are Cash, a fixed deposit is
 * Deposits, a holding is Markets). Accounts that are not assets have none, and an asset of no particular kind, like EPF, is Locked.
 */
fun effectiveTier(account: AccountEntity): LiquidityTier? {
    if (account.type != AccountType.Asset) return null
    account.liquidityTier?.let { return it }
    return when (account.kind) {
        AccountKind.Bank, AccountKind.Cash -> LiquidityTier.Cash
        AccountKind.FixedDeposit -> LiquidityTier.Deposits
        AccountKind.Investment -> LiquidityTier.Markets
        else -> LiquidityTier.Locked
    }
}

/** Where the money sits, from the books (so holdings are at cost, as everywhere in the ledger). */
data class MoneyMap(val byTier: Map<LiquidityTier, Paise>, val byGoal: Map<AccountGoal, Paise>, val unassigned: Paise)

/** Asset balances grouped by liquidity tier and by goal. Archived accounts and drafts are left out, so it matches Accounts. */
fun moneyMap(ledger: LedgerSnapshot): MoneyMap {
    val booked = ledger.transactions.filter { it.status != TransactionStatus.Draft }.map { it.id }.toSet()
    val net = ledger.postings.filter { it.transactionId in booked }.groupBy { it.accountId }
        .mapValues { (_, legs) -> legs.sumOf { (it.debitPaise ?: 0L) - (it.creditPaise ?: 0L) } }
    val tiers = LiquidityTier.entries.associateWith { 0L }.toMutableMap()
    val goals = AccountGoal.entries.associateWith { 0L }.toMutableMap()
    var unassigned = 0L
    for (a in ledger.accounts) {
        if (a.type != AccountType.Asset || a.isArchived) continue
        val balance = net[a.id] ?: 0L
        effectiveTier(a)?.let { tiers[it] = tiers.getValue(it) + balance }
        if (a.goal != null) goals[a.goal] = goals.getValue(a.goal) + balance else unassigned += balance
    }
    return MoneyMap(tiers.mapValues { Paise(it.value) }, goals.mapValues { Paise(it.value) }, Paise(unassigned))
}

fun LiquidityTier.label() = when (this) {
    LiquidityTier.Cash -> "Cash"
    LiquidityTier.Deposits -> "Deposits"
    LiquidityTier.Markets -> "Markets"
    LiquidityTier.Locked -> "Locked"
}

fun AccountGoal.label() = when (this) {
    AccountGoal.EmergencyFund -> "Emergency fund"
    AccountGoal.WealthGoal -> "₹1 Crore by 30"
}
