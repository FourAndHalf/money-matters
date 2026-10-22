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

enum class RestateKind { Split, Bonus }

/** A lot created by a corporate action: [acquiredOn] is kept on a split (the holding period carries over) and is the allotment date on a bonus. */
data class NewLot(val acquiredOn: LocalDate, val units: Units, val cost: Paise)

/** What a split or bonus does to the lots: [consumed] are retired and [created] take their place (a bonus retires nothing). */
data class RestatePlan(val consumed: List<Consumption>, val created: List<NewLot>) {
    val unitsBefore: Units get() = Units(consumed.sumOf { it.units.micro })
}

/** [units] × [ratio], where ratio is in micro-units (5,000,000 = ×5), rounded half-up to a micro-unit. */
fun scaleUnits(units: Units, ratio: Units): Units {
    val product = BigInteger.valueOf(units.micro) * BigInteger.valueOf(ratio.micro)
    val million = BigInteger.valueOf(1_000_000)
    return Units(((product * BigInteger.valueOf(2) + million) / (million * BigInteger.valueOf(2))).toLong())
}

/**
 * Plans a split or bonus on [date] over the lots bought on or before it. A **split** replaces each lot
 * with one holding [ratio] times the units for the same cost, keeping its purchase date. A **bonus**
 * leaves the lots alone and adds one zero-cost lot of [ratio] × the units held, dated [date], since a
 * bonus share's holding period starts at allotment.
 */
fun restate(lots: List<OpenLot>, kind: RestateKind, ratio: Units, date: LocalDate): RestatePlan {
    require(ratio.micro > 0) { "A corporate action needs a positive ratio" }
    val open = lots.filter { it.remainingUnits.micro > 0 && !it.acquiredOn.isAfter(date) }.sortedWith(compareBy({ it.acquiredOn }, { it.id }))
    require(open.isNotEmpty()) { "Nothing was held on that date" }
    return when (kind) {
        RestateKind.Split -> RestatePlan(
            consumed = open.map { Consumption(it.id, it.remainingUnits, it.remainingCost) },
            created = open.map { NewLot(it.acquiredOn, scaleUnits(it.remainingUnits, ratio), it.remainingCost) },
        )
        RestateKind.Bonus -> {
            val held = open.fold(Units.Zero) { acc, lot -> acc + lot.remainingUnits }
            RestatePlan(emptyList(), listOf(NewLot(date, scaleUnits(held, ratio), Paise.Zero)))
        }
    }
}
