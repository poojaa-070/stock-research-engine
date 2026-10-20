import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Search, ArrowRight, GitCompare, Radio } from "lucide-react";
import { searchCompanies, importCompany, errorMessage } from "../api/client";
import type { CompanySummary } from "../types";

export default function SearchPage() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CompanySummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const navigate = useNavigate();

  // Live compare box: two free-text company names, not a DB dropdown --
  // defaults are just a starting suggestion, both fields stay fully
  // editable so any two companies can be compared live.
  const [compareA, setCompareA] = useState("HDFC Bank");
  const [compareB, setCompareB] = useState("ICICI Bank");

  useEffect(() => {
    setLoading(true);
    setImportError(null);
    setSearchError(null);
    const handle = setTimeout(async () => {
      try {
        const r = await searchCompanies(query);
        setResults(r);
      } catch (e) {
        // A real failure to reach the backend -- distinct from "no matches
        // for this query", which is just an empty `results` array. Shown
        // as its own message rather than silently returning fake results.
        setResults([]);
        setSearchError(
          e instanceof Error ? e.message : "Couldn't reach the backend to search."
        );
      } finally {
        setLoading(false);
      }
    }, 200);
    return () => clearTimeout(handle);
  }, [query]);

  async function handleImport() {
    setImporting(true);
    setImportError(null);
    try {
      const company = await importCompany(query);
      navigate(`/report/${company.id}`);
    } catch (e) {
      // v2: show the backend's specific reason (not found on Screener vs
      // scraper offline) rather than one generic message for both.
      setImportError(errorMessage(e));
    } finally {
      setImporting(false);
    }
  }

  function handleCompareLive() {
    const a = compareA.trim();
    const b = compareB.trim();
    if (!a || !b) return;
    navigate(`/compare-live?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`);
  }

  return (
    <div className="max-w-2xl mx-auto pt-16 px-6">
      <p className="text-ledger-gold text-sm font-mono mb-3">NSE / BSE research</p>
      <h1 className="font-display text-4xl mb-2">Look up a company</h1>
      <p className="text-ledger-faint mb-8 max-w-md">
        Every score below is derived from stored facts and a fixed rubric — never a
        buy or sell call.
      </p>

      <div className="relative">
        <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-ledger-faint" />
        <input
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            // Enter opens the single match, or offers the import when there are none.
            if (e.key !== "Enter" || loading) return;
            if (results.length === 1) navigate(`/report/${results[0].id}`);
            else if (results.length === 0 && query.trim() && !importing) handleImport();
          }}
          aria-label="Search companies"
          placeholder="Search by name or ticker — try HDFC, ICICI, TCS, or any NSE company"
          className="w-full bg-ledger-panel border border-ledger-line pl-11 pr-4 py-3 text-lg font-body
                     placeholder:text-ledger-faint focus:outline-none focus:border-ledger-gold
                     transition-colors duration-150"
        />
      </div>

      <div className="mt-6 divide-y divide-ledger-line border-t border-ledger-line">
        {loading && <p className="py-4 text-sm text-ledger-faint">Searching…</p>}
        {!loading && searchError && (
          <div className="py-4" style={{ animation: "fade-slide-in 0.3s ease both" }}>
            <p className="text-sm text-signal-weak">
              Couldn't reach the backend to search: {searchError}
            </p>
            <p className="mt-1 text-xs text-ledger-faint">
              Make sure the Spring Boot API is running at{" "}
              <code className="font-mono">
                {import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api"}
              </code>
              .
            </p>
          </div>
        )}
        {!loading && !searchError && results.length === 0 && query && (
          <div className="py-4" style={{ animation: "fade-slide-in 0.3s ease both" }}>
            <p className="text-sm text-ledger-faint mb-3">
              No matches in the local database for "{query}".
            </p>
            <button
              onClick={handleImport}
              disabled={importing}
              className="btn-animated text-sm text-ledger-gold border border-ledger-gold/40 px-3 py-2
                         hover:bg-ledger-gold/10 disabled:opacity-50"
            >
              {importing ? "Looking up on Screener.in…" : `Look up "${query}" on Screener.in`}
            </button>
            {importError && <p className="mt-3 text-sm text-signal-weak">{importError}</p>}
          </div>
        )}
        {!loading &&
          results.map((c, i) => (
            <button
              key={c.id}
              onClick={() => navigate(`/report/${c.id}`)}
              className="row-animated w-full text-left py-4 flex items-center justify-between group hover:bg-ledger-panel/60 px-2 -mx-2"
              style={{ animation: "fade-slide-in 0.3s ease both", animationDelay: `${i * 30}ms` }}
            >
              <div>
                <p className="font-body text-base">{c.name}</p>
                <p className="text-xs text-ledger-faint font-mono">
                  {c.nseSymbol} · {c.sector}
                </p>
              </div>
              <span className="text-ledger-faint group-hover:text-ledger-gold group-hover:translate-x-0.5 transition-all">
                <ArrowRight size={18} />
              </span>
            </button>
          ))}
      </div>

      <div className="mt-10 border-t border-ledger-line pt-6 pb-16">
        <p className="text-xs text-ledger-faint flex items-center gap-1.5 mb-3">
          <GitCompare size={13} />
          Compare two companies — resolved and scored live, not read back from
          whatever's already stored.
        </p>
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
          <input
            value={compareA}
            onChange={(e) => setCompareA(e.target.value)}
            placeholder="First company (e.g. HDFC Bank)"
            className="flex-1 bg-ledger-panel border border-ledger-line px-3 py-2 text-sm font-body
                       placeholder:text-ledger-faint focus:outline-none focus:border-ledger-gold
                       transition-colors duration-150"
          />
          <span className="text-ledger-faint text-xs font-mono text-center sm:px-1">vs</span>
          <input
            value={compareB}
            onChange={(e) => setCompareB(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleCompareLive()}
            placeholder="Second company (e.g. ICICI Bank)"
            className="flex-1 bg-ledger-panel border border-ledger-line px-3 py-2 text-sm font-body
                       placeholder:text-ledger-faint focus:outline-none focus:border-ledger-gold
                       transition-colors duration-150"
          />
          <button
            onClick={handleCompareLive}
            type="button"
            disabled={!compareA.trim() || !compareB.trim()}
            className="btn-animated flex items-center justify-center gap-1.5 text-sm text-ledger-gold
                       border border-ledger-gold/40 px-4 py-2 hover:bg-ledger-gold/10 disabled:opacity-40
                       disabled:cursor-not-allowed"
          >
            <Radio size={13} className="animate-pulse-slow" />
            Compare live
          </button>
        </div>
        <p className="mt-3 text-xs text-ledger-faint">
          More than two?{" "}
          <Link to="/compare-multi" className="text-ledger-gold hover:underline">Compare up to six</Link>{" "}
          side by side.
        </p>
      </div>
    </div>
  );
}
