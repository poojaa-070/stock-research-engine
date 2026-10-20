import { ArrowUpCircle, ArrowDownCircle } from "lucide-react";

export default function BullBearCase({ bullCase, bearCase }: { bullCase: string; bearCase: string }) {
  if (!bullCase && !bearCase) {
    return (
      <p className="text-sm text-ledger-faint italic">
        Bull/bear case unavailable this run — usually means the AI narrative
        layer didn't return a result (check ANTHROPIC_API_KEY). The scores
        above are unaffected either way.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
      <div className="border-l-2 border-signal-strong pl-4">
        <div className="flex items-center gap-2 mb-2">
          <ArrowUpCircle size={15} className="text-signal-strong" />
          <h3 className="text-sm font-medium">The case for</h3>
        </div>
        <p className="text-sm leading-relaxed text-ledger-ink">{bullCase || "Not enough positive signal to make a strong case."}</p>
      </div>
      <div className="border-l-2 border-signal-weak pl-4">
        <div className="flex items-center gap-2 mb-2">
          <ArrowDownCircle size={15} className="text-signal-weak" />
          <h3 className="text-sm font-medium">The case against</h3>
        </div>
        <p className="text-sm leading-relaxed text-ledger-ink">{bearCase || "No major red flags surfaced in the available data."}</p>
      </div>
    </div>
  );
}
