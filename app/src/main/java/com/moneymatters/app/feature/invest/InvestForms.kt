package com.moneymatters.app.feature.invest

import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.core.money.parseUnitPrice
import com.moneymatters.app.core.money.parseUnits
import java.time.LocalDate

/**
 * A buy or sell being typed in. Fields hold raw text and are parsed only when saving, the same way
 * Quick Add and onboarding do. [holdingId] is set when trading a stock already held; otherwise
 * [symbol] (with optional [name] and [isin]) names a new one on its first buy.
 */
data class TradeForm(
    val side: TradeSide,
    val date: LocalDate,
    val holdingId: String? = null,
    val instrument: Instrument = Instrument.Stock,
    val fundClass: FundClass = FundClass.Equity,
    val exitLoadDaysText: String = "",
    val symbol: String = "",
    val name: String = "",
    val isin: String = "",
    val unitsText: String = "",
    val amountText: String = "",
    /** Where the money comes from (buy) or goes (sale). */
    val accountId: String? = null,
    /** Bonds: the yearly coupon as a percentage ("7.5") and the date the bond is redeemed. */
    val couponText: String = "",
    val maturity: LocalDate? = null,
    /** Set when an existing buy or sell is being corrected rather than a new one entered. */
    val editingTransactionId: String? = null,
) {
    val units: Units? get() = parseUnits(unitsText)?.takeIf { it.micro > 0 }
    val amount: Paise? get() = parseRupees(amountText)?.takeIf { it.value > 0 }

    val isFund: Boolean get() = instrument == Instrument.MutualFund
    val isBond: Boolean get() = instrument == Instrument.Bond

    val couponBps: Int? get() = percentToBps(couponText)

    /** A fund purchase with no units yet is sent In Transit and allotted later. */
    val inTransit: Boolean get() = isFund && side == TradeSide.Buy && unitsText.isBlank()

    private val hasIdentity: Boolean get() = holdingId != null || if (isFund || isBond) name.isNotBlank() else symbol.isNotBlank()

    /** True once everything a save needs is present and valid. */
    val canSave: Boolean get() = hasIdentity && (units != null || inTransit) && amount != null && accountId != null && (couponText.isBlank() || couponBps != null)

    companion object {
        fun empty(side: TradeSide, today: LocalDate, holdingId: String? = null, instrument: Instrument = Instrument.Stock) =
            TradeForm(side, today, holdingId, instrument)
    }
}

/** A manual price being typed in for one holding. */
data class PriceForm(val holdingId: String, val date: LocalDate, val priceText: String = "") {
    val price: UnitPrice? get() = parseUnitPrice(priceText)?.takeIf { it.tenThousandths > 0 }
    val canSave: Boolean get() = price != null
}

/** A dividend received: [netText] is what reached the account, [tdsText] the tax withheld (optional). */
data class DividendForm(
    val holdingId: String,
    val date: LocalDate,
    val netText: String = "",
    val tdsText: String = "",
    val accountId: String? = null,
) {
    val net: Paise? get() = parseRupees(netText)?.takeIf { it.value > 0 }
    val tds: Paise? get() = if (tdsText.isBlank()) Paise.Zero else parseRupees(tdsText)
    val canSave: Boolean get() = net != null && tds != null && accountId != null
}

/** A split or bonus: [ratioText] is new units per old for a split (5 for a 1:5 split), bonus units per unit held for a bonus (0.5 for 1:2). */
data class ActionForm(
    val holdingId: String,
    val kind: RestateKind,
    val date: LocalDate,
    val ratioText: String = "",
) {
    val ratio: Units? get() = parseUnits(ratioText)?.takeIf { it.micro > 0 }
    val canSave: Boolean get() = ratio != null
}

/** A new monthly SIP into one of the investor's funds. [dayText] is the day of the month it is paid on. */
data class SipForm(
    val start: LocalDate,
    val holdingId: String? = null,
    val amountText: String = "",
    val dayText: String = "5",
    val accountId: String? = null,
    /** Set when an existing SIP's amount, day or account is being changed. */
    val editingPlanId: String? = null,
) {
    val amount: Paise? get() = parseRupees(amountText)?.takeIf { it.value > 0 }
    val day: Int? get() = dayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val canSave: Boolean get() = holdingId != null && amount != null && day != null && accountId != null
}

/** Units arriving for an order that was waiting In Transit. [navText] is optional; the price is worked out from the amount otherwise. */
data class AllotForm(val pendingId: String, val date: LocalDate, val unitsText: String = "", val navText: String = "") {
    val units: Units? get() = parseUnits(unitsText)?.takeIf { it.micro > 0 }
    val nav: UnitPrice? get() = if (navText.isBlank()) UnitPrice(0) else parseUnitPrice(navText)
    val canSave: Boolean get() = units != null && nav != null
}

/**
 * A new fixed deposit. [rateText] is a percentage ("7.25"); [existing] means it is already running, so it is
 * recorded against opening balances and no account is debited.
 */
data class DepositForm(
    val name: String = "",
    val start: LocalDate,
    val maturity: LocalDate,
    val principalText: String = "",
    val rateText: String = "",
    val compounding: Compounding = Compounding.Quarterly,
    val penaltyText: String = "1",
    val autoRenew: Boolean = false,
    val existing: Boolean = false,
    val accountId: String? = null,
    /** Set when an existing deposit's terms are being corrected. */
    val editingAccountId: String? = null,
) {
    val principal: Paise? get() = parseRupees(principalText)?.takeIf { it.value > 0 }
    val rateBps: Int? get() = percentToBps(rateText)?.takeIf { it > 0 }
    val penaltyBps: Int? get() = if (penaltyText.isBlank()) 0 else percentToBps(penaltyText)
    val datesValid: Boolean get() = maturity.isAfter(start)
    val canSave: Boolean
        get() = name.isNotBlank() && principal != null && rateBps != null && penaltyBps != null && datesValid && (existing || editingAccountId != null || accountId != null)
}

/** "7.25" → 725 basis points, with at most two decimals; null for anything else. */
internal fun percentToBps(text: String): Int? {
    val match = Regex("""(\d{1,3})(?:\.(\d{1,2}))?""").matchEntire(text.trim().removeSuffix("%").trim()) ?: return null
    val (whole, fraction) = match.destructured
    return whole.toInt() * 100 + fraction.padEnd(2, '0').toInt()
}

/** Paying out a deposit on [date]: where the money goes, or whether it renews, and any TDS withheld. */
data class DepositCloseForm(
    val accountId: String,
    val date: LocalDate,
    val tdsText: String = "",
    val renew: Boolean = false,
    val toAccountId: String? = null,
) {
    val tds: Paise? get() = if (tdsText.isBlank()) Paise.Zero else parseRupees(tdsText)
    val canSave: Boolean get() = tds != null && (renew || toAccountId != null)
}

/** A holding's details being corrected: what it is called and, for a fund or bond, how it is classified. */
data class HoldingEditForm(
    val holdingId: String,
    val instrument: Instrument,
    val name: String,
    val symbol: String = "",
    val isin: String = "",
    val fundClass: FundClass = FundClass.Equity,
    val exitLoadDaysText: String = "",
    val couponText: String = "",
    val maturity: LocalDate? = null,
) {
    val couponBps: Int? get() = percentToBps(couponText)
    val exitLoadDays: Int? get() = exitLoadDaysText.trim().toIntOrNull()?.takeIf { it > 0 }
    val canSave: Boolean
        get() = (name.isNotBlank() || symbol.isNotBlank()) && (couponText.isBlank() || couponBps != null) && (exitLoadDaysText.isBlank() || exitLoadDays != null)
}
