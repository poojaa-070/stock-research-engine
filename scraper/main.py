"""
Entry point for scrape-on-demand. Typical call: the Java API, on receiving
a report-generation request for a company, first checks scrape_metadata
staleness (via a small internal endpoint this script also exposes patterns
for) and calls `refresh_company(company_id)` here if stale.

This script purposefully does the DB writes directly rather than only
printing JSON, per the brief's "scraper can write directly, or expose a
small internal endpoint" — writing directly is simpler for a v1 and keeps
one fewer network hop; swap for a FastAPI/Flask internal endpoint later if
the Java side should stay in control of write timing.
"""

import json
import re
import calendar
from datetime import date, datetime, timedelta

import psycopg2
import psycopg2.extras

from config import DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, STALE_AFTER_HOURS
from screener_scraper import ScreenerScraper
from nse_scraper import NseScraper


def get_connection():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )


def is_stale(conn, company_id: int, source: str) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT last_scraped_at FROM scrape_metadata WHERE company_id=%s AND source=%s "
            "ORDER BY last_scraped_at DESC LIMIT 1",
            (company_id, source),
        )
        row = cur.fetchone()
        if not row or row[0] is None:
            return True
        return datetime.utcnow() - row[0] > timedelta(hours=STALE_AFTER_HOURS)


def record_scrape_metadata(conn, company_id: int, source: str, status: str, raw_payload: dict):
    with conn.cursor() as cur:
        cur.execute(
            """INSERT INTO scrape_metadata (company_id, source, last_scraped_at, status, raw_payload)
               VALUES (%s, %s, %s, %s, %s)""",
            (company_id, source, datetime.utcnow(), status, json.dumps(raw_payload)),
        )
    conn.commit()


def refresh_company(company_id: int, force: bool = False):
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT * FROM companies WHERE id=%s", (company_id,))
            company = cur.fetchone()
        if not company:
            raise ValueError(f"No company with id {company_id}")

        if force or is_stale(conn, company_id, "screener"):
            _refresh_from_screener(conn, company)
        else:
            print(f"[{company['nse_symbol']}] screener data still fresh, skipping")

        if force or is_stale(conn, company_id, "nse"):
            _refresh_from_nse(conn, company)
        else:
            print(f"[{company['nse_symbol']}] NSE data still fresh, skipping")
    finally:
        conn.close()


def _refresh_from_screener(conn, company: dict):
    scraper = ScreenerScraper()
    path = company["screener_url"].replace("https://www.screener.in", "")
    try:
        data = scraper.fetch_company_page(path)
        _upsert_parsed_screener_data(conn, company["id"], data)
        record_scrape_metadata(conn, company["id"], "screener", "success",
                                {"note": "see raw_html in this row for full page snapshot",
                                 "raw_html": data["raw_html"][:20000]})  # truncate defensively
        print(f"[{company['nse_symbol']}] screener refresh OK")
    except Exception as e:
        record_scrape_metadata(conn, company["id"], "screener", "failed", {"error": str(e)})
        print(f"[{company['nse_symbol']}] screener refresh FAILED: {e}")


def _refresh_from_nse(conn, company: dict):
    scraper = NseScraper()
    try:
        to_date = datetime.now().strftime("%d-%m-%Y")
        from_date = (datetime.now() - timedelta(days=400)).strftime("%d-%m-%Y")
        prices = scraper.fetch_price_history(company["nse_symbol"], from_date, to_date)
        _upsert_prices(conn, company["id"], prices)
        announcements = scraper.fetch_corporate_announcements(company["nse_symbol"])
        _upsert_news(conn, company["id"], announcements)
        record_scrape_metadata(conn, company["id"], "nse", "success",
                                {"price_rows": len(prices), "announcement_rows": len(announcements)})
        print(f"[{company['nse_symbol']}] NSE refresh OK ({len(prices)} price rows)")
    except Exception as e:
        record_scrape_metadata(conn, company["id"], "nse", "failed", {"error": str(e)})
        print(f"[{company['nse_symbol']}] NSE refresh FAILED: {e}")


_MONTHS = {m.lower(): i for i, m in enumerate(calendar.month_abbr) if m}


def _clean_label(label: str) -> str:
    """Screener row labels often carry a trailing '+' (expandable group rows)
    and sometimes footnote markers -- normalize before matching."""
    return re.sub(r"[+\*]", "", label).strip().lower()


