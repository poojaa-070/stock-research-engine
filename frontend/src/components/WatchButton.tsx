import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import { addToWatchlist, isWatching, removeFromWatchlist, errorMessage } from "../api/client";

export default function WatchButton({ companyId }: { companyId: number }) {
  const [watching, setWatching] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    isWatching(companyId).then((w) => alive && setWatching(w), () => alive && setWatching(false));
    return () => {
      alive = false;
    };
  }, [companyId]);

  async function toggle() {
    setBusy(true);
    setError(null);
    try {
      if (watching) await removeFromWatchlist(companyId);
      else await addToWatchlist(companyId);
      setWatching(!watching);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (watching === null) return null;
  return (
    <span className="inline-flex flex-col items-start">
      <button
        onClick={toggle}
        disabled={busy}
        aria-pressed={watching}
        className={`btn-animated inline-flex items-center gap-1.5 text-xs border px-2.5 py-1.5 disabled:opacity-50 ${
          watching
            ? "border-ledger-gold/60 text-ledger-gold bg-ledger-gold/10"
            : "border-ledger-line text-ledger-faint hover:text-ledger-ink"
        }`}
      >
        <Star size={13} fill={watching ? "currentColor" : "none"} />
        {watching ? "On watchlist" : "Add to watchlist"}
      </button>
      {error && <span className="text-[11px] text-signal-weak mt-1">{error}</span>}
    </span>
  );
}
