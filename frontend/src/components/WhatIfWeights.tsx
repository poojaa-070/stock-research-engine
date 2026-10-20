import { useMemo, useState } from "react";
import { RotateCcw } from "lucide-react";
import type { CategoryScore } from "../types";
import { CATEGORY_LABELS, CATEGORY_ORDER } from "../types";

/**
 * Lets the reader re-weight the seven categories and see the overall score
 * that results. Pure arithmetic over the category scores already on screen:
 * nothing is sent to the server or saved, and the official score above is
 * unchanged (docs/API_DESIGN.md: custom weights must be an explicit,
 * clearly-labelled personal view).
 */
export default function WhatIfWeights({
  categories,
  officialWeights,
  officialScore,
}: {
  categories: CategoryScore[];
  officialWeights: Record<string, number>;
  officialScore: number;
}) {
  const toPct = (w: Record<string, number>) =>
    Object.fromEntries(CATEGORY_ORDER.map((k) => [k, Math.round((w[k] ?? 0) * 100)]));
  const [weights, setWeights] = useState<Record<string, number>>(() => toPct(officialWeights));

  const byKey = useMemo(() => Object.fromEntries(categories.map((c) => [c.category, c])), [categories]);

  // Same rule as the backend: categories with no data are left out.
  let sum = 0;
  let total = 0;
  for (const k of CATEGORY_ORDER) {
    const c = byKey[k];
    if (!c || c.dataCompleteness <= 0) continue;
    sum += c.score * (weights[k] ?? 0);
    total += weights[k] ?? 0;
  }
  const custom = total > 0 ? sum / total : null;
  const changed = CATEGORY_ORDER.some((k) => weights[k] !== toPct(officialWeights)[k]);

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3">
        {CATEGORY_ORDER.map((k) => {
          const c = byKey[k];
          const noData = !c || c.dataCompleteness <= 0;
          return (
            <label key={k} className={`block ${noData ? "opacity-50" : ""}`}>
              <span className="flex justify-between text-xs mb-1">
                <span>
                  {CATEGORY_LABELS[k]}
                  {noData ? " (no data)" : <span className="text-ledger-faint"> · scored {c.score.toFixed(0)}</span>}
                </span>
                <span className="font-mono tabular text-ledger-faint">{weights[k]}</span>
              </span>
              <input
                type="range"
                min={0}
                max={50}
                step={1}
                value={weights[k]}
                disabled={noData}
                onChange={(e) => setWeights({ ...weights, [k]: Number(e.target.value) })}
                className="w-full accent-[#C9A227]"
                aria-label={`${CATEGORY_LABELS[k]} weight`}
              />
            </label>
          );
        })}
      </div>

      <div className="mt-5 flex items-center justify-between flex-wrap gap-3 border-t border-ledger-line pt-4">
        <p className="text-sm">
          With your weights:{" "}
          <span className="font-mono text-lg tabular">{custom === null ? "—" : custom.toFixed(1)}</span>
          <span className="text-ledger-faint"> vs official {officialScore.toFixed(1)}</span>
          {custom !== null && changed && (
            <span className={`ml-2 font-mono text-xs ${custom >= officialScore ? "text-signal-strong" : "text-signal-weak"}`}>
              {custom >= officialScore ? "+" : ""}
              {(custom - officialScore).toFixed(1)}
            </span>
          )}
        </p>
        <button
          onClick={() => setWeights(toPct(officialWeights))}
          disabled={!changed}
          className="inline-flex items-center gap-1.5 text-xs text-ledger-faint hover:text-ledger-ink disabled:opacity-40"
        >
          <RotateCcw size={12} /> Reset to official weights
        </button>
      </div>
      <p className="text-[11px] text-ledger-faint mt-2">
        A personal view only. It isn't saved, and it doesn't change the verdict or the official score.
      </p>
    </div>
  );
}
