import type { ReportResponse } from "../types";
import { CATEGORY_LABELS } from "../types";
import { num } from "./format";

/** Plain-Markdown export of a report: every score with its evidence and sources. */
export function reportToMarkdown(r: ReportResponse): string {
  const k = r.keyRatios;
  const lines: string[] = [
    `# ${r.companyName} (${r.nseSymbol})`,
    "",
    `Generated ${new Date(r.generatedAt).toLocaleString()} · report #${r.reportId}`,
    "",
    `**Overall score:** ${r.overallScore.toFixed(1)}/100 — ${r.verdict}  `,
    `**Confidence (data completeness):** ${Math.round(r.confidence * 100)}%  `,
    r.currentPrice !== null
      ? `**Price:** ₹${r.currentPrice.toFixed(2)} (${r.priceIsLive ? "live" : "last close"}${r.priceAsOf ? `, as of ${r.priceAsOf}` : ""})`
      : "",
    "",
    "## Summary",
    "",
    r.narrativeSummary,
    "",
    "## Bull case",
    "",
    r.bullCase,
    "",
    "## Bear case",
    "",
    r.bearCase,
    "",
    "## Key ratios",
    "",
    "| Ratio | Value |",
    "|---|---|",
    `| ROE | ${num(k.roePercent)}% |`,
    `| ROCE | ${num(k.rocePercent)}% |`,
    `| Debt / Equity | ${num(k.debtToEquity, 2)} |`,
    `| P/E | ${num(k.peRatio)} |`,
    `| P/B | ${num(k.pbRatio, 2)} |`,
    `| EV/EBITDA | ${num(k.evEbitda)} |`,
    `| Dividend yield | ${num(k.dividendYieldPercent)}% |`,
    `| Revenue CAGR (~3y) | ${num(k.revenueGrowthPercent3y)}% |`,
    `| EPS CAGR (~3y) | ${num(k.epsGrowthPercent3y)}% |`,
    `| Promoter holding | ${num(k.promoterHoldingPercent)}% (${k.promoterHoldingTrend}) |`,
    "",
    "## Score breakdown",
    "",
  ];
  for (const c of r.categories) {
    lines.push(
      `### ${CATEGORY_LABELS[c.category] ?? c.category}: ${c.dataCompleteness > 0 ? c.score.toFixed(1) : "no data"} (${Math.round(c.dataCompleteness * 100)}% data)`,
      "",
    );
    if (c.evidence.length === 0) lines.push("- No evidence recorded.");
    for (const e of c.evidence) lines.push(`- ${e.statement} _(source: ${e.sourceRef})_`);
    lines.push("");
  }
  lines.push("---", "", `_${r.disclaimer}_`, "");
  return lines.filter((l, i, a) => !(l === "" && a[i - 1] === "")).join("\n");
}

export function download(filename: string, content: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
