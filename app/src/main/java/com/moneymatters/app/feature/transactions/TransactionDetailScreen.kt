package com.moneymatters.app.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * Transaction detail (P07): every field, the decision tags and reason, the journal view with a
 * Balanced badge, the audit history, and the one action the status allows.
 */
@Composable
fun TransactionDetailScreen(
    detail: TransactionDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onReverse: () -> Unit = {},
    onPriorPeriodAdjustment: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    var confirmingReverse by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding() + contentPadding.calculateTopPadding())) {
            DetailTopBar(title = detail.payee, subtitle = "${detail.dateLabel} · ${detail.timeLabel}", onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("hero") { Column(Modifier.padding(horizontal = margin)) { AmountHero(detail) } }
            if (detail.reason != null) {
                item("reason") { Column(Modifier.padding(horizontal = margin)) { ReasonPanel(detail.reason) } }
            }
            item("journal") { Column(Modifier.padding(horizontal = margin)) { JournalModule(detail) } }
            item("audit") { Column(Modifier.padding(horizontal = margin)) { AuditModule(detail.audit) } }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = 14.dp),
        ) {
            when (detail.status.primaryAction()) {
                TxnAction.Edit -> Pill("Edit", onEdit, style = PillStyle.Primary, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                TxnAction.Reverse -> Pill("Reverse", { confirmingReverse = true }, style = PillStyle.Destructive, modifier = Modifier.fillMaxWidth(), height = 48.dp)
                TxnAction.PriorPeriodAdjustment -> Pill("Post prior-period adjustment", onPriorPeriodAdjustment, style = PillStyle.Primary, modifier = Modifier.fillMaxWidth(), height = 48.dp)
            }
        }
    }

    if (confirmingReverse) {
        AlertDialog(
            onDismissRequest = { confirmingReverse = false },
            title = { Text("Reverse this transaction?") },
            text = { Text("This posts an equal and opposite entry. The original stays in the books, exactly as reconciled.") },
            confirmButton = {
                TextButton(onClick = { confirmingReverse = false; onReverse() }) { Text("Confirm reverse") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReverse = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AmountHero(detail: TransactionDetail) {
    val c = MoneyTheme.colors
    Module(shape = MoneyTheme.shapes.xl) {
        Text("${detail.category} · ${detail.accountName}", style = MoneyTheme.type.bodySm, color = c.muted)
        MoneyText(detail.amount, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.status.look()?.let { (text, tone, icon) -> StatusBadge(text, tone, icon = icon) }
            detail.decision?.let { StatusBadge(it.name.lowercase().replaceFirstChar(Char::uppercase), Tone.Neutral) }
            detail.plan?.let { StatusBadge(it.name.lowercase().replaceFirstChar(Char::uppercase), Tone.Neutral) }
        }
    }
}

@Composable
private fun ReasonPanel(reason: String) {
    Module {
        Text("Reason", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
        Text(reason, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun JournalModule(detail: TransactionDetail) {
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Journal", subtitle = "Postings")
        InnerPanel {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Account", style = MoneyTheme.type.labelUppercase, color = c.muted, modifier = Modifier.weight(1.8f))
                Text("Dr", style = MoneyTheme.type.labelUppercase, color = c.muted, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                Text("Cr", style = MoneyTheme.type.labelUppercase, color = c.muted, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
            }
            detail.postings.forEachIndexed { i, posting ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(posting.account, style = MoneyTheme.type.ledgerMono, color = c.ink, modifier = Modifier.weight(1.8f))
                    Text(posting.debit?.formatInr() ?: "", style = MoneyTheme.type.ledgerMono, color = c.ink, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                    Text(posting.credit?.formatInr() ?: "", style = MoneyTheme.type.ledgerMono, color = c.ink, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                }
                if (i < detail.postings.lastIndex) RowDivider(Modifier.padding(vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            if (detail.isBalanced) StatusBadge("✓ Balanced", Tone.Positive) else StatusBadge("Unbalanced", Tone.Negative)
        }
    }
}

@Composable
private fun AuditModule(audit: List<AuditEntry>) {
    if (audit.isEmpty()) return
    val c = MoneyTheme.colors
    Module {
        ModuleHeader("Audit history", subtitle = "Every change, before and after")
        audit.forEachIndexed { i, entry ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(entry.whenLabel, style = MoneyTheme.type.labelUppercase, color = c.muted)
                Text(entry.summary, style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.padding(top = 2.dp))
                if (entry.before != null && entry.after != null) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.before, style = MoneyTheme.type.ledgerMono, color = c.muted)
                        Text("→", style = MoneyTheme.type.ledgerMono, color = c.muted)
                        Text(entry.after, style = MoneyTheme.type.ledgerMono, color = c.ink)
                    }
                }
            }
            if (i < audit.lastIndex) RowDivider()
        }
    }
}

@Preview(widthDp = 392, heightDp = 1400, showBackground = true, backgroundColor = 0xFFE8E8EA)
@Composable
private fun TransactionDetailScreenPreview() {
    MoneyMattersTheme { TransactionDetailScreen(SampleTransactions.byId.getValue("t2"), onBack = {}) }
}
