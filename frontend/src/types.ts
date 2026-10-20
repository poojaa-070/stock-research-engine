export interface CompanySummary {
  id: number;
  name: string;
  nseSymbol: string;
  bseCode: string;
  sector: string;
  industry: string;
}

export interface Evidence {
  statement: string;
  sourceRef: string;
  sentiment: "positive" | "neutral" | "negative";
}

export interface CategoryScore {
  category: string;
  score: number;
  dataCompleteness: number;
  evidence: Evidence[];
}

export interface PricePoint {
  date: string;
  close: number;
}

export interface KeyRatios {
  roePercent: number | null;
  rocePercent: number | null;
  debtToEquity: number | null;
  currentRatio: number | null;
  interestCoverageX: number | null;
  peRatio: number | null;
  pbRatio: number | null;
  pegRatio: number | null;
  evEbitda: number | null;
  dividendYieldPercent: number | null;
  epsGrowthPercent3y: number | null;
  revenueGrowthPercent3y: number | null;
  promoterHoldingPercent: number | null;
  promoterHoldingTrend: "improving" | "declining" | "flat" | "n/a";
}

export interface QuarterlyResult {
  periodEndDate: string;
  revenue: number | null;
  netProfit: number | null;
  operatingProfit: number | null;
  operatingMarginPercent: number | null;
  revenueYoyPercent: number | null;
  netProfitYoyPercent: number | null;
  comparedToPeriodEndDate: string | null;
}

export interface TechnicalSnapshot {
  currentPrice: number | null;
  week52High: number | null;
  week52Low: number | null;
  percentFrom52wHigh: number | null;
  dma50: number | null;
  dma200: number | null;
  rsi14: number | null;
}

export interface PeerSnapshot {
  companyId: number | null; // null for a peer that only came from a live scrape, not yet in our DB
  name: string;
  nseSymbol: string;
  peRatio: number | null;
  rocePercent: number | null;
  overallScore: number | null;
  cmp: number | null;   // live current market price, when the peer came from a live sector scrape
  isLive: boolean;       // true if this row came from a live Screener peer-comparison scrape
}

export interface NewsItem {
  headline: string;
  source: string;
  publishedAt: string;
  url: string | null;
}

export interface ReportResponse {
  reportId: number;
  companyId: number;
  companyName: string;
  nseSymbol: string;
  generatedAt: string;
  overallScore: number;
  verdict: string;
  confidence: number;
  categories: CategoryScore[];
  narrativeSummary: string;
  disclaimer: string;
  currentPrice: number | null;
  priceIsLive: boolean;      // true if currentPrice is a live NSE quote fetched just now
  priceAsOf: string | null;  // timestamp currentPrice is accurate as of
  priceChangePercent1y: number | null;
  priceHistory: PricePoint[];
  keyRatios: KeyRatios;
  businessDescription: string | null;
  latestQuarter: QuarterlyResult | null;
  technicalSnapshot: TechnicalSnapshot | null;
  sectorPeers: PeerSnapshot[];
  recentNews: NewsItem[];
  bullCase: string;
  bearCase: string;

  // ---- v2 ----
  sector: string | null;
  industry: string | null;
  liveQuote: LiveQuote | null;
  financialHistory: FinancialPoint[];
  shareholdingHistory: ShareholdingPoint[];
  dataFreshness: DataSourceStatus[];
  narrativeSource: "ai" | "rule-based";
  qualitativeFlagsAvailable: boolean;
  categoryWeights: Record<string, number>;
  servedFromCache: boolean;
}

export interface LiveQuote {
  change: number | null;
  percentChange: number | null;
  previousClose: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  marketState: string | null;
}

export interface FinancialPoint {
  periodEndDate: string;
  revenue: number | null;
  netProfit: number | null;
  operatingProfit: number | null;
  eps: number | null;
  operatingMarginPercent: number | null;
  netMarginPercent: number | null;
}

export interface ShareholdingPoint {
  periodEndDate: string;
  promoterPct: number | null;
  fiiPct: number | null;
  diiPct: number | null;
  publicPct: number | null;
}

export interface DataSourceStatus {
  source: "screener" | "nse" | string;
  lastScrapedAt: string | null;
  status: string | null;
}

export interface ReportHistoryPoint {
  reportId: number;
  generatedAt: string;
  overallScore: number;
  verdict: string;
  confidence: number;
  categoryScores: Record<string, number>;
}

export interface WhatChanged {
  companyId: number;
  previousReportAt: string;
  currentReportAt: string;
  previousOverallScore: number;
  currentOverallScore: number;
  categoryDeltas: { category: string; previousScore: number; currentScore: number; delta: number }[];
  previousVerdict: string | null;
  currentVerdict: string | null;
  evidenceChanges: { category: string; added: string[]; removed: string[] }[];
}

export interface MultiCompareResponse {
  companies: { company: CompanySummary; overallScore: number | null; verdict: string | null }[];
  rows: { metricLabel: string; values: string[]; bestIndices: number[]; higherIsBetter: boolean | null }[];
}

export interface WatchlistItem {
  company: CompanySummary;
  note: string | null;
  addedAt: string;
  latestScore: number | null;
  latestVerdict: string | null;
  lastReportAt: string | null;
  scoreChange: number | null;
  lastClose: number | null;
  lastCloseDate: string | null;
}

export interface Methodology {
  categoryWeights: Record<string, number>;
  rules: {
    category: string;
    metricKey: string;
    sector: string | null;
    thresholds: number[];
    scores: number[];
    weight: number;
    higherIsBetter: boolean;
  }[];
  flags: { flagKey: string; category: string; description: string; effectAtHighConfidence: number }[];
  confidenceMultipliers: Record<string, number>;
  verdictBands: { minScore: number; verdict: string }[];
  skipLeverageForFinancials: boolean;
}

export interface Health {
  status: "ok" | "degraded";
  database: "up" | "down";
  scraper: "up" | "down";
  ai: "configured" | "not configured";
  checkedAt: string;
}

export interface CompareRow {
  metricLabel: string;
  companyAValue: string;
  companyBValue: string;
  winner: "A" | "B" | "Tie" | "N/A";
}

export interface CompareResponse {
  companyA: CompanySummary;
  companyB: CompanySummary;
  overallScoreA: number;
  overallScoreB: number;
  overallWinner: string;
  rows: CompareRow[];
}

export const CATEGORY_ORDER = [
  "financial_health",
  "business_quality",
  "growth",
  "valuation",
  "management",
  "risk",
  "momentum",
];

export const CATEGORY_LABELS: Record<string, string> = {
  financial_health: "Financial Health",
  business_quality: "Business Quality",
  growth: "Growth",
  valuation: "Valuation",
  management: "Management",
  risk: "Risk",
  momentum: "Momentum",
};
