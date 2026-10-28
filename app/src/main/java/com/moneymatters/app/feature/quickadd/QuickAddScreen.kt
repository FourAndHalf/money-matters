package com.moneymatters.app.feature.quickadd

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PlanKind
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.books.displayName
import com.moneymatters.app.feature.books.label
import com.moneymatters.app.ui.components.CollapsibleBody
import com.moneymatters.app.ui.components.CollapsibleModuleHeader
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.DateField
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.PostingLineRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.SearchField
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.components.evaluateAmountExpression
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.format.DateTimeFormatter

private sealed interface PickerTarget {
    data object To : PickerTarget
    data object From : PickerTarget
    data class Split(val index: Int) : PickerTarget
}

/**
 * Quick Add (P04): pick a type, an amount, who it's with and which accounts move, tag it, and
 * save as a draft or post it straight to the real ledger. Opened full-screen from the app's
 * Quick Add FAB, the same way account and transaction detail are opened.
 */
@Composable
fun QuickAddScreen(
    viewModel: QuickAddViewModel,
    prefs: QuickAddPrefs,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val form = viewModel.form
    fun update(block: (QuickAddForm) -> QuickAddForm) {
        viewModel.form = block(viewModel.form)
    }

    // Stock holdings are traded from the Invest tab, which records units and lots; Quick Add would leave them out of step.
    val accounts = snapshot?.accounts.orEmpty().filterNot { it.isArchived || it.id in snapshot?.holdingAccountIds.orEmpty() }
    val accountsById = accounts.associateBy(AccountEntity::id)
    fun accountLabel(id: String?) = id?.let { accountsById[it]?.displayName() } ?: "Choose"

    var amountExpression by rememberSaveable { mutableStateOf("") }
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var amountFocused by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var pickerQuery by rememberSaveable { mutableStateOf("") }
    var splitMode by rememberSaveable(form.kind) { mutableStateOf(form.splits.isNotEmpty()) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showJournal by rememberSaveable(form.kind) { mutableStateOf(prefs.shouldAutoShowPreview(form.kind)) }

    val postings = postingsFor(form)
    val balanced = isBalanced(postings)
    val suggestions = snapshot?.let { payeeSuggestions(it, form.payee) }.orEmpty().take(5)
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()

    // imePadding: the app draws edge to edge, so without it the keyboard would cover the amount buttons.
    Column(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Quick add", subtitle = form.kind.label, onBack = onClose)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
        ) {
            item("kind") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryKind.entries.forEach { kind ->
                        Pill(
                            kind.label,
                            onClick = { update { it.copy(kind = kind, toAccountId = null, fromAccountId = null) } },
                            style = if (kind == form.kind) PillStyle.Primary else PillStyle.Secondary,
                        )
                    }
                }
            }
            item("amount") {
                val result = evaluateAmountExpression(amountExpression)?.takeIf { it.value > 0 }
                fun applyAmount() {
                    if (result != null) {
                        update { it.copy(amount = result) }
                        amountExpression = ""
                        focusManager.clearFocus()
                    } else if (amountExpression.isBlank()) {
                        focusManager.clearFocus()
                    }
                }
                // Zero card padding so the tap area is the whole card; the padding moves inside the clickable Column.
                Module(contentPadding = 0.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            // Focus is asked for here, inside the tap itself, so the system treats the keyboard as user-requested.
                            .clickable(role = Role.Button) {
                                amountFocus.requestFocus()
                                keyboard?.show()
                            }
                            .padding(MoneyTheme.spacing.cardPadding),
                    ) {
                        Text("Amount", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                        MoneyText(form.amount, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp))
                        // Always on the card (never added after the tap), so there is always something to focus. Type a number
                        // or a sum; the operator pills cover keyboards without + − × ÷.
                        FormField(
                            "Type an amount, or a sum like 120+45×2",
                            amountExpression,
                            onValueChange = { amountExpression = it },
                            modifier = Modifier.padding(top = 14.dp),
                            placeholder = "0",
                            keyboardType = KeyboardType.Decimal,
                            focusRequester = amountFocus,
                            onDone = ::applyAmount,
                            onFocusChange = { amountFocused = it },
                        )
                        if (amountFocused) {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("+", "−", "×", "÷").forEach { op ->
                                    Pill(
                                        op,
                                        { if (amountExpression.isNotEmpty() && amountExpression.last() !in "+−×÷") amountExpression += op },
                                        modifier = Modifier.weight(1f),
                                        height = 40.dp,
                                    )
                                }
                            }
                            if (result != null && amountExpression.any { it in "+−×÷-*/xX" }) {
                                Text("= ${result.formatInr(showPaise = result.value % 100 != 0L)}", style = MoneyTheme.type.metricCard, color = MoneyTheme.colors.muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("Cancel", { amountExpression = ""; focusManager.clearFocus() }, modifier = Modifier.weight(1f), height = 44.dp)
                                Pill(
                                    "Done",
                                    ::applyAmount,
                                    style = PillStyle.Primary,
                                    modifier = Modifier.weight(1f).alpha(if (result != null || amountExpression.isBlank()) 1f else 0.4f),
                                    height = 44.dp,
                                )
                            }
                        }
                    }
                }
            }
            item("payee") {
                Module {
                    FormField(
                        "Payee",
                        form.payee,
                        onValueChange = { update { f -> f.copy(payee = it) } },
                        placeholder = "e.g. Swiggy",
                    )
                    if (suggestions.isNotEmpty()) {
                        Column(Modifier.padding(top = 6.dp)) {
                            suggestions.forEachIndexed { i, memory ->
                                ListRow(
                                    title = memory.payee,
                                    subtitle = accountLabel(memory.toAccountId) + " · " + (memory.paymentMethod?.label() ?: "—"),
                                    divider = i < suggestions.lastIndex,
                                    modifier = Modifier.clickable {
                                        update {
                                            it.copy(
                                                payee = memory.payee,
                                                toAccountId = memory.toAccountId ?: it.toAccountId,
                                                fromAccountId = memory.fromAccountId ?: it.fromAccountId,
                                                paymentMethod = memory.paymentMethod ?: it.paymentMethod,
                                                decision = memory.decision ?: it.decision,
                                                plan = memory.plan ?: it.plan,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (!splitMode) {
                item("accounts") {
                    Module {
                        ListRow(
                            title = accountLabel(form.toAccountId),
                            subtitle = form.kind.toLabel,
                            modifier = Modifier.clickable { pickerTarget = PickerTarget.To; pickerQuery = "" },
                        )
                        ListRow(
                            title = accountLabel(form.fromAccountId),
                            subtitle = form.kind.fromLabel,
                            divider = false,
                            modifier = Modifier.clickable { pickerTarget = PickerTarget.From; pickerQuery = "" },
                        )
                        pickerTarget?.takeIf { it !is PickerTarget.Split }?.let { target ->
                            AccountPicker(
                                accounts = accounts.filter { if (target == PickerTarget.To) form.kind.matchesTo(it) else form.kind.matchesFrom(it) },
                                query = pickerQuery,
                                onQueryChange = { pickerQuery = it },
                                modifier = Modifier.padding(top = 10.dp),
                            ) { account ->
                                update { if (target == PickerTarget.To) it.copy(toAccountId = account.id) else it.copy(fromAccountId = account.id) }
                                pickerTarget = null
                            }
                        }
                    }
                }
            }
            item("details") {
                Module {
                    CollapsibleModuleHeader(
                        "Details",
                        expanded = showDetails,
                        onToggle = { showDetails = !showDetails },
                        subtitle = detailsSummary(form),
                    )
                    CollapsibleBody(showDetails) {
                        Column {
                            DateField("Date", form.date, { date -> update { it.copy(date = date) } })
                            FormField("Note", form.note, onValueChange = { update { f -> f.copy(note = it) } }, modifier = Modifier.padding(top = 12.dp), placeholder = "Optional")
                            TagRow("Payment method", PaymentMethod.entries, form.paymentMethod, PaymentMethod::label, modifier = Modifier.padding(top = 16.dp)) { update { f -> f.copy(paymentMethod = it) } }
                            TagRow("Need / want", DecisionKind.entries, form.decision, { it.name }, modifier = Modifier.padding(top = 12.dp)) { update { f -> f.copy(decision = it) } }
                            TagRow("Planned", PlanKind.entries, form.plan, { it.name }, modifier = Modifier.padding(top = 12.dp)) { update { f -> f.copy(plan = it) } }
                        }
                    }
                }
            }
            item("journal") {
                Module {
                    CollapsibleModuleHeader(
                        "Journal",
                        expanded = showJournal,
                        onToggle = { if (!splitMode) showJournal = !showJournal },
                        subtitle = if (splitMode) "Split into ${form.splits.size} postings" else "Postings",
                        action = { StatusBadge(if (balanced) "✓ Balanced" else "Not balanced yet", if (balanced) Tone.Positive else Tone.Negative) },
                    )
                    CollapsibleBody(showJournal) {
                        Column {
                            if (splitMode) {
                                InnerPanel {
                                    form.splits.forEachIndexed { index, line ->
                                        Column(Modifier.padding(vertical = 6.dp)) {
                                            ListRow(
                                                title = accountLabel(line.accountId),
                                                subtitle = "Every line needs an account and one of Dr/Cr",
                                                divider = false,
                                                modifier = Modifier.clickable { pickerTarget = PickerTarget.Split(index); pickerQuery = "" },
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FormField("Dr", line.debitText, onValueChange = { text -> update { f -> f.copy(splits = f.splits.replaceAt(index) { it.copy(debitText = text, creditText = "") }) } }, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                                                FormField("Cr", line.creditText, onValueChange = { text -> update { f -> f.copy(splits = f.splits.replaceAt(index) { it.copy(creditText = text, debitText = "") }) } }, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                                                if (form.splits.size > 2) {
                                                    Pill("✕", onClick = { update { f -> f.copy(splits = f.splits.filterIndexed { i, _ -> i != index }) } }, height = 40.dp)
                                                }
                                            }
                                        }
                                        if (index < form.splits.lastIndex) RowDivider()
                                    }
                                }
                                pickerTarget?.let { it as? PickerTarget.Split }?.let { target ->
                                    AccountPicker(
                                        accounts = accounts,
                                        query = pickerQuery,
                                        onQueryChange = { pickerQuery = it },
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) { account ->
                                        update { f -> f.copy(splits = f.splits.replaceAt(target.index) { it.copy(accountId = account.id) }) }
                                        pickerTarget = null
                                    }
                                }
                            } else {
                                InnerPanel {
                                    postings.forEachIndexed { i, posting ->
                                        PostingLineRow(accountLabel(posting.accountId), posting.debit, posting.credit, Modifier.padding(vertical = 5.dp))
                                        if (i < postings.lastIndex) RowDivider(Modifier.padding(vertical = 2.dp))
                                    }
                                }
                            }
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(
                                    if (splitMode) "Remove split" else "Split",
                                    onClick = {
                                        splitMode = !splitMode
                                        pickerTarget = null
                                        showJournal = true
                                        update { it.copy(splits = if (splitMode) listOf(SplitLine(), SplitLine()) else emptyList()) }
                                    },
                                )
                                if (splitMode) Pill("+ Add line", onClick = { update { it.copy(splits = it.splits + SplitLine()) } })
                            }
                        }
                    }
                }
            }
            if (viewModel.saveFailed) {
                item("error") { Text("Couldn't save — try again.", color = MoneyTheme.colors.negative, style = MoneyTheme.type.bodySm) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val canSave = form.payee.isNotBlank() && balanced && !viewModel.isSaving
            Pill(
                "Save as draft",
                onClick = { viewModel.save(asDraft = true) { prefs.recordSave(form.kind); onClose() } },
                modifier = Modifier.weight(1f).alpha(if (canSave) 1f else 0.4f),
                height = 48.dp,
            )
            Pill(
                "Post",
                onClick = { viewModel.save(asDraft = false) { prefs.recordSave(form.kind); onClose() } },
                style = PillStyle.Primary,
                modifier = Modifier.weight(1f).alpha(if (canSave) 1f else 0.4f),
                height = 48.dp,
            )
        }
    }
}

/** One-line summary shown on the collapsed Details header, so the tags stay visible without opening it. */
private fun detailsSummary(form: QuickAddForm): String = listOfNotNull(
    form.date.format(DateTimeFormatter.ofPattern("d MMM")),
    form.paymentMethod?.label(),
    form.decision?.name,
    form.plan?.name,
).joinToString(" · ")

/** Inline search-and-pick list for an account; the caller decides what a pick means. */
@Composable
private fun AccountPicker(
    accounts: List<AccountEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPick: (AccountEntity) -> Unit,
) {
    val filtered = accounts.filter { it.displayName().contains(query, ignoreCase = true) }
    InnerPanel(modifier) {
        SearchField(query, onQueryChange, placeholder = "Search accounts")
        Column(Modifier.padding(top = 6.dp)) {
            filtered.forEachIndexed { i, account ->
                ListRow(account.displayName(), divider = i < filtered.lastIndex, modifier = Modifier.clickable { onPick(account) })
            }
        }
    }
}

private fun List<SplitLine>.replaceAt(index: Int, block: (SplitLine) -> SplitLine): List<SplitLine> =
    mapIndexed { i, line -> if (i == index) block(line) else line }

@Composable
private fun <T> TagRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T?) -> Unit,
) {
    Column(modifier) {
        Text(label, style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Pill(
                    optionLabel(option),
                    onClick = { onSelect(if (isSelected) null else option) },
                    style = if (isSelected) PillStyle.Primary else PillStyle.Secondary,
                )
            }
        }
    }
}
