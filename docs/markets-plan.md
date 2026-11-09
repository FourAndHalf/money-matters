# Markets plan: bringing Tickertape's six tools into Money Matters

Status: PLAN, written before any further building. The first slice (below, "What exists today") was built before this plan was written and is still being verified. Nothing new gets built until this plan is agreed.

Source: the "How Tickertape's Top Features Work" doc (claude.ai artifact 92d8e148). Scope chosen by the owner: everything the app's own free data supports, with no mocked numbers.

## 1. Ground rules

1. Money safety first. The ledger is never touched by Markets. Market tables are separate, additive, and can be dropped without affecting books.
2. No invented data. A measure without enough history shows "needs N days". A feed we lack is named as missing.
3. Prices are integer 1/10,000 rupee on disk. Floats exist only in display and screening maths and are never stored or posted.
4. Every schema change is an Auto Migration with a new exported schema JSON and a migration test.
5. Every rule in this plan has a test before the UI for it ships. UI uses `MoneyTheme` tokens and `ui/components` only.
6. Informational only, never advice. Each screen carries the disclaimer.
7. `./gradlew lint test` must pass before each merge. Work lands on the feature branch and a draft PR; the owner merges.
8. Security: no keys in chat. Any third-party API key goes in environment settings as a named variable.

## 2. Data we can and cannot get

| Need | Source | Cost | Status |
|---|---|---|---|
| Daily OHLC, previous close, volume, series for all NSE stocks | NSE UDiFF bhavcopy (already used) | free | Have |
| Fund NAV today | AMFI NAVAll (already used) | free | Have |
| Fund NAV history | AMFI portal history download, or mfapi.in (third party) | free | Decision D1 |
| Sector and industry per stock | NSE index constituent CSVs (sector indices) | free, partial | Decision D2 |
| Fundamentals (P/E, ROE, margins, growth) | none free and reliable offline | paid or scraped | Not planned (D3) |
| Analyst forecasts | S&P Global via vendor | licensed | Not planned |
| FII futures position | NSE participant-wise OI file | free but fiddly | Decision D4 |
| India VIX | NSE indices daily file | free | Decision D4 |
| Gold return vs Nifty | Nifty index file + a gold price (GOLDBEES ETF is in bhavcopy) | free | Decision D4 |
| Nifty 50 / index levels | NSE `ind_close_all_<date>.csv` | free | Decision D4 |
| Promoter pledging, ASM/GSM lists, default probability | NSE/BSE pages, no stable file | fragile | Not planned |

Open decisions (owner to answer, each has a recommended default):
- D1 Fund history: use AMFI's own historical download for the funds you hold and watch only. Default yes. Enables fund XIRR vs category, returns, volatility, Sharpe, but not 5,000-fund screening.
- D2 Sector tags: pull NSE sector index constituents to tag about 500 large stocks with a sector. Enables peer-ranked scorecards for those. Default yes, with "Unclassified" for the rest.
- D3 Fundamentals: skip. Default skip. If wanted later, choose a provider first.
- D4 Index file: add NSE's daily index file (Nifty 50, India VIX, sector indices). Default yes. Unlocks the VIX and gold factors of the mood gauge, index benchmark, and sector momentum. FII needs a separate file; default defer.

## 3. What exists today (first slice, branch `claude/optimistic-newton-eb7ox6`)

- DB v17: `market_bars`, `watchlist`, `alert_rules`, `alert_events`, `saved_screens`.
- `core/market`: indicators (SMA, Wilder RSI, returns, 52-week range, volume ratio), alert engine (edge-triggered), screener (13 measures, AND, presets, save/load), scorecard (4 market-relative cards plus red flags), mood (3 of 6 factors, 45-day z-score), portfolio checks, repository, updater, nightly worker, notifier.
- `feature/market`: Markets screen under Invest with Watchlist, Screener, Mood, Alerts, Portfolio tabs and a stock page.
- Tests written: indicators, alerts, screener, mood, scorecard, portfolio, repository and updater, screen tests. Full lint + test run: result pending at the time of writing.
- Not verified: a live download from NSE from a real phone.

Known weaknesses to fix in this plan (see Section 5): no sector peers; mood has 3 of 6 factors and no history chart; alert set is smaller than Tickertape's; the universe loads all stocks into memory on open; no CSV export; no watchlist groups UI; no benchmark comparison; the stock page lacks a candlestick or range selector; first-run backfill is slow and not resumable in the background.

