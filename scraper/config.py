import os
from dotenv import load_dotenv

load_dotenv()

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "stock_research")
DB_USER = os.getenv("DB_USER", "stock_research")
DB_PASSWORD = os.getenv("DB_PASSWORD", "changeme")

# Deliberate: scrape on-demand, not on a fixed schedule. A company's data is
# considered "fresh enough" if scraped within this window; otherwise the API
# layer should trigger a re-scrape before generating a new report.
STALE_AFTER_HOURS = int(os.getenv("STALE_AFTER_HOURS", "24"))

# Rate limiting: minimum seconds between requests to the same source, to
# stay a well-behaved scraper rather than hammering screener.in / NSE.
MIN_REQUEST_INTERVAL_SECONDS = float(os.getenv("MIN_REQUEST_INTERVAL_SECONDS", "2.0"))

SCREENER_BASE_URL = "https://www.screener.in"
NSE_BASE_URL = "https://www.nseindia.com"
