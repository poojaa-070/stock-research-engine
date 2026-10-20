package com.stockresearch.ai;

import com.stockresearch.dto.CategoryScoreResult;
import com.stockresearch.dto.QualitativeFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * The ONLY place in the codebase that talks to the Claude API. Two jobs,
 * both bounded so the model can never set a score:
 *
 *  1. extractFlags(): reads scraped text (news headlines, annual report
 *     excerpts) and returns a list of QualitativeFlag records — a fixed
 *     enum-like flagKey, a boolean, a confidence tier, and a verbatim quote.
 *     FlagDeductionRules (scoring package) is what turns these into points.
 *
 *  2. summarize(): given the ALREADY-COMPUTED category scores + evidence,
 *     writes the human-readable narrative. It is instructed to restate the
 *     given facts/numbers, not invent new ones or issue a buy/sell call.
 */
@Service
public class ClaudeAiService {

    private final WebClient webClient;
    private final String model;
    private final boolean configured;
    private final java.time.Duration timeout;

    private static final java.util.Set<String> ALLOWED_FLAG_KEY_SET = java.util.Set.of(
            // Business Quality: moat depth, market position, industry structure
            "moat_erosion", "market_share_gain", "durable_pricing_power",
            "network_effects_present", "high_switching_costs", "cost_leadership_advantage",
            "strong_intangible_assets", "large_addressable_market",
            "intense_competitive_rivalry", "high_threat_of_substitutes",
            // Management: governance, capital allocation
            "management_credibility_concern", "management_capital_allocation_strength",
            "related_party_transaction_flag", "auditor_qualification",
            // Risk: regulatory, commodity, concentration, bargaining power
            "customer_concentration_risk", "regulatory_overhang", "commodity_cost_exposure",
            "high_supplier_bargaining_power", "high_buyer_bargaining_power"
    );
    private static final String ALLOWED_FLAG_KEYS = String.join(", ", ALLOWED_FLAG_KEY_SET);

