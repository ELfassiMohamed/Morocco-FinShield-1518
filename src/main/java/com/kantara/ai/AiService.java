package com.kantara.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiUri;
    private final boolean simulateCall;

    public AiService() {
        this(HttpClient.newHttpClient(), URI.create("https://api.example.com/v1/insights"), true);
    }

    public AiService(String apiUrl) {
        this(HttpClient.newHttpClient(), URI.create(apiUrl), false);
    }

    public AiService(HttpClient httpClient, URI apiUri, boolean simulateCall) {
        this.httpClient = httpClient;
        this.apiUri = apiUri;
        this.simulateCall = simulateCall;
        this.objectMapper = new ObjectMapper();
    }

    public String generateInsights(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload must not be null or empty");
        }

        try {
            String payloadJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            String prompt = buildPrompt(payloadJson);

            if (simulateCall) {
                return buildSimulatedResponse(payload);
            }

            String requestBody = objectMapper.writeValueAsString(Map.of("prompt", prompt));

            HttpRequest request = HttpRequest.newBuilder(apiUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            throw new IllegalStateException(
                    "AI API call failed. Status: " + response.statusCode() + ", Body: " + response.body()
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to convert payload or request to JSON", e);
        } catch (IOException e) {
            throw new IllegalStateException("I/O error while calling AI API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI API call was interrupted", e);
        }
    }

    public AiResponse parseAiResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("AI response is empty.");
        }

        String json = extractJsonObject(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> keyInsights = readStringArray(root, "keyInsights", "key_insights");
            List<Slide> presentation = readPresentation(root);

            AiResponse parsed = new AiResponse(keyInsights, presentation);
            validate(parsed);
            return parsed;
        } catch (JacksonException e) {
            throw new IllegalStateException("AI response contains invalid JSON.", e);
        }
    }

    private String buildPrompt(String payloadJson) {
        return """
                You are a senior business analyst.

                Analyze the following structured data:

                %s

                CRITICAL:
                If your response contains anything outside JSON, it will be rejected.
                Start immediately with '{' and end with '}'.
                Do not write "AI Insights" or any prefix.

                STRICT INSTRUCTIONS:
                - Return ONLY valid JSON.
                - The response MUST start with '{' and end with '}'.
                - Do NOT include any text before or after the JSON.
                - Do NOT include explanations or comments.

                REQUIREMENTS:
                1. keyInsights:
                - Provide 3-5 insights.
                - Each insight MUST reference actual data (numbers, categories, trends).
                - Each insight MUST be specific and actionable (no generic statements).
                - Each insight MUST include business interpretation.

                2. presentation:
                - Generate EXACTLY 5 slides.
                - Each slide must include "title" and "bullets" (3-5 bullet points).

                3. Slides structure:
                - Slide 1: Executive Summary
                - Slide 2: Key Metrics
                - Slide 3: Trends / Analysis
                - Slide 4: Risks / Issues
                - Slide 5: Recommendations

                4. Executive Summary:
                - MUST highlight key business outcomes and insights.
                - MUST NOT include metadata (source, file name, or generic labels).
                - Focus on performance, growth, risks, and key drivers.

                5. Data usage:
                - Use the provided data explicitly in insights and slides.
                - Avoid statements that could apply to any dataset.

                6. Number formatting:
                - Format large numbers in a human-readable way (e.g., 1.25M or 1,250,000).
                - Keep number formatting consistent across all slides.

                7. Avoid redundancy:
                - Do NOT repeat the same information across multiple slides.
                - Each slide must provide distinct and complementary insights.

                OUTPUT FORMAT:
                {
                  "keyInsights": [
                    "...",
                    "...",
                    "..."
                  ],
                  "presentation": [
                    {
                      "title": "...",
                      "bullets": ["...", "..."]
                    }
                  ]
                }
                """.formatted(payloadJson);
    }

    private String buildSimulatedResponse(Map<String, Object> payload) throws JacksonException {
        Map<String, Object> kpis = asMap(payload.get("kpis"));

        String period = String.valueOf(payload.getOrDefault("period", "N/A"));

        double revenue = asDouble(kpis.get("revenue"), 0.0);
        double cost = asDouble(kpis.get("cost"), 0.0);
        double churnRatio = asDouble(kpis.get("customer_churn"), 0.0);
        double margin = revenue - cost;
        double marginPct = revenue > 0 ? (margin / revenue) * 100.0 : 0.0;
        double costToRevenuePct = revenue > 0 ? (cost / revenue) * 100.0 : 0.0;
        int highlightsCount = payload.get("highlights") instanceof List<?> h ? h.size() : 0;

        List<String> insights = new ArrayList<>();
        insights.add(String.format(
                Locale.ROOT,
                "In %s, revenue reached %s against %s in costs, generating %s gross margin (%.1f%%); sustain pricing and mix discipline to protect profitability.",
                period, formatHumanNumber(revenue), formatHumanNumber(cost), formatHumanNumber(margin), marginPct
        ));
        insights.add(String.format(
                Locale.ROOT,
                "Customer churn is %.1f%% (retention %.1f%%), signaling growth drag; prioritize retention programs in high-value segments before scaling acquisition spend.",
                churnRatio * 100.0, (1.0 - churnRatio) * 100.0
        ));
        insights.add(String.format(
                Locale.ROOT,
                "Cost-to-revenue is %.1f%%, leaving %.1f%% contribution headroom; tightening variable costs by even 1-2 points would materially improve operating flexibility.",
                costToRevenuePct, marginPct
        ));
        insights.add(String.format(
                Locale.ROOT,
                "The dataset includes %d KPI fields and %d highlighted signals, enough for directional decisions now but requiring broader KPI coverage for root-cause precision.",
                kpis.size(), highlightsCount
        ));

        List<Map<String, Object>> slides = List.of(
                Map.of(
                        "title", "Executive Summary",
                        "bullets", List.of(
                                String.format(Locale.ROOT, "%s delivered %s revenue and %s gross margin, confirming positive unit economics.", period, formatHumanNumber(revenue), formatHumanNumber(margin)),
                                String.format(Locale.ROOT, "Profitability stands at %.1f%% margin, but growth quality is pressured by %.1f%% churn.", marginPct, churnRatio * 100.0),
                                String.format(Locale.ROOT, "Near-term performance depends on balancing cost control (%.1f%% cost-to-revenue) with retention recovery.", costToRevenuePct)
                        )
                ),
                Map.of(
                        "title", "Key Metrics",
                        "bullets", List.of(
                                String.format(Locale.ROOT, "Revenue: %s", formatHumanNumber(revenue)),
                                String.format(Locale.ROOT, "Cost: %s", formatHumanNumber(cost)),
                                String.format(Locale.ROOT, "Gross margin: %s (%.1f%%)", formatHumanNumber(margin), marginPct),
                                String.format(Locale.ROOT, "Customer churn: %.1f%% | Retention: %.1f%%", churnRatio * 100.0, (1.0 - churnRatio) * 100.0)
                        )
                ),
                Map.of(
                        "title", "Trends / Analysis",
                        "bullets", List.of(
                                String.format(Locale.ROOT, "Current model converts %.1f%% of revenue into gross margin, indicating pricing/cost structure is viable.", marginPct),
                                String.format(Locale.ROOT, "Churn at %.1f%% suggests acquisition efficiency may erode unless lifecycle interventions improve retention.", churnRatio * 100.0),
                                String.format(Locale.ROOT, "%d highlighted signals indicate directional momentum, but deeper trend confidence needs time-series and segment breakdowns.", highlightsCount)
                        )
                ),
                Map.of(
                        "title", "Risks / Issues",
                        "bullets", List.of(
                                String.format(Locale.ROOT, "Primary risk: %.1f%% churn can offset net new growth and inflate customer acquisition payback periods.", churnRatio * 100.0),
                                String.format(Locale.ROOT, "Cost exposure: %s cost base (%.1f%% of revenue) may compress margin under demand volatility.", formatHumanNumber(cost), costToRevenuePct),
                                String.format(Locale.ROOT, "Data risk: only %d KPI fields can mask channel, cohort, or regional underperformance.", kpis.size())
                        )
                ),
                Map.of(
                        "title", "Recommendations",
                        "bullets", List.of(
                                String.format(Locale.ROOT, "Launch a retention sprint targeting a churn reduction of 1-2 points from the current %.1f%% baseline.", churnRatio * 100.0),
                                String.format(Locale.ROOT, "Set a monthly guardrail to keep cost-to-revenue below %.1f%% and protect margin above %.1f%%.", costToRevenuePct, marginPct),
                                "Expand KPI tracking by segment, channel, and region to improve intervention precision and capital allocation."
                        )
                )
        );

        Map<String, Object> simulated = Map.of(
                "keyInsights", insights,
                "presentation", slides
        );

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simulated);
    }

    private String extractJsonObject(String rawResponse) {
        int start = rawResponse.indexOf('{');
        if (start < 0) {
            throw new IllegalStateException("AI response does not contain a JSON object.");
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < rawResponse.length(); i++) {
            char c = rawResponse.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return rawResponse.substring(start, i + 1);
                }
            }
        }

        throw new IllegalStateException("AI response JSON is incomplete.");
    }

    private List<String> readStringArray(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (!node.isArray()) {
                continue;
            }

            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return values;
        }

        return Collections.emptyList();
    }

    private List<Slide> readPresentation(JsonNode root) {
        JsonNode presentationNode = root.path("presentation");
        if (!presentationNode.isArray()) {
            return Collections.emptyList();
        }

        List<Slide> slides = new ArrayList<>();
        for (JsonNode slideNode : presentationNode) {
            String title = slideNode.path("title").asText("").trim();
            List<String> bullets = readStringArray(slideNode, "bullets");
            slides.add(new Slide(title, bullets));
        }
        return slides;
    }

    private void validate(AiResponse response) {
        if (response.presentation().isEmpty()) {
            throw new IllegalStateException("AI response validation failed: presentation is empty.");
        }

        for (int i = 0; i < response.presentation().size(); i++) {
            Slide slide = response.presentation().get(i);
            if (slide.title() == null || slide.title().isBlank()) {
                throw new IllegalStateException(
                        "AI response validation failed: slide " + (i + 1) + " has no title."
                );
            }
            if (slide.bullets() == null || slide.bullets().isEmpty()) {
                throw new IllegalStateException(
                        "AI response validation failed: slide " + (i + 1) + " has no bullets."
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String formatHumanNumber(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000) {
            return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0);
        }
        if (absolute >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
        }
        if (absolute >= 1_000) {
            return String.format(Locale.US, "%,.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
