package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import java.math.BigInteger
import java.time.LocalDate

/** A purchase lot with what is still unsold: units and the cost that goes with them. */
data class OpenLot(val id: String, val acquiredOn: LocalDate, val remainingUnits: Units, val remainingCost: Paise)

/** How much of one lot a sale used, and the cost that moved out of the holding with it. */
data class Consumption(val lotId: String, val units: Units, val cost: Paise)

class InsufficientUnitsException(message: String) : IllegalStateException(message)

/**
 * Sells [units] from the oldest lots first. A lot's cost follows its units pro rata, rounded
 * half-up; the sale that empties a lot takes whatever cost is left, so costs across any number of
 * partial sales always add back up to the lot's cost exactly.
 */
fun allocateFifo(lots: List<OpenLot>, units: Units): List<Consumption> {
    require(units.micro > 0) { "Units to sell must be positive" }
    val ordered = lots.filter { it.remainingUnits.micro > 0 }.sortedWith(compareBy({ it.acquiredOn }, { it.id }))
    val available = ordered.fold(Units.Zero) { acc, lot -> acc + lot.remainingUnits }
    if (available < units) throw InsufficientUnitsException("Selling ${units.micro} micro-units but only ${available.micro} are held")
    var left = units
    val result = mutableListOf<Consumption>()
    for (lot in ordered) {
        if (left.micro == 0L) break
        val take = if (left < lot.remainingUnits) left else lot.remainingUnits
        val cost = if (take == lot.remainingUnits) lot.remainingCost else proRata(lot.remainingCost, take, lot.remainingUnits)
        result += Consumption(lot.id, take, cost)
        left -= take
    }
    return result
}

internal fun proRata(amount: Paise, part: Units, whole: Units): Paise {
    val numerator = BigInteger.valueOf(amount.value) * BigInteger.valueOf(part.micro)
    val denominator = BigInteger.valueOf(whole.micro)
    return Paise(((numerator * BigInteger.valueOf(2) + denominator) / (denominator * BigInteger.valueOf(2))).toLong())
}
