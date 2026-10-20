# API Design

Base URL (dev): `http://localhost:8080/api`

## Companies

### `GET /companies/search?q={query}`
Search by name or NSE symbol (partial, case-insensitive).
```json
[
  { "id": 1, "name": "HDFC Bank Ltd", "nseSymbol": "HDFCBANK", "bseCode": "500180", "sector": "Financial Services", "industry": "Private Bank" }
]
```

### `GET /companies/{id}`
Single company summary (same shape as above).

## Reports

### `POST /reports/generate/{companyId}`
Runs the deterministic scoring pass against whatever is currently in Postgres
for this company (the scraper is responsible for having refreshed stale data
before this is called — see `docs/ROADMAP.md` for how that handoff is wired
in the MVP). Persists a new `analysis_reports` row + its `report_evidence`
rows, and returns:

```json
{
  "reportId": 42,
  "companyId": 1,
  "companyName": "HDFC Bank Ltd",
  "nseSymbol": "HDFCBANK",
  "generatedAt": "2026-09-05T10:15:00",
  "overallScore": 70.4,
  "verdict": "🟢 Worth Researching",
  "confidence": 0.83,
  "categories": [
    {
      "category": "financial_health",
      "score": 81.2,
      "dataCompleteness": 0.75,
      "evidence": [
        { "statement": "Debt-to-equity is 0.4 -> scored 88/100", "sourceRef": "ratios table, latest period", "sentiment": "positive" }
      ]
    }
  ],
  "narrativeSummary": "HDFC Bank shows...",
  "disclaimer": "This is an analytical research tool, not personalized financial advice."
}
```

Note there is deliberately no `POST /reports` without a company id, and no
endpoint that accepts a free-text prediction request — the system only ever
scores what's in the database against the fixed rubric.

### `GET /reports/what-changed/{companyId}`
Diffs the two most recent `analysis_reports` rows for a company.
```json
{
  "companyId": 1,
  "previousReportAt": "2026-08-01T09:00:00",
  "currentReportAt": "2026-09-05T10:15:00",
  "previousOverallScore": 66.1,
  "currentOverallScore": 70.4,
  "categoryDeltas": [
    { "category": "financial_health", "previousScore": 78.0, "currentScore": 81.2, "delta": 3.2 }
  ]
}
```
Returns **HTTP 409** if fewer than 2 reports exist yet. v2 appends
`previousVerdict`, `currentVerdict` and `evidenceChanges`
(`[{category, added: [...], removed: [...]}]`, unchanged lines omitted).

## Compare

### `GET /compare?a={companyIdA}&b={companyIdB}`
Row-by-row metric comparison with a per-row winner (`"A"`, `"B"`, `"Tie"`, or
`"N/A"` when either side is missing that metric) plus an overall winner
based on `overall_score`.

```json
{
  "companyA": { "id": 1, "name": "HDFC Bank Ltd", ... },
  "companyB": { "id": 2, "name": "ICICI Bank Ltd", ... },
  "overallScoreA": 70.4,
  "overallScoreB": 68.9,
  "overallWinner": "HDFC Bank Ltd",
  "rows": [
    { "metricLabel": "P/E ratio", "companyAValue": "19.5", "companyBValue": "18.2", "winner": "B" }
  ]
}
```

## v2 endpoints

### Errors
Every error is JSON: `{status, error, message, path, timestamp}`. `message`
is written for the end user. 400 bad input · 404 unknown company / not on
Screener · 409 state conflict · 502 scraper, Screener or NSE failed.

### `GET /reports/latest/{companyId}`
What the report page loads. Returns a report generated in the last
`app.reports.cache-minutes` (15) if there is one (`servedFromCache: true`),
otherwise generates. Concurrent requests for one company share one generation.

### `POST /reports/generate/{companyId}?force=true`
Unchanged without `force`, except a report from the last
`app.reports.reuse-seconds` (60) is reused. `force=true` always regenerates.

The report response appends: `sector`, `industry`, `liveQuote`
(`change, percentChange, previousClose, dayHigh, dayLow, marketState`),
`financialHistory`, `shareholdingHistory`, `dataFreshness`,
`narrativeSource` (`"ai" | "rule-based"`), `qualitativeFlagsAvailable`,
`categoryWeights`, `servedFromCache`. All v1 fields are unchanged.

### `GET /reports/history/{companyId}?limit=30`
Stored reports, oldest first:
`[{reportId, generatedAt, overallScore, verdict, confidence, categoryScores: {category: score}}]`.

### `GET /compare/multi?ids=1,2,3`
2–6 companies. `{companies: [{company, overallScore, verdict}], rows:
[{metricLabel, values: [...], bestIndices: [...], higherIsBetter}]}`.
`bestIndices` is empty on an all-way tie or when fewer than two values exist.
Companies never analysed are scored first.

### Watchlist
- `GET /watchlist` → `[{company, note, addedAt, latestScore, latestVerdict, lastReportAt, scoreChange, lastClose, lastCloseDate}]`
- `GET /watchlist/{companyId}` → `{watching: true|false}`
- `POST /watchlist/{companyId}` (optional body `{"note": "..."}`) — idempotent
- `DELETE /watchlist/{companyId}` → 204

### `GET /scoring/methodology`
The live rubric: `categoryWeights`, `rules` (with `sector` overrides),
`flags` with their signed effect, `confidenceMultipliers`, `verdictBands`,
`skipLeverageForFinancials`.

### `GET /health`
`{status, database, scraper, ai, checkedAt}`; HTTP 503 only if the database is down.

## What's intentionally NOT an endpoint

- No "predict price" or "buy/sell signal" endpoint — that's the one hard
  constraint from the brief, enforced at the API surface, not just in the
  prompt text.
- No endpoint to submit a custom weighting per request; category weights
  live in `application.yml` + `scoring_rules` so every user sees the same
  rubric applied to the same facts. v2's "Try your own weights" panel is
  client-side arithmetic only, labelled as a personal view, and never saved.
- No auth/session endpoints yet. The watchlist is single-user. Add auth
  before any multi-user deployment.
