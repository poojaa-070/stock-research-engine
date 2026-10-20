import type { KeyRatios } from "../types";

function Stat({
  label,
  value,
  suffix = "",
  tone,
}: {
  label: string;
  value: number | string | null;
  suffix?: string;
  tone?: "positive" | "negative";
}) {
  const display = value === null || value === undefined ? "—" : `${value}${suffix}`;
  const toneClass =
    tone === "positive" ? "text-signal-strong" : tone === "negative" ? "text-signal-weak" : "text-ledger-ink";
  return (
    <div className="border border-ledger-line px-4 py-3">
      <p className="text-xs text-ledger-faint">{label}</p>
      <p className={`font-mono text-lg tabular mt-1 ${toneClass}`}>{display}</p>
    </div>
  );
}

function fmt(v: number | null, decimals = 1): string | null {
  if (v === null || v === undefined) return null;
  return v.toFixed(decimals);
}

export default function KeyRatiosGrid({ ratios }: { ratios: KeyRatios }) {
  const promoterToneMap = { improving: "positive", declining: "negative", flat: undefined, "n/a": undefined } as const;

  return (
    <div className="mt-10">
      <h2 className="font-display text-xl mb-1">Key ratios</h2>
      <p className="text-sm text-ledger-faint mb-3">
        The raw numbers behind the scores above — dashes mean the data
        hasn't been scraped yet, not zero.
      </p>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Stat label="ROE" value={fmt(ratios.roePercent)} suffix="%" />
        <Stat label="ROCE" value={fmt(ratios.rocePercent)} suffix="%" />
        <Stat label="Debt / Equity" value={fmt(ratios.debtToEquity, 2)} />
        <Stat label="Current ratio" value={fmt(ratios.currentRatio, 2)} />
        <Stat label="Interest coverage" value={fmt(ratios.interestCoverageX, 1)} suffix="x" />
        <Stat label="P/E" value={fmt(ratios.peRatio)} />
        <Stat label="P/B" value={fmt(ratios.pbRatio, 2)} />
        <Stat label="PEG" value={fmt(ratios.pegRatio, 2)} />
        <Stat label="EV/EBITDA" value={fmt(ratios.evEbitda)} />
        <Stat label="Dividend yield" value={fmt(ratios.dividendYieldPercent)} suffix="%" />
        <Stat
          label="EPS growth (CAGR)"
          value={fmt(ratios.epsGrowthPercent3y)}
          suffix="%"
          tone={ratios.epsGrowthPercent3y !== null ? (ratios.epsGrowthPercent3y >= 0 ? "positive" : "negative") : undefined}
        />
        <Stat
          label="Revenue growth (CAGR)"
          value={fmt(ratios.revenueGrowthPercent3y)}
          suffix="%"
          tone={ratios.revenueGrowthPercent3y !== null ? (ratios.revenueGrowthPercent3y >= 0 ? "positive" : "negative") : undefined}
        />
        <Stat
          label="Promoter holding"
          value={fmt(ratios.promoterHoldingPercent)}
          suffix="%"
        />
        <Stat
          label="Promoter holding trend"
          value={ratios.promoterHoldingTrend === "n/a" ? null : ratios.promoterHoldingTrend}
          tone={promoterToneMap[ratios.promoterHoldingTrend]}
        />
      </div>
    </div>
  );
}
