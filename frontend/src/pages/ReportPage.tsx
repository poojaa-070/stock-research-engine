import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Building2, BarChart3, Landmark, Users, Activity, Newspaper, Swords, Scale, Radio,
  RefreshCw, History, LineChart as LineChartIcon, PieChart, SlidersHorizontal, GitCompareArrows,
} from "lucide-react";
import { generateReport, getLatestReport, errorMessage } from "../api/client";
import StockLoader from "../components/StockLoader";
import type { ReportResponse } from "../types";
import ScoreRing from "../components/ScoreRing";
import VerdictBadge from "../components/VerdictBadge";
import CategoryRow from "../components/CategoryRow";
import HighlightsPanel from "../components/HighlightsPanel";
import PriceChart from "../components/PriceChart";
import KeyRatiosGrid from "../components/KeyRatiosGrid";
import Section from "../components/Section";
import BusinessOverview from "../components/BusinessOverview";
import QuarterlyResultsCard from "../components/QuarterlyResultsCard";
import TechnicalSnapshotCard from "../components/TechnicalSnapshotCard";
import PeerComparisonTable from "../components/PeerComparisonTable";
import RecentNewsFeed from "../components/RecentNewsFeed";
import BullBearCase from "../components/BullBearCase";
import WatchButton from "../components/WatchButton";
import WhatChangedPanel from "../components/WhatChangedPanel";
import ScoreHistoryChart from "../components/ScoreHistoryChart";
import FinancialHistoryChart from "../components/FinancialHistoryChart";
import ShareholdingChart from "../components/ShareholdingChart";
import WhatIfWeights from "../components/WhatIfWeights";
import ExportMenu from "../components/ExportMenu";
import DataFreshness from "../components/DataFreshness";
import { pct, timeAgo } from "../lib/format";

