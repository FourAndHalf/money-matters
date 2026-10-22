package com.moneymatters.app.feature.invest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.InsufficientUnitsException
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.LockedUnitsException
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
    /** The wealth goal SIP shortfalls are measured against, if the investor has one. */
    private val goal: () -> SipGoal? = { null },
) : ViewModel() {

    val state: StateFlow<InvestState?> = combine(ledger.snapshot(), invest.snapshot()) { l, i -> buildInvest(l, i, today(), rules, goal()) }
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

    fun startTrade(side: TradeSide, holdingId: String? = null, instrument: Instrument = Instrument.Stock) {
        tradeForm = TradeForm.empty(side, today(), holdingId, instrument)
        error = null
    }

    fun saveTrade(onSaved: () -> Unit) {
        val form = tradeForm
        val amount = form.amount
        val accountId = form.accountId
        if (!form.canSave || amount == null || accountId == null || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val symbol = form.symbol.trim().uppercase().ifBlank { null }
                val holdingId = form.holdingId ?: invest.findOrCreateHolding(
                    form.instrument, symbol, form.isin.trim().uppercase().ifBlank { null }, form.name.trim().ifBlank { null },
                    fundClass = if (form.isFund) form.fundClass else null,
                    exitLoadDays = form.exitLoadDaysText.trim().toIntOrNull(),
                )
                val label = symbol ?: form.name.trim().ifBlank { "holding" }
                val units = form.units
                when {
                    form.inTransit -> invest.orderFund(holdingId, form.date, amount, accountId, "Buy $label (in transit)")
                    units == null -> return@launch
                    form.side == TradeSide.Buy -> invest.buy(holdingId, form.date, units, impliedPrice(units, amount), amount, accountId, "Buy $label")
                    else -> invest.sell(holdingId, form.date, units, impliedPrice(units, amount), amount, accountId, "Sell $label")
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InsufficientUnitsException) {
                error = "That's more than you held on that date."
            } catch (e: LockedUnitsException) {
                error = (e.message ?: "Some of those units are locked") + "."
            } catch (_: Exception) {
                error = "Couldn't save the trade. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    // ---- SIPs ----
    var sipForm by mutableStateOf<SipForm?>(null)

    fun startSip() {
        sipForm = SipForm(start = today())
        error = null
    }

    fun saveSip(onSaved: () -> Unit) {
        val form = sipForm ?: return
        val holdingId = form.holdingId ?: return
        val amount = form.amount ?: return
        val day = form.day ?: return
        val accountId = form.accountId ?: return
        viewModelScope.launch {
            try {
                invest.createSipPlan(holdingId, amount, day, accountId, form.start)
                sipForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the SIP."
            }
        }
    }

    /** Confirms a due instalment as paid; it goes In Transit and can then be allotted. */
    fun payInstalment(planId: String, due: LocalDate) = launchSip { invest.payInstalment(planId, due) }

    fun skipInstalment(planId: String, due: LocalDate) = launchSip { invest.skipInstalment(planId, due) }

    fun stopSip(planId: String) = launchSip { invest.stopSip(planId, today()) }

    private fun launchSip(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't update the SIP."
            }
        }
    }

    // ---- Fixed deposits ----
    var depositForm by mutableStateOf<DepositForm?>(null)

    fun startDeposit() {
        depositForm = DepositForm(start = today(), maturity = today().plusYears(1))
        error = null
    }

    fun saveDeposit(onSaved: () -> Unit) {
        val form = depositForm ?: return
        val principal = form.principal ?: return
        val rateBps = form.rateBps ?: return
        val penaltyBps = form.penaltyBps ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val from = if (form.existing) ledger.createAccountIfAbsent(OPENING_EQUITY, AccountType.Equity) else form.accountId!!
                invest.openDeposit(form.name, principal, rateBps, form.compounding, form.start, form.maturity, from, form.autoRenew, penaltyBps)
                depositForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the deposit. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    /** Books the interest earned to today as income. */
    fun postInterest(depositAccountId: String) {
        viewModelScope.launch {
            try {
                invest.accrueInterest(depositAccountId, today())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't post the interest."
            }
        }
    }

    var closeForm by mutableStateOf<DepositCloseForm?>(null)

    fun startClose(depositAccountId: String) {
        closeForm = DepositCloseForm(depositAccountId, today())
        error = null
    }

    fun saveClose(onSaved: () -> Unit) {
        val form = closeForm ?: return
        val tds = form.tds ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                invest.closeDeposit(form.accountId, form.date, form.toAccountId, tds, form.renew)
                closeForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't close the deposit. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    // ---- Allotment of a fund order waiting In Transit ----
    var allotForm by mutableStateOf<AllotForm?>(null)

    fun startAllot(pendingId: String) {
        allotForm = AllotForm(pendingId, today())
        error = null
    }

    fun saveAllot(onSaved: () -> Unit) {
        val form = allotForm ?: return
        val units = form.units ?: return
        val nav = form.nav ?: return
        viewModelScope.launch {
            try {
                invest.allot(form.pendingId, form.date, units, nav)
                allotForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't allot the units. Check the date is on or after the order."
            }
        }
    }

    fun cancelOrder(pendingId: String) {
        viewModelScope.launch {
            try {
                invest.cancelPending(pendingId, today())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't cancel that order."
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
                importDone = invest.importTrades(rows, settlement)
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
