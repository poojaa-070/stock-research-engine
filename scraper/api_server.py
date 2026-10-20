"""
Turns the scraper into a small internal HTTP service the Java backend calls
for real-time company discovery, instead of requiring companies to be
pre-registered by hand via add_company.py.

Run with: uvicorn api_server:app --port 8000
(needs `pip install fastapi uvicorn` -- see requirements.txt)

Endpoints:
  GET  /search?q=...     -> searches Screener.in's own search index directly
                             (does NOT touch our Postgres -- these are raw
                             candidates the company may or may not have
                             been imported yet)
  POST /import?query=... -> takes the first Screener search match for
                             `query`, registers it in our `companies` table
                             if it isn't there yet, scrapes it immediately
                             (synchronous -- the caller waits), and returns
                             the resulting company id

This keeps "search the whole internet" and "our local database" as two
distinct concerns: /search is read-only against Screener, /import is the
only place that writes a new company row and triggers a scrape.
"""

import re
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from config import SCREENER_BASE_URL
from rate_limited_client import RateLimitedClient
from screener_scraper import ScreenerScraper, HEADERS
from nse_scraper import NseScraper
from main import get_connection, refresh_company

app = FastAPI(title="Stock Research Scraper Service")

_search_client = RateLimitedClient(SCREENER_BASE_URL, headers=HEADERS)
_nse_client = NseScraper()


class SearchResult(BaseModel):
    name: str
    nse_symbol: str
    screener_url: str


class ImportResult(BaseModel):
    company_id: int
    name: str
    nse_symbol: str
    newly_registered: bool


@app.get("/health")
def health():
    """Liveness probe used by the Java backend's /api/health. Deliberately
    doesn't touch Screener/NSE, so it stays fast and never rate-limits us."""
    return {"status": "ok"}


@app.get("/search", response_model=list[SearchResult])
def search(q: str):
    """Hits Screener's own search-autocomplete endpoint directly -- this is
    the part I could NOT verify from my sandbox (no network access to
    screener.in there). If this 404s or returns something unparseable when
    you actually run it, the endpoint path or response shape below is the
    first thing to check against what your browser's Network tab shows when
    you type into Screener's own search box."""
    try:
        # v2: let requests URL-encode the query (v1 interpolated it raw, so
        # names with '&' or spaces, e.g. "M&M", broke the search).
        resp = _search_client.get("/api/company/search/", params={"q": q})
        raw = resp.json()
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Screener search failed: {e}")

    results = []
    for item in raw:
        url = item.get("url", "")
        match = re.search(r"/company/([A-Z0-9&]+)/", url)
        symbol = match.group(1) if match else None
        if not symbol:
            continue
        results.append(SearchResult(
            name=item.get("name", symbol),
            nse_symbol=symbol,
            screener_url=f"{SCREENER_BASE_URL}/company/{symbol}/",
        ))
    return results


@app.post("/import", response_model=ImportResult)
def import_company(query: str):
    candidates = search(query)
    if not candidates:
        raise HTTPException(status_code=404, detail=f"No Screener match for '{query}'")
    best = candidates[0]

    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM companies WHERE nse_symbol = %s", (best.nse_symbol,))
            row = cur.fetchone()

            newly_registered = row is None
            if row is None:
                # sector/industry aren't in Screener's search results; they're
                # filled in from the company page by the scrape just below
                # (v2). NULL rather than "" until then, so the company can't
                # be mistaken for a peer of every other unclassified one.
                cur.execute(
                    """INSERT INTO companies (name, nse_symbol, sector, industry, screener_url)
                       VALUES (%s, %s, NULL, NULL, %s) RETURNING id""",
                    (best.name, best.nse_symbol, best.screener_url),
                )
                company_id = cur.fetchone()[0]
                conn.commit()
            else:
                company_id = row[0]
    finally:
        conn.close()

    # Synchronous scrape -- the HTTP caller waits for this to finish so the
    # very next report-generation call has real data to work with. This is
    # fine for a single-user tool; a multi-user version should make this
    # async with a "scraping..." status the frontend can poll instead.
    refresh_company(company_id, force=newly_registered)

    return ImportResult(
        company_id=company_id, name=best.name, nse_symbol=best.nse_symbol,
        newly_registered=newly_registered,
    )


