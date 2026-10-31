# Data Sources and System Architecture for an Always-On Investment Opportunity Engine (India, Android-first)

Scope note: research done 29 Oct 2026 with about 17 search/fetch calls. `www.mfapi.in` and `www.amfiindia.com` could not be fetched from this session (blocked by network allowlist), so AMFI and mfapi details rely on search snippets. Several statements marked "unverified (prior knowledge)" come from the researcher's background knowledge, not from a source fetched in this session. They are listed under Inferences or Gaps and must be checked before use.

## Price/NAV data: NSE/BSE bhavcopy, AMFI NAV, mfapi.in, broker APIs, unofficial APIs: licensing, latency, reliability

### Takeaway
For a single-user, end-of-day engine, the cheapest workable stack is AMFI NAVs (directly or through mfapi.in) for mutual funds, plus a broker API for equity prices. Kite Connect is free for execution, and its paid tier (about Rs 2,000/month) now includes historical candles. NSE's own website terms explicitly ban automated scraping and redistribution, so scraping nseindia.com (which libraries like jugaad-data do) is a personal-risk grey area. It must never feed anything redistributed to other users.

### Cited Findings
- NSE India Terms of Use: "User is prohibited to conduct any systematic or automated data collection activities (including scraping, data mining, data extraction and data harvesting) on or in relation to our Website / Mobile Application." Copying, reproducing or distributing site content needs NSE's prior written permission, and NSE reserves "strict actions" against unauthorised use. — [NSE Terms of Use](https://www.nseindia.com/static/nse-terms-of-use)
- NSE's data usage policy says market data accessed for commercial purposes is provided by NSE Data (NSE Data & Analytics) at a Board-approved price. Creating indices or financial products from the data needs a separate licence, and trading members and subscribers may not use market data to create a financial index. — [NSE Data Sharing & Usage Policy](https://www.nseindia.com/static/market-data/nse-data-policy); [NSE Data Usage and Sharing Policy PDF](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/NSE_DataUsageandSharingPolicy.pdf)
- NSE has a separate non-display usage policy covering algorithmic use ("high frequency trading, automated order or quote generation and/or order pegging"), with separate agreements and fees for external redistribution of derived data. — [NSE Non-Display Policy](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/Non_Display_Policy.pdf)
- NSE data may not be used for gaming, virtual trading or simulation, which Zerodha relayed as an announcement to its users. — [Trading Q&A by Zerodha](https://tradingqna.com/t/nse-data-is-now-not-allowed-for-gaming-and-virtual-trading/146115). This matters if the app ever offers "paper portfolios" built on NSE-sourced data.
- Zerodha Kite Connect: execution APIs became free for personal use in March 2025, but the "Personal" tier includes no market data (live or historical). The paid Connect tier costs Rs 2,000/month. From 8 Feb 2025 the separate historical-data add-on (previously another Rs 2,000) was dropped and folded into the base Connect subscription. — [Marketcalls](https://www.marketcalls.in/fintech/zerodha-makes-trading-api-free-for-personal-use-bundles-historical-data-with-connect-api.html); [Kite forum: Historical data is now free](https://kite.trade/forum/discussion/14806/historical-data-is-now-free); [Zerodha Z-Connect update 24 Apr 2025](https://zerodha.com/z-connect/?p=411859)
- Upstox API: one third-party comparison cites Rs 10 per executed API order, extended to 31 Mar 2026. No Upstox market-data pricing was found. — [Stratzy cost comparison](https://stratzy.in/blog/cost-comparison-algo-trading-apis-india)
- mfapi.in is a free JSON API with full NAV history and no API key. Its endpoints are `https://api.mfapi.in/mf` (list all schemes), `https://api.mfapi.in/mf/{scheme_code}` (full history), `https://api.mfapi.in/mf/{scheme_code}/latest` and `https://api.mfapi.in/mf/search?q=`. — [plus2net guide](https://www.plus2net.com/python/bs4-automate-mutual-fund-nav-tracking-mfapi.php); [write.as listing](https://write.as/nvk/). (Secondary sources only. One API-directory page that describes it is self-labelled AI-generated and unverified: [matrix-db directory](https://matrix-db-api-directory.pages.dev/api/indian-mutual-fund).)
- Another free MF NAV API advertises "20+ years of historical nav" with "No keys, no tokens, no setup" at `https://finapi.famvest.online`. — [Kite forum post](https://kite.trade/forum/discussion/15931/free-public-api-to-get-the-mutual-fund-details-and-exchange-information). This is a small, unknown operator, so treat it as a fallback only.

### Inferences
- MF NAV is the easiest feed: NAVs are published once per business day, so a once-daily pull, after the evening NAV deadline, is sufficient. mfapi.in is a community mirror of AMFI data with no SLA, so the engine should fall back to AMFI's own file (see Gaps) if mfapi fails.
- For equities, the only clean way to get prices for automated use is through a licensed intermediary: a broker API you subscribe to (Kite Connect at Rs 2,000/month, or broker APIs that are free for clients, see Gaps), or a licensed vendor. Scraping nseindia.com or bseindia.com violates NSE's stated terms. Doing it at low volume for personal use is a risk the user takes on; doing it in a backend serving others is clearly out.
- Broker API data is licensed for the account holder's own use. The phone being the source of truth is compatible with this, but the data should not be shared across users or re-exposed through a public endpoint.
- Yahoo Finance (`.NS` / `.BO` tickers via yfinance) is unofficial, has no published terms permitting programmatic use, and is known to break. It is acceptable only as a last-resort fallback for personal EOD history (unverified, prior knowledge; no source fetched).
- Latency needs are low: an opportunity engine for a long-term investor (MF/FD/stocks) does not need tick data or websockets. EOD plus intraday snapshots every 15–60 minutes are plenty, which keeps the engine within free or low tiers.

### Gaps
- AMFI's own NAV file could not be fetched (host blocked). Unverified, prior knowledge: AMFI publishes all latest NAVs as a semicolon-delimited text file at `https://www.amfiindia.com/spages/NAVAll.txt`, typically refreshed through the evening (NAV deadline about 11 pm for most schemes; liquid funds publish on calendar days). It also offers a historical NAV download by date range on its portal (`portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx`). AMFI's terms of reuse were not checked.
- NSE bhavcopy files: not verified this session. Unverified, prior knowledge: NSE moved its equity and F&O bhavcopy to the "UDiFF" common format in July 2024, and older `cmDDMMMYYYYbhav.csv.zip` archive URLs stopped updating. The archive host (`nsearchives.nseindia.com`) requires browser-like headers and cookies, and the same Terms of Use apply. BSE also publishes a daily equity bhavcopy (prior knowledge, not checked).
- No primary source found for Dhan, Upstox, Angel One SmartAPI, Fyers or ICICI Breeze market-data pricing. Prior knowledge (unverified): several of these brokers offer free API and market data to their own account holders, which could be cheaper than Kite Connect if the user already holds an account there.
- No official rate limits were found for any of these feeds. The Kite Connect docs (prior knowledge: about 3 requests/second on historical data) should be checked.
- Yahoo Finance's current terms and India coverage reliability were not researched.

## Fundamentals and corporate data: Screener.in, Trendlyne, Tickertape, Tijori, exchange filings and XBRL, shareholding, bulk/block deals, SAST/PIT; MF portfolios, TER, rolling returns

### Takeaway
None of the retail research sites (Screener, Tickertape, Tijori) offers a public self-serve API, and Trendlyne's API status is unknown. The defensible structured source is the exchanges' own disclosures: NSE publishes official RSS feeds for announcements, financial results, board meetings, corporate actions and insider trading, and results are filed in XBRL under SEBI rules. Ratio computation (P/E, ROE and so on) has to be done by the engine itself from filings and prices.

### Cited Findings
- NSE runs an official RSS page listing feeds for corporate announcements, financial results, board meetings, corporate actions, insider trading and related-party transactions. — [NSE RSS feeds](https://www.nseindia.com/static/rss-feed)
- A third-party feed directory lists BSE XML/RSS feeds (Latest Corporate Announcements, Financial Results, Board Meetings, Corporate Actions), but flags some as broken or stale ("defekt oder veraltet"). — [feeder.co BSE feeds](https://feeder.co/de/discover/site/bseindia.com)
- NSE has pushed XBRL for significant business announcements. Quarterly results must be filed in XBRL under SEBI rules, and on BSE the shareholding pattern, corporate governance report and voting results are mandatorily filed in XBRL. — [KS&K newsletter](https://ksandk.com/newsletter/nse-on/); [S.S. Rana on BSE CAFS](https://ssrana.in/articles/bombay-stock-exchange-sets-up-new-corporate-announcement-filing-system/)
- A paid Apify actor scrapes NSE/BSE announcements and exposes a `has_xbrl` flag. It is a scraper, not an official API. — [Apify NSE/BSE announcements scraper](https://apify.com/nexgendata/nse-bse-announcements)
- Tijori has "no documented self-serve public API", and community projects "require personal logins and browser automation; they are not production infrastructure". (The source is a competing API vendor, so treat it with caution.) — [Drishti blog](https://drishti.manasija.in/blog/does-tijori-have-an-api-alternatives-for-builders)
- A comparison site lists API access as "No" for both Screener.in and Tickertape. — [FindMyMoat Screener vs Tickertape](https://www.findmymoat.com/vs/screener-in-vs-tickertape)
- Retail tool landscape guides for 2026 (Screener, Trendlyne, Tickertape, Tijori, Strike and others) describe these as UI products rather than data feeds. — [Winvesta 2026 guide](https://www.winvesta.in/blog/investors/fundamental-analysis-tools-and-screeners-2026-guide); [Strike Screener alternatives](https://www.strike.money/reviews/screener-alternatives)
- BSE exposes disclosure pages such as encumbrance (pledge) data per scrip on bseindia.com. — [BSE encumbrance](https://bseindia.com/corporates/encumbrance.aspx?scripcd=0)

### Inferences
- A personal engine can legitimately subscribe to the exchange RSS feeds (RSS is designed for automated polling). It would poll every 15–30 minutes on the backend, filter by the user's watchlist and holdings, and fetch the linked PDF or XBRL only for matches. This is the cleanest "event" source.
- Screener.in does allow logged-in users to export company data to Excel (prior knowledge, unverified). For a single user, a manual export imported into the app is a ToS-safe way to seed fundamentals. Automated scraping of Screener, Tickertape or Tijori should be avoided.
- XBRL quarterly results are the best route to deterministic fundamentals: parse revenue, PAT, EPS and similar items from the XBRL instance and compute ratios locally. Numbers then come from filings, never from an LLM.
- Shareholding pattern (quarterly), SAST and PIT insider disclosures, pledges and bulk/block deals are published by the exchanges as web tables or reports. Pulling them automatically means touching nseindia.com or bseindia.com, so the same ToS caveat applies. The insider-trading RSS feed is the safer route.

### Gaps
- No official NSE/BSE API documentation or rate limits were found for announcements, shareholding, SAST/PIT, or bulk/block deals. Prior knowledge (unverified): NSE publishes daily bulk and block deal CSVs and BSE has equivalent pages, while BSE's website is backed by an unofficial JSON API (`api.bseindia.com`) that community libraries call.
- Trendlyne's API offering and terms were not found. Prior knowledge: Trendlyne sells data and widgets to businesses, but no personal API tier was confirmed.
- MF portfolio disclosures: not researched directly (AMFI blocked). Prior knowledge (unverified): SEBI requires AMCs to publish monthly portfolios (Excel) on their own websites, and AMFI aggregates them. Total Expense Ratio (TER) is disclosed daily per scheme on AMC websites, and AMFI hosts a TER page. There is no unified free API for these, so expect per-AMC Excel parsing. Rolling returns are best computed locally from NAV history (mfapi or AMFI) rather than sourced.
- The ToS of Screener.in, Trendlyne, Tickertape and Tijori were not read directly.

## FD rate data: sources for comparing bank, SFB and NBFC FD rates

### Takeaway
There is no free official API for FD rates. Practical sources are bank and SFB published rate sheets, which an RBI rule now makes more uniform and more frequently published for SFBs, plus aggregators such as Stable Money and Paisabazaar. FD rates change rarely (monthly at most), so a small hand-curated or scraped dataset refreshed weekly is adequate.

### Cited Findings
- RBI amended rules so that from 1 October (2026), small finance banks must offer the same interest rate on identical retail FDs across all branches. — [Business Today, 5 Aug 2026](https://www.businesstoday.in/personal-finance/investment/story/rbi-bars-small-finance-banks-from-offering-different-fd-rates-for-identical-deposits-547216-2026-08-05)
- SFBs will also be required to publish bulk FD interest rates on their websites every business day. — [The CSR Journal](https://thecsrjournal.in/rbi-implements-new-fd-rules-for-small-finance-banks-effective-october-1/)
- Aggregator snapshot (Aug 2025, Paisabazaar-compiled): slice Small Finance Bank at 8.50% for 18 months, with Suryoday and Jana SFBs above 8% at longer tenures. — [Business Standard, Aug 2025](https://www.business-standard.com/amp/finance/personal-finance/fixed-deposits-after-rbi-holds-repo-rate-steady-banks-with-best-returns-125080700567_1.html)
- Aggregators like Stable Money publish SFB FD rate comparison tables. — [Stable Money SFB FD rates](https://stablemoney.in/blog/bn/small-finance-bank-fd-rates)
- Context on SFB risk versus 9%+ rates. — [Cafemutual](https://cafemutual.com/news/press-news/29223-should-you-invest-in-fixed-deposits-of-small-finance-banks-promising-over-9-returns)

### Inferences
- The engine should keep FD rates as versioned data (institution, tenure bucket, general/senior rate, effective date, source URL). This mirrors the repo rule that rates live in versioned data, not code.
- A useful FD rule is: alert when a DICGC-insured bank or SFB offers more than X bps over the user's current FD at a comparable tenure, net of premature-withdrawal penalty and tax slab. The arithmetic is deterministic and the data refresh is weekly.
- NBFC and corporate FDs (for example Bajaj Finance, Shriram) are not DICGC-insured. They should be flagged with credit rating (from CRISIL/ICRA/CARE) rather than compared purely on rate.

### Gaps
- No free structured API for bank FD rates was found. RBI DBIE publishes aggregate term-deposit rate ranges (weighted average domestic term deposit rates, WADTDR) but not per-bank retail cards (prior knowledge, unverified).
- The terms of Stable Money and Paisabazaar for automated reuse were not checked.

## News, sentiment, event feeds and macro data (RSS, exchange announcements, RBI DBIE, MOSPI)

### Takeaway
Exchange RSS feeds (NSE official, BSE XML) are the backbone for corporate events. For macro data, RBI DBIE and MOSPI are the official sources. jugaad-data can pull some RBI data. No official API documentation for DBIE was found this session.

### Cited Findings
- NSE official RSS feeds cover announcements, financial results, board meetings, corporate actions, insider trading and related-party transactions. — [NSE RSS feeds](https://www.nseindia.com/static/rss-feed)
- jugaad-data can download "economic data" from RBI websites in addition to NSE stock data. — [jugaad-data on PyPI](https://pypi.org/project/jugaad-data)

### Inferences
- Event-driven scanning (results, dividends, buybacks, pledges, insider buys, rating changes) for the user's holdings and watchlist gives the highest signal per rupee. It is all RSS or disclosure based and needs no paid market data.
- News-sentiment from general media RSS (Economic Times, Moneycontrol, Business Standard and Mint all publish RSS; prior knowledge, unverified) is noisy. It is best used as context for LLM summaries, not as a trading signal (see the LLM section on evidence).

### Gaps
- RBI DBIE (`data.rbi.org.in`) API availability, MOSPI's API (prior knowledge: MOSPI launched an eSankhyiki portal with an API around 2024–25) and their terms were not verified.
- No source found on reliability and uptime of NSE/BSE RSS feeds. The BSE ones are flagged as partly stale by a third party.

## Architecture: on-device (WorkManager, Doze, foreground services) vs a small personal backend with FCM; sync, privacy and encryption for an offline-first double-entry app

### Takeaway
Android cannot reliably run an "always-on" scanner. WorkManager's floor is 15 minutes and it is a suggestion only. Standby buckets cap job runtime (as little as 10 min per 24 h in "rare", once a day in "restricted"), and an app unused for 8 days drops to "restricted" on Android 13+. The right pattern is a small personal backend (GitHub Actions cron or Cloudflare Workers cron, or a cheap VPS) that does the scanning on public market data only, then sends a high-priority FCM data message. The phone pulls the details, matches them against its private ledger and holdings locally, and remains the sole source of truth.

### Cited Findings
- Minimum periodic interval is `PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS` = 15 minutes. Under Doze and app standby, "consider the supplied interval to be a suggestion, more than a requirement." — [CommonsWare, Periodic Work](https://commonsware.com/AndroidArch/pages/chap-workmgr-014)
- Official standby-bucket limits (approximate, may change):

  | Bucket | Regular jobs | Expedited jobs | Alarms | Network |
  |---|---|---|---|---|
  | Active | up to 20 min per rolling 60 min (from Android 16; previously unlimited) | up to 30 min per 24 h | no limit | unrestricted |
  | Working set | 10 min per rolling 4 h | 15 min per 24 h | 10 per hour | unrestricted |
  | Frequent | 10 min per rolling 12 h | 10 min per 24 h | 2 per hour | unrestricted |
  | Rare | 10 min per rolling 24 h | 10 min per 24 h | 1 per hour | disabled |
  | Restricted | once per day, up to 10 min | 5 min per 24 h | 1 per day | disabled |

  — [Android power management resource limits](https://developer.android.com/topic/performance/power/power-details)
- Since Android 13, standby buckets no longer determine high-priority FCM quotas. The system downgrades high-priority messages if an app consistently sends them without showing a notification. — [Android power details](https://developer.android.com/topic/performance/power/power-details)
- On Android 13+, an app goes to the restricted bucket after 8 days without user interaction (45 days on Android 12/12L). Restricted-bucket limits apply even while charging, and are loosened only when charging, idle and on an unmetered network. Apps with active widgets are exempt, as are a few other cases. — [Android App Standby Buckets](https://developer.android.com/topic/performance/appstandby)
- The system defers work until the device leaves Doze. — [Android power details](https://developer.android.com/topic/performance/power/power-details)
- Practitioner guidance: polling fails under Doze and in the Restricted bucket, so prefer high-priority FCM for time-sensitive events. OEM battery killers (Xiaomi, Samsung and others) add further unreliability. — [ProAndroidDev, Beyond Doze](https://proandroiddev.com/beyond-doze-building-reliable-background-execution-on-modern-android-including-oem-realities-5fa0a6e05672)
- GitHub Actions is free for standard GitHub-hosted runners in public repositories. Private repos get a plan-dependent minute quota. — [GitHub Actions billing docs](https://docs.github.com/en/actions/concepts/billing-and-usage). Community sources cite about 2,000 free Linux minutes per month on the free plan. — [DEV Community](https://dev.to/0012303/github-actions-has-a-free-tier-2000-cicd-minutes-matrix-builds-and-reusable-workflows-3n4c)
- GitHub scheduled workflows are often delayed 5–30 minutes at the top of the hour and can be skipped during incidents. — [cronuru guide](https://cronuru.com/guides/github-actions-scheduled-workflows)
- Cloudflare Cron Triggers have one-minute minimum granularity, and sub-minute scheduling needs Durable Object alarms. — [DevToolBox comparison](https://devtoolbox-blue.vercel.app/pl/blog/cron-schedule-serverless-github-actions-vercel-cloudflare); see also the 2026 roundup [MerginIT free cron comparison](https://merginit.com/blog/26062026-free-cron-job-scheduler-comparison)
- "Git scraping" (cron job fetches data and commits snapshots to a repo) is an established pattern for GitHub Actions. — [swyx, Automated Data Scraping with GitHub Actions](https://swyx.io/github-scraping.md)

### Inferences
- **Recommended split ("public-data backend, private-data phone"):**
  - The backend holds only public market data plus a non-sensitive watchlist (ISINs or symbols, optionally salted and hashed if the user doesn't want even holdings names exposed; in practice ISINs are needed to filter feeds). It runs scans (price/NAV refresh, RSS event filter, FD rate refresh, rule evaluation) and writes compact "signal" records. It sends a high-priority FCM *data* message only when a signal fires, and the phone must then post a user-visible notification so Android 13+ does not downgrade future messages.
  - On FCM receipt the phone runs an expedited WorkManager job. It fetches the signal payload, joins it locally with Room data (cost basis, tax lots, LTCG/STCG holding periods, allocation drift, emergency-fund runway) and decides whether to surface it. Portfolio-aware logic (tax-loss harvesting, rebalancing, FD-ladder gaps) stays on-device, so the ledger never leaves the phone.
  - A daily or 6-hourly WorkManager periodic job is the fallback when FCM is unavailable (de-Googled phones, OEM killers). A home-screen widget both shows signals and exempts the app from bucket restrictions, per Android docs.
- **Hosting options by cost:**
  - A GitHub Actions cron in a private repo costs nothing for EOD and hourly jobs, with jitter and skips. Repo secrets hold the broker API token and FCM key.
  - Cloudflare Workers cron suits lightweight RSS polling every 1–15 minutes. Python data libraries such as pandas don't run there, so it is a fetch-and-filter tier.
  - A small VPS (for example a Rs 300–600/month class box; price not verified) or Google Cloud Run job suits websocket feeds or heavier backtests.
- **Kite Connect wrinkle:** access tokens expire daily and require an interactive login (prior knowledge, unverified), which complicates fully headless backends. A pure EOD engine on public files plus mfapi avoids this.
- **Sync and privacy for the double-entry ledger:** avoid server-side sync of the ledger. If backup or sync is needed, use client-side encryption (for example SQLCipher for Room at rest, plus an encrypted export to the user's own Google Drive app-data folder) so the backend never sees plaintext journal entries. Signals flowing to the phone are public-data derived, so they need integrity (signing) more than confidentiality. A key held in Android Keystore can verify an HMAC from the backend. Library and spec details (SQLCipher licensing, Drive appDataFolder quotas) were not verified this session.
- Foreground services are not appropriate for a scanner. Android 14+ requires a declared foreground-service type, and "data sync" type services face time limits on Android 15+ (prior knowledge, unverified).

### Gaps
- Cloudflare Workers free-plan limits (requests per day, CPU ms, number of cron triggers) were not confirmed from Cloudflare docs.
- Exact FCM rate limits and payload size (prior knowledge: 4 KB data payload) were not verified.
- No sourced cost figures for Indian-region VPS or Cloud Run free tier.
- Android 15 foreground service time-limit specifics were not fetched.

## Using LLMs (for example the Claude API) in the engine: summarisation, explanations, agentic research, tool use; costs, pitfalls, deterministic-numbers patterns; evidence on LLM stock-picking

### Takeaway
Use LLMs as narrators and summarisers on top of deterministic signals, not as the signal generator. Academic evidence (2023–2026) shows LLM return forecasts over-extrapolate, are over-optimistic and are contaminated by look-ahead bias and memorisation. The engine should compute every number in code, pass the numbers to the LLM as structured tool output, and validate that any number in the generated text matches the input. At current Claude prices, summarising a handful of filings per day costs cents per month with a small model plus batch and caching.

### Cited Findings
- Chen, Green, Gulen and Zhou (2025): GPT-4o return forecasts "overextrapolate, placing excessive weight on recent performance" while actual stock returns show short-term reversal, and forecasts were higher than both historical means and realised returns. The patterns persisted in post-training samples. — [UAlberta PDF, "What Does ChatGPT Make of Historical Stock Returns?"](https://www.ualberta.ca/en/finance-department/media-library/llm-bias-green.pdf); [AFA listing](https://afajof.org/management/viewp.php?n=170324); [Markus' Academy summary](https://markusacademy.substack.com/p/chatgpts-stock-return-biases)
- Lopez-Lira and Tang: GPT sentiment scores on headlines predicted next-day US stock returns. This is the main early return-predictability paper, and its sample is US-only. — [arXiv 2304.07619](https://arxiv.org/html/2304.07619v6); [Larry Swedroe summary](https://larryswedroe.substack.com/p/can-chatgpt-forecast-stock-price)
- Glasserman and Lin: in-sample, anonymised headlines outperformed, so the "distraction effect" outweighed look-ahead bias, and out-of-sample look-ahead bias was not a concern but distraction remained. — [arXiv 2309.17322](https://arxiv.org/pdf/2309.17322)
- Gao et al. (2025) propose a "Lookahead Propensity" test: a positive correlation between prompt familiarity and forecast accuracy signals look-ahead bias, which is stronger in small caps. — [arXiv 2512.23847](https://arxiv.org/html/2512.23847v1)
- Look-Ahead-Bench (2026) is a benchmark for look-ahead bias in point-in-time finance LLMs. Its results were not reviewed. — [arXiv 2601.13770](https://arxiv.org/pdf/2601.13770)
- Lopez-Lira, Tang and Zhu (2025) show that LLMs memorise historical financial data, which undermines naive LLM backtests (as cited in Chen et al.). — [UAlberta PDF](https://www.ualberta.ca/en/finance-department/media-library/llm-bias-green.pdf)
- Claude API pricing as listed on claude.com/pricing at research time (per million tokens, input / output):

  | Model | Input | Output |
  |---|---|---|
  | Fable 5.1 | $10 | $50 |
  | Opus 5.5 | $4 | $20 |
  | Sonnet 5.5 | $2 | $10 |
  | Haiku 5.5 (≤100K-token prompts) | $0.10 | $0.50 |
  | Haiku 5.5 (>100K-token prompts) | $0.50 | $2.50 |

  Batch processing saves 50%. Prompt-cache writes are 1.25x input and cache reads are 0.025x–0.1x input (5-minute TTL). The web search tool costs $10 per 1,000 searches plus tokens. — [Claude pricing](https://claude.com/pricing)

### Inferences
- **Pattern: "numbers in code, words from the model."**
  1. A deterministic rules engine (Kotlin on-device or Python on the backend) computes signals: valuation versus own 5–10 year history, drawdown from high, SIP top-up triggers, rebalancing drift, LTCG harvesting headroom, FD rate gaps, expense-ratio switches (regular to direct), and so on. Each signal is a typed record with all inputs.
  2. The LLM receives the record as JSON (tool result) and writes a 2–4 sentence "why this, why now, what could go wrong" explanation. The prompt instructs it to use only the supplied numbers.
  3. A post-validator extracts every number or percent from the text and rejects or regenerates if any isn't present in the input record, within rounding. Use structured output (JSON schema) with explanation fields separate from numeric fields.
  4. Store the explanation alongside the signal with model ID and prompt version for auditability. This matches the repo's versioned-data ethos.
- **Filing and news summarisation:** feed the exchange announcement PDF or XBRL to a small model (Haiku-class) for "what changed, material or not, which holding is affected". At about 5K input and 300 output tokens per filing, 50 filings/day is roughly 7.5M tokens/month, about $0.75 input + $0.23 output on Haiku 5.5 before the 50% batch discount, i.e. under $1/month. This is a computed estimate from the listed prices; actual token counts per filing are not measured.
- **Agentic research loops** (the model calling tools such as `get_fundamentals`, `get_price_history`, `get_filings` and a web search) are useful for on-demand "deep dive on X" requests, triggered from the phone and run on the backend. They are too costly and non-deterministic for continuous scanning. Use a larger model only on demand, and budget per request (web search at $10/1K adds up).
- **Privacy:** sending only public data and signal records to the LLM keeps the user's ledger private. If portfolio-aware explanations are wanted, send aggregated or relative figures (for example "allocation to small caps 38% vs target 25%") rather than raw account data, or generate those sentences with templates on-device.
- **Evidence summary for product framing:** no credible evidence supports LLMs as standalone stock pickers. The literature points to biases (extrapolation, optimism, memorisation), so the Invest tab should present LLM text as explanation of rule-based signals, with disclaimers, and never as advice.
- **Regulatory note (unverified, prior knowledge):** SEBI regulates investment advice (Investment Adviser and Research Analyst regulations). A personal tool for the owner's own use is fine, but distributing recommendations to others could fall under these regulations. This was not researched this session.

### Gaps
- No India-specific study of LLM stock-picking or LLM sentiment on NSE/BSE stocks was found.
- Live performance data from public "AI fund" or LLM-agent portfolios (for example the 2024–25 LLM trading competitions) was not gathered.
- Claude model names and prices were taken verbatim from the pricing page fetched on 29 Oct 2026. Re-check before implementation, as pricing changes.

## Open-source projects and libraries for Indian markets and portfolio analytics

### Takeaway
jugaad-data is the most established Python library for NSE bhavcopy, history and RBI data, with built-in caching to avoid blocks. It depends on the NSE website, so the NSE ToS caveat applies. Backtesting and portfolio libraries (vectorbt, backtrader, zipline-reloaded, QuantStats, PyPortfolioOpt, Riskfolio-Lib) are market-agnostic and work on any price DataFrame, but none were verified this session.

### Cited Findings
- jugaad-data downloads historical and live stock and index data plus economic data from NSE and RBI websites. It has `bhavcopy_save` and F&O bhavcopy functions, supports the new NSE website (older-site libraries "might stop working"), and has built-in caching "to avoid unnecessary requests to NSE's website and getting blocked". PyPI shows versions up to 0.33.0. — [jugaad-data on PyPI](https://pypi.org/project/jugaad-data); [v0.33.0](https://pypi.org/project/jugaad-data/0.33.0/)

### Inferences
- Use the Python libraries on the backend tier only. The Android app should consume the backend's normalised JSON or Parquet output, or compute its own indicators in Kotlin, rather than embedding Python.
- For MF analytics (rolling returns, XIRR, Sharpe, Sortino, drawdown, category comparison), QuantStats-style metrics are easy to reimplement in Kotlin over NAV series. Doing that keeps the phone self-sufficient offline and aligns with integer-paise, deterministic-math rules (convert to double only for ratios, never for stored money).

### Gaps
- nselib, nsepython, vectorbt, backtrader, zipline-reloaded, QuantStats, PyPortfolioOpt and Riskfolio-Lib were not checked this session (maintenance status, licence, India calendar support). Prior knowledge (unverified): nsepython and nselib are community NSE wrappers with the same ToS exposure; backtrader is largely unmaintained since about 2021; zipline-reloaded is maintained by Stefan Jansen but needs a custom NSE calendar and bundle; vectorbt has an open-source version and a paid "PRO"; PyPortfolioOpt (MIT) and Riskfolio-Lib (BSD) are active portfolio-optimisation libraries; QuantStats (Apache-2.0) generates tearsheets.
- Kotlin/JVM equivalents (for example ta4j for technical indicators) were not researched.