    public ClaudeAiService(
            WebClient.Builder webClientBuilder,
            @Value("${claude.base-url}") String baseUrl,
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model}") String model,
            @Value("${claude.timeout-seconds:45}") long timeoutSeconds) {
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank();
        this.timeout = java.time.Duration.ofSeconds(timeoutSeconds);
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /** True when an API key is set. When false, every call short-circuits instead
     * of making three doomed HTTP requests per report (v1 behaviour). */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Outcome of a flag-extraction pass. {@code ran} is false when the AI layer
     * could not run at all (no key, no text to read, API error, unparseable
     * reply); the scoring
     * engine then treats the qualitative slot as missing data rather than as
     * "no flags found", which would otherwise count as a clean bill of health.
     */
    public record FlagExtraction(List<QualitativeFlag> flags, boolean ran) {}

    public FlagExtraction extractFlagsWithStatus(String companyName, List<String> newsHeadlinesAndSources) {
        if (!configured) return new FlagExtraction(List.of(), false);
        // No text at all means there is nothing to base a qualitative read on —
        // treated as missing data, not as a clean "no red flags" result.
        if (newsHeadlinesAndSources.isEmpty()) return new FlagExtraction(List.of(), false);
        String raw = callClaude(buildFlagPrompt(companyName, newsHeadlinesAndSources));
        if (raw == null) return new FlagExtraction(List.of(), false);
        List<QualitativeFlag> parsed = parseFlags(raw);
        return parsed == null ? new FlagExtraction(List.of(), false) : new FlagExtraction(parsed, true);
    }

    /** v1 signature, kept for compatibility. */
    public List<QualitativeFlag> extractFlags(String companyName, List<String> newsHeadlinesAndSources) {
        return extractFlagsWithStatus(companyName, newsHeadlinesAndSources).flags();
    }

    private String buildFlagPrompt(String companyName, List<String> newsHeadlinesAndSources) {
        String prompt = """
            You are extracting structured risk/quality flags for %s from the
            text snippets below. You may ONLY use these exact flagKey values:
            %s

            For each flag you find clear textual evidence for, output one
            JSON object: {"flagKey": "...", "present": true, "confidence":
            "low"|"medium"|"high", "evidenceQuote": "<verbatim short quote>",
            "sourceRef": "<which snippet it came from>"}.

            Do NOT invent a numeric score of any kind. Do NOT output a flagKey
            outside the allowed list. If no snippet supports a flag, omit it
            entirely rather than including it with present:false.

            Respond ONLY with a JSON array, nothing else.

            Snippets:
            %s
            """.formatted(companyName, ALLOWED_FLAG_KEYS, String.join("\n---\n", newsHeadlinesAndSources));
        return prompt;
    }

    public String summarizeReport(String companyName, Map<String, CategoryScoreResult> categoryScores,
                                   double overallScore, String verdict) {
        if (!configured) return "";
        StringBuilder factSheet = new StringBuilder();
        categoryScores.forEach((category, result) -> {
            factSheet.append("- ").append(category).append(": score ").append(Math.round(result.score))
                    .append("/100 (data completeness ").append(Math.round(result.dataCompleteness * 100)).append("%)\n");
            result.evidence.forEach(e -> factSheet.append("    * ").append(e.statement())
                    .append(" [Source: ").append(e.sourceRef()).append("]\n"));
        });

        String prompt = """
            Write a short, plain-English research summary (150-250 words) for
            %s based ONLY on the facts below. Overall score: %.1f/100.
            Verdict label (do not change it, just weave it in naturally): %s.

            Hard rules:
            - Never say "buy", "sell", "will rise", "will fall", or make any
              price prediction.
            - Every claim must trace back to a fact listed below — do not
              introduce new numbers or facts.
            - Explicitly mention at least one "watch out for" item, drawn
              from the lowest-scoring category below.
            - End with: "This is an analytical research tool, not
              personalized financial advice."

            Facts:
            %s
            """.formatted(companyName, overallScore, verdict, factSheet);

        String text = callClaude(prompt);
        return text == null ? "" : text.trim();
    }

    /**
     * Generates the strongest case FOR and the strongest case AGAINST,
     * using only the same evidence trail the report already shows. This is
     * presenting both sides of an argument from existing facts, not issuing
     * a recommendation -- it never tells the reader what to do, and the
     * hard rules below explicitly forbid buy/sell/hold language creeping in
     * through this door instead.
     */
    public BullBearCase generateBullBearCase(String companyName, Map<String, CategoryScoreResult> categoryScores) {
        if (!configured) return new BullBearCase("", "");
        StringBuilder factSheet = new StringBuilder();
        categoryScores.forEach((category, result) -> {
            factSheet.append("- ").append(category).append(": score ").append(Math.round(result.score)).append("/100\n");
            result.evidence.forEach(e -> factSheet.append("    * ").append(e.statement())
                    .append(" [Source: ").append(e.sourceRef()).append("]\n"));
        });

        String prompt = """
            Based ONLY on the facts below for %s, write two short paragraphs
            (80-120 words each):

            1. "bullCase": the strongest case FOR this company, drawing on
               its highest-scoring categories and most positive evidence.
            2. "bearCase": the strongest case AGAINST / what could go wrong,
               drawing on its lowest-scoring categories, weakest evidence,
               and any data gaps (low data-completeness categories count as
               a real weakness -- say so explicitly if relevant).

            Hard rules for BOTH paragraphs:
            - Never say "buy", "sell", "hold", "avoid", or issue any
              recommendation or directive about what the reader should do.
            - Never predict a future price or price direction.
            - Every claim must trace back to a fact listed below.
            - If the available evidence is too thin to make a strong case
              either way, say that plainly instead of overstating it.

            Respond ONLY with a JSON object: {"bullCase": "...", "bearCase": "..."}

            Facts:
            %s
            """.formatted(companyName, factSheet);

        String raw = callClaude(prompt);
        return raw == null ? new BullBearCase("", "") : parseBullBearCase(raw);
    }

    public record BullBearCase(String bullCase, String bearCase) {}

    private BullBearCase parseBullBearCase(String jsonText) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String cleaned = extractJson(jsonText, '{', '}');
            Map<String, Object> raw = mapper.readValue(cleaned, Map.class);
            return new BullBearCase(
                    String.valueOf(raw.getOrDefault("bullCase", "")).trim(),
                    String.valueOf(raw.getOrDefault("bearCase", "")).trim());
        } catch (Exception e) {
            // Same degrade-gracefully pattern as flag extraction -- an empty
            // bull/bear case is fine, the rest of the report still generates.
            return new BullBearCase("", "");
        }
    }

    private String callClaude(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<?, ?> response;
        try {
            response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout);
        } catch (Exception e) {
            // Missing/invalid ANTHROPIC_API_KEY, network failure, or a Claude
            // API error all land here. The deterministic scores never touch
            // this method, so a failure here should only mean an empty
            // narrative/flag list, not a 500 for the whole report.
            // v2: returns null (not "") so callers can tell "failed" from "empty".
            return null;
        }

        if (response == null) return null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        StringBuilder text = new StringBuilder();
        if (content != null) {
            for (Map<String, Object> block : content) {
                if ("text".equals(block.get("type"))) {
                    text.append(block.get("text"));
                }
            }
        }
        return text.toString();
    }

    private List<QualitativeFlag> parseFlags(String jsonArrayText) {
        // Minimal, dependency-free JSON array parsing kept deliberately
        // simple here; swap in Jackson's ObjectMapper for production use
        // (already on the classpath via spring-boot-starter-web).
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String cleaned = extractJson(jsonArrayText, '[', ']');
            List<Map<String, Object>> raw = mapper.readValue(cleaned, List.class);
            return raw.stream()
                    .filter(m -> ALLOWED_FLAG_KEY_SET.contains(String.valueOf(m.get("flagKey"))))
                    .map(m -> new QualitativeFlag(
                            String.valueOf(m.get("flagKey")),
                            Boolean.TRUE.equals(m.get("present")),
                            String.valueOf(m.getOrDefault("confidence", "medium")),
                            String.valueOf(m.getOrDefault("evidenceQuote", "")),
                            String.valueOf(m.getOrDefault("sourceRef", ""))))
                    .toList();
        } catch (Exception e) {
            // A malformed AI response degrades to "no flags this run" rather
            // than crashing the whole report — the deterministic scores are
            // still valid and shown; only the qualitative slice is skipped.
            // v2: null signals "didn't run" to extractFlagsWithStatus.
            return null;
        }
    }

    /**
     * Strips code fences and any prose around the JSON payload. Models
     * occasionally prefix a sentence despite instructions; v1 then failed to
     * parse and silently dropped every flag.
     */
    static String extractJson(String text, char open, char close) {
        String cleaned = text.replaceAll("(?s)```json|```", "").trim();
        int start = cleaned.indexOf(open);
        int end = cleaned.lastIndexOf(close);
        return start >= 0 && end > start ? cleaned.substring(start, end + 1) : cleaned;
    }
}