def _parse_number(raw):
    """Screener numbers use commas as thousands separators and sometimes a
    trailing '%'; negative values may appear either as '-123' or '(123)'.

    v2: also strips currency/unit decorations. Screener's top-ratio boxes
    render values like "₹ 250" and "₹ 12,345 Cr."; v1 returned None for
    those, so Current Price / Book Value never parsed and P/B was never
    computed for any scraped company.
    """
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        return float(raw)
    s = str(raw)
    s = re.sub(r"(?i)₹|rs\.?|cr\.?|%|,", "", s).strip()
    if s in ("", "-", "NA", "N/A", "na", "n/a"):
        return None
    if s.startswith("(") and s.endswith(")"):
        s = "-" + s[1:-1].strip()
    try:
        return float(s)
    except ValueError:
        return None


def _parse_period_to_date(period_label: str):
    """'Mar 2024' -> date(2024, 3, 31). Returns None if the label doesn't
    match Screener's usual 'Mon YYYY' column header format (e.g. a 'TTM'
    or 'Best Estimates' column some pages include -- those are skipped
    deliberately rather than guessed at)."""
    match = re.match(r"([A-Za-z]{3})[a-z]*\s+(\d{4})", period_label.strip())
    if not match:
        return None
    month = _MONTHS.get(match.group(1).lower())
    if not month:
        return None
    year = int(match.group(2))
    last_day = calendar.monthrange(year, month)[1]
    return date(year, month, last_day)


def _find_row(rows: list[dict], *label_fragments: str):
    """Returns the first row whose cleaned label contains ALL given
    fragments (case-insensitive) -- e.g. _find_row(rows, 'net', 'profit')
    matches 'Net Profit +' but not 'Net Profit Margin' style variants that
    might appear on some company pages."""
    for row in rows:
        cleaned = _clean_label(row.get("row_label", ""))
        if all(frag in cleaned for frag in label_fragments):
            return row
    return None


def _upsert_financials_from_table(conn, company_id: int, rows: list[dict], period_type: str,
                                   revenue_frags, profit_frags, netprofit_frags, eps_frags,
                                   debt_frags=None, ocf_frags=None, interest_frags=None,
                                   current_assets_frags=None, current_liabilities_frags=None):
    """Shared logic for turning a Screener table (profit-loss OR combined
    with balance-sheet/cash-flow row lookups passed in) into `financials`
    rows, one per period column. Pass frags=None to skip a metric this table
    doesn't cover (e.g. balance-sheet has no EPS row)."""
    revenue_row = _find_row(rows, *revenue_frags) if revenue_frags else None
    profit_row = _find_row(rows, *profit_frags) if profit_frags else None
    netprofit_row = _find_row(rows, *netprofit_frags) if netprofit_frags else None
    eps_row = _find_row(rows, *eps_frags) if eps_frags else None
    debt_row = _find_row(rows, *debt_frags) if debt_frags else None
    ocf_row = _find_row(rows, *ocf_frags) if ocf_frags else None
    interest_row = _find_row(rows, *interest_frags) if interest_frags else None
    current_assets_row = _find_row(rows, *current_assets_frags) if current_assets_frags else None
    current_liabilities_row = _find_row(rows, *current_liabilities_frags) if current_liabilities_frags else None

    any_row = (revenue_row or profit_row or netprofit_row or eps_row or debt_row or ocf_row
               or interest_row or current_assets_row or current_liabilities_row)
    if not any_row:
        return 0

    period_columns = [k for k in any_row.keys() if k != "row_label"]
    written = 0
    with conn.cursor() as cur:
        for period_label in period_columns:
            period_end = _parse_period_to_date(period_label)
            if not period_end:
                continue  # skip 'TTM' / estimate columns Screener sometimes adds

            revenue = _parse_number(revenue_row[period_label]) if revenue_row else None
            operating_profit = _parse_number(profit_row[period_label]) if profit_row else None
            net_profit = _parse_number(netprofit_row[period_label]) if netprofit_row else None
            eps = _parse_number(eps_row[period_label]) if eps_row else None
            total_debt = _parse_number(debt_row[period_label]) if debt_row else None
            operating_cash_flow = _parse_number(ocf_row[period_label]) if ocf_row else None
            interest_expense = _parse_number(interest_row[period_label]) if interest_row else None
            current_assets = _parse_number(current_assets_row[period_label]) if current_assets_row else None
            current_liabilities = _parse_number(current_liabilities_row[period_label]) if current_liabilities_row else None

            if all(v is None for v in (revenue, operating_profit, net_profit, eps, total_debt,
                                        operating_cash_flow, interest_expense, current_assets, current_liabilities)):
                continue

            cur.execute(
                """INSERT INTO financials (company_id, period_type, period_end_date, revenue,
                       net_profit, operating_profit, eps, total_debt, operating_cash_flow,
                       interest_expense, current_assets, current_liabilities)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                   ON CONFLICT (company_id, period_type, period_end_date) DO UPDATE SET
                     revenue = COALESCE(EXCLUDED.revenue, financials.revenue),
                     net_profit = COALESCE(EXCLUDED.net_profit, financials.net_profit),
                     operating_profit = COALESCE(EXCLUDED.operating_profit, financials.operating_profit),
                     eps = COALESCE(EXCLUDED.eps, financials.eps),
                     total_debt = COALESCE(EXCLUDED.total_debt, financials.total_debt),
                     operating_cash_flow = COALESCE(EXCLUDED.operating_cash_flow, financials.operating_cash_flow),
                     interest_expense = COALESCE(EXCLUDED.interest_expense, financials.interest_expense),
                     current_assets = COALESCE(EXCLUDED.current_assets, financials.current_assets),
                     current_liabilities = COALESCE(EXCLUDED.current_liabilities, financials.current_liabilities)""",
                (company_id, period_type, period_end, revenue, net_profit, operating_profit,
                 eps, total_debt, operating_cash_flow, interest_expense, current_assets, current_liabilities),
            )
            written += 1
    conn.commit()
    return written


