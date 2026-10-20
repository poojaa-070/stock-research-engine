import { Link, useSearchParams } from "react-router-dom";
import { Radio } from "lucide-react";
import { compareCompaniesByName } from "../api/client";
import StockLoader from "../components/StockLoader";
import CompareTable from "../components/CompareTable";
import { useAsync } from "../lib/useAsync";

/**
 * The search page's "compare two companies" box lands here: two free-text
 * names in the query string (?a=...&b=...), resolved and compared live.
 */
export default function LiveComparePage() {
  const [params] = useSearchParams();
  const nameA = params.get("a") ?? "";
  const nameB = params.get("b") ?? "";
  const { data, error } = useAsync(
    () => (nameA && nameB ? compareCompaniesByName(nameA, nameB) : Promise.resolve(null)),
    [nameA, nameB],
  );

  if (!nameA || !nameB) {
    return (
      <div className="max-w-3xl mx-auto pt-16 px-6">
        <p className="text-ledger-faint">Pick two companies to compare from the search page.</p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="max-w-3xl mx-auto pt-12 px-6">
        <p className="text-signal-weak">Couldn't live-compare "{nameA}" vs "{nameB}": {error}</p>
        <Link to="/" className="btn-animated inline-block mt-4 text-sm text-ledger-gold border border-ledger-gold/40 px-3 py-2">
          Back to search
        </Link>
      </div>
    );
  }
  if (!data) {
    return (
      <StockLoader
        title={`Live-comparing ${nameA} vs ${nameB}…`}
        subtitle="Resolving both companies and pulling fresh quotes and ratios — not relying on cached data."
      />
    );
  }

  return (
    <div className="max-w-3xl mx-auto pt-10 pb-24 px-6" style={{ animation: "fade-slide-in 0.4s ease both" }}>
      <p className="flex items-center gap-1.5 text-xs text-ledger-gold font-mono mb-2">
        <Radio size={12} className="animate-pulse-slow" />
        LIVE COMPARISON
      </p>
      <h1 className="font-display text-3xl mb-1">
        <Link to={`/report/${data.companyA.id}`} className="hover:text-ledger-gold">{data.companyA.name}</Link>{" "}
        <span className="text-ledger-faint font-body text-xl">vs</span>{" "}
        <Link to={`/report/${data.companyB.id}`} className="hover:text-ledger-gold">{data.companyB.name}</Link>
      </h1>
      <p className="text-sm text-ledger-faint mb-8">
        Higher overall score: <span className="text-ledger-ink font-medium">{data.overallWinner}</span>
      </p>
      <CompareTable data={data} />
      <p className="mt-6 text-xs text-ledger-faint">
        ● marks the better value per row. Figures are fetched live for this
        comparison, not read back from a stored snapshot. This is a factual
        comparison, not a recommendation to prefer one company over the other.
      </p>
    </div>
  );
}
