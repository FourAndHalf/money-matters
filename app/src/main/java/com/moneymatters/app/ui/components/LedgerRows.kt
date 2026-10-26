package com.moneymatters.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme
import kotlin.math.abs

/** The account tree's row (P08, Home): kind chip, name over subtitle, balance with an optional badge. */
@Composable
fun AccountRow(
    name: String,
    subtitle: String,
    kind: AccountKind,
    balance: Paise,
    isLiability: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    val amountText = if (isLiability) "−${balance.formatInr()}" else balance.formatInr()
    ListRow(
        title = name,
        subtitle = subtitle,
        divider = divider,
        modifier = modifier.clickable(role = Role.Button, onClickLabel = "Open $name ledger", onClick = onClick),
        leading = { GlyphChip(icon = kind.icon(), text = kind.glyph()) },
        trailing = { AmountWithBadge(amountText, badge) },
    )
}

private fun AccountKind.icon() = when (this) {
    AccountKind.Bank -> MoneyIcons.Bank
    AccountKind.Cash -> null
    AccountKind.CreditCard -> MoneyIcons.Accounts
    AccountKind.Loan -> MoneyIcons.Percent
    AccountKind.FixedDeposit -> null
    AccountKind.Investment -> MoneyIcons.Invest
}

private fun AccountKind.glyph() = when (this) {
    AccountKind.Cash -> "₹"
    AccountKind.FixedDeposit -> "◈"
    else -> null
}

/** Money out reads red, money in green, and a move between your own accounts (or nothing at all) stays the neutral ink. */
@Composable
fun amountColor(amount: Paise, isTransfer: Boolean = false): Color {
    val c = MoneyTheme.colors
    return when {
        isTransfer || amount.value == 0L -> c.ink
        amount.value < 0 -> c.negative
        else -> c.positive
    }
}

/** How much moved, with no sign: the colour from [amountColor] says which way. */
fun amountText(amount: Paise): String = Paise(abs(amount.value)).formatInr()

/** A transaction in a list (P06, Home): payee chip, payee over subtitle, signed amount with an optional status badge. */
@Composable
fun TransactionRow(
    payee: String,
    subtitle: String,
    chip: String,
    amount: Paise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    isTransfer: Boolean = false,
) {
    ListRow(
        title = payee,
        subtitle = subtitle,
        divider = divider,
        modifier = modifier.clickable(role = Role.Button, onClickLabel = "Open $payee", onClick = onClick),
        leading = { GlyphChip(text = chip) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(amountText(amount), style = MoneyTheme.type.metricTable, color = amountColor(amount, isTransfer), textAlign = TextAlign.End, softWrap = false)
                if (badge != null) Column(Modifier.padding(top = 4.dp)) { badge() }
            }
        },
    )
}

/** Net worth over an assets/liabilities split bar (P08 hero, Home). */
@Composable
fun NetWorthCard(netWorth: Paise, assets: Paise, liabilities: Paise, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val total = (assets.value + liabilities.value).coerceAtLeast(1)
    Module(modifier, shape = MoneyTheme.shapes.xl) {
        Text("Net worth", style = MoneyTheme.type.bodySm, color = c.muted)
        MoneyText(netWorth, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        SegmentBar(
            segments = listOf(
                BarSegment(assets.value.toFloat() / total, c.anchor),
                BarSegment(liabilities.value.toFloat() / total, c.negative),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Assets", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(assets.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink, softWrap = false)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Liabilities", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(liabilities.formatInr(), style = MoneyTheme.type.metricTable, color = c.negative, textAlign = TextAlign.End, softWrap = false)
            }
        }
    }
}

/**
 * One journal leg: the account on the left (it wraps), the amount on the right with `Dr` or `Cr` after it.
 * The amount never wraps, so a long figure can't be split across lines the way a narrow column would.
 */
@Composable
fun PostingLineRow(account: String, debit: Paise?, credit: Paise?, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val amount = debit ?: credit ?: Paise.Zero
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(account, style = MoneyTheme.type.ledgerMono, color = c.ink, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(amount.formatInr(), style = MoneyTheme.type.ledgerMono, color = c.ink, softWrap = false)
            Text(if (debit != null) "Dr" else "Cr", style = MoneyTheme.type.ledgerMono, color = c.muted, softWrap = false)
        }
    }
}
