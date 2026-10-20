"""
Registers a new company row so main.py's refresh_company() has something
to scrape into. Screener URLs follow a predictable pattern:
https://www.screener.in/company/<NSE_SYMBOL>/

Usage:
    python add_company.py RELIANCE "Reliance Industries Ltd" Energy "Oil & Gas"
    python add_company.py INFY "Infosys Ltd" IT "IT Services"

Sector/industry are free text used for peer-comparison grouping in the
scoring engine (ScoringEngine looks up sector peers by exact string match on
`sector`) -- keep spelling consistent across companies you add, e.g. always
"Financial Services" not sometimes "Finance" / "BFSI" / "Financials".
"""

import sys
import psycopg2
from config import DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD


def add_company(nse_symbol: str, name: str, sector: str, industry: str, bse_code: str | None = None):
    screener_url = f"https://www.screener.in/company/{nse_symbol.upper()}/"
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO companies (name, nse_symbol, bse_code, sector, industry, screener_url)
                   VALUES (%s, %s, %s, %s, %s, %s)
                   ON CONFLICT DO NOTHING
                   RETURNING id""",
                (name, nse_symbol.upper(), bse_code, sector, industry, screener_url),
            )
            row = cur.fetchone()
        conn.commit()
        if row:
            print(f"Added {name} ({nse_symbol}) with id={row[0]}")
            print(f"Next: python main.py {row[0]}")
        else:
            print(f"{nse_symbol} already exists (or insert was skipped) -- check the companies table.")
    finally:
        conn.close()


if __name__ == "__main__":
    if len(sys.argv) < 5:
        print('Usage: python add_company.py SYMBOL "Full Company Name" Sector Industry [bse_code]')
        sys.exit(1)
    symbol, name, sector, industry = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    bse = sys.argv[5] if len(sys.argv) > 5 else None
    add_company(symbol, name, sector, industry, bse)