def compute_debt_to_equity(balance_rows: list[dict]) -> dict:
    """v2: debt-to-equity per balance-sheet period, as
    Borrowings / (Equity Capital + Reserves).

    v1 never wrote debt_to_equity at all, so for every scraped company D/E
    was missing from Financial Health and Risk (only the demo seed had it).
    Returns {period_end_date: ratio}; periods with missing or non-positive
    equity are skipped rather than guessed.
    """
    borrowings = _find_row(balance_rows, "borrowings")
    equity_cap = _find_row(balance_rows, "equity", "capital")
    reserves = _find_row(balance_rows, "reserves")
    if not borrowings or not (equity_cap or reserves):
        return {}
    result = {}
    for period_label in (k for k in borrowings if k != "row_label"):
        period_end = _parse_period_to_date(period_label)
        if not period_end:
            continue
        debt = _parse_number(borrowings.get(period_label))
        cap = _parse_number(equity_cap.get(period_label)) if equity_cap else None
        res = _parse_number(reserves.get(period_label)) if reserves else None
        equity = (cap or 0) + (res or 0)
        if debt is None or (cap is None and res is None) or equity <= 0:
            continue
        result[period_end] = round(debt / equity, 4)
    return result


def _upsert_ratios_snapshot(conn, company_id: int, top_ratios: dict, latest_period: date,
                            debt_to_equity: float | None = None):
    """Screener's top-ratios box is a live snapshot, not a per-period series
    -- so this writes ONE ratios row keyed at `latest_period` (the most
    recent period_end_date we found in the financials tables). Re-scraping
    within the same period just updates that same row via ON CONFLICT.

    NOTE: ev_ebitda and peg_ratio are NOT in Screener's standard top-ratios
    box for most companies and are left null here -- computing them
    properly needs EBITDA + enterprise value or a separate peer-comparison
    page, which is a documented gap rather than a guess. ROE is also
    inconsistently present depending on company/sector; left null if absent
    rather than guessed."""
    def get(*label_fragments):
        for key, value in top_ratios.items():
            if all(frag in key.lower() for frag in label_fragments):
                return _parse_number(value)
        return None

    pe_ratio = get("p/e") or get("stock", "p/e")
    roce = get("roce")
    roe = get("roe")
    dividend_yield = get("dividend", "yield")
    current_price = get("current", "price")
    book_value = get("book", "value")
    pb_ratio = (current_price / book_value) if (current_price and book_value) else None

    if all(v is None for v in (pe_ratio, roce, roe, dividend_yield, pb_ratio, debt_to_equity)):
        return

    with conn.cursor() as cur:
        cur.execute(
            """INSERT INTO ratios (company_id, period_end_date, roe, roce, debt_to_equity, pe_ratio, pb_ratio, dividend_yield)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
               ON CONFLICT (company_id, period_end_date) DO UPDATE SET
                 roe = COALESCE(EXCLUDED.roe, ratios.roe),
                 roce = COALESCE(EXCLUDED.roce, ratios.roce),
                 debt_to_equity = COALESCE(EXCLUDED.debt_to_equity, ratios.debt_to_equity),
                 pe_ratio = COALESCE(EXCLUDED.pe_ratio, ratios.pe_ratio),
                 pb_ratio = COALESCE(EXCLUDED.pb_ratio, ratios.pb_ratio),
                 dividend_yield = COALESCE(EXCLUDED.dividend_yield, ratios.dividend_yield)""",
            (company_id, latest_period, roe, roce, debt_to_equity, pe_ratio, pb_ratio, dividend_yield),
        )
    conn.commit()


