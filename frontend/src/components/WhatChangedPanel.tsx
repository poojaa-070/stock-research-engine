import { useState } from "react";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { getWhatChanged } from "../api/client";
import { useAsync } from "../lib/useAsync";
import { shortDate } from "../lib/format";
import { CATEGORY_LABELS } from "../types";

function Delta({ value }: { value: number }) {
  if (Math.abs(value) < 0.05) return <span className="text-ledger-faint inline-flex items-center gap-0.5"><Minus size={12} />0.0</span>;
  const up = value > 0;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={`inline-flex items-center gap-0.5 ${up ? "text-signal-strong" : "text-signal-weak"}`}>
      <Icon size={13} />
      {up ? "+" : ""}
      {value.toFixed(1)}
    </span>
  );
}

/**
 * Diff of this report against the previous one: score deltas per category,
 * then the evidence lines that appeared or dropped out, which is the "why".
 * `reportId` is a dependency so the panel refreshes after a regenerate.
 */
export default function WhatChangedPanel({ companyId, reportId }: { companyId: number; reportId: number }) {
  const { data, error, loading } = useAsync(() => getWhatChanged(companyId), [companyId, reportId]);
  const [showEvidence, setShowEvidence] = useState(false);

  if (loading) return <p className="text-sm text-ledger-faint">Comparing with the previous report…</p>;
  if (error) return <p className="text-sm text-signal-weak">{error}</p>;
  if (!data) {
    return (
      <p className="text-sm text-ledger-faint italic">
        This is the first report for this company. Regenerate it later to see what moved.
      </p>
    );
  }

  const moved = data.categoryDeltas.filter((d) => Math.abs(d.delta) >= 0.05);
  const overall = data.currentOverallScore - data.previousOverallScore;

  return (
    <div>
      <p className="text-sm text-ledger-faint mb-3">
        Since {shortDate(data.previousReportAt)}: overall{" "}
        <span className="font-mono tabular text-ledger-ink">
          {data.previousOverallScore.toFixed(1)} → {data.currentOverallScore.toFixed(1)}
        </span>{" "}
        <span className="font-mono tabular"><Delta value={overall} /></span>
        {data.previousVerdict && data.currentVerdict && data.previousVerdict !== data.currentVerdict && (
          <> · verdict moved from {data.previousVerdict} to {data.currentVerdict}</>
        )}
      </p>

      {moved.length === 0 ? (
        <p className="text-sm text-ledger-faint italic">No category score moved between the two reports.</p>
      ) : (
        <table className="w-full text-sm">
          <tbody>
            {moved.map((d) => (
              <tr key={d.category} className="border-b border-ledger-line">
                <td className="py-2">{CATEGORY_LABELS[d.category] ?? d.category}</td>
                <td className="py-2 text-right font-mono tabular text-ledger-faint">
                  {d.previousScore.toFixed(1)} → {d.currentScore.toFixed(1)}
                </td>
                <td className="py-2 text-right font-mono tabular w-20"><Delta value={d.delta} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {data.evidenceChanges.length > 0 && (
        <div className="mt-4">
          <button
            onClick={() => setShowEvidence(!showEvidence)}
            aria-expanded={showEvidence}
            className="text-xs text-ledger-gold hover:underline"
          >
            {showEvidence ? "Hide" : "Show"} the evidence that changed ({data.evidenceChanges.length} categor
            {data.evidenceChanges.length === 1 ? "y" : "ies"})
          </button>
          {showEvidence && (
            <div className="mt-3 space-y-4">
              {data.evidenceChanges.map((c) => (
                <div key={c.category}>
                  <p className="text-xs text-ledger-faint mb-1">{CATEGORY_LABELS[c.category] ?? c.category}</p>
                  <ul className="space-y-1 text-sm">
                    {c.removed.map((s, i) => (
                      <li key={`r${i}`} className="text-ledger-faint line-through decoration-signal-weak/60">{s}</li>
                    ))}
                    {c.added.map((s, i) => (
                      <li key={`a${i}`} className="text-ledger-ink">
                        <span className="text-signal-strong mr-1.5">+</span>
                        {s}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
