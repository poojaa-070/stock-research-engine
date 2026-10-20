import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";
import type { PricePoint } from "../types";

export default function PriceChart({ data }: { data: PricePoint[] }) {
  if (data.length < 2) {
    return (
      <p className="text-sm text-ledger-faint italic">
        Not enough price history scraped yet to draw a chart — needs at least a
        couple of trading sessions in the `prices` table.
      </p>
    );
  }

  // Thin the x-axis labels so they don't overlap on a year of daily data.
  const tickInterval = Math.max(1, Math.floor(data.length / 6));

  return (
    <div className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <XAxis
            dataKey="date"
            interval={tickInterval}
            tick={{ fill: "#8FA1B3", fontSize: 11, fontFamily: "IBM Plex Mono" }}
            axisLine={{ stroke: "#2A3B4E" }}
            tickLine={false}
          />
          <YAxis
            domain={["auto", "auto"]}
            tick={{ fill: "#8FA1B3", fontSize: 11, fontFamily: "IBM Plex Mono" }}
            axisLine={false}
            tickLine={false}
            width={50}
          />
          <Tooltip
            contentStyle={{
              background: "#152538",
              border: "1px solid #2A3B4E",
              borderRadius: 0,
              fontFamily: "IBM Plex Sans",
              fontSize: 12,
            }}
            labelStyle={{ color: "#E7EAEE" }}
            itemStyle={{ color: "#C9A227" }}
            formatter={(value) => [`₹${Number(value).toFixed(2)}`, "Close"]}
          />
          <Line
            type="monotone"
            dataKey="close"
            stroke="#C9A227"
            strokeWidth={1.5}
            dot={false}
            activeDot={{ r: 3, fill: "#C9A227" }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
