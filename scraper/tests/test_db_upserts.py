"""Upsert tests against a real Postgres. Skipped unless TEST_DB_NAME is set:

    createdb stock_research_test
    psql stock_research_test -f ../database/schema.sql
    TEST_DB_NAME=stock_research_test pytest
"""
import os
from datetime import date

import psycopg2
import pytest
from bs4 import BeautifulSoup

import main
from config import DB_HOST, DB_PORT, DB_USER, DB_PASSWORD
from screener_scraper import ScreenerScraper
from conftest import fixture_html

TEST_DB = os.getenv("TEST_DB_NAME")
pytestmark = pytest.mark.skipif(not TEST_DB, reason="set TEST_DB_NAME to run database tests")


@pytest.fixture
def conn():
    c = psycopg2.connect(host=DB_HOST, port=DB_PORT, dbname=TEST_DB, user=DB_USER, password=DB_PASSWORD)
    with c.cursor() as cur:
        cur.execute("TRUNCATE companies, financials, ratios, shareholding_pattern, news_items, prices RESTART IDENTITY CASCADE")
        cur.execute("INSERT INTO companies (name, nse_symbol, screener_url) VALUES ('Acme', 'ACME', 'x') RETURNING id")
    c.commit()
    yield c
    c.close()


def _parsed():
    s = ScreenerScraper.__new__(ScreenerScraper)
    soup = BeautifulSoup(fixture_html("screener_company.html"), "html.parser")
    return {
        "ratios": s._parse_top_ratios(soup),
        "annual_results": s._parse_table(soup, "profit-loss"),
        "quarterly_results": s._parse_table(soup, "quarterly-results"),
        "balance_sheet": s._parse_table(soup, "balance-sheet"),
        "cash_flow": s._parse_table(soup, "cash-flow"),
        "shareholding": s._parse_table(soup, "shareholding"),
        "business_description": s._parse_about_section(soup),
        "classification": s._parse_classification(soup),
    }


def _one(conn, sql):
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchone()


def test_full_upsert(conn):
    main._upsert_parsed_screener_data(conn, 1, _parsed())

    assert _one(conn, "SELECT count(*) FROM financials WHERE period_type='annual'")[0] == 3  # TTM skipped
    assert _one(conn, "SELECT count(*) FROM financials WHERE period_type='quarterly'")[0] == 2
    rev, debt, ocf, interest = _one(conn, "SELECT revenue, total_debt, operating_cash_flow, interest_expense "
                                          "FROM financials WHERE period_type='annual' AND period_end_date='2026-03-31'")
    assert (float(rev), float(debt), float(ocf), float(interest)) == (4400, 440, -15, 30)

    pe, de, pb = _one(conn, "SELECT pe_ratio, debt_to_equity, pb_ratio FROM ratios")
    assert float(pe) == 25.0
    assert float(de) == pytest.approx(0.2)   # v1 never wrote this
    assert float(pb) == pytest.approx(2.5)   # 250 / 100

    sector, industry, desc = _one(conn, "SELECT sector, industry, business_description FROM companies WHERE id=1")
    assert (sector, industry) == ("Capital Goods", "Industrial Products")
    assert desc.startswith("Acme")

    assert float(_one(conn, "SELECT promoter_pct FROM shareholding_pattern WHERE period_end_date='2026-06-30'")[0]) == pytest.approx(54.8)


def test_manual_sector_is_not_overwritten(conn):
    with conn.cursor() as cur:
        cur.execute("UPDATE companies SET sector='Engineering' WHERE id=1")
    conn.commit()
    main._upsert_parsed_screener_data(conn, 1, _parsed())
    assert _one(conn, "SELECT sector, industry FROM companies WHERE id=1") == ("Engineering", "Industrial Products")


def test_rescrape_is_idempotent(conn):
    main._upsert_parsed_screener_data(conn, 1, _parsed())
    main._upsert_parsed_screener_data(conn, 1, _parsed())
    assert _one(conn, "SELECT count(*) FROM financials")[0] == 5
    assert _one(conn, "SELECT count(*) FROM ratios")[0] == 1


def test_news_is_not_duplicated(conn):
    items = [{"headline": "Board meeting outcome", "published_at": "2026-08-01 10:00:00", "source": "NSE"},
             {"headline": "Undated filing", "published_at": None, "source": "NSE"}]
    main._upsert_news(conn, 1, items)
    main._upsert_news(conn, 1, items)  # v1 inserted these twice
    assert _one(conn, "SELECT count(*) FROM news_items")[0] == 2
