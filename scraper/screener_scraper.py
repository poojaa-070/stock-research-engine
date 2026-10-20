"""
Scrapes a single company's Screener.in page and returns a dict of parsed
data ready for the `financials`, `ratios`, and `shareholding_pattern` tables,
plus the raw HTML for `scrape_metadata.raw_payload` (so a broken parser
never loses the underlying data — you can re-parse without re-scraping).

Screener's markup changes occasionally; this file is intentionally the only
place that knows about Screener's specific table/class structure, so a
markup change means editing here, not touching the scoring engine or API.
"""

import re
from datetime import datetime
from bs4 import BeautifulSoup

from config import SCREENER_BASE_URL
from rate_limited_client import RateLimitedClient

HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; StockResearchBot/0.1; "
                  "+contact: you@example.com) research tool, on-demand, rate-limited"
}


class ScreenerScraper:
    def __init__(self):
        self.client = RateLimitedClient(SCREENER_BASE_URL, headers=HEADERS)

    def fetch_company_page(self, screener_url_path: str) -> dict:
        """screener_url_path e.g. '/company/HDFCBANK/' """
        resp = self.client.get(screener_url_path)
        html = resp.text
        soup = BeautifulSoup(html, "html.parser")

        result = {
            "scraped_at": datetime.utcnow().isoformat(),
            "raw_html": html,          # -> scrape_metadata.raw_payload
            "ratios": self._parse_top_ratios(soup),
            "quarterly_results": self._parse_table(soup, "quarterly-results"),
            "annual_results": self._parse_table(soup, "profit-loss"),
            "balance_sheet": self._parse_table(soup, "balance-sheet"),
            "cash_flow": self._parse_table(soup, "cash-flow"),
            "shareholding": self._parse_table(soup, "shareholding"),
            "business_description": self._parse_about_section(soup),
            "peers": self._parse_peer_comparison(soup),
            "classification": self._parse_classification(soup),
        }
        return result

    def _parse_classification(self, soup: BeautifulSoup) -> dict:
        """v2: sector/industry from the market-classification links Screener
        shows above its peer table (links into /market/..., titled e.g.
        "Broad Sector", "Sector", "Broad Industry", "Industry").

        v1 stored sector="" for every imported company, and since sector is
        what peer matching keys on, every imported company became a "peer" of
        every other one. Best effort: returns {} when the links aren't found,
        and the caller then leaves the existing value alone.
        """
        links = [a for a in soup.select("a[href^='/market/']") if a.get_text(strip=True)]
        if not links:
            return {}
        by_title = {}
        for a in links:
            title = (a.get("title") or "").strip().lower()
            if title and title not in by_title:
                by_title[title] = a.get_text(strip=True)
        sector = by_title.get("sector") or by_title.get("broad sector")
        industry = by_title.get("industry") or by_title.get("broad industry")
        if not sector and not industry:
            # Untitled links: Screener orders them broad -> narrow.
            names = [a.get_text(strip=True) for a in links]
            sector = names[0]
            industry = names[-1] if len(names) > 1 else None
        result = {}
        if sector:
            result["sector"] = sector
        if industry:
            result["industry"] = industry
        return result

    def _parse_peer_comparison(self, soup: BeautifulSoup) -> list[dict]:
        """Screener's own 'Peer comparison' section (id="peers") lists
        other companies Screener itself considers same-sector, each with
        its own live CMP/P/E/ROCE/etc pulled straight off Screener at
        request time. This is what should back sector-relative comparisons
        -- it reflects the real market right now, not only whichever
        companies happen to already have a row in our own `companies`
        table. Returns [] (not a guess) if the section isn't present on a
        given company's page, e.g. very illiquid/unlisted-peer names."""
        section = soup.select_one("#peers")
        if not section:
            return []
        table = section.select_one("table")
        if not table:
            return []

        headers = [th.get_text(strip=True) for th in table.select("thead th")]
        peers = []
        for tr in table.select("tbody tr"):
            cells = tr.select("td")
            if not cells:
                continue
            name_link = cells[0].select_one("a")
            name = name_link.get_text(strip=True) if name_link else cells[0].get_text(strip=True)
            href = name_link.get("href") if name_link else None
            symbol_match = re.search(r"/company/([A-Z0-9&]+)/", href) if href else None
            row = {"name": name, "nse_symbol": symbol_match.group(1) if symbol_match else None}
            for i, header in enumerate(headers[1:], start=1):
                if i < len(cells):
                    row[header] = cells[i].get_text(strip=True)
            peers.append(row)
        return peers

    def _parse_about_section(self, soup: BeautifulSoup) -> str | None:
        """Screener shows a short "About <Company>" paragraph near the top
        of the page (business description: what they do, how they make
        money). Selector is a best guess -- Screener's About block has
        historically lived in a div with class 'company-profile' or
        similar containing a 'sub' paragraph; verify against a live page
        and adjust if this comes back empty. Returns None rather than an
        empty string when not found, so the DB stores NULL, not "" ."""
        container = soup.select_one("#company-profile") or soup.select_one(".company-profile")
        if not container:
            return None
        about_p = container.select_one(".sub") or container.select_one("p")
        if not about_p:
            return None
        text = about_p.get_text(strip=True)
        return text if text else None

    def _parse_top_ratios(self, soup: BeautifulSoup) -> dict:
        """The ratio boxes at the top of a Screener company page
        (Market Cap, P/E, ROCE, ROE, etc.) — id/class names as of Screener's
        layout at time of writing; verify against the live page and adjust
        selectors here if Screener changes its markup."""
        ratios = {}
        container = soup.select_one("#top-ratios")
        if not container:
            return ratios
        for li in container.select("li"):
            name_el = li.select_one(".name")
            value_el = li.select_one(".value")
            if name_el and value_el:
                key = name_el.get_text(strip=True)
                value = value_el.get_text(strip=True)
                ratios[key] = value
        return ratios

    def _parse_table(self, soup: BeautifulSoup, section_id: str) -> list[dict]:
        """Generic parser for Screener's period-column tables (profit-loss,
        balance-sheet, cash-flow, quarterly-results, shareholding). Returns
        a list of {row_label, period_1: value, period_2: value, ...} dicts."""
        section = soup.select_one(f"#{section_id}")
        if not section:
            return []
        table = section.select_one("table")
        if not table:
            return []

        headers = [th.get_text(strip=True) for th in table.select("thead th")]
        rows = []
        for tr in table.select("tbody tr"):
            cells = [td.get_text(strip=True) for td in tr.select("td")]
            if not cells:
                continue
            row = {"row_label": cells[0]}
            for i, header in enumerate(headers[1:], start=1):
                if i < len(cells):
                    row[header] = cells[i]
            rows.append(row)
        return rows


if __name__ == "__main__":
    # Manual smoke-test entrypoint — not run automatically. Requires network
    # access to screener.in, which this sandbox does not have; run locally.
    scraper = ScreenerScraper()
    data = scraper.fetch_company_page("/company/HDFCBANK/")
    print(f"Parsed {len(data['annual_results'])} annual result rows")
