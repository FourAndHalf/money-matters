package com.moneymatters.app.core.recurring

import com.moneymatters.app.core.ledger.ClosedPeriodException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** What one run did. [skippedClosed] counts dates that fell in a closed month: they are not booked, and not retried. */
data class GenerationResult(val posted: Int = 0, val drafts: Int = 0, val skippedClosed: Int = 0, val rulesSkipped: Int = 0) {
    val total: Int get() = posted + drafts
}

/** Recurring rules and the entries they book. Every entry goes through [LedgerRepository], so each one balances and is audited. */
class RecurringRepository(
    private val database: MoneyMattersDatabase,
    private val ledger: LedgerRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.recurringDao()

    fun rules(): Flow<List<RecurringRuleEntity>> = dao.rules()

    suspend fun rule(id: String): RecurringRuleEntity? = dao.get(id)

    suspend fun create(rule: RecurringRuleEntity): String {
        val saved = rule.copy(id = newId(), lastGeneratedOn = null)
        validate(saved)
        dao.insert(saved)
        return saved.id
    }

    /** Changes a rule's fields. What it has already booked is kept; the schedule carries on from [RecurringRuleEntity.lastGeneratedOn]. */
    suspend fun update(rule: RecurringRuleEntity) {
        val old = requireNotNull(dao.get(rule.id)) { "No such recurring rule" }
        val saved = rule.copy(lastGeneratedOn = old.lastGeneratedOn)
        validate(saved)
        dao.update(saved)
    }

    suspend fun setActive(id: String, active: Boolean) {
        val rule = requireNotNull(dao.get(id)) { "No such recurring rule" }
        dao.update(rule.copy(isActive = active))
    }

    /** Deletes the rule only; entries it already booked stay in the ledger. */
    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Books every occurrence that has come due on or before [today], once. The entries and the rule's
     * progress are written in one transaction, so a failure books nothing and a rerun never repeats a date.
     */
    suspend fun generateDue(today: LocalDate): GenerationResult = ledger.inTransaction {
        var result = GenerationResult()
        for (rule in dao.activeRules()) {
            val accountsOk = listOf(rule.fromAccountId, rule.toAccountId).all { id -> database.ledgerDao().getAccount(id)?.isArchived == false }
            if (!accountsOk) {
                result = result.copy(rulesSkipped = result.rulesSkipped + 1)
                continue
            }
            val dates = rule.dueDates(today)
            if (dates.isEmpty()) continue
            val amount = Paise(rule.amountPaise)
            for (date in dates) {
                try {
                    ledger.postTransaction(
                        date = date,
                        payee = rule.name,
                        postings = listOf(PostingLine(rule.toAccountId, debit = amount), PostingLine(rule.fromAccountId, credit = amount)),
                        memo = "Recurring",
                        status = if (rule.autoPost) TransactionStatus.Posted else TransactionStatus.Draft,
                        tags = TransactionTags(rule.decision, rule.plan, rule.paymentMethod),
                    )
                    result = if (rule.autoPost) result.copy(posted = result.posted + 1) else result.copy(drafts = result.drafts + 1)
                } catch (_: ClosedPeriodException) {
                    result = result.copy(skippedClosed = result.skippedClosed + 1)
                }
            }
            dao.update(rule.copy(lastGeneratedOn = dates.last().toString()))
        }
        result
    }

    private fun validate(rule: RecurringRuleEntity) {
        require(rule.name.isNotBlank()) { "A recurring rule needs a name" }
        require(rule.amountPaise > 0) { "The amount must be more than zero" }
        require(rule.fromAccountId != rule.toAccountId) { "Choose two different accounts" }
        val start = LocalDate.parse(rule.startDate)
        rule.endDate?.let { require(!LocalDate.parse(it).isBefore(start)) { "The end date can't be before the start" } }
    }
}