def _upsert_shareholding_from_table(conn, company_id: int, rows: list[dict]):
    promoter_row = _find_row(rows, "promoter")
    fii_row = _find_row(rows, "fii")
    dii_row = _find_row(rows, "dii")
    public_row = _find_row(rows, "public")

    any_row = promoter_row or fii_row or dii_row or public_row
    if not any_row:
        return

    period_columns = [k for k in any_row.keys() if k != "row_label"]
    with conn.cursor() as cur:
        for period_label in period_columns:
            period_end = _parse_period_to_date(period_label)
            if not period_end:
                continue
            promoter_pct = _parse_number(promoter_row[period_label]) if promoter_row else None
            fii_pct = _parse_number(fii_row[period_label]) if fii_row else None
            dii_pct = _parse_number(dii_row[period_label]) if dii_row else None
            public_pct = _parse_number(public_row[period_label]) if public_row else None
            if all(v is None for v in (promoter_pct, fii_pct, dii_pct, public_pct)):
                continue
            cur.execute(
                """INSERT INTO shareholding_pattern (company_id, period_end_date, promoter_pct, fii_pct, dii_pct, public_pct)
                   VALUES (%s, %s, %s, %s, %s, %s)
                   ON CONFLICT (company_id, period_end_date) DO UPDATE SET
                     promoter_pct = COALESCE(EXCLUDED.promoter_pct, shareholding_pattern.promoter_pct),
                     fii_pct = COALESCE(EXCLUDED.fii_pct, shareholding_pattern.fii_pct),
                     dii_pct = COALESCE(EXCLUDED.dii_pct, shareholding_pattern.dii_pct),
                     public_pct = COALESCE(EXCLUDED.public_pct, shareholding_pattern.public_pct)""",
                (company_id, period_end, promoter_pct, fii_pct, dii_pct, public_pct),
            )
    conn.commit()


