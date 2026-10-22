package com.moneymatters.app.feature.invest

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
    val symbol: String = "",
    val name: String = "",
    val isin: String = "",
    val unitsText: String = "",
    val amountText: String = "",
    /** Where the money comes from (buy) or goes (sale). */
    val accountId: String? = null,
) {
    val units: Units? get() = parseUnits(unitsText)?.takeIf { it.micro > 0 }
    val amount: Paise? get() = parseRupees(amountText)?.takeIf { it.value > 0 }

    /** True once everything a save needs is present and valid. */
    val canSave: Boolean get() = (holdingId != null || symbol.isNotBlank()) && units != null && amount != null && accountId != null

    companion object {
        fun empty(side: TradeSide, today: LocalDate, holdingId: String? = null) = TradeForm(side, today, holdingId)
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
