import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from "recharts";
import type { ShareholdingPoint } from "../types";
import { CHART, monthYear, num } from "../lib/format";

const SERIES = [
  { key: "promoterPct", name: "Promoters", color: "#C9A227" },
  { key: "fiiPct", name: "FIIs", color: "#5E86AD" },
  { key: "diiPct", name: "DIIs", color: "#3FA96B" },
  { key: "publicPct", name: "Public", color: "#56677A" },
] as const;

/** Who owns the company, period by period (stacked to ~100%). */
export default function ShareholdingChart({ data }: { data: ShareholdingPoint[] }) {
  if (data.length === 0) {
    return <p className="text-sm text-ledger-faint italic">No shareholding pattern scraped yet.</p>;
  }
  const rows = data.map((d) => ({ ...d, period: monthYear(d.periodEndDate) }));
  const first = data[0];
  const last = data[data.length - 1];
  const delta = (k: keyof ShareholdingPoint) => {
    const a = first[k] as number | null;
    const b = last[k] as number | null;
    return a === null || b === null ? null : b - a;
  };

  return (
    <div>
      <div className="h-48 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 6, right: 4, bottom: 0, left: 0 }}>
            <XAxis dataKey="period" tick={CHART.axis} axisLine={{ stroke: CHART.line }} tickLine={false} />
            <YAxis domain={[0, 100]} tick={CHART.axis} axisLine={false} tickLine={false} width={32} tickFormatter={(v) => `${v}%`} />
            <Tooltip {...CHART.tooltip} formatter={(v, name) => [`${num(Number(v), 2)}%`, name]} />
            <Legend wrapperStyle={{ fontSize: 11, color: CHART.faint }} iconSize={8} />
            {SERIES.map((s) => (
              <Bar key={s.key} dataKey={s.key} name={s.name} stackId="own" fill={s.color} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      {data.length >= 2 && (
        <p className="text-[11px] text-ledger-faint mt-1">
          Change since {monthYear(first.periodEndDate)}:{" "}
          {SERIES.map((s) => {
            const d = delta(s.key);
            return d === null ? null : `${s.name} ${d > 0 ? "+" : ""}${d.toFixed(2)}pp`;
          })
            .filter(Boolean)
            .join(", ")}
          .
        </p>
      )}
    </div>
  );
}
