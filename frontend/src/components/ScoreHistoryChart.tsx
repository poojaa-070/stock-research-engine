import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine } from "recharts";
import { getReportHistory } from "../api/client";
import { useAsync } from "../lib/useAsync";
import { CHART, shortDate } from "../lib/format";

/** Overall score across every stored report for this company. */
export default function ScoreHistoryChart({ companyId, reportId }: { companyId: number; reportId: number }) {
  const { data, error, loading } = useAsync(() => getReportHistory(companyId, 60), [companyId, reportId]);

  if (loading) return <p className="text-sm text-ledger-faint">Loading score history…</p>;
  if (error) return <p className="text-sm text-signal-weak">{error}</p>;
  if (!data || data.length < 2) {
    return (
      <p className="text-sm text-ledger-faint italic">
        One report so far. Each regeneration adds a point here, so you can see the score move as new results come in.
      </p>
    );
  }

  const points = data.map((p) => ({ ...p, label: shortDate(p.generatedAt) }));
  return (
    <div className="h-44 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 6, right: 8, bottom: 0, left: 0 }}>
          <XAxis dataKey="label" tick={CHART.axis} axisLine={{ stroke: CHART.line }} tickLine={false} minTickGap={24} />
          <YAxis domain={[0, 100]} ticks={[0, 35, 50, 65, 80, 100]} tick={CHART.axis} axisLine={false} tickLine={false} width={32} />
          {/* verdict band boundaries */}
          {[35, 50, 65, 80].map((y) => (
            <ReferenceLine key={y} y={y} stroke={CHART.line} strokeDasharray="2 4" />
          ))}
          <Tooltip
            {...CHART.tooltip}
            itemStyle={{ color: CHART.gold }}
            formatter={(v) => [Number(v).toFixed(1), "Overall score"]}
          />
          <Line type="monotone" dataKey="overallScore" stroke={CHART.gold} strokeWidth={1.5} dot={{ r: 2.5, fill: CHART.gold }} />
        </LineChart>
      </ResponsiveContainer>
      <p className="text-[11px] text-ledger-faint mt-1">Dashed lines mark the verdict boundaries (35 / 50 / 65 / 80).</p>
    </div>
  );
}
