import type { QuarterlyResult } from "../types";

function fmtCr(v: number | null): string {
  if (v === null) return "—";
  return `₹${(v / 1000).toFixed(1)}k Cr`;
}

function fmtPct(v: number | null): { text: string; tone: string } {
  if (v === null) return { text: "—", tone: "text-ledger-faint" };
  const tone = v >= 0 ? "text-signal-strong" : "text-signal-weak";
  return { text: `${v >= 0 ? "+" : ""}${v.toFixed(1)}%`, tone };
}

export default function QuarterlyResultsCard({ quarter }: { quarter: QuarterlyResult | null }) {
  if (!quarter) {
    return (
      <p className="text-sm text-ledger-faint italic">
        No quarterly results scraped yet — only annual figures are available for this company.
      </p>
    );
  }

  const revenueYoy = fmtPct(quarter.revenueYoyPercent);
  const profitYoy = fmtPct(quarter.netProfitYoyPercent);

  return (
    <div>
      <p className="text-xs text-ledger-faint mb-3">
        Quarter ended {new Date(quarter.periodEndDate).toLocaleDateString(undefined, { month: "short", year: "numeric" })}
        {quarter.comparedToPeriodEndDate && (
          <> — YoY vs {new Date(quarter.comparedToPeriodEndDate).toLocaleDateString(undefined, { month: "short", year: "numeric" })}</>
        )}
      </p>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">Revenue</p>
          <p className="font-mono text-lg tabular mt-1">{fmtCr(quarter.revenue)}</p>
          <p className={`text-xs font-mono mt-0.5 ${revenueYoy.tone}`}>{revenueYoy.text} YoY</p>
        </div>
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">Net profit</p>
          <p className="font-mono text-lg tabular mt-1">{fmtCr(quarter.netProfit)}</p>
          <p className={`text-xs font-mono mt-0.5 ${profitYoy.tone}`}>{profitYoy.text} YoY</p>
        </div>
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">Operating profit</p>
          <p className="font-mono text-lg tabular mt-1">{fmtCr(quarter.operatingProfit)}</p>
        </div>
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">Operating margin</p>
          <p className="font-mono text-lg tabular mt-1">
            {quarter.operatingMarginPercent !== null ? `${quarter.operatingMarginPercent.toFixed(1)}%` : "—"}
          </p>
        </div>
      </div>
    </div>
  );
}
