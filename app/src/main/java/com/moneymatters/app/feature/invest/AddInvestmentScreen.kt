package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.ui.components.SegmentedControl

private enum class InvestmentKind(val label: String) { Stock("Stock"), Fund("Mutual fund"), Deposit("FD") }

/**
 * The page the + button opens on the Invest tab: one place to add a stock, a mutual fund or a fixed
 * deposit, switched with a toggle. The caller starts both forms before showing it.
 */
@Composable
fun AddInvestmentScreen(viewModel: InvestViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var kind by rememberSaveable { mutableStateOf(InvestmentKind.Stock) }
    val accounts by viewModel.settlementAccounts.collectAsState()
    val toggle: @Composable () -> Unit = {
        SegmentedControl(InvestmentKind.entries, kind, {
            kind = it
            when (it) {
                InvestmentKind.Stock -> viewModel.tradeForm = viewModel.tradeForm.copy(instrument = Instrument.Stock)
                InvestmentKind.Fund -> viewModel.tradeForm = viewModel.tradeForm.copy(instrument = Instrument.MutualFund)
                InvestmentKind.Deposit -> Unit
            }
        }, { it.label })
    }
    val deposit = viewModel.depositForm
    if (kind == InvestmentKind.Deposit && deposit != null) {
        DepositContent(deposit, accounts, viewModel.error, viewModel.isSaving, { viewModel.depositForm = it }, { viewModel.saveDeposit(onSaved = onClose) }, onClose, modifier, header = toggle)
    } else {
        TradeContent(
            form = viewModel.tradeForm,
            accounts = accounts,
            error = viewModel.error,
            isSaving = viewModel.isSaving,
            onChange = { viewModel.tradeForm = it },
            onSave = { viewModel.saveTrade(onSaved = onClose) },
            onClose = onClose,
            modifier = modifier,
            header = toggle,
        )
    }
}
