package com.moneymatters.app.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.feature.books.label
import com.moneymatters.app.ui.components.AccountCardFace
import com.moneymatters.app.feature.onboarding.NewAccountDraft
import com.moneymatters.app.feature.onboarding.OPENING_EQUITY_ACCOUNT
import com.moneymatters.app.feature.onboarding.isValidOptionalAmount
import com.moneymatters.app.feature.onboarding.openingPaise
import com.moneymatters.app.feature.onboarding.shortLabel
import com.moneymatters.app.feature.onboarding.toLedgerType
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate

/** The kinds you can add by hand; deposits and holdings have their own flows on the Invest tab. */
val AddableKinds = listOf(AccountKind.Bank, AccountKind.Cash, AccountKind.CreditCard, AccountKind.Loan)

/**
 * Creates the account and, if an opening balance (or amount owed) is given, books it against `Equity:Opening`
 * in the same database transaction. A name that is already taken is refused rather than silently reused.
 */
suspend fun addAccountWithOpening(repo: LedgerRepository, draft: NewAccountDraft, date: LocalDate) {
    val name = draft.name.trim()
    require(name.isNotEmpty()) { "Give the account a name" }
    require(isValidOptionalAmount(draft.openingBalance)) { "Enter an amount like 12,500.50" }
    val isCard = draft.kind == AccountKind.CreditCard
    val limit = if (isCard) requireNotNull(parseRupees(draft.creditLimit)?.takeIf { it.value > 0 }) { "Enter the card's limit" } else null
    val statementDay = if (isCard) requireNotNull(draft.statementDay.trim().toIntOrNull()?.takeIf { it in 1..28 }) { "Enter the statement day, from 1 to 28" } else null
    repo.inTransaction {
        require(repo.accountIdByName(name) == null) { "There is already an account called $name" }
        val type = draft.kind.toLedgerType()
        val accountId = repo.createAccount(name, type, kind = draft.kind)
        if (limit != null && statementDay != null) repo.addCreditCardDetails(accountId, limit, statementDay)
        val amount = draft.openingPaise() ?: return@inTransaction
        val equityId = repo.createAccountIfAbsent(OPENING_EQUITY_ACCOUNT, AccountType.Equity)
        val postings = if (type == AccountType.Liability) {
            listOf(PostingLine(accountId, credit = amount), PostingLine(equityId, debit = amount))
        } else {
            listOf(PostingLine(accountId, debit = amount), PostingLine(equityId, credit = amount))
        }
        repo.postTransaction(date = date, payee = "Opening balance: $name", postings = postings)
    }
}

/** Add a bank account, cash wallet, credit card or loan, with what it holds (or owes) today. */
@Composable
fun AddAccountScreen(
    draft: NewAccountDraft,
    error: String?,
    isSaving: Boolean,
    onChange: (NewAccountDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val owed = draft.kind.toLedgerType() == AccountType.Liability
    val isCard = draft.kind == AccountKind.CreditCard
    val cardReady = !isCard || (parseRupees(draft.creditLimit)?.value.let { it != null && it > 0 } && draft.statementDay.trim().toIntOrNull() in 1..28)
    val canSave = draft.name.isNotBlank() && isValidOptionalAmount(draft.openingBalance) && cardReady && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Add account", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("preview") {
                // A live preview, so the card you are adding looks like it will in the stack.
                val limit = parseRupees(draft.creditLimit)
                val owedOrHeld = parseRupees(draft.openingBalance) ?: com.moneymatters.app.core.money.Paise.Zero
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(176.dp).clip(MoneyTheme.shapes.xl)) {
                    AccountCardFace(
                        finish = draft.kind.finish(),
                        typeLabel = draft.kind.label(),
                        name = draft.name.ifBlank { if (isCard) "Card name" else "Account name" },
                        amountLabel = if (owed) "Owed" else "Balance",
                        amount = owedOrHeld,
                        footLeft = if (isCard) (limit?.let { "Limit ${it.formatInr()}" } ?: "Limit") else "",
                        footRight = if (isCard && draft.statementDay.isNotBlank()) "Statement ${draft.statementDay}" else "",
                        glyph = draft.kind.glyph(),
                    )
                }
            }
            item("fields") {
                Module {
                    SegmentedControl(AddableKinds, draft.kind, { onChange(draft.copy(kind = it)) }, { it.shortLabel() })
                    Column(Modifier.padding(top = 12.dp)) {
                        FormField(
                            if (draft.kind == AccountKind.CreditCard) "Card name" else "Account name", draft.name, { onChange(draft.copy(name = it)) },
                            placeholder = if (draft.kind == AccountKind.CreditCard) "e.g. HDFC Regalia" else "e.g. HDFC Bank", testTag = "account-name-field",
                        )
                    }
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            if (owed) "Amount owed today (₹)" else "Balance today (₹)", draft.openingBalance, { onChange(draft.copy(openingBalance = it)) },
                            placeholder = "0", keyboardType = KeyboardType.Decimal,
                            error = if (isValidOptionalAmount(draft.openingBalance)) null else "Enter an amount like 12,500.50",
                            testTag = "opening-balance-field",
                        )
                    }
                    if (isCard) {
                        Column(Modifier.padding(top = 10.dp)) {
                            FormField("Credit limit (₹)", draft.creditLimit, { onChange(draft.copy(creditLimit = it)) }, placeholder = "e.g. 1,00,000", keyboardType = KeyboardType.Decimal, testTag = "card-limit-field")
                        }
                        Column(Modifier.padding(top = 10.dp)) {
                            FormField(
                                "Statement day of the month", draft.statementDay, { onChange(draft.copy(statementDay = it.filter(Char::isDigit).take(2))) },
                                placeholder = "1 to 28", keyboardType = KeyboardType.Number, testTag = "card-statement-field",
                                error = if (draft.statementDay.isNotEmpty() && draft.statementDay.toIntOrNull() !in 1..28) "Pick a day from 1 to 28" else null,
                            )
                        }
                    }
                }
            }
            error?.let { item("error") { Text(it, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) } }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (isSaving) "Saving…" else "Save account",
                onClick = { if (canSave) onSave() },
                style = PillStyle.Primary,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f),
            )
        }
    }
}
