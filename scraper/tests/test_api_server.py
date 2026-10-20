"""FastAPI endpoint tests with Screener/NSE calls stubbed out."""
from fastapi.testclient import TestClient

import api_server

client = TestClient(api_server.app)


def test_health():
    assert client.get("/health").json() == {"status": "ok"}


def test_search_encodes_query_and_parses_results(monkeypatch):
    seen = {}

    class FakeResp:
        def json(self):
            return [{"name": "Mahindra & Mahindra", "url": "/company/M&M/consolidated/"},
                    {"name": "Not a company", "url": "/screen/raw/"}]

    def fake_get(path, **kwargs):
        seen.update(path=path, params=kwargs.get("params"))
        return FakeResp()

    monkeypatch.setattr(api_server._search_client, "get", fake_get)
    res = client.get("/search", params={"q": "M&M"}).json()
    assert seen == {"path": "/api/company/search/", "params": {"q": "M&M"}}
    assert [r["nse_symbol"] for r in res] == ["M&M"]


def test_quote_maps_upstream_failure_to_502(monkeypatch):
    def boom(symbol):
        raise RuntimeError("NSE bot check")
    monkeypatch.setattr(api_server._nse_client, "fetch_live_quote", boom)
    r = client.get("/quote/TCS")
    assert r.status_code == 502
    assert "NSE bot check" in r.json()["detail"]
