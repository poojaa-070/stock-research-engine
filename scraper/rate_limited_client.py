import time
import requests
from tenacity import retry, stop_after_attempt, wait_exponential

from config import MIN_REQUEST_INTERVAL_SECONDS

_last_request_at = {}


class RateLimitedClient:
    """Thin wrapper around requests.Session that enforces a minimum gap
    between requests to the same host, and retries transient failures with
    backoff. Deliberately conservative — we're guests on screener.in/NSE."""

    def __init__(self, base_url: str, headers: dict | None = None):
        self.base_url = base_url
        self.session = requests.Session()
        if headers:
            self.session.headers.update(headers)

    def _throttle(self):
        now = time.monotonic()
        last = _last_request_at.get(self.base_url, 0)
        wait = MIN_REQUEST_INTERVAL_SECONDS - (now - last)
        if wait > 0:
            time.sleep(wait)
        _last_request_at[self.base_url] = time.monotonic()

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    def get(self, path: str, **kwargs) -> requests.Response:
        self._throttle()
        url = path if path.startswith("http") else f"{self.base_url}{path}"
        resp = self.session.get(url, timeout=15, **kwargs)
        resp.raise_for_status()
        return resp
