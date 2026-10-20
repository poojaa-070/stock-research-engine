import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Radio } from "lucide-react";
import { importCompany, errorMessage } from "../api/client";
import type { PeerSnapshot } from "../types";

export default function PeerComparisonTable({
  peers,
  companyId,
}: {
  peers: PeerSnapshot[];
  companyId: number;
}) {
  const navigate = useNavigate();
  const [importing, setImporting] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  // v2: a live peer we don't hold yet can be imported in place, instead of
  // being a dead row (v1).
  async function analyse(symbol: string) {
    setImporting(symbol);
    setImportError(null);
    try {
      const c = await importCompany(symbol);
      navigate(`/report/${c.id}`);
    } catch (e) {
      setImportError(errorMessage(e));
    } finally {
      setImporting(null);
    }
  }

  if (peers.length === 0) {
    return (
      <p className="text-sm text-ledger-faint italic">
        No live sector peers found for this company right now, and none are in this
        system yet either — competitive position needs peers to compare against.
      </p>
    );
  }

  const anyLive = peers.some((p) => p.isLive);

  return (
    <div>
      {anyLive && (
        <p className="mb-2 flex items-center gap-1.5 text-[11px] text-ledger-faint">
          <Radio size={11} className="text-signal-strong animate-pulse-slow" />
          Live sector peers, fetched from the market just now — not limited to
          companies already in this system.
        </p>
      )}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-ledger-faint border-b border-ledger-line">
            <th className="py-2 font-normal">Company</th>
            <th className="py-2 font-normal text-right font-mono">CMP</th>
            <th className="py-2 font-normal text-right font-mono">P/E</th>
            <th className="py-2 font-normal text-right font-mono">ROCE</th>
            <th className="py-2 font-normal text-right font-mono">Score</th>
          </tr>
        </thead>
        <tbody>
          {peers.map((p, i) => {
            const clickable = p.companyId !== null;
            return (
              <tr
                key={`${p.nseSymbol ?? p.name}-${i}`}
                onClick={() => clickable && navigate(`/compare/${companyId}/${p.companyId}`)}
                className={`border-b border-ledger-line transition-colors duration-150 ${
                  clickable ? "cursor-pointer hover:bg-ledger-panel/60" : "opacity-80"
                }`}
                style={{ animation: `fade-slide-in 0.35s ease both`, animationDelay: `${i * 40}ms` }}
              >
                <td className="py-2.5">
                  {p.name}
                  {p.isLive && (
                    <span className="ml-1.5 align-middle inline-block w-1.5 h-1.5 rounded-full bg-signal-strong animate-pulse-slow" />
                  )}
                </td>
                <td className="py-2.5 text-right font-mono tabular">
                  {p.cmp !== null ? `₹${p.cmp.toFixed(2)}` : "—"}
                </td>
                <td className="py-2.5 text-right font-mono tabular">{p.peRatio?.toFixed(1) ?? "—"}</td>
                <td className="py-2.5 text-right font-mono tabular">
                  {p.rocePercent !== null ? `${p.rocePercent.toFixed(1)}%` : "—"}
                </td>
                <td className="py-2.5 text-right font-mono tabular">
                  {p.overallScore !== null ? (
                    p.overallScore.toFixed(0)
                  ) : p.companyId === null && p.nseSymbol ? (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        analyse(p.nseSymbol);
                      }}
                      disabled={importing !== null}
                      className="text-xs text-ledger-gold hover:underline disabled:opacity-50 font-body"
                    >
                      {importing === p.nseSymbol ? "Importing…" : "Analyse"}
                    </button>
                  ) : (
                    <span className="text-xs text-ledger-faint font-body">not yet analysed</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {importError && <p className="mt-2 text-xs text-signal-weak">{importError}</p>}
    </div>
  );
}
