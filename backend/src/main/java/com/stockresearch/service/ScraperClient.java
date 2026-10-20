package com.stockresearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Python scraper's FastAPI service (scraper/api_server.py) for
 * anything that needs live data: importing a company Screener knows about
 * but our database doesn't yet, and forcing a fresh re-scrape before every
 * report generation. Kept as a distinct client (not folded into
 * ReportService) so it's obvious this is the one place in the Java codebase
 * that depends on a second running process.
 */
@Service
public class ScraperClient {

    private final WebClient webClient;
    // v2: every call is bounded. v1 used block() with no timeout, so a hung
    // scraper (Screener slow, NSE bot-check) hung report generation forever.
    private final Duration scrapeTimeout;   // import/refresh: a full synchronous scrape
    private final Duration quickTimeout;    // quote/peers/health: should be fast

    public ScraperClient(WebClient.Builder webClientBuilder,
                          @Value("${scraper.base-url}") String baseUrl,
                          @Value("${scraper.scrape-timeout-seconds:120}") long scrapeTimeoutSeconds,
                          @Value("${scraper.quick-timeout-seconds:15}") long quickTimeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.scrapeTimeout = Duration.ofSeconds(scrapeTimeoutSeconds);
        this.quickTimeout = Duration.ofSeconds(quickTimeoutSeconds);
    }

    /** Lightweight liveness probe for /api/health. Never throws. */
    public boolean isReachable() {
        try {
            webClient.get().uri("/health").retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(3));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record ImportResult(Long companyId, String name, String nseSymbol, boolean newlyRegistered) {}

    /**
     * Looks up `query` against Screener's own search, registers it locally
     * if it's new, scrapes it, and returns the resulting company id.
     * Throws if the scraper service isn't reachable -- unlike refresh(),
     * there's no reasonable fallback for "import a company we don't have."
     */
    public ImportResult importCompany(String query) {
        Map<?, ?> response = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/import").queryParam("query", query).build())
                .retrieve()
                .bodyToMono(Map.class)
                .block(scrapeTimeout);
        if (response == null) {
            throw new IllegalStateException("Scraper service returned no response for import of '" + query + "'");
        }
        return new ImportResult(
                ((Number) response.get("company_id")).longValue(),
                String.valueOf(response.get("name")),
                String.valueOf(response.get("nse_symbol")),
                Boolean.TRUE.equals(response.get("newly_registered")));
    }

    /**
     * Forces a fresh scrape of an already-known company. Called before every
     * report generation so reports reflect live data rather than a
     * possibly-stale cache. Failures here are swallowed by the caller
     * (ReportService) -- if the scraper service isn't running, the report
     * still generates from whatever's already in Postgres rather than
     * failing outright, which keeps the demo-data-only path working for
     * anyone who hasn't set up the Python service at all.
     */
    public void forceRefresh(Long companyId) {
        webClient.post()
                .uri("/refresh/{id}", companyId)
                .retrieve()
                .bodyToMono(Map.class)
                .block(scrapeTimeout);
    }

    /** Real-time last-traded-price snapshot straight from NSE, independent
     * of whatever end-of-day close is sitting in the `prices` table. */
    public record LiveQuote(Double lastPrice, Double change, Double percentChange, Double previousClose,
                             Double dayHigh, Double dayLow, String asOf, String marketState) {}

    /** One sector peer as scraped live from Screener's own peer-comparison
     * table -- not necessarily a company we have a row for locally. */
    public record LivePeer(String name, String nseSymbol, Double cmp, Double peRatio, Double roce,
                            Double dividendYield, Double marketCap) {}

    /**
     * Fetches the live last-traded price for `nseSymbol` directly from NSE
     * (via the scraper's /quote endpoint), bypassing Postgres entirely so
     * the number reflects the actual moment it's requested, not whatever
     * close price was cached at the last scrape. Throws if the scraper
     * service or NSE itself is unreachable -- callers should catch this
     * and fall back to the last scraped close from the `prices` table.
     */
    public LiveQuote getLiveQuote(String nseSymbol) {
        Map<?, ?> response = webClient.get()
                .uri("/quote/{symbol}", nseSymbol)
                .retrieve()
                .bodyToMono(Map.class)
                .block(quickTimeout);
        if (response == null) {
            throw new IllegalStateException("Scraper service returned no live quote for " + nseSymbol);
        }
        return new LiveQuote(
                asDouble(response.get("last_price")),
                asDouble(response.get("change")),
                asDouble(response.get("percent_change")),
                asDouble(response.get("previous_close")),
                asDouble(response.get("day_high")),
                asDouble(response.get("day_low")),
                response.get("as_of") == null ? null : String.valueOf(response.get("as_of")),
                response.get("market_state") == null ? null : String.valueOf(response.get("market_state")));
    }

    /**
     * Fetches this company's sector peers straight from Screener's own
     * "Peer comparison" table at request time, rather than only ever
     * comparing against whichever companies our own `companies` table
     * happens to have rows for. Throws if unreachable; callers fall back
     * to DB-only peers when that happens.
     */
    @SuppressWarnings("unchecked")
    public List<LivePeer> getLiveSectorPeers(String nseSymbol) {
        List<Map<String, Object>> response = webClient.get()
                .uri("/peers/{symbol}", nseSymbol)
                .retrieve()
                .bodyToMono(List.class)
                .block(quickTimeout);
        if (response == null) {
            throw new IllegalStateException("Scraper service returned no peer data for " + nseSymbol);
        }
        return response.stream().map(m -> new LivePeer(
                m.get("name") == null ? null : String.valueOf(m.get("name")),
                m.get("nse_symbol") == null ? null : String.valueOf(m.get("nse_symbol")),
                asDouble(m.get("cmp")),
                asDouble(m.get("pe_ratio")),
                asDouble(m.get("roce")),
                asDouble(m.get("dividend_yield")),
                asDouble(m.get("market_cap"))
        )).toList();
    }

    private Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
