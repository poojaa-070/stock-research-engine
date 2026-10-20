import { useEffect, useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { BookOpen } from "lucide-react";
import { getHealth } from "../api/client";
import type { Health } from "../types";

const NAV = [
  { to: "/", label: "Search", end: true },
  { to: "/watchlist", label: "Watchlist", end: false },
  { to: "/compare-multi", label: "Compare", end: false },
  { to: "/methodology", label: "Methodology", end: false },
];

/** One dot summarising the three moving parts; the tooltip names what's missing. */
function StatusDot() {
  const [health, setHealth] = useState<Health | null | undefined>(undefined);

  useEffect(() => {
    let alive = true;
    const check = () => getHealth().then((h) => alive && setHealth(h));
    check();
    const id = setInterval(check, 60_000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  if (health === undefined) return null;

  let color = "bg-signal-strong";
  let label = "All services running";
  if (!health || health.database === "down") {
    color = "bg-signal-weak";
    label = !health ? "Backend unreachable" : "Database unreachable";
  } else if (health.scraper === "down" || health.ai !== "configured") {
    color = "bg-signal-watch";
    const missing = [
      health.scraper === "down" && "scraper service offline: showing stored data only",
      health.ai !== "configured" && "no ANTHROPIC_API_KEY: summaries are rule-based",
    ].filter(Boolean);
    label = missing.join("; ");
  }

  return (
    <span className="flex items-center gap-1.5 text-[11px] text-ledger-faint" title={label}>
      <span className={`h-2 w-2 rounded-full ${color}`} aria-hidden />
      <span className="sr-only sm:not-sr-only sm:max-w-[16rem] sm:truncate">{label}</span>
    </span>
  );
}

export default function Header() {
  return (
    <header className="border-b border-ledger-line bg-ledger-bg/80 backdrop-blur sticky top-0 z-10 print:hidden">
      <div className="max-w-5xl mx-auto px-6 h-14 flex items-center justify-between gap-4">
        <Link to="/" className="flex items-center gap-2 group shrink-0">
          <BookOpen size={18} className="text-ledger-gold transition-transform duration-200 group-hover:rotate-6" />
          <span className="font-display text-lg tracking-tight">Ledger</span>
        </Link>
        <nav className="flex items-center gap-1 sm:gap-3 overflow-x-auto">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) =>
                `text-xs sm:text-sm px-2 py-1 whitespace-nowrap transition-colors focus-visible:outline focus-visible:outline-1 focus-visible:outline-ledger-gold ${
                  isActive ? "text-ledger-gold" : "text-ledger-faint hover:text-ledger-ink"
                }`
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden md:block shrink-0">
          <StatusDot />
        </div>
      </div>
    </header>
  );
}
