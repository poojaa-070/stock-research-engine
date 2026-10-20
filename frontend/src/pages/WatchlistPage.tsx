import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowDownRight, ArrowUpRight, Trash2 } from "lucide-react";
import { getWatchlist, removeFromWatchlist, addToWatchlist, errorMessage } from "../api/client";
import type { WatchlistItem } from "../types";
import CompanyPicker from "../components/CompanyPicker";
import VerdictBadge from "../components/VerdictBadge";
import { useAsync } from "../lib/useAsync";
import { rupees, shortDate, timeAgo } from "../lib/format";

export default function WatchlistPage() {
  const [reload, setReload] = useState(0);
  const { data, error, loading } = useAsync(getWatchlist, [reload]);
  const [actionError, setActionError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function act(fn: () => Promise<unknown>) {
    setActionError(null);
    try {
      await fn();
      setReload((n) => n + 1);
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  const items: WatchlistItem[] = data ?? [];

  return (
    <div className="max-w-4xl mx-auto pt-10 pb-24 px-6">
      <h1 className="font-display text-3xl mb-1">Watchlist</h1>
      <p className="text-sm text-ledger-faint mb-6 max-w-xl">
        Companies you're following, with their latest stored score and how it moved since the report before.
        Scores refresh when you open a report or regenerate it.
      </p>

      <div className="max-w-md mb-8">
        <CompanyPicker
          exclude={items.map((i) => i.company.id)}
          placeholder="Add a company to your watchlist"
          onPick={(c) => act(() => addToWatchlist(c.id))}
        />
      </div>

      {actionError && <p className="text-sm text-signal-weak mb-4">{actionError}</p>}
      {loading && <p className="text-sm text-ledger-faint">Loading your watchlist…</p>}
      {error && <p className="text-sm text-signal-weak">{error}</p>}
      {!loading && !error && items.length === 0 && (
        <p className="text-sm text-ledger-faint italic">
          Nothing here yet. Add a company above, or use "Add to watchlist" on any report.
        </p>
      )}

      {items.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-t border-ledger-line">
              <thead>
                <tr className="text-left text-ledger-faint border-b border-ledger-line">
                  <th className="py-2 font-normal">Company</th>
                  <th className="py-2 font-normal text-right">Score</th>
                  <th className="py-2 font-normal text-right">Change</th>
                  <th className="py-2 font-normal hidden sm:table-cell pl-4">Verdict</th>
                  <th className="py-2 font-normal text-right">Last close</th>
                  <th className="py-2 font-normal text-right hidden md:table-cell">Analysed</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {items.map((w) => (
                  <tr key={w.company.id} className="border-b border-ledger-line hover:bg-ledger-panel/60 cursor-pointer"
                      onClick={() => navigate(`/report/${w.company.id}`)}>
                    <td className="py-3">
                      <p>{w.company.name}</p>
                      <p className="text-xs text-ledger-faint font-mono">{w.company.nseSymbol}</p>
                      {w.note && <p className="text-xs text-ledger-faint italic mt-0.5">{w.note}</p>}
                    </td>
                    <td className="py-3 text-right font-mono tabular">{w.latestScore === null ? "—" : w.latestScore.toFixed(1)}</td>
                    <td className="py-3 text-right font-mono tabular">
                      {w.scoreChange === null ? (
                        <span className="text-ledger-faint">—</span>
                      ) : Math.abs(w.scoreChange) < 0.05 ? (
                        <span className="text-ledger-faint">0.0</span>
                      ) : (
                        <span className={`inline-flex items-center ${w.scoreChange > 0 ? "text-signal-strong" : "text-signal-weak"}`}>
                          {w.scoreChange > 0 ? <ArrowUpRight size={13} /> : <ArrowDownRight size={13} />}
                          {Math.abs(w.scoreChange).toFixed(1)}
                        </span>
                      )}
                    </td>
                    <td className="py-3 pl-4 hidden sm:table-cell">
                      {w.latestVerdict ? <VerdictBadge verdict={w.latestVerdict} /> : <span className="text-xs text-ledger-faint">Not analysed yet</span>}
                    </td>
                    <td className="py-3 text-right font-mono tabular">
                      {rupees(w.lastClose)}
                      <p className="text-[10px] text-ledger-faint">{shortDate(w.lastCloseDate)}</p>
                    </td>
                    <td className="py-3 text-right text-xs text-ledger-faint hidden md:table-cell">{timeAgo(w.lastReportAt)}</td>
                    <td className="py-3 text-right">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          act(() => removeFromWatchlist(w.company.id));
                        }}
                        aria-label={`Remove ${w.company.name} from watchlist`}
                        className="text-ledger-faint hover:text-signal-weak p-1"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {items.length >= 2 && (
            <Link
              to={`/compare-multi?ids=${items.slice(0, 6).map((i) => i.company.id).join(",")}`}
              className="inline-block mt-6 text-sm text-ledger-gold border border-ledger-gold/40 px-3 py-2 hover:bg-ledger-gold/10"
            >
              Compare {Math.min(items.length, 6)} side by side
            </Link>
          )}
        </>
      )}
    </div>
  );
}
