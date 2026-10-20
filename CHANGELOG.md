# Changelog

## 0.2.0

Every v1 feature and endpoint is still here and behaves the same, except
where a bug fix below changes a result. Existing v1 databases upgrade in place
with `database/migrations/002_v2_enhancements.sql` (safe to run more than once).

### Scoring fixes (these change scores)

| # | Problem in 0.1.0 | Fix |
|---|---|---|
| 1 | CAGR of a figure that turned negative was `NaN`; `BandScorer` let `NaN` fall through to the **best** band, so a company whose profit became a loss scored 100 on profit growth. | A collapse into a loss is recorded as −100% (lowest band). `BandScorer` now rejects non-finite values outright. |
| 2 | `margin_stability` was seeded with thresholds in descending order. `BandScorer` assumes ascending, so stable margins scored worst. | Re-seeded ascending (migration 002). `BandScorer` now rejects non-ascending thresholds. |
| 3 | Qualitative flag pools started at 100. "No flags found" and "AI not configured" both scored a perfect 40–45% of three categories, and positive flags were clamped to no effect. | Pools start at a neutral 60 (stored in the rule's `band_scores`). When the AI layer didn't run, or there was no text to read, the slot counts as missing data instead. |
| 4 | A category with no data at all scored 0 and was averaged into the overall score. | Excluded from the overall; still lowers confidence; shown as "No data yet". |
| 5 | Banks/NBFCs were scored on debt-to-equity and debt/EBITDA, which the project already treated as inapplicable for interest coverage and current ratio. | Skipped for financial institutions. `scoring.skip-leverage-for-financials: false` restores v1 behaviour. |
| 6 | `*_cagr_3y` metrics used the whole history (often 5+ years). | A genuine ~3-year window; shorter histories are labelled with their real span. |
| 7 | RSI used a plain 14-day average; duplicated, slightly different code in two places. | Wilder's smoothing over full history, in one shared `Technicals` class. |
| 8 | ROCE trend called a 0.01pp move "improving". | 0.5pp dead band. |
| 9 | `cash_conversion` divided by a negative profit (read as excellent conversion for a loss-maker). | Only computed against a positive profit. |

### Data and scraper fixes

- **P/B was never computed for scraped companies.** Screener renders values like `₹ 250` and `₹ 12,345 Cr.`, which the number parser rejected. It now strips currency and unit decorations.
- **Debt-to-equity was never scraped.** It is now computed from the balance sheet as Borrowings / (Equity Capital + Reserves).
- **Imported companies had `sector = ""`**, so every imported company was treated as a peer of every other. Sector and industry are now parsed from Screener's classification links (existing hand-set values are kept). A blank sector now means "no peers".
- **News duplicated on every refresh.** Duplicates are removed by the migration, a unique index stops new ones, and the scraper uses `ON CONFLICT DO NOTHING`.
- Screener search now URL-encodes the query (names like `M&M` broke it).
- `1y` price change used ~10 months of data; it now uses the close from one year earlier, or shows nothing if the history is shorter. The 52-week range covers a full year.

### Backend fixes

- React StrictMode fired two report generations per page view (two scrapes, six Claude calls, two stored reports that made "what changed?" compare a report with its twin). `ReportCache` makes concurrent requests share one generation.
- Errors now map to proper status codes with a readable JSON `message`: 404 unknown company, 409 "need two reports", 400 bad input, 502 scraper/Screener/NSE failures. They were all 500s.
- Every scraper call has a timeout. v1 used `block()` with none, so a hung scraper hung report generation.
- Scraping and AI calls run outside the database transaction. Narrative, bull/bear case, live quote and live peers run concurrently.
- The AI layer skips all calls when no API key is set. It extracts JSON even when the model adds a sentence around it.
- Comparing a never-analysed company used a score of 0. A report is now generated first. Live compare no longer scrapes each company three times.
- Live peers are matched against all stored companies, not just same-sector ones. The company itself is removed from its own peer list.
- CORS origins are configurable (`CORS_ALLOWED_ORIGINS`).
- `pom.xml` had `<n>` instead of `<name>`.

### New features

- **Watchlist**: track companies, see each one's latest score and how it moved since the previous report. Optional scheduled re-scoring (`WATCHLIST_REFRESH_CRON`).
- **What changed?**: the existing endpoint now has a UI. It shows category deltas, verdict changes, and the evidence lines that appeared or dropped out (ROADMAP Phase 4).
- **Score over time** chart from every stored report.
- **N-way comparison** of 2–6 companies with the best value per row marked (ROADMAP Phase 5). The URL is shareable.
- **Sector-specific scoring bands** (ROADMAP Phase 3): a `scoring_rules` row with a `sector` overrides the default for that sector. Seeded examples: P/B for Financial Services, ROCE for IT.
- **Methodology page**: the live rubric, with weights, every band, flag effects and verdict bands, read from the same rules the engine uses.
- **What-if weights**: re-weight the categories and see the resulting overall. Client-side only, never saved, clearly labelled as a personal view (as `API_DESIGN.md` required).
- **Financial history** chart (revenue, net profit, operating margin) and **shareholding** chart (promoters/FII/DII/public over time).
- Report export to **Markdown**, **JSON**, and **print / PDF** (print stylesheet).
- Live intraday detail (day change, day range), sector/industry, data freshness per source, notes when the summary is rule-based or the qualitative checks didn't run.
- **Analyse** button on live peers not yet in the database (v1 showed them as dead rows).
- **Health** endpoint and a header status dot naming the missing piece (DB, scraper, AI key).
- **Regenerate** button; the report page loads via `GET /reports/latest` (reuses a report from the last 15 minutes).
- Demo data now includes FY2022–FY2024 history and 267 price sessions, so P/E-vs-history, true 3-year CAGR and the whole Momentum category work in the demo. Momentum had 0% data in v1's demo.
- Docker Compose for the whole stack.
- Pages are lazy-loaded; the first download fell from ~650 kB to ~234 kB.

### Tests (new)

- Backend: 27 JUnit tests (`BandScorer`, `Technicals`, `MetricCalculator`, `ScoringEngine`, 1-year price change).
- Scraper: 33 pytest tests, covering parsers against an HTML fixture, FastAPI endpoints, and database upserts against Postgres (`TEST_DB_NAME`).
- Frontend: 13 Vitest + Testing Library tests rendering the report, compare, methodology and watchlist pages against mocked API responses.

### New API endpoints

`GET /api/reports/latest/{id}`, `GET /api/reports/history/{id}`,
`POST /api/reports/generate/{id}?force=true`, `GET /api/compare/multi?ids=`,
`GET|POST|DELETE /api/watchlist[/{id}]`, `GET /api/scoring/methodology`,
`GET /api/health`; scraper `GET /health`. See `docs/API_DESIGN.md`.
