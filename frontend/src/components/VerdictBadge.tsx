const VERDICT_COLORS: Record<string, string> = {
  "🟢 Strong Candidate": "text-signal-strong border-signal-strong/40 bg-signal-strong/10",
  "🟢 Worth Researching": "text-signal-strong border-signal-strong/40 bg-signal-strong/10",
  "🟡 Neutral–Watch": "text-signal-watch border-signal-watch/40 bg-signal-watch/10",
  "🟠 High Risk": "text-signal-risk border-signal-risk/40 bg-signal-risk/10",
  "🔴 Weak Fundamentals": "text-signal-weak border-signal-weak/40 bg-signal-weak/10",
};

export default function VerdictBadge({ verdict }: { verdict: string }) {
  const classes = VERDICT_COLORS[verdict] ?? "text-ledger-faint border-ledger-line bg-ledger-panel";
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-sm border px-3 py-1 text-sm font-medium ${classes}`}
      style={{ animation: "fade-slide-in 0.4s ease both" }}
    >
      {verdict}
    </span>
  );
}
