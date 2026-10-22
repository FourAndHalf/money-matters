# Invest tab: build plan

Extends plan.html (P13 Portfolio, P14 Holding detail, P15 Prices; Phase 2). It adds no new product decisions; it says how to build what plan.html already decided, in what order, and what has to change in the code that exists today. Covers stocks, mutual funds (MF), fixed deposits (FD) and SIPs, built in that order. Decisions in section 8 are settled.

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
- Market value is derived, never stored, until month-end close posts the revaluation entry (P17). Net worth on Home and Accounts uses market value: ledger balance + (market value - cost) per priced holding, cost where no price exists, labelled "as of last price". Price status badges show wherever a stale or unverified price feeds that number.
- EPF stays out of net worth. `AccountEntity` gets `inNetWorth` (default true; EPF accounts false). `buildBooks` sums assets only over accounts with it set, and Accounts shows flagged accounts in their own "Not in net worth" group. EPF gets no other tracking in this plan.
- Trades are one net rupee amount (proceeds or cost after all charges); brokerage, STT and stamp duty are not itemised. That amount is the posting; units and price are detail.

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

`PostingEntity` gains nullable `units` and `unitPrice`; `AccountEntity` gains `inNetWorth`. The existing account/transaction/audit tables are unchanged. `price_fetch_log` arrives with downloads (slice 5).

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

0. **Engine and schema v3. (Done.)** Fixed-point units/price types; FIFO lot allocator; XIRR; holding-period and gain bucketing; FD accrual and break value; `inNetWorth` flag. Migration v2 to v3. Tests: every investment transaction balances, FIFO sums equal cost, sale then reverse restores lots, XIRR matches known cases, net worth excludes flagged accounts. No UI.
1. **Stocks. (Done, including dividends with TDS and split/bonus.)** Holdings and lots, buy (net amount), sell with FIFO and gain buckets, dividends with TDS, split/bonus, manual price entry. Invest tab shell with Portfolio (P13) and Holding detail (P14), market-value net worth on Home/Accounts, EPF group. Opening history is loaded from your full purchase history: a trade CSV import (date, symbol/ISIN, buy/sell, units, net amount) so lots and past sales are rebuilt exactly, with a manual multi-lot form as the fallback. Quick Add investment types rerouted here.
2. **Mutual funds. (Done.)** Reuses the lot engine: fund class (equity/debt/hybrid/ELSS), lump-sum buy through In Transit and allotment, sell, ELSS 3-year lock per instalment, exit-load window, debt-fund slab-rate rule, NAV manual entry, same CSV import (CAMS/KFintech statement columns mapped).
3. **Fixed deposits. (Done.)** Open FD, monthly accrual, TDS, maturity, auto-renew, break-today value; FD rows in Portfolio and Holding detail.
4. **SIPs. (Done.)** `sip_plan`, monthly proposals, allotment confirmation, missed-SIP flag with goal-date effect. Needs MFs (slice 2); proposals appear on opening the Invest tab until recurring rules (P11) land.
5. **Prices.** NSE end-of-day file first (stocks come first), then AMFI NAV; ISIN matching, status badges, failure messages, download log, weekly WorkManager job, source URLs as settings. Until now prices are manual.
6. **Analytics and tax.** Allocation vs target and drift alert, concentration/overlap, tax-if-sold-today, capital-gains register feeding Tax (P20), month-end revaluation and accrual hooks into close (P17).

Stocks first means the lot engine, Portfolio and net worth change are proven on one instrument before MFs reuse them; FDs need none of the lot machinery, so they can slip without blocking anything. Downloads stay late so the model is proven on manual prices first.

## 7. Risks

- Trade-history import must be exact: a wrong date or unit count changes holding periods and tax. Import shows a preview with per-holding units and cost, and refuses a sale that exceeds units held on that date.
- NSE changes its file format and location (plan.html). Downloads stay behind a source-URL setting, and a failure is shown, never hidden behind an old price.
- AMFI rows for wound-up schemes freeze; the row date is always checked.
- Rounding drift between unit x price and what the broker charged. Store the broker's actual rupee amount as the posting amount and the units/price as detail; never recompute money from them.

## 8. Decisions (settled)

1. **Net worth** uses market value (cost where a holding has no price yet), labelled "as of last price".
2. **Scope** is stocks, MFs and FDs, plus SIPs into MFs. **EPF** exists as an account but is excluded from net worth.
3. **Existing holdings**: full purchase history is available, so lots and past sales are rebuilt exactly through trade import. No estimated dates are expected; the approximate-date warning only applies if something is entered by hand.
4. **Order**: stocks, MFs, FDs, SIPs.
5. **Charges**: one net rupee amount per trade.
