import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { X } from "lucide-react";
import { compareMany, getWatchlist, searchCompanies } from "../api/client";
import type { CompanySummary } from "../types";
import CompanyPicker from "../components/CompanyPicker";
import StockLoader from "../components/StockLoader";
import VerdictBadge from "../components/VerdictBadge";
import { useAsync } from "../lib/useAsync";

const MAX = 6;

/** Compare 2–6 companies side by side. The selection lives in the URL (?ids=1,2,3) so it can be shared. */
export default function MultiComparePage() {
  const [params, setParams] = useSearchParams();
  const ids = (params.get("ids") ?? "")
    .split(",")
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0)
    .filter((n, i, a) => a.indexOf(n) === i)
    .slice(0, MAX);
  const key = ids.join(",");

  // Names for the chips, before (or without) a comparison result.
  const [names, setNames] = useState<Record<number, CompanySummary>>({});
  useEffect(() => {
    searchCompanies("").then(
      (all) => setNames(Object.fromEntries(all.map((c) => [c.id, c]))),
      () => undefined,
    );
  }, []);

  const result = useAsync(() => (ids.length >= 2 ? compareMany(ids) : Promise.resolve(null)), [key]);

  const setIds = (next: number[]) => setParams(next.length ? { ids: next.join(",") } : {});

  async function addWatchlist() {
    const list = await getWatchlist().catch(() => []);
    const merged = [...ids, ...list.map((w) => w.company.id)].filter((n, i, a) => a.indexOf(n) === i).slice(0, MAX);
    setIds(merged);
  }

  return (
    <div className="max-w-5xl mx-auto pt-10 pb-24 px-6">
      <h1 className="font-display text-3xl mb-1">Compare companies</h1>
      <p className="text-sm text-ledger-faint mb-6 max-w-xl">
        Up to {MAX} side by side. Companies that haven't been analysed yet are scored first, so this can take a moment.
      </p>

      <div className="flex flex-wrap gap-2 mb-3">
        {ids.map((id) => (
          <span key={id} className="inline-flex items-center gap-1.5 border border-ledger-line px-2.5 py-1 text-sm">
            {names[id]?.name ?? `Company #${id}`}
            <button onClick={() => setIds(ids.filter((x) => x !== id))} aria-label="Remove" className="text-ledger-faint hover:text-signal-weak">
              <X size={13} />
            </button>
          </span>
        ))}
      </div>
      <div className="flex flex-col sm:flex-row gap-2 max-w-xl">
        <div className="flex-1">
          <CompanyPicker
            exclude={ids}
            disabled={ids.length >= MAX}
            placeholder={ids.length >= MAX ? `${MAX} companies is the maximum` : "Add a company by name or ticker"}
            onPick={(c) => {
              setNames((n) => ({ ...n, [c.id]: c }));
              setIds([...ids, c.id]);
            }}
          />
        </div>
        <button onClick={addWatchlist} disabled={ids.length >= MAX}
          className="text-sm border border-ledger-line px-3 py-2 text-ledger-faint hover:text-ledger-ink disabled:opacity-40">
          Add my watchlist
        </button>
      </div>

      <div className="mt-8">
        {ids.length < 2 ? (
          <p className="text-sm text-ledger-faint italic">Add at least two companies to see the comparison.</p>
        ) : result.error ? (
          <p className="text-signal-weak text-sm">{result.error}</p>
        ) : !result.data ? (
          <StockLoader title="Building the comparison…" subtitle="Fetching live quotes and scoring anything not yet analysed." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-t border-ledger-line">
              <thead>
                <tr className="border-b border-ledger-line align-bottom">
                  <th className="py-3 font-normal text-left text-ledger-faint">Metric</th>
                  {result.data.companies.map((c) => (
                    <th key={c.company.id} className="py-3 px-2 font-normal text-right min-w-[8rem]">
                      <Link to={`/report/${c.company.id}`} className="hover:text-ledger-gold block">{c.company.name}</Link>
                      <span className="font-mono text-xs text-ledger-faint">{c.company.nseSymbol}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr className="border-b border-ledger-line">
                  <td className="py-3 text-ledger-faint">Verdict</td>
                  {result.data.companies.map((c) => (
                    <td key={c.company.id} className="py-3 px-2 text-right">
                      {c.verdict ? <VerdictBadge verdict={c.verdict} /> : <span className="text-ledger-faint">—</span>}
                    </td>
                  ))}
                </tr>
                {result.data.rows.map((row) => (
                  <tr key={row.metricLabel} className="border-b border-ledger-line">
                    <td className="py-3">
                      {row.metricLabel}
                      {row.higherIsBetter !== null && (
                        <span className="text-[10px] text-ledger-faint ml-1.5">{row.higherIsBetter ? "higher is better" : "lower is better"}</span>
                      )}
                    </td>
                    {row.values.map((v, i) => {
                      const best = row.bestIndices.includes(i);
                      return (
                        <td key={i} className={`py-3 px-2 text-right font-mono tabular ${best ? "text-signal-strong" : ""}`}>
                          {v}
                          {best && <span className="ml-1.5 text-xs" aria-label="best value">●</span>}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="mt-6 text-xs text-ledger-faint">
              ● marks the best value in each row. This is a factual comparison, not a recommendation.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
