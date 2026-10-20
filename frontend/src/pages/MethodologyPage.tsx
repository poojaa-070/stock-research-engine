import { getMethodology } from "../api/client";
import { useAsync } from "../lib/useAsync";
import { CATEGORY_LABELS, CATEGORY_ORDER } from "../types";
import VerdictBadge from "../components/VerdictBadge";

const METRIC_LABELS: Record<string, string> = {
  debt_to_equity: "Debt to equity",
  interest_coverage: "Interest coverage (x)",
  current_ratio: "Current ratio",
  net_debt_to_ebitda: "Debt / EBITDA (x)",
  cash_conversion: "Operating cash flow / net profit",
  fcf_cagr_3y: "Free cash flow growth, ~3y (%)",
  roce_latest: "ROCE (%)",
  roce_trend: "ROCE trend (−1 / 0 / +1)",
  margin_stability: "Operating margin volatility (std-dev)",
  qualitative_flags: "Qualitative flags (AI-extracted, rule-scored)",
  revenue_cagr_3y: "Revenue growth, ~3y (%)",
  profit_cagr_3y: "Net profit growth, ~3y (%)",
  eps_cagr_3y: "EPS growth, ~3y (%)",
  pe_vs_own_history: "P/E vs own history (ratio)",
  pe_vs_peer_median: "P/E vs sector median (ratio)",
  peg_ratio: "PEG ratio",
  ev_ebitda_vs_peer: "EV/EBITDA vs sector median (ratio)",
  pb_ratio: "Price to book",
  promoter_holding_delta_3y: "Promoter holding change (pp)",
  promoter_holding_latest: "Promoter holding (%)",
  pledged_share_pct: "Pledged shares (%)",
  dividend_years_paid_of_5: "Dividend consistency (of 5)",
  customer_concentration: "Customer concentration (%)",
  price_vs_200dma_pct: "Price vs 200-day average (%)",
  ma_crossover_signal: "50/200-day crossover (−1 / +1)",
  rsi_14: "RSI (14)",
};

/** "below 0.5 → 100 · 0.5 → 85 · 1 → 60 · …" — the scoring curve in words. */
function bands(thresholds: number[], scores: number[]): string {
  if (thresholds.length === 0) return `starts at ${scores[0] ?? "—"}; flags add or deduct points`;
  const parts = [`below ${thresholds[0]} → ${scores[0]}`];
  thresholds.forEach((t, i) => parts.push(`${t} → ${scores[i + 1]}`));
  return parts.join(" · ");
}

export default function MethodologyPage() {
  const { data, error, loading } = useAsync(getMethodology, []);

  return (
    <div className="max-w-4xl mx-auto pt-10 pb-24 px-6">
      <h1 className="font-display text-3xl mb-2">How the score is calculated</h1>
      <p className="text-ledger-faint max-w-2xl leading-relaxed">
        Every score is a fixed function of stored facts. Each metric is placed on a scoring curve, metrics
        are averaged within their category, and categories are averaged into the overall score. The AI layer
        only reads news for yes/no flags; the points those flags are worth are set below, in code. This page is
        read live from the same rules the engine uses.
      </p>

      {loading && <p className="mt-8 text-sm text-ledger-faint">Loading the rubric…</p>}
      {error && <p className="mt-8 text-sm text-signal-weak">{error}</p>}

      {data && (
        <>
          <h2 className="font-display text-xl mt-10 mb-3">Verdicts</h2>
          <ul className="space-y-2">
            {data.verdictBands.map((b, i) => (
              <li key={b.verdict} className="flex items-center gap-3 text-sm">
                <span className="font-mono tabular w-24 text-ledger-faint">
                  {b.minScore}–{i === 0 ? 100 : (data.verdictBands[i - 1].minScore - 0.1).toFixed(1)}
                </span>
                <VerdictBadge verdict={b.verdict} />
              </li>
            ))}
          </ul>

          <h2 className="font-display text-xl mt-10 mb-1">Categories and metrics</h2>
          <p className="text-sm text-ledger-faint mb-4 max-w-2xl">
            A metric without data is skipped and the rest of its category is re-weighted. A category with no data at
            all is left out of the overall score, and the confidence figure drops.
            {data.skipLeverageForFinancials &&
              " Debt-based metrics are skipped for banks, NBFCs and insurers, where borrowing is the business itself."}
          </p>
          {CATEGORY_ORDER.map((cat) => {
            const rules = data.rules.filter((r) => r.category === cat);
            if (rules.length === 0) return null;
            return (
              <section key={cat} className="mt-6">
                <h3 className="text-base font-medium">
                  {CATEGORY_LABELS[cat]}{" "}
                  <span className="text-ledger-faint font-mono text-sm">{Math.round((data.categoryWeights[cat] ?? 0) * 100)}% of overall</span>
                </h3>
                <div className="overflow-x-auto mt-2">
                  <table className="w-full text-sm">
                    <tbody>
                      {rules.map((r) => (
                        <tr key={`${r.metricKey}-${r.sector ?? ""}`} className="border-b border-ledger-line align-top">
                          <td className="py-2 pr-4 min-w-[12rem]">
                            {METRIC_LABELS[r.metricKey] ?? r.metricKey}
                            {r.sector && <span className="block text-[11px] text-ledger-gold">{r.sector} only</span>}
                          </td>
                          <td className="py-2 pr-4 font-mono tabular text-ledger-faint w-12 text-right">{Math.round(r.weight * 100)}%</td>
                          <td className="py-2 font-mono text-xs text-ledger-faint">{bands(r.thresholds, r.scores)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            );
          })}

          <h2 className="font-display text-xl mt-10 mb-1">Qualitative flags</h2>
          <p className="text-sm text-ledger-faint mb-4 max-w-2xl">
            Points at high confidence. Medium confidence counts {data.confidenceMultipliers.medium * 100}%, low{" "}
            {data.confidenceMultipliers.low * 100}%. Each flag affects only its own category.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <tbody>
                {data.flags.map((f) => (
                  <tr key={f.flagKey} className="border-b border-ledger-line">
                    <td className="py-2 pr-4">{f.description}</td>
                    <td className="py-2 pr-4 text-ledger-faint text-xs">{CATEGORY_LABELS[f.category] ?? f.category}</td>
                    <td className={`py-2 text-right font-mono tabular ${f.effectAtHighConfidence >= 0 ? "text-signal-strong" : "text-signal-weak"}`}>
                      {f.effectAtHighConfidence > 0 ? "+" : ""}
                      {f.effectAtHighConfidence}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