export default function ReportPage() {
  const { companyId } = useParams();
  const id = Number(companyId);
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [regenerating, setRegenerating] = useState(false);
  const [regenError, setRegenError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isFinite(id)) return;
    let current = true;
    setReport(null);
    setError(null);
    // GET /latest is safe to call twice (React StrictMode does in dev): the
    // backend shares one generation between concurrent requests.
    getLatestReport(id).then(
      (r) => current && setReport(r),
      (e) => current && setError(errorMessage(e)),
    );
    return () => {
      current = false;
    };
  }, [id]);

  async function regenerate() {
    setRegenerating(true);
    setRegenError(null);
    try {
      setReport(await generateReport(id, true));
    } catch (e) {
      setRegenError(errorMessage(e));
    } finally {
      setRegenerating(false);
    }
  }

  if (!Number.isFinite(id)) {
    return <div className="max-w-3xl mx-auto pt-12 px-6 text-signal-weak">That isn't a valid company link.</div>;
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto pt-12 px-6">
        <p className="text-signal-weak">Couldn't generate a report: {error}</p>
        <Link to="/" className="inline-block mt-4 text-sm text-ledger-gold hover:underline">Back to search</Link>
      </div>
    );
  }

  if (!report) {
    return (
      <StockLoader
        title="Generating report…"
        subtitle="Re-scraping this company and pulling a live quote, sector peers, and the latest fundamentals."
      />
    );
  }

  const q = report.liveQuote;
  const classification = [report.sector, report.industry].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i);

  return (
    <div className="max-w-3xl mx-auto pt-10 pb-24 px-6" style={{ animation: "fade-slide-in 0.4s ease both" }}>
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div>
          <p className="font-mono text-sm text-ledger-faint">
            {report.nseSymbol}
            {classification.length > 0 && <span className="font-body"> · {classification.join(" / ")}</span>}
          </p>
          <h1 className="font-display text-3xl mt-1">{report.companyName}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2 print:hidden">
            <WatchButton companyId={report.companyId} />
            <button
              onClick={regenerate}
              disabled={regenerating}
              className="btn-animated inline-flex items-center gap-1.5 text-xs border border-ledger-line px-2.5 py-1.5 text-ledger-faint hover:text-ledger-ink disabled:opacity-50"
            >
              <RefreshCw size={13} className={regenerating ? "animate-spin-slow" : ""} />
              {regenerating ? "Regenerating…" : "Regenerate"}
            </button>
            <Link
              to={`/compare-multi?ids=${report.companyId}`}
              className="btn-animated inline-flex items-center gap-1.5 text-xs border border-ledger-line px-2.5 py-1.5 text-ledger-faint hover:text-ledger-ink"
            >
              <GitCompareArrows size={13} /> Compare with…
            </Link>
          </div>
          {regenError && <p className="text-xs text-signal-weak mt-2">{regenError}</p>}
        </div>
        {report.currentPrice !== null && (
          <div className="text-right">
            <p className="font-mono text-2xl tabular">₹{report.currentPrice.toFixed(2)}</p>
            {q && q.change !== null && (
              <p className={`text-xs font-mono tabular ${q.change >= 0 ? "text-signal-strong" : "text-signal-weak"}`}>
                {q.change >= 0 ? "+" : ""}
                {q.change.toFixed(2)} ({pct(q.percentChange, 2, true)}) today
              </p>
            )}
            <p className="flex items-center justify-end gap-1 text-[10px] text-ledger-faint mt-0.5">
              {report.priceIsLive ? (
                <>
                  <Radio size={10} className="text-signal-strong animate-pulse-slow" />
                  <span className="text-signal-strong">LIVE</span>
                </>
              ) : (
                <span>last close</span>
              )}
              {report.priceAsOf && <span>· as of {report.priceAsOf}</span>}
            </p>
            {q && q.dayLow !== null && q.dayHigh !== null && (
              <p className="text-[10px] text-ledger-faint font-mono">
                Day range ₹{q.dayLow.toFixed(2)}–₹{q.dayHigh.toFixed(2)}
              </p>
            )}
            {report.priceChangePercent1y !== null && (
              <p className={`text-xs font-mono tabular ${report.priceChangePercent1y >= 0 ? "text-signal-strong" : "text-signal-weak"}`}>
                {pct(report.priceChangePercent1y, 1, true)} over 1 year
              </p>
            )}
          </div>
        )}
      </div>

      {/* Hero: the score IS the headline of this whole report */}
      <div className="mt-8 border border-ledger-line bg-ledger-panel/40 p-8 flex flex-col sm:flex-row items-center sm:items-start gap-8">
        <ScoreRing score={report.overallScore} />
        <div className="flex-1 text-center sm:text-left">
          <div className="flex justify-center sm:justify-start">
            <VerdictBadge verdict={report.verdict} />
          </div>
          <p className="mt-4 text-sm text-ledger-faint max-w-md">
            Confidence <span className="text-ledger-ink font-mono">{Math.round(report.confidence * 100)}%</span>{" "}
            — this reflects how much of the rubric had usable data, not how good the result is.
          </p>
          <p className="mt-1 text-xs text-ledger-faint">
            Generated {new Date(report.generatedAt).toLocaleString()}
            {report.servedFromCache && <> ({timeAgo(report.generatedAt)}, reused rather than re-scraped)</>}
          </p>
          {!report.qualitativeFlagsAvailable && (
            <p className="mt-2 text-xs text-ledger-faint max-w-md">
              The qualitative checks (moat, governance, regulatory risk) didn't run, either because no AI key is
              configured or because there was no news to read. They're counted as missing data, not as a pass.
            </p>
          )}
        </div>
      </div>

      <HighlightsPanel categories={report.categories} />

      <Section icon={History} title="What changed since the last report">
        <WhatChangedPanel companyId={report.companyId} reportId={report.reportId} />
      </Section>

      <Section icon={Building2} title="Business overview">
        <BusinessOverview description={report.businessDescription} />
      </Section>

      <Section icon={BarChart3} title="Latest quarter">
        <QuarterlyResultsCard quarter={report.latestQuarter} />
      </Section>

      <KeyRatiosGrid ratios={report.keyRatios} />

      <Section icon={LineChartIcon} title="Financial history" subtitle="Annual revenue and profit, with operating margin.">
        <FinancialHistoryChart data={report.financialHistory} />
      </Section>

      {report.priceHistory.length > 0 && (
        <Section icon={Activity} title="Price, trailing year">
          <PriceChart data={report.priceHistory} />
        </Section>
      )}

      <Section icon={Landmark} title="Technical setup">
        <TechnicalSnapshotCard snapshot={report.technicalSnapshot} />
      </Section>

      <Section icon={PieChart} title="Shareholding" subtitle="Who owns the company, quarter by quarter.">
        <ShareholdingChart data={report.shareholdingHistory} />
      </Section>

      <Section
        icon={Users}
        title="Competitive position"
        subtitle="Live sector peers, fetched from the market right now. Tap a row to compare."
      >
        <PeerComparisonTable peers={report.sectorPeers} companyId={report.companyId} />
      </Section>

      <Section icon={Swords} title="Bull case / bear case">
        <BullBearCase bullCase={report.bullCase} bearCase={report.bearCase} />
      </Section>

      <Section icon={Newspaper} title="Recent developments">
        <RecentNewsFeed news={report.recentNews} />
      </Section>

      <Section icon={Scale} title="Score breakdown">
        <p className="text-sm text-ledger-faint mb-2">
          Tap a category to see the evidence behind its score.{" "}
          <Link to="/methodology" className="text-ledger-gold hover:underline print:hidden">How scoring works</Link>
        </p>
        <div className="border-t border-ledger-line">
          {report.categories.map((c) => (
            <CategoryRow key={c.category} category={c} />
          ))}
        </div>
      </Section>

      <Section icon={History} title="Score over time">
        <ScoreHistoryChart companyId={report.companyId} reportId={report.reportId} />
      </Section>

      <Section
        icon={SlidersHorizontal}
        title="Try your own weights"
        subtitle="See how the overall score would change if you cared more about some categories than others."
      >
        <div className="print:hidden">
          <WhatIfWeights
            key={report.reportId}
            categories={report.categories}
            officialWeights={report.categoryWeights}
            officialScore={report.overallScore}
          />
        </div>
      </Section>

      <div className="mt-10">
        <h2 className="font-display text-xl mb-2">Summary</h2>
        <p className="text-ledger-ink leading-relaxed text-[15px]">{report.narrativeSummary}</p>
        {report.narrativeSource === "rule-based" && (
          <p className="text-[11px] text-ledger-faint mt-2">
            Written from the scores and evidence above by a fixed template, because the AI summary wasn't available.
          </p>
        )}
      </div>

      <div className="mt-10 border-t border-ledger-line pt-4 space-y-3">
        <DataFreshness sources={report.dataFreshness} />
        <ExportMenu report={report} />
        <p className="text-xs text-ledger-faint">{report.disclaimer}</p>
      </div>
    </div>
  );
}
