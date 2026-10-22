package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What one fixed deposit is worth on a day. [postedInterest] is what the ledger already holds (its
 * balance above principal); [accruedInterest] is what has been earned to [asOf]. The difference,
 * [unposted], is real money earned but not yet booked, so net worth adds it.
 */
data class DepositValuation(
    val deposit: FixedDepositEntity,
    val postedInterest: Paise,
    val accruedInterest: Paise,
    val maturityValue: Paise,
    val breakProceeds: Paise,
    val breakPenalty: Paise,
    val daysToMaturity: Long,
    val isClosed: Boolean,
) {
    val principal: Paise get() = Paise(deposit.principalPaise)
    val unposted: Paise get() = if (isClosed) Paise.Zero else accruedInterest - postedInterest

    /** Principal plus everything earned so far; zero once closed. */
    val value: Paise get() = if (isClosed) Paise.Zero else principal + accruedInterest
}

/** [ledgerBalance] is the deposit account's booked balance: principal plus interest posted so far. */
fun depositValuation(deposit: FixedDepositEntity, ledgerBalance: Paise, asOf: LocalDate): DepositValuation {
    val principal = Paise(deposit.principalPaise)
    val start = LocalDate.parse(deposit.startDate)
    val maturity = LocalDate.parse(deposit.maturityDate)
    val accrued = accruedInterest(principal, deposit.rateBps, deposit.compounding, start, asOf, maturity)
    val broken = breakValue(principal, deposit.rateBps, deposit.compounding, start, maturity, asOf, deposit.penaltyBps)
    return DepositValuation(
        deposit = deposit,
        postedInterest = ledgerBalance - principal,
        accruedInterest = accrued,
        maturityValue = maturityValue(principal, deposit.rateBps, deposit.compounding, start, maturity),
        breakProceeds = broken.proceeds,
        breakPenalty = broken.penalty,
        daysToMaturity = ChronoUnit.DAYS.between(asOf, maturity),
        isClosed = deposit.closedOn != null,
    )
}