## 4. Feature-by-feature specification

### F1 Stock screener
Goal: filter the market to a short list in under a minute.
- Measures now: price, 1d, 1m, 3m, 6m, 1y return, below 52-week high, above 52-week low, RSI, vs 50d and 200d average, volume ratio, turnover.
- Add: 1-week return, 52-week range position (0 to 100), average daily turnover over 3 months, price vs sector median, sector (equals / in list), series, market-cap proxy rank by turnover (labelled as such), "new 52-week high today", "crossed 50d average this week".
- Add: OR groups are out of scope; AND only, matching Tickertape.
- Add: sort selector and direction, results count, CSV export through the existing export path, copy symbols to a watchlist group in one tap.
- Add: universe choices: all, my watchlist, my holdings, a sector.
- Performance: query the latest-metrics table, not raw bars (see F8 cache).
- Tests: each measure on known series; AND semantics; missing-metric exclusion; encode/decode round trip; sector filter; CSV content.
- Acceptance: screen of 2,000 stocks returns under 300 ms after cache is warm.

### F2 Market Mood Index
Goal: one 0 to 100 reading of fear and greed, honest about inputs.
- Factors now: breadth (volume in rising stocks), price strength (near highs vs lows), momentum (above 50d average).
- Add with D4: volatility (India VIX level and its change vs 45d), demand for gold (GOLDBEES 1-month return minus Nifty 1-month return, high = fear), index momentum (Nifty vs its 50d average). FII activity only if D4 includes the participant file.
- Method fixed as published: per factor, 45-day mean and standard deviation, z-score, rescale 0 to 100, equal-weight average of available factors. Factors with fewer than 20 observations are excluded and listed as such.
- Store each day's reading in `mood_readings(date, score, factorsJson)` so the chart does not recompute a year.
- UI: gauge, zone, 90-day line chart with zone bands, factor table, "N of 6 factors" label, plain explanation of each factor.
- Alerts: zone change notification.
- Tests: z-score and CDF accuracy; zone cut-offs (30/50/70); excluded factors; stored reading idempotence; VIX direction inversion; gold inversion.

### F3 Stock pages and scorecards
Goal: a report card that says whether the stock is strong against peers and sensible to buy now.
- Cards now: performance, trend, entry point, liquidity (ranked against all stocks).
- Change with D2: rank within sector when the sector has 20 or more stocks, else market-wide, and say which was used.
- Add: price chart with 1M / 3M / 6M / 1Y / max selector, 50d and 200d overlays, 52-week band, volume bars; return table vs Nifty 50 (needs D4); delivery of "peer rank" text.
- Red flags: keep series, sharp fall, far below 200d, halved from high, thin trading. Add: long suspension gap, repeated circuit-like moves, price below ₹10. State clearly that pledging, ASM/GSM and default risk are not available.
- Forecast: none. No fabricated targets.
- Tests: rank in sector vs market fallback; each flag threshold both sides; chart range slicing.

### F4 Portfolio tracking and red flags
Goal: one view of what you hold with risk checks.
- Already in the app: holdings, value, gains, XIRR for portfolio and holding, allocation, SIPs, FDs.
- Add: red flags per holding (stocks with stored bars), shown on the Invest holdings list as a small badge, not only in Markets.
- Add: concentration with the editable 10% single-stock limit already in the plan (reuse the existing setting), sector concentration once D2 is in.
- Add: portfolio vs benchmark: portfolio return and XIRR next to Nifty 50 over the same dates (needs D4).
- Add: spread score (own formula, labelled), fund overlap stays out (no holdings data).
- Add: portfolio value history chart from lots and stored prices.
- Not planned: broker linking (no consent API), diversification vs community, portfolio forecast.
- Tests: weights sum to 100; HHI cases; flags roll up by value; benchmark window alignment.

### F5 Mutual fund screener
Goal: shortlist funds by cost, return and risk.
- Reality: AMFI daily file has NAV only. No expense ratio, AUM, alpha, tracking error offline.
- Plan with D1: store NAV history for held and watched funds only (about 5 years each). From NAV compute 1/3/5y CAGR, rolling returns, volatility, max drawdown, Sharpe and Sortino (risk-free rate as a versioned setting), downside deviation, SIP XIRR. Category comes from the AMFI file's category header.
- A "fund screener" over watched funds plus a category browser of today's NAV and 1-year return is possible only if we store history for every fund (about 14,000 schemes); judged too heavy, so screening is limited to watched funds unless the owner decides otherwise (D5).
- Expense ratio and AUM: manual entry field per fund, clearly marked as user-entered, used in a cost-drag calculator (the 12% vs 11% example from the source doc).
- Tests: CAGR, Sharpe, Sortino, drawdown on known series; category parsing; NAV history ingestion and de-duplication.

