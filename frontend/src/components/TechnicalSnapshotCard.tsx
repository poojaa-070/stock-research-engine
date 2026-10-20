import type { TechnicalSnapshot } from "../types";

export default function TechnicalSnapshotCard({ snapshot }: { snapshot: TechnicalSnapshot | null }) {
  if (!snapshot) {
    return (
      <p className="text-sm text-ledger-faint italic">
        Not enough price history scraped yet for a technical read.
      </p>
    );
  }

  const rangePct =
    snapshot.week52High !== null && snapshot.week52Low !== null && snapshot.currentPrice !== null
      ? ((snapshot.currentPrice - snapshot.week52Low) / (snapshot.week52High - snapshot.week52Low)) * 100
      : null;

  return (
    <div>
      {rangePct !== null && (
        <div className="mb-4">
          <div className="flex justify-between text-xs text-ledger-faint mb-1.5 font-mono">
            <span>₹{snapshot.week52Low?.toFixed(0)}</span>
            <span>52-week range</span>
            <span>₹{snapshot.week52High?.toFixed(0)}</span>
          </div>
          <div className="h-1.5 bg-ledger-line relative">
            <div
              className="absolute top-1/2 -translate-y-1/2 h-3 w-3 rounded-full bg-ledger-gold border-2 border-ledger-bg"
              style={{ left: `calc(${Math.max(0, Math.min(100, rangePct))}% - 6px)` }}
            />
          </div>
          {snapshot.percentFrom52wHigh !== null && (
            <p className="text-xs text-ledger-faint mt-1.5">
              {snapshot.percentFrom52wHigh >= -0.05
                ? "At or above its 52-week high"
                : `${Math.abs(snapshot.percentFrom52wHigh).toFixed(1)}% below 52-week high`}
            </p>
          )}
        </div>
      )}

      <div className="grid grid-cols-3 gap-3">
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">50-day avg</p>
          <p className="font-mono text-base tabular mt-1">
            {snapshot.dma50 !== null ? `₹${snapshot.dma50.toFixed(0)}` : "—"}
          </p>
        </div>
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">200-day avg</p>
          <p className="font-mono text-base tabular mt-1">
            {snapshot.dma200 !== null ? `₹${snapshot.dma200.toFixed(0)}` : "—"}
          </p>
        </div>
        <div className="border border-ledger-line px-4 py-3">
          <p className="text-xs text-ledger-faint">RSI (14)</p>
          <p className="font-mono text-base tabular mt-1">
            {snapshot.rsi14 !== null ? snapshot.rsi14.toFixed(0) : "—"}
          </p>
        </div>
      </div>
    </div>
  );
}
