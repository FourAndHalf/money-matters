package com.moneymatters.app.feature.close

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.close.CheckFix
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme

/** Month-end close (P17): live checklist with a fix for each failing item, balance confirmation, the typed close, and history. */
@Composable
fun CloseScreen(
    state: CloseState,
    typedName: String,
    error: String?,
    onTypedName: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: (accountId: String) -> Unit,
    onConfirmAll: () -> Unit,
    onReconcile: () -> Unit,
    onPostInterest: () -> Unit,
    onOpenPrices: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBookRecurring: () -> Unit = {},
    onConfirmUnits: () -> Unit = {},
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val period = state.period
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Month-end close", subtitle = period?.label, onBack = onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (period == null) {
                item("none") { Module { ModuleHeader("Nothing to close", subtitle = state.nextLabel) } }
            } else {
                item("checklist") {
                    Module {
                        ModuleHeader("Checklist", subtitle = "Everything must pass before ${period.label} can close")
                        state.items.forEachIndexed { i, item ->
                            ListRow(
                                title = item.title,
                                subtitle = item.detail,
                                divider = i < state.items.lastIndex,
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (!item.ok) when (item.fix) {
                                            CheckFix.Prices -> Pill("Prices", onOpenPrices, height = 26.dp)
                                            CheckFix.Interest -> Pill("Post", onPostInterest, height = 26.dp)
                                            CheckFix.Reconcile -> Pill("Reconcile", onReconcile, height = 26.dp)
                                            CheckFix.Confirm -> Pill("Confirm all", onConfirmAll, height = 26.dp)
                                            CheckFix.Recurring -> Pill("Book now", onBookRecurring, height = 26.dp)
                                            CheckFix.Units -> Pill("Confirm", onConfirmUnits, height = 26.dp)
                                            CheckFix.Drafts, CheckFix.None -> Unit
                                        }
                                        if (item.ok) StatusBadge("✓ Done", Tone.Positive) else StatusBadge("✕ To do", Tone.Warning)
                                    }
                                },
                            )
                        }
                    }
                }
                state.report?.let { report -> item("report") { MonthEndReportModule(report) } }
                if (state.balances.isNotEmpty()) {
                    item("balances") {
                        Module {
                            ModuleHeader("Balances at month end", subtitle = "Check each against your statement")
                            state.balances.forEachIndexed { i, row ->
                                ListRow(
                                    title = row.line.name,
                                    subtitle = row.line.balance.formatInr(showPaise = true),
                                    divider = i < state.balances.lastIndex,
                                    trailing = { if (row.confirmed) StatusBadge("✓ Confirmed", Tone.Positive) else Pill("Confirm", { onConfirm(row.line.accountId) }, height = 26.dp) },
                                )
                            }
                        }
                    }
                }
                item("close") {
                    Module {
                        ModuleHeader("Close ${period.label}", subtitle = "Irreversible. Entries lock and the month joins the hash chain. Type ${period.label} to confirm.")
                        FormField("Month name", typedName, onTypedName, placeholder = period.label, testTag = "close-name")
                        error?.let { StatusBadge(it, Tone.Negative, modifier = Modifier.padding(top = 10.dp), height = 24.dp) }
                        Row(Modifier.padding(top = 12.dp)) {
                            if (state.canClose && typedName.trim().equals(period.label, ignoreCase = true)) Pill("Close ${period.label}", onClose, style = PillStyle.Primary)
                            else Text(if (state.canClose) "Type the month name to enable closing" else "Finish the checklist first", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                        }
                    }
                }
            }
            if (state.history.isNotEmpty()) {
                item("history") {
                    Module {
                        ModuleHeader("Closed months", subtitle = "Each hash covers the month's entries and the one before it")
                        state.history.forEachIndexed { i, row ->
                            ListRow(
                                title = row.label,
                                subtitle = row.problem ?: "${row.rangeLabel} · ${row.shortHash}",
                                divider = i < state.history.lastIndex,
                                trailing = { if (row.ok) StatusBadge("✓ Intact", Tone.Positive) else StatusBadge("✕ Changed", Tone.Negative) },
                            )
                        }
                    }
                }
            }
        }
    }
}
