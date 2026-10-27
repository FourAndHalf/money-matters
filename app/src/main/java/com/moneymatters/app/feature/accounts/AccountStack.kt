package com.moneymatters.app.feature.accounts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.books.label
import com.moneymatters.app.feature.books.ordinal
import com.moneymatters.app.ui.components.AccountCardFace
import com.moneymatters.app.ui.components.CardFinish
import com.moneymatters.app.ui.components.CardStack
import com.moneymatters.app.ui.components.Gauge
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.StackDots
import com.moneymatters.app.ui.theme.MoneyTheme

internal fun AccountKind.finish() = when (this) {
    AccountKind.Bank -> CardFinish.Graphite
    AccountKind.Cash -> CardFinish.Champagne
    AccountKind.CreditCard -> CardFinish.Pearl
    AccountKind.Loan -> CardFinish.Smoke
    AccountKind.FixedDeposit, AccountKind.Investment -> CardFinish.Silver
}

/** Bank and card faces carry a chip; the rest a small glyph for what they hold. */
internal fun AccountKind.glyph(): String? = when (this) {
    AccountKind.Bank, AccountKind.CreditCard -> null
    AccountKind.Cash -> "₹"
    AccountKind.Loan -> "%"
    AccountKind.FixedDeposit -> "◈"
    AccountKind.Investment -> "↗"
}

private fun AccountSummary.amountLabel() = when {
    isLiability -> "Owed"
    kind == AccountKind.FixedDeposit -> "Value"
    kind == AccountKind.Investment -> "Market value"
    else -> "Balance"
}

/** Share of the limit in use, 0 to 1; null for anything but a card with a limit. */
internal fun AccountSummary.utilisation(): Float? = cardLimit?.takeIf { it.value > 0 }?.let { (balance.value.toFloat() / it.value).coerceIn(0f, 1f) }

/**
 * The account stack: swipe, tap the peeking edge or use the dots to bring another account to the front, and
 * the panel under it shows that account's facts with a way into its ledger. [onShown] fires with the account at the front.
 * A pin sits in each card's top-right corner when [onTogglePin] is given.
 */
@Composable
fun AccountStack(accounts: List<AccountSummary>, onOpenAccount: (accountId: String) -> Unit, modifier: Modifier = Modifier, onEditCard: (AccountSummary) -> Unit = {}, onShown: (AccountSummary) -> Unit = {}, onTogglePin: ((AccountSummary) -> Unit)? = null) {
    if (accounts.isEmpty()) return
    var front by rememberSaveable { mutableIntStateOf(0) }
    val index = front.coerceIn(0, accounts.lastIndex)
    // Tells the caller which account is up front, so the list below can open the group it belongs to.
    LaunchedEffect(accounts[index].id) { onShown(accounts[index]) }
    Column(modifier) {
        CardStack(
            items = accounts,
            frontIndex = index,
            onFrontIndexChange = { front = it },
            cardLabel = { "${it.name}, ${it.amountLabel()} ${it.balance.formatInr()}" },
            cardHeight = 176.dp,
        ) { account ->
            val util = account.utilisation()
            AccountCardFace(
                finish = account.kind.finish(),
                typeLabel = account.kind.label(),
                name = account.name,
                amountLabel = account.amountLabel(),
                amount = account.balance,
                footLeft = account.cardLimit?.let { "Limit ${it.formatInr()}" } ?: if (account.entryCount == 0) "No entries yet" else "${account.entryCount} ${if (account.entryCount == 1) "entry" else "entries"}",
                footRight = util?.let { "${(it * 100).toInt()}% used" } ?: "",
                glyph = account.kind.glyph(),
                pinned = account.isPinned,
                onTogglePin = onTogglePin?.let { toggle -> { toggle(account) } },
            )
        }
        if (accounts.size > 1) {
            StackDots(
                count = accounts.size,
                selected = index,
                onSelect = { front = it },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 10.dp),
            )
        }
        Module(Modifier.padding(top = if (accounts.size > 1) 0.dp else 10.dp)) {
            AnimatedContent(
                targetState = accounts[index],
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(140)) },
                label = "account-panel",
            ) { account -> AccountPanel(account, onOpen = { onOpenAccount(account.id) }, onEditCard = { onEditCard(account) }) }
        }
    }
}

@Composable
private fun AccountPanel(account: AccountSummary, onOpen: () -> Unit, onEditCard: () -> Unit) {
    val util = account.utilisation()
    Column {
        ModuleHeader(
            account.name, subtitle = account.kind.label(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (account.kind == AccountKind.CreditCard) Pill(if (account.cardLimit == null) "Add limit" else "Edit card", onEditCard)
                    Pill("Open ledger", onOpen)
                }
            },
        )
        if (util != null && account.cardLimit != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Gauge(util, "of ${account.cardLimit.formatInr()}")
                Column(Modifier.weight(1f)) {
                    KeyValueRow("Owed", account.balance.formatInr())
                    KeyValueRow("Available", (account.cardLimit - account.balance).coerceAtLeast(Paise.Zero).formatInr())
                    account.statementDay?.let { KeyValueRow("Statement", "${ordinal(it)} of the month", divider = false) }
                }
            }
        } else {
            KeyValueRow(if (account.isLiability) "Owed" else "Balance", account.balance.formatInr())
            KeyValueRow("Entries", account.entryCount.toString(), divider = false)
        }
    }
}

private fun Paise.coerceAtLeast(min: Paise) = if (value < min.value) min else this
