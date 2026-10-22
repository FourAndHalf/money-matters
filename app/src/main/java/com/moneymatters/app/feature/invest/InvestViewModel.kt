package com.moneymatters.app.feature.invest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.InsufficientUnitsException
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.invest.TradeParse
import com.moneymatters.app.core.invest.TradePreview
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.invest.parseTrades
import com.moneymatters.app.core.invest.previewTrades
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.impliedPrice
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val OPENING_EQUITY = "Equity:Opening"

/** The accounts a stock can be paid for from or sold into: live assets that aren't holdings or other investments. */
internal fun settlementAccountsIn(ledger: LedgerSnapshot): List<AccountEntity> =
    ledger.accounts
        .filter { it.type == AccountType.Asset && !it.isArchived && it.id !in ledger.holdingAccountIds && it.kind != AccountKind.Investment }
        .sortedBy { it.name.lowercase() }

/** Streams the Invest tab's state and holds the buy/sell, price and import forms (plan.html → P13–P15). */
class InvestViewModel(
    private val ledger: LedgerRepository,
    private val invest: InvestRepository,
    private val rules: CapitalGainsRules,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val state: StateFlow<InvestState?> = combine(ledger.snapshot(), invest.snapshot()) { l, i -> buildInvest(l, i, today(), rules) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Real accounts money can be paid from or received into: assets that aren't themselves holdings. */
    val settlementAccounts: StateFlow<List<AccountEntity>> = ledger.snapshot()
        .map(::settlementAccountsIn)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Buy / sell ----
    var tradeForm by mutableStateOf(TradeForm.empty(TradeSide.Buy, today()))
    var isSaving by mutableStateOf(false)
        private set

    /** A plain-language reason the last save didn't go through, or null. */
    var error by mutableStateOf<String?>(null)
        private set

    fun startTrade(side: TradeSide, holdingId: String? = null) {
        tradeForm = TradeForm.empty(side, today(), holdingId)
        error = null
    }

    fun saveTrade(onSaved: () -> Unit) {
        val form = tradeForm
        val units = form.units
        val amount = form.amount
        val accountId = form.accountId
        if (!form.canSave || units == null || amount == null || accountId == null || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val holdingId = form.holdingId ?: invest.findOrCreateStock(form.symbol.trim().uppercase(), form.isin.trim().uppercase().ifBlank { null }, form.name.trim().ifBlank { null })
                val symbol = form.symbol.trim().uppercase().ifBlank { "stock" }
                val price = impliedPrice(units, amount)
                if (form.side == TradeSide.Buy) {
                    invest.buy(holdingId, form.date, units, price, amount, accountId, "Buy $symbol")
                } else {
                    invest.sell(holdingId, form.date, units, price, amount, accountId, "Sell $symbol", rules.listedEquity(form.date))
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InsufficientUnitsException) {
                error = "That's more than you held on that date."
            } catch (_: Exception) {
                error = "Couldn't save the trade. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    // ---- Dividend ----
    var dividendForm by mutableStateOf<DividendForm?>(null)

    fun startDividend(holdingId: String) {
        dividendForm = DividendForm(holdingId, today())
        error = null
    }

    fun saveDividend(symbol: String, onSaved: () -> Unit) {
        val form = dividendForm ?: return
        val net = form.net ?: return
        val tds = form.tds ?: return
        val accountId = form.accountId ?: return
        viewModelScope.launch {
            try {
                invest.recordDividend(form.holdingId, form.date, net, tds, accountId, "Dividend $symbol")
                dividendForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the dividend."
            }
        }
    }

    // ---- Split / bonus ----
    var actionForm by mutableStateOf<ActionForm?>(null)

    fun startAction(holdingId: String, kind: RestateKind) {
        actionForm = ActionForm(holdingId, kind, today())
        error = null
    }

    fun saveAction(onSaved: () -> Unit) {
        val form = actionForm ?: return
        val ratio = form.ratio ?: return
        viewModelScope.launch {
            try {
                invest.applyCorporateAction(form.holdingId, form.kind, ratio, form.date)
                actionForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = "Nothing was held on that date."
            } catch (_: Exception) {
                error = "Couldn't save the corporate action. Nothing was changed."
            }
        }
    }

    // ---- Manual price ----
    var priceForm by mutableStateOf<PriceForm?>(null)

    fun startPrice(holdingId: String) {
        priceForm = PriceForm(holdingId, today())
        error = null
    }

    fun savePrice(onSaved: () -> Unit) {
        val form = priceForm ?: return
        val price = form.price ?: return
        viewModelScope.launch {
            try {
                invest.recordPrice(form.holdingId, form.date, price, "manual", PriceStatus.Manual)
                priceForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the price."
            }
        }
    }

    // ---- Trade history import ----
    var importParse by mutableStateOf<TradeParse?>(null)
        private set
    var importPreview by mutableStateOf<TradePreview?>(null)
        private set
    var importDone by mutableStateOf<Int?>(null)
        private set

    fun resetImport() {
        importParse = null
        importPreview = null
        importDone = null
        error = null
    }

    /** Parses [csv] and replays it against the current lots, so the preview shows exactly what an import would do or refuse. */
    fun loadCsv(csv: String) {
        viewModelScope.launch {
            val parsed = parseTrades(csv)
            val existing = existingLotsBySymbol()
            importParse = parsed
            importPreview = previewTrades(parsed.rows, existing)
            importDone = null
        }
    }

    /** Whether the loaded file can be imported: it has trades and no bad row or impossible sale. */
    val canImport: Boolean
        get() = importParse?.let { it.rows.isNotEmpty() && it.errors.isEmpty() } == true && importPreview?.errors?.isEmpty() == true

    fun confirmImport() {
        val rows = importParse?.rows ?: return
        if (!canImport || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val settlement = ledger.createAccountIfAbsent(OPENING_EQUITY, AccountType.Equity)
                importDone = invest.importTrades(rows, settlement, rules)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "The import failed and nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    private suspend fun existingLotsBySymbol() = buildMap {
        for ((symbol, id) in invest.holdingSymbols()) put(symbol.uppercase(), invest.openLots(id))
    }

    /** Units held, as shown on the sell form. */
    suspend fun unitsHeldLabel(holdingId: String) = formatUnits(invest.unitsHeld(holdingId))
}
