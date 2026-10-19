package com.moneymatters.app.feature.dashboard

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.ui.components.Tone

/**
 * Everything the dashboard renders. Pure data: no formatting decisions beyond labels
 * that come from the domain (dates, account names). Amounts are always [Paise].
 */
@Immutable
data class DashboardState(
    val greeting: String,
    val dateLabel: String,
    val profileInitial: String,
    val hasUnreadAlerts: Boolean,
    val leeway: Leeway,
    val kpis: List<Kpi>,
    val accounts: List<AccountCard>,
    val runway: Runway,
    val budgets: Budgets,
    val spending: Spending,
    val upcoming: List<UpcomingItem>,
    val goals: Goals,
    val portfolio: Portfolio,
    val insights: Insights,
    val attention: List<AttentionItem>,
)

/** Safe-to-spend until payday, with the day-by-day spend behind it. */
@Immutable
data class Leeway(
    val safeToSpend: Paise,
    val paydayLabel: String,
    val daysLeft: Int,
    val usedFraction: Float,
    val reserved: Paise,
    val reservedCount: Int,
    /** Spend per elapsed day of the period; the last entry is today. */
    val dailySpend: List<Paise>,
    val daysInPeriod: Int,
    val periodStartLabel: String,
    val periodEndLabel: String,
) {
    val dailyAllowance: Paise get() = dailyAllowance(safeToSpend, daysLeft)
}

@Immutable
data class Kpi(val label: String, val value: String, val delta: String, val tone: Tone, val trend: List<Float>)

enum class CardFinish { Pearl, Graphite, Silver, Champagne, Smoke }

/** One card in the account stack, plus the facts its context panel shows. */
@Immutable
data class AccountCard(
    /** Stable id, shared with the Accounts list (P08) and its ledger (P09). */
    val id: String,
    val finish: CardFinish,
    val typeLabel: String,
    val name: String,
    val amountLabel: String,
    val amount: Paise,
    val footLeft: String,
    val footRight: String,
    /** Short glyph in the top-right corner; null draws an EMV chip. */
    val glyph: String?,
    val detail: AccountDetail,
)

sealed interface AccountDetail {
    val title: String
    val subtitle: String
    val action: String

    @Immutable
    data class Bank(
        override val title: String,
        override val subtitle: String,
        override val action: String,
        val moneyIn: Paise,
        val moneyOut: Paise,
        val recent: List<TxnLine>,
    ) : AccountDetail

    @Immutable
    data class CreditCard(
        override val title: String,
        override val subtitle: String,
        override val action: String,
        val utilisation: Float,
        val limit: Paise,
        val statement: Paise,
        val unbilled: Paise,
        val dueLabel: String,
        val coveredBy: String?,
    ) : AccountDetail

    @Immutable
    data class Cash(
        override val title: String,
        override val subtitle: String,
        override val action: String,
        val onHand: Paise,
        val spentThisMonth: Paise,
        val withdrawnThisMonth: Paise,
        val status: String,
    ) : AccountDetail

    @Immutable
    data class FdLadder(
        override val title: String,
        override val subtitle: String,
        override val action: String,
        val deposits: List<Fd>,
        val breakTodayValue: Paise,
    ) : AccountDetail

    @Immutable
    data class Investments(
        override val title: String,
        override val subtitle: String,
        override val action: String,
        val cost: Paise,
        val unrealized: Paise,
        val unrealizedPct: String,
        val xirr: String,
        val ltcgUsed: Paise,
        val ltcgLimit: Paise,
        val pricesLabel: String,
    ) : AccountDetail
}

@Immutable
data class TxnLine(val chip: String, val payee: String, val meta: String, val amount: Paise, val badge: String, val tone: Tone)

@Immutable
data class Fd(val principal: Paise, val rate: String, val maturesLabel: String, val accrued: Paise)

/** Months of essential spend covered at each rung of the ladder (cumulative). */
@Immutable
data class Runway(
    val essentialMonthly: Paise,
    val cashMonths: Float,
    val withFdMonths: Float,
    val withMarketsMonths: Float,
    val scaleMonths: Int,
    val trajectoryLabel: String,
    val trajectoryTone: Tone,
)

@Immutable
data class Budgets(val periodLabel: String, val elapsedFraction: Float, val lines: List<BudgetLine>)

@Immutable
data class BudgetLine(val name: String, val spent: Paise, val cap: Paise)

@Immutable
data class Spending(val total: Paise, val categories: List<CategoryShare>, val needWantLabel: String, val unplannedCount: Int)

@Immutable
data class CategoryShare(val name: String, val amount: Paise)

@Immutable
data class UpcomingItem(val day: String, val month: String, val title: String, val meta: String, val amount: Paise, val badge: String, val tone: Tone)

@Immutable
data class Goals(val emergency: EmergencyGoal, val corpus: CorpusGoal)

@Immutable
data class EmergencyGoal(val saved: Paise, val target: Paise, val blockSize: Paise, val coverageLabel: String)

@Immutable
data class CorpusGoal(
    val title: String,
    val timeLeftLabel: String,
    val deadlineLabel: String,
    val invested: Paise,
    val target: Paise,
    val monthlySip: Paise,
    val monthsLeft: Int,
    /** Assumed annual return used for the projection, e.g. 0.10. */
    val annualReturn: Double,
    val startLabel: String,
) {
    val requiredSip: Paise get() = requiredMonthlyContribution(target, invested, monthsLeft, annualReturn)
    val projected: Paise get() = projectedValue(invested, monthlySip, monthsLeft, annualReturn)
    val onTrack: Boolean get() = projected >= target
}

@Immutable
data class Portfolio(
    val value: Paise,
    val unrealized: Paise,
    val unrealizedPct: String,
    val weekChange: Paise,
    val xirr: String,
    val equityFraction: Float,
    val targetEquityFraction: Float,
    val sources: List<PriceSource>,
)

@Immutable
data class PriceSource(val name: String, val asOf: String, val status: String, val tone: Tone)

@Immutable
data class Insights(val digestDate: String, val digest: String, val items: List<InsightItem>)

@Immutable
data class InsightItem(val glyph: InsightGlyph, val title: String, val detail: String, val action: String)

enum class InsightGlyph { Alert, Recurring }

@Immutable
data class AttentionItem(
    val kind: AttentionKind,
    val title: String,
    val detail: String,
    val badge: String? = null,
    val tone: Tone = Tone.Info,
    val amount: Paise? = null,
)

enum class AttentionKind { CloseMonth, Drafts, Uncategorized, InTransit, WeeklyReview, AdvanceTax }