def _upsert_parsed_screener_data(conn, company_id: int, data: dict):
    """Maps Screener's row_label strings onto our financials/ratios/
    shareholding_pattern columns. Based on Screener's stable, well-known
    page structure (profit-loss / balance-sheet / cash-flow / shareholding
    sections with period-column tables, plus a top-ratios snapshot box) --
    but I could not verify this against a live page from my environment
    (no network access to screener.in here), so treat this as a strong
    first draft: run it once, check the row counts it prints, and if a
    company's page uses slightly different label wording than expected,
    adjust the _find_row(...) fragment calls below rather than the parsing
    logic itself."""
    annual_written = _upsert_financials_from_table(
        conn, company_id, data["annual_results"], "annual",
        revenue_frags=("sales",), profit_frags=("operating", "profit"),
        netprofit_frags=("net", "profit"), eps_frags=("eps",),
        interest_frags=("interest",),  # profit-loss table usually has an "Interest" line item
    )
    quarterly_written = _upsert_financials_from_table(
        conn, company_id, data["quarterly_results"], "quarterly",
        revenue_frags=("sales",), profit_frags=("operating", "profit"),
        netprofit_frags=("net", "profit"), eps_frags=("eps",),
        interest_frags=("interest",),
    )
    # Debt, current assets/liabilities live in the balance sheet; operating
    # cash flow lives in the cash-flow statement -- merge them into the same
    # annual financials rows rather than duplicating logic. NOTE: Screener's
    # balance sheet doesn't always split out "current" assets/liabilities
    # explicitly (many templates show only total assets/liabilities) --
    # these frags are a best guess and may come back empty for companies
    # whose balance sheet layout doesn't break this out; that's fine, the
    # current_ratio metric just stays absent for those rather than guessed.
    _upsert_financials_from_table(
        conn, company_id, data["balance_sheet"], "annual",
        revenue_frags=None, profit_frags=None, netprofit_frags=None, eps_frags=None,
        debt_frags=("borrowings",),
        current_assets_frags=("other", "assets"),        # best-guess fragment, verify against live page
        current_liabilities_frags=("other", "liabilities"),  # best-guess fragment, verify against live page
    )
    _upsert_financials_from_table(
        conn, company_id, data["cash_flow"], "annual",
        revenue_frags=None, profit_frags=None, netprofit_frags=None, eps_frags=None,
        ocf_frags=("cash", "operating", "activity"),
    )
    # free_cash_flow is intentionally left null here -- Screener's standard
    # cash-flow statement doesn't break out capex cleanly enough to derive
    # it reliably across arbitrary companies. Revisit if a company-specific
    # capex row is needed later.

    all_annual_periods = sorted(
        {_parse_period_to_date(k) for row in data["annual_results"] for k in row if k != "row_label"} - {None}
    )
    if all_annual_periods and data.get("ratios"):
        debt_to_equity = compute_debt_to_equity(data.get("balance_sheet", []))
        _upsert_ratios_snapshot(conn, company_id, data["ratios"], all_annual_periods[-1],
                                debt_to_equity.get(all_annual_periods[-1]) if debt_to_equity else None)

    classification = data.get("classification") or {}
    if classification:
        # Only fill blanks: a sector set by hand (add_company.py) is kept,
        # since peer matching depends on consistent spelling.
        with conn.cursor() as cur:
            cur.execute(
                """UPDATE companies
                      SET sector = COALESCE(NULLIF(sector, ''), %s),
                          industry = COALESCE(NULLIF(industry, ''), %s)
                    WHERE id = %s""",
                (classification.get("sector"), classification.get("industry"), company_id),
            )
        conn.commit()

    _upsert_shareholding_from_table(conn, company_id, data["shareholding"])

    if data.get("business_description"):
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE companies SET business_description = %s WHERE id = %s",
                (data["business_description"], company_id),
            )
        conn.commit()

    print(f"  parsed {annual_written} annual period(s), {quarterly_written} quarterly period(s)")


def _upsert_prices(conn, company_id: int, rows: list[dict]):
    if not rows:
        return
    with conn.cursor() as cur:
        for row in rows:
            if not row.get("trade_date"):
                continue
            cur.execute(
                """INSERT INTO prices (company_id, trade_date, open, high, low, close, volume)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)
                   ON CONFLICT (company_id, trade_date) DO UPDATE SET
                     open=EXCLUDED.open, high=EXCLUDED.high, low=EXCLUDED.low,
                     close=EXCLUDED.close, volume=EXCLUDED.volume""",
                (company_id, row["trade_date"], row.get("open"), row.get("high"),
                 row.get("low"), row.get("close"), row.get("volume")),
            )
    conn.commit()


def _upsert_news(conn, company_id: int, items: list[dict]):
    if not items:
        return
    with conn.cursor() as cur:
        for item in items:
            if not item.get("headline"):
                continue
            # v2: ON CONFLICT DO NOTHING against ux_news_company_headline_published.
            # v1 re-inserted every announcement on every refresh.
            cur.execute(
                """INSERT INTO news_items (company_id, headline, source, published_at, sentiment)
                   VALUES (%s, %s, %s, %s, %s)
                   ON CONFLICT DO NOTHING""",
                (company_id, item["headline"], item.get("source"),
                 item.get("published_at"), None),  # sentiment left null; AI layer tags it later if needed
            )
    conn.commit()


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Usage: python main.py <company_id> [--force]")
        sys.exit(1)
    refresh_company(int(sys.argv[1]), force="--force" in sys.argv)
