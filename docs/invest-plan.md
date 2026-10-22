# Invest tab: build plan

Extends plan.html (P13 Portfolio, P14 Holding detail, P15 Prices; Phase 2). It adds no new product decisions; it says how to build what plan.html already decided, in what order, and what has to change in the code that exists today. Covers fixed deposits (FD), stocks, mutual funds (MF) and SIPs.

## 1. Where the code is today

- The Invest tab shows "not built yet" (`MoneyMattersApp.kt`, tab index 3).
- Schema is v2. `PostingEntity` has only `debitPaise` / `creditPaise`: no quantity, unit price or lot. There are no lot, price or holding tables.
- `AccountKind` has `FixedDeposit` and `Investment`, so FDs, stocks and MFs already appear on Accounts at book value. Stocks and MFs are not told apart.
- Quick Add's "Investment buy / sell" is a plain money transfer to or from an `Investment` account. It records cost but no units, so nothing could be sold FIFO and no gain could be computed. **This path must be replaced by the investment engine, or the books will hold cost with no lots behind it.**
- Onboarding takes one opening balance per account. Existing holdings need lots, not one number.
- No recurring-rule engine, WorkManager jobs or network code exist yet (P11 and prices are Phase 2).

## 2. Design rules (from AGENTS.md, applied here)

- Every investment transaction is still a balanced journal entry. Units and lots are extra detail on the postings, never a second source of truth for money.
- Money stays integer paise. Units are integers in micro-units (1 unit = 1,000,000), because MF units go to 3-4 places. Prices are integers in 1/10,000 rupee (NAV has 4 decimals). Value = units x price, rounded half-up to paise in one helper, covered by tests. No floats in stored values; XIRR is the only float and is display-only.
- Lots are never edited. A sale writes `lot_consumption` rows (lot, units, cost); remaining units = lot units - consumed. Reversing a sale removes its consumption rows, which is how reversal restores lots without mutating history.
- Reconciled and closed-month entries stay immutable, including the lots they touched. Corrections are reversals or prior-period adjustments.
- Tax thresholds, rates and the exemption limit live in versioned per-tax-year data, not code. Figures must be verified against the Income-tax Act 2025 text first (plan.html open item).
- Market value is derived, never stored, until month-end close posts the revaluation entry (P17).

## 3. Data model: schema v3

New tables (Room auto-migration where possible, hand migration + `MigrationTest` otherwise):

| Table | Purpose |
| --- | --- |
| `holding` | One row per stock/fund: `accountId`, `instrument` (Stock, MutualFund), `isin`, `symbol`/`schemeCode`, `fundClass` (Equity, Debt, Hybrid, ELSS), `exitLoadDays`, `exitLoadBps` |
| `lot` | `holdingId`, `acquiredOn`, `units`, `costPaise` (incl. stamp duty, brokerage, taxes), `lockUntil`, `fmv2018Paise` (nullable), `sourcePostingId` |
| `lot_consumption` | `lotId`, `saleTransactionId`, `units`, `costPaise` |
| `price` | `holdingId`, `date`, `price`, `source`, `status`, `fetchedAt`, per plan.html |
| `fixed_deposit` | `accountId`, `principalPaise`, `rateBps`, `compounding` (Monthly, Quarterly, Maturity), `startDate`, `maturityDate`, `autoRenew`, `tdsRelevant`, `penaltyBps` |
| `sip_plan` | `holdingId`, `amountPaise`, `dayOfMonth`, `fromAccountId`, `startDate`, `endDate?`, `goalId?` |
| `corporate_action` | `holdingId`, `kind` (Split, Bonus, Dividend), `date`, `ratioNum`, `ratioDen`, `amountPaise?` |

`PostingEntity` gains nullable `units` and `unitPrice`. The existing account/transaction/audit tables are unchanged. `price_fetch_log` arrives with downloads (slice 5).

New code lives in `core/invest` (pure, tested math) and `feature/invest` (state + screens), mirroring `core/ledger` and `feature/books`. `BooksState` stays as is; a separate `InvestState` is built from the same `LedgerSnapshot` plus the new tables so pages never disagree.

## 4. Ledger entries per instrument

**Stock buy.** Dr `Assets:Stocks:<Name>` (qty, price, cost incl. charges), Cr bank. Charges are capitalised into the lot's cost (plan.html). Creates a lot.

**Stock/MF sale.** Dr bank (proceeds net of charges), Cr holding (cost of lots consumed FIFO), Cr/Dr `Income:Capital Gains:{STCG|LTCG}:{Equity|Other}` for the difference. A sale spanning lots of different ages posts one gain line per bucket.

**MF lump sum and SIP** (via In Transit, as in plan.html):
- Day 0: Dr `Assets:Investments:In Transit`, Cr bank. Shows in the In Transit list.
- Allotment (NAV day, usually T+1): Dr holding (units @ NAV), Cr In Transit. Creates a lot with `lockUntil` for ELSS (3 years per instalment).
- A SIP is a `sip_plan` that proposes the Day 0 entry each month; you confirm the allotment units/NAV (or they are filled from the price file once downloads exist). A plan with no allotment after N days is flagged, and a missed instalment is flagged with its effect on the goal date.

