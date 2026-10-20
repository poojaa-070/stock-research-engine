import { useState } from "react";
import { Landmark, Award, TrendingUp, Scale, Users, ShieldAlert, Activity, ChevronRight } from "lucide-react";
import type { CategoryScore } from "../types";
import { CATEGORY_LABELS } from "../types";
import ScoreReadout from "./ScoreReadout";

const SENTIMENT_DOT: Record<string, string> = {
  positive: "bg-signal-strong",
  neutral: "bg-signal-watch",
  negative: "bg-signal-weak",
};

const CATEGORY_ICONS: Record<string, typeof Landmark> = {
  financial_health: Landmark,
  business_quality: Award,
  growth: TrendingUp,
  valuation: Scale,
  management: Users,
  risk: ShieldAlert,
  momentum: Activity,
};

export default function CategoryRow({ category }: { category: CategoryScore }) {
  const [open, setOpen] = useState(false);
  const label = CATEGORY_LABELS[category.category] ?? category.category;
  const completenessPct = Math.round(category.dataCompleteness * 100);
  const Icon = CATEGORY_ICONS[category.category] ?? Landmark;

  return (
    <div className="border-b border-ledger-line">
      <button
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="row-animated w-full flex items-center gap-5 py-4 text-left group"
      >
        <Icon size={16} className="shrink-0 text-ledger-faint" />
        <span className="w-36 shrink-0 font-body text-sm">{label}</span>
        <div className="flex-1">
          {category.dataCompleteness > 0 ? (
            <ScoreReadout score={category.score} size="sm" />
          ) : (
            // v2: no data is shown as such, and is left out of the overall score.
            <span className="text-xs text-ledger-faint italic">No data yet, not counted in the overall score</span>
          )}
        </div>
        <span className="w-20 shrink-0 text-right text-xs text-ledger-faint">
          {completenessPct}% data
        </span>
        <ChevronRight
          size={15}
          className={`shrink-0 text-ledger-faint transition-transform duration-200 ${open ? "rotate-90" : ""}`}
        />
      </button>

      {open && (
        <div className="pb-5 pl-[3.75rem] pr-8">
          {category.evidence.length === 0 ? (
            <p className="text-sm text-ledger-faint italic">
              No evidence recorded for this category yet — likely a data gap rather than a
              neutral finding.
            </p>
          ) : (
            <ul className="space-y-2.5">
              {category.evidence.map((ev, i) => (
                <li key={i} className="flex gap-3 text-sm">
                  <span
                    className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${SENTIMENT_DOT[ev.sentiment] ?? "bg-ledger-faint"}`}
                  />
                  <div>
                    <p className="text-ledger-ink">{ev.statement}</p>
                    <p className="text-xs text-ledger-faint mt-0.5">Source: {ev.sourceRef}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
