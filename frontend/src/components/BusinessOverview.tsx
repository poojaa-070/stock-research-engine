export default function BusinessOverview({ description }: { description: string | null }) {
  if (!description) {
    return (
      <p className="text-sm text-ledger-faint italic">
        No business description scraped yet for this company — this comes from
        Screener's "About" section, which the scraper doesn't pull yet for every
        company. See docs/ROADMAP.md.
      </p>
    );
  }
  return <p className="text-[15px] leading-relaxed text-ledger-ink">{description}</p>;
}