### F6 Watchlists and alerts
Goal: decide levels in advance, then let the phone watch.
- Now: one flat watchlist; 10 rule kinds; evening worker; in-app list and notification.
- Add: named groups (create, rename, move, delete) and a screener "use watchlist as universe".
- Add: funds in the watchlist (needs F5 history) with NAV-change alerts.
- Add alert kinds: daily market and portfolio summary (scheduled), mood zone change, results/dividend dates only if a free calendar source is chosen (default: skip), target-price alert on holdings from average cost (for example "up 20% from cost").
- Add: alert snooze, per-rule "once" vs "re-arm", quiet hours.
- Delivery limits: background runs are best-effort on Android; first check happens when the app opens.
- Tests: re-arm behaviour; snooze; summary content; zone-change edge trigger; funds.

### F7 Data layer
- `market_latest(symbol, metricsJson, asOf)`: cache of per-stock metrics, rebuilt after each ingest, so screens do not scan 450k rows.
- `market_index_bars(index, date, open, high, low, close)` for D4.
- `fund_navs(holdingOrSchemeCode, date, nav)` for D1.
- `sector_map(symbol, sector)` for D2.
- `mood_readings` as in F2.
- Retention: 380 days of bars; indexes 3 years; fund NAVs 5 years.
- Backfill: a resumable background job (WorkManager, expedited, network constrained) with progress, so a year of files does not depend on the screen staying open. Stops cleanly on 429 or 403 and resumes later.
- Source URLs stay settings, as for prices.

### F8 Performance and memory
- Never load the whole market into memory: batch by symbol (done), plus the latest-metrics cache above.
- Budget: DB growth under 60 MB for a year; first backfill under 10 minutes on a normal connection; open Markets under 1 second after cache.
- Measure on a mid-range device profile in tests where possible, and record sizes in the PR.

## 5. Phases (each ends with tests green, a draft PR, and your review)

- Phase M0: verify what exists. Finish the lint and test run, fix anything red, run a real download once on your phone and record what the NSE file returned. Add a "Data health" row (last file, rows read, rows kept).
- Phase M1: data layer F7 (cache, resumable backfill, index file, D4 inputs). No new UI beyond progress.
- Phase M2: mood completion F2 and stored readings, chart, zone-change alert.
- Phase M3: screener upgrade F1 including sectors (D2), sort, export, universes.
- Phase M4: stock page F3 with chart ranges, sector peers, benchmark.
- Phase M5: portfolio F4 badges, benchmark, history chart, concentration limit setting.
- Phase M6: watchlist groups and alert upgrades F6.
- Phase M7: funds F5 and fund alerts, subject to D1 and D5.
- Phase M8: polish: empty states, dark theme screenshots, accessibility labels, plan.html update, release notes.

## 6. Test plan summary

Unit: indicators, z-score, CDF, each alert kind, screener AND logic, sector rank, flags, concentration, fund metrics, parsers (every file format with a real-shaped fixture, including a malformed one).
Repository: in-memory Room for ingest, prune, cache rebuild, resumable backfill, edge-triggered alerts.
Migration: v17 to later versions open an existing database with data intact.
UI: Compose tests for each tab and the stock page, with light and dark Roborazzi captures.
Manual on device: one real download; a notification; airplane-mode behaviour; a 5-minute scroll with 2,000 results.

## 7. Risks

- NSE changes its file or blocks automated requests (it has before). Mitigation: URLs are settings, failures are explained, history stays usable.
- Android may delay the nightly job. Mitigation: catch up on app open.
- Sector and index sources are unofficial CSVs. Mitigation: label, and degrade to market-wide ranking.
- Scores can be mistaken for advice. Mitigation: disclaimer on every screen, scores never named "buy" or "sell".
- Database size. Mitigation: retention, cache table, batch reads.

## 8. Definition of done for the whole Markets effort

All six areas have their data-supported parts shipped; every missing feed is named in the app and in `docs/plan.html`; lint and tests pass; a real download has been run on the owner's phone and noted; the owner has merged the PRs.
