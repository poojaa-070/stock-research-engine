import { Link, useParams } from "react-router-dom";
import { compareCompanies } from "../api/client";
import StockLoader from "../components/StockLoader";
import CompareTable from "../components/CompareTable";
import { useAsync } from "../lib/useAsync";

export default function ComparePage() {
  const { aId, bId } = useParams();
  const { data, error } = useAsync(() => compareCompanies(Number(aId), Number(bId)), [aId, bId]);

  if (error) {
    return (
      <div className="max-w-3xl mx-auto pt-12 px-6">
        <p className="text-signal-weak">Couldn't load this comparison: {error}</p>
      </div>
    );
  }
  if (!data) {
    return (
      <StockLoader
        title="Building this comparison…"
        subtitle="Pulling live quotes, and scoring either company first if it hasn't been analysed yet."
      />
    );
  }

  return (
    <div className="max-w-3xl mx-auto pt-10 pb-24 px-6" style={{ animation: "fade-slide-in 0.4s ease both" }}>
      <h1 className="font-display text-3xl mb-1">
        <Link to={`/report/${data.companyA.id}`} className="hover:text-ledger-gold">{data.companyA.name}</Link>{" "}
        <span className="text-ledger-faint font-body text-xl">vs</span>{" "}
        <Link to={`/report/${data.companyB.id}`} className="hover:text-ledger-gold">{data.companyB.name}</Link>
      </h1>
      <p className="text-sm text-ledger-faint mb-8">
        Higher overall score: <span className="text-ledger-ink font-medium">{data.overallWinner}</span>
        {" · "}
        <Link to={`/compare-multi?ids=${data.companyA.id},${data.companyB.id}`} className="text-ledger-gold hover:underline">
          Add more companies
        </Link>
      </p>
      <CompareTable data={data} />
      <p className="mt-6 text-xs text-ledger-faint">
        ● marks the better value per row. This is a factual comparison, not a
        recommendation to prefer one company over the other.
      </p>
    </div>
  );
}