class LiveQuote(BaseModel):
    symbol: str
    last_price: float | None
    change: float | None
    percent_change: float | None
    previous_close: float | None
    day_high: float | None
    day_low: float | None
    as_of: str
    market_state: str | None = None


@app.get("/quote/{symbol}", response_model=LiveQuote)
def quote(symbol: str):
    """Live last-traded price straight from NSE -- deliberately bypasses
    Postgres entirely so what's shown is accurate at the moment the person
    is looking, not whatever end-of-day close was cached at the last
    scrape. This is the fix for "price isn't accurate at the right time":
    fetch_price_history() (used for the chart/technicals) only ever
    returns closed sessions; this hits NSE's live quote endpoint instead."""
    try:
        data = _nse_client.fetch_live_quote(symbol.upper())
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"NSE live quote failed for '{symbol}': {e}")
    if data.get("last_price") is None:
        raise HTTPException(status_code=502, detail=f"NSE returned no live price for '{symbol}'")
    return LiveQuote(symbol=symbol.upper(), **data)


class LivePeer(BaseModel):
    name: str
    nse_symbol: str | None
    cmp: float | None
    pe_ratio: float | None
    roce: float | None
    dividend_yield: float | None
    market_cap: float | None


def _peer_num(row: dict, *label_fragments: str):
    for key, value in row.items():
        if key == "name" or key == "nse_symbol":
            continue
        if all(frag in key.lower() for frag in label_fragments):
            cleaned = value.replace(",", "").replace("%", "").strip()
            try:
                return float(cleaned)
            except ValueError:
                return None
    return None


@app.get("/peers/{symbol}", response_model=list[LivePeer])
def peers(symbol: str):
    """Live sector-peer comparison, scraped straight from Screener's own
    'Peer comparison' table for this company at request time -- this is
    the live alternative to only comparing against whichever companies
    happen to already be rows in our own `companies` table. Independent of
    Postgres: works even for a company we've never imported."""
    scraper = ScreenerScraper()
    try:
        data = scraper.fetch_company_page(f"/company/{symbol.upper()}/")
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Screener peer lookup failed for '{symbol}': {e}")

    result = []
    for row in data.get("peers", []):
        result.append(LivePeer(
            name=row.get("name", ""),
            nse_symbol=row.get("nse_symbol"),
            cmp=_peer_num(row, "cmp") or _peer_num(row, "price"),
            pe_ratio=_peer_num(row, "p/e"),
            roce=_peer_num(row, "roce"),
            dividend_yield=_peer_num(row, "div"),
            market_cap=_peer_num(row, "mar", "cap") or _peer_num(row, "market", "cap"),
        ))
    return result


class RefreshResult(BaseModel):
    company_id: int
    refreshed: bool


@app.post("/refresh/{company_id}", response_model=RefreshResult)
def refresh(company_id: int):
    """Called before EVERY report generation (not just first import) so the
    report reflects a fresh scrape rather than whatever's already sitting in
    Postgres, however old. force=True bypasses the normal staleness cache
    entirely -- this is the deliberate trade-off for a single-user tool
    that wants freshness over being polite to Screener/NSE's rate limits;
    see the note in config.py's STALE_AFTER_HOURS if you want to dial that
    back for a multi-user deployment instead."""
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM companies WHERE id = %s", (company_id,))
            if cur.fetchone() is None:
                raise HTTPException(status_code=404, detail=f"No company with id {company_id}")
    finally:
        conn.close()

    refresh_company(company_id, force=True)
    return RefreshResult(company_id=company_id, refreshed=True)
