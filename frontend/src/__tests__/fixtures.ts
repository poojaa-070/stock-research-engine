import type { ReportResponse } from "../types";

export const report: ReportResponse = {
  reportId: 12,
  companyId: 3,
  companyName: "Tata Consultancy Services Ltd",
  nseSymbol: "TCS",
  generatedAt: "2026-10-12T09:30:00",
  overallScore: 76.2,
  verdict: "🟢 Worth Researching",
  confidence: 0.73,
  categories: [
    { category: "financial_health", score: 90.6, dataCompleteness: 1, evidence: [
      { statement: "Debt-to-equity is 0.02 -> scored 100/100", sourceRef: "ratios table, latest period", sentiment: "positive" },
    ] },
    { category: "business_quality", score: 93.8, dataCompleteness: 0.6, evidence: [
      { statement: "Latest ROCE is 58.6% -> scored 100/100 (IT bands)", sourceRef: "ratios table", sentiment: "positive" },
    ] },
    { category: "growth", score: 71.2, dataCompleteness: 1, evidence: [] },
    { category: "valuation", score: 30.1, dataCompleteness: 0.6, evidence: [
      { statement: "PEG ratio is 2.6 -> scored 24/100", sourceRef: "ratios table", sentiment: "negative" },
    ] },
    { category: "management", score: 88.6, dataCompleteness: 0.6, evidence: [] },
    { category: "risk", score: 0, dataCompleteness: 0, evidence: [] },
    { category: "momentum", score: 75.4, dataCompleteness: 1, evidence: [] },
  ],
  narrativeSummary: "TCS scores 76/100 overall.",
  disclaimer: "This is an analytical research tool, not personalized financial advice.",
  currentPrice: 3913.82,
  priceIsLive: true,
  priceAsOf: "12-Oct-2026 15:30",
  priceChangePercent1y: 4.2,
  priceHistory: [
    { date: "2026-08-31", close: 3917.04 },
    { date: "2026-09-01", close: 3913.82 },
  ],
  keyRatios: {
    roePercent: 43.2, rocePercent: 58.6, debtToEquity: 0.02, currentRatio: 2.39, interestCoverageX: 453.6,
    peRatio: 27.8, pbRatio: 12.1, pegRatio: 2.6, evEbitda: 19.5, dividendYieldPercent: 1.6,
    epsGrowthPercent3y: 6.4, revenueGrowthPercent3y: 7.3, promoterHoldingPercent: 71.8, promoterHoldingTrend: "flat",
  },
  businessDescription: "India's largest IT services company.",
  latestQuarter: null,
  technicalSnapshot: { currentPrice: 3913.82, week52High: 4000, week52Low: 3500, percentFrom52wHigh: -2.2, dma50: 3880, dma200: 3870, rsi14: 55 },
  sectorPeers: [
    { companyId: null, name: "Infosys", nseSymbol: "INFY", peRatio: 24.1, rocePercent: 39.9, overallScore: null, cmp: 1500, isLive: true },
  ],
  recentNews: [],
  bullCase: "Strong balance sheet.",
  bearCase: "Valuation is rich.",
  sector: "IT",
  industry: "IT Services",
  liveQuote: { change: -3.2, percentChange: -0.08, previousClose: 3917.02, dayHigh: 3930, dayLow: 3901, marketState: "Open" },
  financialHistory: [
    { periodEndDate: "2025-03-31", revenue: 245000, netProfit: 45000, operatingProfit: 60000, eps: 124.5, operatingMarginPercent: 24.5, netMarginPercent: 18.4 },
    { periodEndDate: "2026-03-31", revenue: 262000, netProfit: 48500, operatingProfit: 63500, eps: 133.8, operatingMarginPercent: 24.2, netMarginPercent: 18.5 },
  ],
  shareholdingHistory: [
    { periodEndDate: "2025-06-30", promoterPct: 71.8, fiiPct: 12.4, diiPct: 9.1, publicPct: 6.7 },
    { periodEndDate: "2026-06-30", promoterPct: 71.8, fiiPct: 12.0, diiPct: 9.6, publicPct: 6.6 },
  ],
  dataFreshness: [{ source: "screener", lastScrapedAt: "2026-10-12T08:00:00", status: "success" }],
  narrativeSource: "rule-based",
  qualitativeFlagsAvailable: false,
  categoryWeights: { financial_health: 0.2, business_quality: 0.2, growth: 0.15, valuation: 0.2, management: 0.1, risk: 0.1, momentum: 0.05 },
  servedFromCache: false,
};

/** Routes fetch() by URL substring; unmatched URLs fail loudly. */
export function mockFetch(routes: Record<string, unknown | ((init?: RequestInit) => unknown)>) {
  const calls: { url: string; init?: RequestInit }[] = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    const key = Object.keys(routes).find((k) => url.includes(k));
    if (!key) return new Response(JSON.stringify({ message: `unmocked ${url}` }), { status: 500 });
    const val = routes[key];
    const body = typeof val === "function" ? (val as (i?: RequestInit) => unknown)(init) : val;
    if (body instanceof Response) return body;
    return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  return calls;
}
