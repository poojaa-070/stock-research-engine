"""
NSE's site requires an initial GET to the homepage to pick up session
cookies before its data endpoints will respond (it 401s otherwise). This
module handles that handshake, then hits the price-history and
corporate-announcements endpoints for a given symbol.

NSE occasionally serves JS-rendered content or bot-checks that requests+bs4
can't get past — that's the case where the Playwright fallback in
`fetch_with_playwright_fallback` kicks in. Try the cheap path first.
"""

from datetime import datetime
from config import NSE_BASE_URL
from rate_limited_client import RateLimitedClient

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    "Accept": "application/json",
}


class NseScraper:
    def __init__(self):
        self.client = RateLimitedClient(NSE_BASE_URL, headers=HEADERS)
        self._session_primed = False

    def _prime_session(self):
        # NSE requires a homepage hit first to set cookies the API endpoints check.
        if not self._session_primed:
            self.client.get("/")
            self._session_primed = True

    def fetch_price_history(self, symbol: str, from_date: str, to_date: str) -> list[dict]:
        """from_date/to_date format: DD-MM-YYYY. Returns OHLCV rows ready for
        the `prices` table."""
        self._prime_session()
        path = (f"/api/historical/cm/equity?symbol={symbol}"
                f"&series=[%22EQ%22]&from={from_date}&to={to_date}")
        resp = self.client.get(path)
        raw = resp.json()
        rows = []
        for item in raw.get("data", []):
            rows.append({
                "trade_date": item.get("CH_TIMESTAMP"),
                "open": item.get("CH_OPENING_PRICE"),
                "high": item.get("CH_TRADE_HIGH_PRICE"),
                "low": item.get("CH_TRADE_LOW_PRICE"),
                "close": item.get("CH_CLOSING_PRICE"),
                "volume": item.get("CH_TOT_TRADED_QTY"),
            })
        return rows

    def fetch_live_quote(self, symbol: str) -> dict:
        """Real-time last-traded-price snapshot from NSE's live quote
        endpoint -- as opposed to fetch_price_history, which only ever
        returns yesterday's (or earlier) end-of-day close. This is what
        should back "current price" in the UI so what's displayed is
        accurate at the moment someone is actually looking at it, not
        whatever was cached at the last scrape.

        NSE's exact field names here have shifted before between
        'lastPrice'/'change'/'pChange' — verify against a live response if
        this comes back with everything null and adjust the key lookups
        below rather than the calling code."""
        self._prime_session()
        resp = self.client.get(f"/api/quote-equity?symbol={symbol}")
        raw = resp.json()
        price_info = raw.get("priceInfo", {}) or {}
        last_price = price_info.get("lastPrice")
        prev_close = price_info.get("previousClose") or price_info.get("close")
        change = price_info.get("change")
        p_change = price_info.get("pChange")
        if change is None and last_price is not None and prev_close is not None:
            change = last_price - prev_close
        if p_change is None and change is not None and prev_close:
            p_change = (change / prev_close) * 100
        intraday = price_info.get("intraDayHighLow") or {}

        market_state = None
        market_status = raw.get("marketStatus")
        if isinstance(market_status, dict):
            market_state = market_status.get("marketStatus")
        elif isinstance(market_status, list) and market_status:
            market_state = market_status[0].get("marketStatus")

        return {
            "last_price": last_price,
            "change": change,
            "percent_change": p_change,
            "previous_close": prev_close,
            "day_high": intraday.get("max"),
            "day_low": intraday.get("min"),
            "as_of": raw.get("lastUpdateTime") or datetime.now().isoformat(),
            "market_state": market_state,
        }

    def fetch_corporate_announcements(self, symbol: str) -> list[dict]:
        self._prime_session()
        path = f"/api/corporate-announcements?index=equities&symbol={symbol}"
        resp = self.client.get(path)
        raw = resp.json()
        items = []
        for item in raw if isinstance(raw, list) else raw.get("data", []):
            items.append({
                "headline": item.get("desc") or item.get("subject"),
                "published_at": item.get("an_dt") or item.get("date"),
                "source": "NSE Corporate Announcements",
            })
        return items

    def fetch_with_playwright_fallback(self, url: str) -> str:
        """Use only when the plain requests path is blocked by NSE's bot
        protection or the content is JS-rendered. Kept as a separate method
        so the common path stays cheap (no headless browser startup cost)."""
        from playwright.sync_api import sync_playwright

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(user_agent=HEADERS["User-Agent"])
            page.goto(NSE_BASE_URL)  # prime cookies in-browser too
            page.wait_for_timeout(1500)
            page.goto(url)
            content = page.content()
            browser.close()
            return content


if __name__ == "__main__":
    # Manual smoke test — requires network access to nseindia.com, not
    # available in this sandbox; run locally.
    scraper = NseScraper()
    today = datetime.now().strftime("%d-%m-%Y")
    print("NSE scraper module loaded OK; run fetch_price_history(...) locally to test.")
