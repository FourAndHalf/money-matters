package com.moneymatters.app.feature.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.dashboard.BudgetPace
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.format.DateTimeFormatter

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")

internal fun BudgetPeriod.label() = when (this) {
    BudgetPeriod.Weekly -> "Weekly"
    BudgetPeriod.Monthly -> "Monthly"
    BudgetPeriod.Annual -> "Yearly"
}

/** Budgets: each category's spending against its budget, and the form to set one. */
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val form = viewModel.form
    if (form != null) {
        BackHandler { viewModel.cancel() }
        BudgetFormContent(form, categories, viewModel.error, { viewModel.form = it }, viewModel::save, viewModel::delete, viewModel::cancel, modifier)
    } else {
        BackHandler(onBack = onBack)
        BudgetsListContent(state, onNew = viewModel::startNew, onOpen = viewModel::startEdit, onBack = onBack, modifier = modifier)
    }
}

@Composable
internal fun BudgetsListContent(state: BudgetsState, onNew: () -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Budgets", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (state.hasMonthly) item("summary") {
                Module {
                    Text("This month", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    Text(
                        "${state.monthlySpent.formatInr()} of ${state.monthlyBudget.formatInr()}",
                        style = MoneyTheme.type.bodyLg, color = MoneyTheme.colors.ink, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item("budgets") {
                Module {
                    ModuleHeader("Categories", subtitle = "${state.rows.size} budgeted", action = { Pill("+ New", onNew, height = 28.dp) })
                    if (state.rows.isEmpty()) {
                        Text(
                            "Set a budget for a category to see how your spending is pacing against it.",
                            style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    state.rows.forEach { row -> BudgetRowView(row, onClick = { onOpen(row.id) }) }
                }
            }
        }
    }
}

@Composable
private fun BudgetRowView(row: BudgetRow, onClick: () -> Unit) {
    val c = MoneyTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.category, style = MoneyTheme.type.bodyLg, color = c.ink)
                Text("${row.period.label()} · ${row.range.start.format(DayMonth)} – ${row.range.end.format(DayMonth)}", style = MoneyTheme.type.bodySm, color = c.muted)
            }
            when {
                row.isOver -> StatusBadge("Over", Tone.Negative)
                row.pace == BudgetPace.OverPace -> StatusBadge("Ahead of pace", Tone.Negative)
                row.pace == BudgetPace.Watch -> StatusBadge("Watch", Tone.Warning)
            }
        }
        ProgressTrack(row.usedFraction, Modifier.padding(top = 8.dp), pace = row.elapsedFraction, over = row.isOver)
        Text(
            "${row.spent.formatInr()} of ${row.budget.formatInr()} · " + if (row.isOver) "over by ${(row.spent - row.budget).formatInr()}" else "${row.left.formatInr()} left",
            style = MoneyTheme.type.bodySm, color = if (row.isOver) c.negative else c.muted, modifier = Modifier.padding(top = 2.dp),
        )
        row.runsOutOn?.let {
            Text("At this pace it runs out on ${it.format(DayMonth)}", style = MoneyTheme.type.bodySm, color = c.muted)
        }
    }
}

@Composable
internal fun BudgetFormContent(
    form: BudgetForm,
    categories: List<AccountEntity>,
    error: String?,
    onChange: (BudgetForm) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = if (form.editingId != null) "Edit budget" else "New budget", onBack = onClose)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("category") {
                Module {
                    Text("Category", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    if (categories.isEmpty()) {
                        Text("Add an expense category first.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { a ->
                                Pill(categoryLabel(a.name), { if (form.editingId == null) onChange(form.copy(accountId = a.id)) }, style = if (form.accountId == a.id) PillStyle.Primary else PillStyle.Secondary)
                            }
                        }
                    }
                }
            }
            item("amount") {
                Module {
                    FormField("Budget (₹)", form.amountText, { onChange(form.copy(amountText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 15,000")
                    Text("Repeats", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 14.dp))
                    Column(Modifier.padding(top = 8.dp)) {
                        SegmentedControl(BudgetPeriod.entries, form.period, { onChange(form.copy(period = it)) }, { it.label() })
                    }
                    Text(
                        "It applies to every period, and sub-categories count towards it. Monthly follows your month start day; yearly is April to March.",
                        style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (form.editingId != null) item("delete") {
                Module { Pill("Delete budget", { onDelete(form.editingId) }, style = PillStyle.Destructive) }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (form.editingId != null) "Save changes" else "Set budget", { if (form.canSave) onSave() },
                style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f),
            )
        }
    }
}
