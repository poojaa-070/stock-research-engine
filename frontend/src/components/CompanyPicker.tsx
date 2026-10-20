import { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { searchCompanies } from "../api/client";
import type { CompanySummary } from "../types";

/** Type-ahead over companies already in the database. */
export default function CompanyPicker({
  onPick,
  exclude = [],
  placeholder = "Add a company by name or ticker",
  disabled = false,
}: {
  onPick: (c: CompanySummary) => void;
  exclude?: number[];
  placeholder?: string;
  disabled?: boolean;
}) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<CompanySummary[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!q.trim()) {
      setResults([]);
      return;
    }
    let current = true;
    const t = setTimeout(() => {
      searchCompanies(q).then((r) => current && setResults(r), () => current && setResults([]));
    }, 200);
    return () => {
      current = false;
      clearTimeout(t);
    };
  }, [q]);

  const shown = results.filter((r) => !exclude.includes(r.id)).slice(0, 8);

  return (
    <div className="relative">
      <input
        value={q}
        disabled={disabled}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={placeholder}
        className="w-full bg-ledger-panel border border-ledger-line px-3 py-2 text-sm placeholder:text-ledger-faint
                   focus:outline-none focus:border-ledger-gold disabled:opacity-50"
      />
      {open && shown.length > 0 && (
        <ul className="absolute z-20 mt-1 w-full bg-ledger-panel border border-ledger-line max-h-64 overflow-y-auto">
          {shown.map((c) => (
            <li key={c.id}>
              <button
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onPick(c);
                  setQ("");
                  setOpen(false);
                }}
                className="w-full text-left px-3 py-2 text-sm hover:bg-ledger-line/50 flex items-center justify-between"
              >
                <span>
                  {c.name} <span className="text-ledger-faint font-mono text-xs">{c.nseSymbol}</span>
                </span>
                <Plus size={14} className="text-ledger-faint" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {open && q.trim() && shown.length === 0 && (
        <p className="absolute z-20 mt-1 w-full bg-ledger-panel border border-ledger-line px-3 py-2 text-xs text-ledger-faint">
          Not in the database yet. Import it from the search page first.
        </p>
      )}
    </div>
  );
}
