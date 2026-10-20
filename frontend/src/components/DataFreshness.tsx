import type { DataSourceStatus } from "../types";
import { timeAgo } from "../lib/format";

const NAMES: Record<string, string> = { screener: "Fundamentals (Screener)", nse: "Prices & announcements (NSE)" };

/** When each source was last scraped, and whether that scrape worked. */
export default function DataFreshness({ sources }: { sources: DataSourceStatus[] }) {
  if (sources.length === 0) {
    return <p className="text-xs text-ledger-faint">No scrape recorded for this company yet. Figures come from seeded or manually loaded data.</p>;
  }
  return (
    <ul className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ledger-faint">
      {sources.map((s) => (
        <li key={s.source}>
          {NAMES[s.source] ?? s.source}: updated {timeAgo(s.lastScrapedAt)}
          {s.status && s.status !== "success" && <span className="text-signal-weak"> (last attempt {s.status})</span>}
        </li>
      ))}
    </ul>
  );
}
