package com.moneymatters.app.feature.recurring

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
import androidx.compose.foundation.lazy.items
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
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.recurring.Frequency
import com.moneymatters.app.ui.components.DateField
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

internal fun Frequency.label() = when (this) {
    Frequency.Weekly -> "Weekly"
    Frequency.Monthly -> "Monthly"
    Frequency.Yearly -> "Yearly"
}

/** Recurring entries: the schedule, and the form to add or change a rule. */
@Composable
fun RecurringScreen(viewModel: RecurringViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val rows by viewModel.rows.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val form = viewModel.form
    if (form != null) {
        BackHandler { viewModel.cancel() }
        RecurringFormContent(
            form, accounts, viewModel.error,
            onChange = { viewModel.form = it },
            onSave = { viewModel.save {} },
            onDelete = { id -> viewModel.delete(id) {} },
            onClose = viewModel::cancel,
            modifier = modifier,
        )
    } else {
        BackHandler(onBack = onBack)
        RecurringListContent(rows, viewModel.notice, onNew = viewModel::startNew, onOpen = viewModel::startEdit, onToggle = viewModel::setActive, onBack = onBack, modifier = modifier)
    }
}

@Composable
internal fun RecurringListContent(
    rows: List<RecurringRow>,
    notice: String?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Recurring entries", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (notice != null) item("notice") {
                Module { Text("Just booked: $notice", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink) }
            }
            item("rules") {
                Module {
                    ModuleHeader("Schedule", subtitle = "${rows.count { it.isActive }} active", action = { Pill("+ New", onNew, height = 28.dp) })
                    if (rows.isEmpty()) {
                        Text(
                            "Add salary, rent, EMIs and subscriptions once, and they are booked for you on their dates.",
                            style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    rows.sortedWith(compareBy({ !it.isActive }, { it.next ?: LocalDate.MAX }, { it.name.lowercase() })).forEachIndexed { i, row ->
                        ListRow(
                            title = row.name,
                            subtitle = subtitleFor(row),
                            divider = i < rows.lastIndex,
                            modifier = Modifier.clickable { onOpen(row.id) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(row.amount.formatInr(showPaise = true), style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink)
                                    when {
                                        row.needsAttention -> StatusBadge("Account missing", Tone.Warning)
                                        !row.isActive -> StatusBadge("Paused", Tone.Neutral)
                                        !row.autoPost -> StatusBadge("Draft", Tone.Info)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun subtitleFor(row: RecurringRow): String {
    val next = when {
        !row.isActive -> "paused"
        row.next == null -> "ended"
        else -> "next ${row.next.format(DayMonthYear)}"
    }
    return "${row.frequency.label()} · $next · ${row.fromName} → ${row.toName}"
}

@Composable
internal fun RecurringFormContent(
    form: RecurringForm,
    accounts: List<AccountEntity>,
    error: String?,
    onChange: (RecurringForm) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = if (form.editingId != null) "Edit recurring entry" else "New recurring entry", onBack = onClose)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    FormField("Name (becomes the payee)", form.name, { onChange(form.copy(name = it)) }, placeholder = "e.g. Rent")
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField("Amount (₹)", form.amountText, { onChange(form.copy(amountText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 25,000")
                    }
                }
            }
            item("accounts") {
                Module {
                    Text("From (money leaves or is earned here)", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    PillRow(fromChoices(accounts), form.fromId) { onChange(form.copy(fromId = it)) }
                    Text("To (money arrives or is spent on)", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 14.dp))
                    PillRow(toChoices(accounts), form.toId) { onChange(form.copy(toId = it)) }
                    if (form.fromId != null && form.fromId == form.toId) {
                        Text("Choose two different accounts.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.negative, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item("schedule") {
                Module {
                    Text("Repeats", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    Column(Modifier.padding(top = 8.dp)) {
                        SegmentedControl(Frequency.entries, form.frequency, { onChange(form.copy(frequency = it)) }, { it.label() })
                    }
                    Column(Modifier.padding(top = 12.dp)) {
                        DateField("First date", form.start, { onChange(form.copy(start = it)) })
                    }
                    Text("Dates already past are booked as soon as you save.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
                    ListRow("Ends on a date", divider = false, trailing = { Toggle(form.hasEnd, { onChange(form.copy(hasEnd = it, end = if (it) form.end ?: form.start.plusYears(1) else null)) }) })
                    if (form.hasEnd) DateField("Last date", form.end, { onChange(form.copy(end = it)) }, error = form.endError)
                }
            }
            item("posting") {
                Module {
                    ListRow(
                        "Post automatically",
                        subtitle = if (form.autoPost) "Each entry is posted on its date." else "Each entry is saved as a draft for you to confirm.",
                        divider = form.editingId != null,
                        trailing = { Toggle(form.autoPost, { onChange(form.copy(autoPost = it)) }) },
                    )
                    if (form.editingId != null) ListRow(
                        "Active",
                        subtitle = if (form.isActive) "Booking on schedule." else "Paused: nothing is booked until you turn it back on.",
                        divider = false,
                        trailing = { Toggle(form.isActive, { onChange(form.copy(isActive = it)) }) },
                    )
                }
            }
            if (form.editingId != null) item("delete") {
                Module {
                    Pill("Delete rule", { onDelete(form.editingId) }, style = PillStyle.Destructive)
                    Text("Entries it already booked stay in your books.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (form.editingId != null) "Save changes" else "Add rule", { if (form.canSave) onSave() },
                style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f),
            )
        }
    }
}

@Composable
private fun PillRow(choices: List<AccountEntity>, selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { a -> Pill(a.name, { onSelect(a.id) }, style = if (selected == a.id) PillStyle.Primary else PillStyle.Secondary) }
    }
}
