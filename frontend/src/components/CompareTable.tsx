import type { CompareResponse } from "../types";

function WinnerMark({ side, winner }: { side: "A" | "B"; winner: string }) {
  if (winner !== side) return null;
  return <span className="ml-2 text-signal-strong text-xs" aria-label="better value">●</span>;
}

/** Pairwise metric table shared by the by-id and live-by-name compare pages. */
export default function CompareTable({ data }: { data: CompareResponse }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border-t border-ledger-line">
        <thead>
          <tr className="text-left text-ledger-faint border-b border-ledger-line">
            <th className="py-3 font-normal">Metric</th>
            <th className="py-3 font-normal text-right font-mono">{data.companyA.nseSymbol}</th>
            <th className="py-3 font-normal text-right font-mono">{data.companyB.nseSymbol}</th>
          </tr>
        </thead>
        <tbody>
          {data.rows.map((row, i) => (
            <tr
              key={row.metricLabel}
              className="row-animated border-b border-ledger-line"
              style={{ animation: "fade-slide-in 0.3s ease both", animationDelay: `${i * 35}ms` }}
            >
              <td className="py-3">{row.metricLabel}</td>
              <td className="py-3 text-right font-mono tabular">
                {row.companyAValue}
                <WinnerMark side="A" winner={row.winner} />
              </td>
              <td className="py-3 text-right font-mono tabular">
                {row.companyBValue}
                <WinnerMark side="B" winner={row.winner} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
