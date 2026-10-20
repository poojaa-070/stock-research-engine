export default function ScoreReadout({
  score,
  size = "lg",
}: {
  score: number;
  size?: "lg" | "sm";
}) {
  const pct = Math.max(0, Math.min(100, score));
  const color =
    pct >= 80 ? "bg-signal-strong" : pct >= 65 ? "bg-signal-strong" : pct >= 50 ? "bg-signal-watch" : pct >= 35 ? "bg-signal-risk" : "bg-signal-weak";

  if (size === "sm") {
    return (
      <div className="flex items-center gap-2 w-full">
        <div className="h-1.5 flex-1 bg-ledger-line rounded-none overflow-hidden">
          <div className={`h-full ${color}`} style={{ width: `${pct}%` }} />
        </div>
        <span className="font-mono text-sm tabular w-10 text-right">{pct.toFixed(0)}</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline gap-2">
        <span className="font-display text-6xl font-medium tabular">{pct.toFixed(1)}</span>
        <span className="text-ledger-faint text-lg">/ 100</span>
      </div>
      <div className="h-1.5 w-full bg-ledger-line overflow-hidden">
        <div className={`h-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
