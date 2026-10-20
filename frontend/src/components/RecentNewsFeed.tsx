import type { NewsItem } from "../types";

export default function RecentNewsFeed({ news }: { news: NewsItem[] }) {
  if (news.length === 0) {
    return (
      <p className="text-sm text-ledger-faint italic">
        No recent news scraped for this company yet. Note: this is headline
        coverage, not a forward calendar of results dates or order-book
        announcements — there's no source for upcoming events wired in yet.
      </p>
    );
  }

  return (
    <ul className="space-y-3">
      {news.map((n, i) => (
        <li key={i} className="flex items-start justify-between gap-4 text-sm">
          <div>
            {n.url ? (
              <a href={n.url} target="_blank" rel="noopener noreferrer" className="hover:text-ledger-gold">
                {n.headline}
              </a>
            ) : (
              <span>{n.headline}</span>
            )}
            <p className="text-xs text-ledger-faint mt-0.5">{n.source}</p>
          </div>
          <span className="text-xs text-ledger-faint shrink-0 font-mono">
            {new Date(n.publishedAt).toLocaleDateString()}
          </span>
        </li>
      ))}
    </ul>
  );
}
