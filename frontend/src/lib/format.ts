/** Shared number/date formatting so every screen renders figures the same way. */

export function num(v: number | null | undefined, decimals = 1): string {
  return v === null || v === undefined || Number.isNaN(v) ? "—" : v.toFixed(decimals);
}

export function pct(v: number | null | undefined, decimals = 1, signed = false): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  return `${signed && v > 0 ? "+" : ""}${v.toFixed(decimals)}%`;
}

export function rupees(v: number | null | undefined, decimals = 2): string {
  return v === null || v === undefined ? "—" : `₹${v.toLocaleString("en-IN", { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}`;
}

/** Screener reports statement figures in ₹ crore. */
export function crore(v: number | null | undefined): string {
  if (v === null || v === undefined) return "—";
  const abs = Math.abs(v);
  if (abs >= 100000) return `₹${(v / 100000).toFixed(2)} lakh Cr`;
  return `₹${v.toLocaleString("en-IN", { maximumFractionDigits: 0 })} Cr`;
}

export function shortDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

export function monthYear(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
}

export function fiscalYear(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : `FY${String(d.getFullYear()).slice(2)}`;
}

/** "3 hours ago", "2 days ago" — for data freshness. */
export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return "never";
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return iso;
  const mins = Math.round(ms / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"} ago`;
}

export function scoreColor(score: number): string {
  if (score >= 65) return "#3FA96B";
  if (score >= 50) return "#D0A526";
  if (score >= 35) return "#C9622E";
  return "#B4452F";
}

export const CHART = {
  axis: { fill: "#8FA1B3", fontSize: 11, fontFamily: "IBM Plex Mono" },
  line: "#2A3B4E",
  gold: "#C9A227",
  ink: "#E7EAEE",
  faint: "#8FA1B3",
  tooltip: {
    contentStyle: { background: "#152538", border: "1px solid #2A3B4E", borderRadius: 0, fontFamily: "IBM Plex Sans", fontSize: 12 },
    labelStyle: { color: "#E7EAEE" },
  },
};
