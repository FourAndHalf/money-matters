package com.moneymatters.app.core.recurring

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PlanKind
import java.time.LocalDate

/** How often a recurring rule repeats (plan.html → P11). */
enum class Frequency { Weekly, Monthly, Yearly }

/**
 * A rule that books the same two-leg entry on a schedule: salary, rent, an EMI, a subscription, a transfer.
 * [toAccountId] is debited and [fromAccountId] is credited, as in Quick Add. [lastGeneratedOn] is the latest
 * occurrence already handled, so a run never books the same date twice.
 */
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    /** Becomes the payee of every entry the rule books. */
    val name: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amountPaise: Long,
    val frequency: Frequency,
    /** ISO date of the first occurrence. Monthly and yearly rules keep this day, clamped to short months. */
    val startDate: String,
    val endDate: String? = null,
    /** True posts each entry; false leaves it as a Draft for you to confirm. */
    val autoPost: Boolean,
    val decision: DecisionKind? = null,
    val plan: PlanKind? = null,
    val paymentMethod: PaymentMethod? = null,
    val lastGeneratedOn: String? = null,
    val isActive: Boolean = true,
)

/** Most entries one rule books in a single run, so a rule left alone for years can't flood the ledger. The rest follow on the next run. */
const val MAX_PER_RUN = 366

/** Upper bound on the walk through a rule's dates, so a bad start date can't loop for long. */
private const val MAX_STEPS = 20_000

/** The [n]th occurrence, counted from the start date (0 is the start date itself). Counting from the start keeps "the 31st" from drifting to the 28th. */
fun RecurringRuleEntity.occurrence(n: Int): LocalDate {
    val start = LocalDate.parse(startDate)
    return when (frequency) {
        Frequency.Weekly -> start.plusWeeks(n.toLong())
        Frequency.Monthly -> start.plusMonths(n.toLong())
        Frequency.Yearly -> start.plusYears(n.toLong())
    }
}

/** Occurrences after [lastGeneratedOn] and on or before [through] (and the end date, if any), oldest first. */
fun RecurringRuleEntity.dueDates(through: LocalDate, limit: Int = MAX_PER_RUN): List<LocalDate> {
    val last = lastGeneratedOn?.let(LocalDate::parse)
    val end = endDate?.let(LocalDate::parse)
    val out = mutableListOf<LocalDate>()
    for (n in 0 until MAX_STEPS) {
        val date = occurrence(n)
        if (date.isAfter(through) || (end != null && date.isAfter(end))) break
        if (last == null || date.isAfter(last)) {
            out += date
            if (out.size == limit) break
        }
    }
    return out
}

/** The next occurrence not yet booked, or null once the rule has run past its end date. */
fun RecurringRuleEntity.nextDue(): LocalDate? {
    val last = lastGeneratedOn?.let(LocalDate::parse)
    val end = endDate?.let(LocalDate::parse)
    for (n in 0 until MAX_STEPS) {
        val date = occurrence(n)
        if (end != null && date.isAfter(end)) return null
        if (last == null || date.isAfter(last)) return date
    }
    return null
}
