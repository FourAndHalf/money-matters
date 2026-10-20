package com.moneymatters.app.feature.onboarding

import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.security.PasscodeStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val OPENING_EQUITY_ACCOUNT = "Equity:Opening"

/**
 * Commits a finished [OnboardingForm]: ledger accounts and opening balances, then the passcode,
 * then the profile, which also marks setup complete. Setup-complete is written last, so an
 * interrupted save simply shows the wizard again on next launch.
 */
class OnboardingSaver(
    private val ledger: LedgerRepository,
    private val passcodes: PasscodeStore,
    private val profiles: ProfileStore,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend fun save(form: OnboardingForm) = withContext(Dispatchers.IO) {
        val date = today()
        val profile = form.toUserProfile(date)
        postSetupToLedger(ledger, form, date)
        passcodes.save(form.passcode, form.biometricEnabled, form.lockTimeout)
        profiles.completeSetup(profile)
    }
}

/**
 * Creates every named account, the category and income accounts, and balances each non-zero
 * opening balance against `Equity:Opening`, all in one database transaction. `Equity:Opening` is
 * created in that same transaction, so finding it already there means an earlier, interrupted
 * save got this far; posting again would double every opening balance, so it's skipped.
 */
internal suspend fun postSetupToLedger(repo: LedgerRepository, form: OnboardingForm, date: LocalDate) = repo.inTransaction {
    if (repo.accountIdByName(OPENING_EQUITY_ACCOUNT) != null) return@inTransaction
    val equityId = repo.createAccount(OPENING_EQUITY_ACCOUNT, AccountType.Equity)
    for (draft in form.accounts) {
        if (draft.name.isBlank()) continue
        val type = draft.kind.toLedgerType()
        val accountId = repo.createAccountIfAbsent(draft.name.trim(), type, kind = draft.kind)
        val amount = draft.openingPaise() ?: continue
        val postings = if (type == AccountType.Liability) {
            listOf(PostingLine(accountId, credit = amount), PostingLine(equityId, debit = amount))
        } else {
            listOf(PostingLine(accountId, debit = amount), PostingLine(equityId, credit = amount))
        }
        repo.postTransaction(date = date, payee = "Opening balance: ${draft.name.trim()}", postings = postings)
    }
    for (category in form.categories) {
        repo.createAccountIfAbsent("Expenses:${category.name}", AccountType.Expense)
    }
    repo.createAccountIfAbsent("Income:Salary", AccountType.Income)
    if (form.income.hasFreelanceIncome) repo.createAccountIfAbsent("Income:Freelance", AccountType.Income)
}
