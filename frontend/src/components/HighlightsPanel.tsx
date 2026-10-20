import { TrendingUp, TrendingDown } from "lucide-react";
import type { CategoryScore } from "../types";
import { CATEGORY_LABELS } from "../types";

export default function HighlightsPanel({ categories }: { categories: CategoryScore[] }) {
  const allEvidence = categories.flatMap((c) =>
    c.evidence.map((e) => ({ ...e, category: c.category }))
  );

  const positives = allEvidence.filter((e) => e.sentiment === "positive").slice(0, 3);
  const negatives = allEvidence.filter((e) => e.sentiment === "negative").slice(0, 3);

  if (positives.length === 0 && negatives.length === 0) return null;

  return (
    <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 gap-6">
      <div className="border border-ledger-line bg-ledger-panel/50 p-5">
        <div className="flex items-center gap-2 mb-3">
          <TrendingUp size={16} className="text-signal-strong" />
          <h3 className="font-body text-sm font-medium">What's working</h3>
        </div>
        {positives.length === 0 ? (
          <p className="text-sm text-ledger-faint italic">No strong positive signals yet.</p>
        ) : (
          <ul className="space-y-2.5">
            {positives.map((e, i) => (
              <li key={i} className="text-sm leading-snug">
                <span className="text-ledger-faint text-xs font-mono">
                  {CATEGORY_LABELS[e.category] ?? e.category}
                </span>
                <p>{e.statement}</p>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="border border-ledger-line bg-ledger-panel/50 p-5">
        <div className="flex items-center gap-2 mb-3">
          <TrendingDown size={16} className="text-signal-weak" />
          <h3 className="font-body text-sm font-medium">Watch out for</h3>
        </div>
        {negatives.length === 0 ? (
          <p className="text-sm text-ledger-faint italic">No major red flags in the available data.</p>
        ) : (
          <ul className="space-y-2.5">
            {negatives.map((e, i) => (
              <li key={i} className="text-sm leading-snug">
                <span className="text-ledger-faint text-xs font-mono">
                  {CATEGORY_LABELS[e.category] ?? e.category}
                </span>
                <p>{e.statement}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
