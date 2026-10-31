package com.moneymatters.app.feature.reconcile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.format.DateTimeFormatter

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")

/** Reconcile one account against its statement. [onDone] gets how many entries were reconciled. */
@Composable
fun ReconcileScreen(viewModel: ReconcileViewModel, onBack: () -> Unit, onDone: (Int) -> Unit = { onBack() }, modifier: Modifier = Modifier) {
    val view by viewModel.view.collectAsState()
    val v = view ?: return
    BackHandler(onBack = onBack)
    ReconcileContent(
        ReconcileScreenState(v, viewModel.statementText, viewModel.ticked), viewModel.noteText, viewModel.error,
        onStatement = { viewModel.statementText = it }, onToggle = viewModel::toggle, onTickAll = viewModel::tickAll,
        onNote = { viewModel.noteText = it }, onPostDifference = viewModel::postDifference,
        onFinish = { viewModel.finish(onDone) }, onBack = onBack, modifier = modifier,
    )
}

@Composable
internal fun ReconcileContent(
    state: ReconcileScreenState,
    note: String,
    error: String?,
    onStatement: (String) -> Unit,
    onToggle: (String) -> Unit,
    onTickAll: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    onPostDifference: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val view = state.view
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Reconcile", subtitle = view.accountName, onBack = onBack) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("statement") {
                Module {
                    FormField(
                        if (view.isLiability) "Amount owed on the statement (₹)" else "Balance on the statement (₹)",
                        state.statementText, onStatement, keyboardType = KeyboardType.Text, placeholder = "e.g. 48,250.00 (use - if overdrawn)",
                        error = if (state.statementText.isNotBlank() && state.statement == null) "Enter an amount" else null,
                    )
                    Text("Already reconciled: ${view.cleared.formatInr(showPaise = true)}", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item("entries") {
                Module {
                    ModuleHeader(
                        "Entries to tick off", subtitle = "${state.ticked.size} of ${view.lines.size} ticked",
                        action = { Pill(if (state.ticked.size == view.lines.size && view.lines.isNotEmpty()) "Clear" else "Tick all", { onTickAll(state.ticked.size != view.lines.size) }, height = 28.dp) },
                    )
                    if (view.lines.isEmpty()) Text("Nothing is waiting to be reconciled.", style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                    view.lines.forEachIndexed { i, line ->
                        ListRow(
                            title = line.payee, subtitle = line.date.format(DayMonth), divider = i < view.lines.lastIndex,
                            modifier = Modifier.clickable { onToggle(line.transactionId) },
                            trailing = {
                                Text(line.amount.formatInr(showPaise = true, signed = true), style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.padding(end = 10.dp))
                                Toggle(line.transactionId in state.ticked, { onToggle(line.transactionId) })
                            },
                        )
                    }
                }
            }
            if (state.statement != null) item("difference") {
                Module {
                    val diff = state.difference ?: return@Module
                    ModuleHeader("Difference", action = { if (state.matches) StatusBadge("✓ Matches", Tone.Positive) else StatusBadge("${diff.formatInr(showPaise = true)} out", Tone.Warning) })
                    if (!state.matches) {
                        Text(
                            "The statement shows ${diff.formatInr(showPaise = true, signed = true)} compared with your books and the ticked entries. Add the missing entry with + and come back, or book it to Unaccounted.",
                            style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                        Column(Modifier.padding(top = 10.dp)) { FormField("What is the difference?", note, onNote, placeholder = "e.g. Bank charge") }
                        Pill("Book to Unaccounted", { if (note.isNotBlank()) onPostDifference() }, modifier = Modifier.padding(top = 10.dp).alpha(if (note.isNotBlank()) 1f else 0.4f))
                    }
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = c.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (state.ticked.isEmpty()) "Reconcile" else "Reconcile ${state.ticked.size} ${if (state.ticked.size == 1) "entry" else "entries"}",
                { if (state.canFinish) onFinish() }, style = PillStyle.Primary, height = 44.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (state.canFinish) 1f else 0.4f),
            )
        }
    }
}