**Dividend.** Dr bank (net) + Dr `Assets:TDS Receivable`, Cr `Income:Dividends`.

**Split / bonus.** Rewrites no history. It adds a corporate-action row; lot units are scaled in the read model (split) or a zero-cost lot is added dated as the original (bonus, per the tax rule). Unit tests prove cost and holding period come out right.

**FD.**
- Open: Dr `Assets:FD:<Bank, date>` (one account per FD), Cr bank. Writes a `fixed_deposit` row.
- Interest accrues monthly at close: Dr FD account (accrued), Cr `Income:Interest`; TDS: Dr `Assets:TDS Receivable`, Cr FD account.
- Maturity or break: Dr bank, Cr FD account; break penalty is Dr `Expenses:Fees`. Auto-renew opens the next FD from the proceeds.
- Day-count and compounding go in one tested function. Break-today value uses the penalty, with no penalty inside 30 days of maturity (plan.html).

## 5. Screens

- **Portfolio (P13)**: market value, cost, unrealised gain (Rs and %), XIRR; sections Stocks, Mutual funds, FDs, each row with a price-status badge; In Transit list; buy/sell/SIP/dividend/new-FD actions. Allocation, drift and concentration cards land in slice 6.
- **Holding detail (P14)**: stock/MF = units, average cost, lots table (age, short/long, lock-in, exit-load end), price chart, transactions, XIRR, "tax if sold today". FD = principal, rate, maturity value, accrued interest, break-today value, TDS, auto-renew.
- **Buy/sell/SIP/FD forms**: use `ui/components` (AmountPad, FormField, SegmentedControl). Existing Quick Add Investment types are rerouted to these forms.
- **Prices (P15)**: status bar per source, snapshot table, manual entry, download log.
- Opening holdings: an "Add existing holding" flow that accepts several lots (date, units, cost), because tax needs real purchase dates. Onboarding gets an optional holdings step later.

## 6. Build order (each slice is its own `feature/` branch, tested and merged)

0. **Engine and schema v3.** Fixed-point units/price types; FIFO lot allocator; XIRR; holding-period and gain bucketing; FD accrual and break value. Migration v2 to v3. Property-style tests: every investment transaction balances, FIFO sums equal cost, sale then reverse restores lots, XIRR matches known cases. No UI.
1. **Fixed deposits end to end.** Needs no prices or lots, so it proves the new tables, the Invest tab shell and the list-to-detail flow on real data. Open FD, accrual, maturity, break value.
2. **Mutual funds, manual NAV.** Holdings, lots, lump-sum buy through In Transit, sell, "add existing holding", manual price entry, Portfolio and Holding detail with market value, gain and XIRR. ELSS lock and exit-load window.
3. **SIPs.** `sip_plan`, monthly proposals, allotment confirmation, missed-SIP flag. Depends on the recurring/reminder work (P11); until it lands, proposals appear when you open the Invest tab.
4. **Stocks.** Reuses the lot engine: buy with charges, sell, dividends with TDS, split/bonus.
5. **Prices.** AMFI daily NAV file first (simple, stable), then NSE end-of-day; ISIN matching, status badges, failure messages, download log, weekly WorkManager job. Source URLs are settings.
6. **Analytics and tax.** Allocation vs target and drift alert, concentration/overlap, tax-if-sold-today, capital-gains register feeding Tax (P20), month-end revaluation and accrual hooks into close (P17).

Slices 1 and 2 give a useful tab early; 5 is deliberately late so manual prices prove the model before a network dependency lands.

## 7. Risks

- Wrong holding periods from estimated opening-lot dates change tax. The add-holding flow warns when a lot date is approximate and tags those lots.
- NSE changes its file format and location (plan.html). Downloads stay behind a source-URL setting, and a failure is shown, never hidden behind an old price.
- AMFI rows for wound-up schemes freeze; the row date is always checked.
- Rounding drift between unit x price and what the broker charged. Store the broker's actual rupee amount as the posting amount and the units/price as detail; never recompute money from them.

## 8. Decisions needed from you

1. **Net worth basis.** Home/Accounts today show book value. Once prices exist, should net worth use market value (recommended, labelled "as of last price") with the ledger revaluation still posted only at close, or stay at cost until close?
2. **Recurring deposits (RD).** You listed FD, stocks, MFs and SIPs. Do you hold RDs, PPF, EPF, NPS or gold? plan.html says "stocks, MFs and FDs only"; RD is a small extension of the FD model.
3. **Existing holdings.** Do you have full purchase history (broker/CAMS statements) or only current units and average cost? That decides whether statement import is needed in slice 2.
4. **Order.** Is FD, then MF + SIP, then stocks the right priority for you, or do stocks matter most?
5. **Brokerage and charges.** Enter the net rupee amount per trade (recommended) or itemise brokerage, STT and stamp duty?
