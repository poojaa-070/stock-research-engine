/**
 * Full-panel loading state used anywhere the person would otherwise be
 * staring at a blank/black screen while we wait on a network call — report
 * generation, a live compare, etc. Deliberately stock-market-themed
 * (candlesticks + a scrolling ticker tape) rather than a generic spinner,
 * so the wait itself reinforces "we're out fetching live market data" and
 * doesn't read as a stuck or broken page.
 */

const TICKER_SYMBOLS = [
  "NIFTY 50", "SENSEX", "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "ITC",
];

function TickerTape() {
  // Duplicate the list once so the 50%-translate loop is seamless.
  const items = [...TICKER_SYMBOLS, ...TICKER_SYMBOLS];
  return (
    <div className="overflow-hidden border-y border-ledger-line py-2 mt-8 select-none">
      <div className="flex gap-8 w-max animate-ticker">
        {items.map((sym, i) => {
          const up = (i * 7) % 3 !== 0;
          const pct = (((i * 13) % 40) / 10 + 0.1).toFixed(2);
          return (
            <span key={i} className="font-mono text-xs text-ledger-faint whitespace-nowrap flex items-center gap-1.5">
              {sym}
              <span className={up ? "text-signal-strong" : "text-signal-weak"}>
                {up ? "▲" : "▼"} {pct}%
              </span>
            </span>
          );
        })}
      </div>
    </div>
  );
}

function Candlesticks() {
  const bars = [40, 65, 30, 80, 55, 90, 45, 70, 35, 60];
  return (
    <svg viewBox="0 0 220 100" className="w-56 h-24 mx-auto" aria-hidden>
      {bars.map((h, i) => {
        const up = i % 2 === 0;
        const x = 8 + i * 21;
        return (
          <g
            key={i}
            className="animate-candle"
            style={{ animationDelay: `${i * 0.09}s` }}
          >
            <line x1={x + 4} y1={100 - h - 12} x2={x + 4} y2={100} stroke="rgba(201,162,39,0.35)" strokeWidth="1" />
            <rect
              x={x}
              y={100 - h}
              width="9"
              height={Math.max(h - 12, 8)}
              fill={up ? "#4ADE80" : "#F87171"}
              opacity="0.9"
              rx="1"
            />
          </g>
        );
      })}
    </svg>
  );
}

export default function StockLoader({
  title = "Fetching live market data…",
  subtitle = "Pulling a fresh quote, sector peers, and the latest fundamentals — this can take a few seconds.",
}: {
  title?: string;
  subtitle?: string;
}) {
  return (
    <div className="max-w-3xl mx-auto pt-16 px-6 text-center">
      <Candlesticks />
      <p className="mt-4 font-display text-xl text-ledger-ink">{title}</p>
      <p className="mt-2 text-sm text-ledger-faint max-w-md mx-auto">{subtitle}</p>
      <TickerTape />
    </div>
  );
}
