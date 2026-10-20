import { ComposedChart, Bar, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from "recharts";
import type { FinancialPoint } from "../types";
import { CHART, fiscalYear, pct } from "../lib/format";

/** Annual revenue and net profit (bars, ₹ Cr) with operating margin (line, %). */
export default function FinancialHistoryChart({ data }: { data: FinancialPoint[] }) {
  const rows = data
    .filter((d) => d.revenue !== null || d.netProfit !== null)
    .map((d) => ({ ...d, fy: fiscalYear(d.periodEndDate) }));

  if (rows.length < 2) {
    return (
      <p className="text-sm text-ledger-faint italic">
        Needs at least two annual periods. Only {rows.length} has been scraped for this company so far.
      </p>
    );
  }

  const latest = rows[rows.length - 1];
  return (
    <div>
      <div className="h-60 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={rows} margin={{ top: 6, right: 4, bottom: 0, left: 0 }}>
            <XAxis dataKey="fy" tick={CHART.axis} axisLine={{ stroke: CHART.line }} tickLine={false} />
            <YAxis yAxisId="cr" tick={CHART.axis} axisLine={false} tickLine={false} width={56}
              tickFormatter={(v) => Number(v).toLocaleString("en-IN")} />
            <YAxis yAxisId="pct" orientation="right" tick={CHART.axis} axisLine={false} tickLine={false} width={36}
              tickFormatter={(v) => `${v}%`} />
            <Tooltip
              {...CHART.tooltip}
              formatter={(v, name) =>
                name === "Operating margin"
                  ? [pct(Number(v)), name]
                  : [`₹${Number(v).toLocaleString("en-IN")} Cr`, name]
              }
            />
            <Legend wrapperStyle={{ fontSize: 11, color: CHART.faint }} iconSize={8} />
            <Bar yAxisId="cr" dataKey="revenue" name="Revenue" fill="#3B536B" />
            <Bar yAxisId="cr" dataKey="netProfit" name="Net profit" fill={CHART.gold} />
            <Line yAxisId="pct" type="monotone" dataKey="operatingMarginPercent" name="Operating margin"
              stroke={CHART.ink} strokeWidth={1.25} dot={{ r: 2 }} connectNulls />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <p className="text-[11px] text-ledger-faint mt-1">
        {latest.fy}: operating margin {pct(latest.operatingMarginPercent)}, net margin {pct(latest.netMarginPercent)}. Figures in ₹ crore as reported on Screener.
      </p>
    </div>
  );
}
