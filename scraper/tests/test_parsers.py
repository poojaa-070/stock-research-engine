"""Parser tests against saved HTML — no network, no database."""
from datetime import date

import pytest
from bs4 import BeautifulSoup

import main
from screener_scraper import ScreenerScraper
from conftest import fixture_html


@pytest.fixture(scope="module")
def parsed():
    scraper = ScreenerScraper.__new__(ScreenerScraper)  # no HTTP client needed
    soup = BeautifulSoup(fixture_html("screener_company.html"), "html.parser")
    return {
        "ratios": scraper._parse_top_ratios(soup),
        "annual_results": scraper._parse_table(soup, "profit-loss"),
        "quarterly_results": scraper._parse_table(soup, "quarterly-results"),
        "balance_sheet": scraper._parse_table(soup, "balance-sheet"),
        "cash_flow": scraper._parse_table(soup, "cash-flow"),
        "shareholding": scraper._parse_table(soup, "shareholding"),
        "business_description": scraper._parse_about_section(soup),
        "peers": scraper._parse_peer_comparison(soup),
        "classification": scraper._parse_classification(soup),
    }


def test_top_ratios(parsed):
    assert parsed["ratios"]["Stock P/E"] == "25.0"
    assert parsed["ratios"]["ROCE"] == "18.5 %"


def test_period_tables(parsed):
    sales = main._find_row(parsed["annual_results"], "sales")
    assert sales["Mar 2026"] == "4,400"
    assert sales["TTM"] == "4,550"  # kept by the parser, skipped at upsert time


def test_about_section(parsed):
    assert parsed["business_description"].startswith("Acme Industries makes widgets")


def test_classification_prefers_titled_links(parsed):
    # v1 left sector blank for every imported company
    assert parsed["classification"] == {"sector": "Capital Goods", "industry": "Industrial Products"}


def test_classification_untitled_links_fall_back_to_order():
    scraper = ScreenerScraper.__new__(ScreenerScraper)
    soup = BeautifulSoup('<a href="/market/A/">Energy</a><a href="/market/A/B/">Refineries</a>', "html.parser")
    assert scraper._parse_classification(soup) == {"sector": "Energy", "industry": "Refineries"}


def test_classification_absent():
    scraper = ScreenerScraper.__new__(ScreenerScraper)
    assert scraper._parse_classification(BeautifulSoup("<p>nothing</p>", "html.parser")) == {}


def test_peer_table(parsed):
    peers = parsed["peers"]
    assert [p["nse_symbol"] for p in peers] == ["ACME", "WIDGETCO"]
    assert peers[1]["CMP Rs."] == "1,180.50"


def test_debt_to_equity(parsed):
    de = main.compute_debt_to_equity(parsed["balance_sheet"])
    assert de[date(2026, 3, 31)] == pytest.approx(440 / 2200, rel=1e-4)
    assert de[date(2024, 3, 31)] == pytest.approx(900 / 1800, rel=1e-4)


def test_debt_to_equity_skips_missing_equity():
    rows = [{"row_label": "Borrowings +", "Mar 2026": "100"}]
    assert main.compute_debt_to_equity(rows) == {}


@pytest.mark.parametrize("raw,expected", [
    ("1,234.5", 1234.5), ("12 %", 12.0), ("(15)", -15.0), ("-", None), ("", None),
    (None, None), ("NA", None), (7, 7.0),
    ("₹ 250", 250.0), ("₹ 12,345 Cr.", 12345.0), ("Rs. 1,180.50", 1180.5),  # v1 returned None for these
])
def test_parse_number(raw, expected):
    assert main._parse_number(raw) == expected


@pytest.mark.parametrize("label,expected", [
    ("Mar 2026", date(2026, 3, 31)), ("Jun 2025", date(2025, 6, 30)),
    ("Feb 2024", date(2024, 2, 29)), ("TTM", None), ("Best Estimates", None),
])
def test_parse_period(label, expected):
    assert main._parse_period_to_date(label) == expected


def test_find_row_requires_all_fragments():
    rows = [{"row_label": "Net Profit +"}, {"row_label": "Operating Profit"}]
    assert main._find_row(rows, "operating", "profit")["row_label"] == "Operating Profit"
    assert main._find_row(rows, "gross", "profit") is None
