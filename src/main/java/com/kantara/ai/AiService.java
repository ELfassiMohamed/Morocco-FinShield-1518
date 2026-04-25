package com.kantara.ai;

import com.kantara.config.Config;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AiService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiService() {
        this(HttpClient.newHttpClient());
    }

    public AiService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public String generateInsights(Map<String, Object> payload, Config config) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("[Kantara] ERROR: Payload must not be empty.");
        }
        if (config == null) {
            throw new IllegalArgumentException("[Kantara] ERROR: Configuration is missing.");
        }

        try {
            String payloadJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            String prompt = buildPrompt(payloadJson);
            URI requestUri = buildGenerateUri(config);
            String requestBody = buildGeminiRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("[Kantara] ERROR: AI request failed with status " + response.statusCode() + ".");
            }

            return extractGeneratedText(response.body());
        } catch (JacksonException e) {
            throw new IllegalStateException("[Kantara] ERROR: Failed to build AI request payload.");
        } catch (IOException e) {
            throw new IllegalStateException("[Kantara] ERROR: Network error while calling AI service.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[Kantara] ERROR: AI request was interrupted.");
        }
    }

    public AiResponse parseAiResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("[Kantara] ERROR: AI response is empty.");
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
            throw new IllegalStateException("[Kantara] ERROR: AI response contains invalid JSON.");
        }
    }

    private URI buildGenerateUri(Config config) {
        String endpoint = normalizeSegment(config.endpoint());
        String model = normalizeSegment(config.model());
        String apiKey = config.apiKey();

        if (endpoint.isEmpty()) {
            throw new IllegalStateException("[Kantara] ERROR: Endpoint is missing.");
        }
        if (model.isEmpty()) {
            throw new IllegalStateException("[Kantara] ERROR: Model is missing.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("[Kantara] ERROR: Missing API key (KANTARA_API_KEY)");
        }

        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(endpoint + "/" + model + ":generateContent?key=" + encodedKey);
    }

    private String normalizeSegment(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String buildGeminiRequestBody(String prompt) throws JacksonException {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );
        return objectMapper.writeValueAsString(body);
    }

    private String extractGeneratedText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("[Kantara] ERROR: Empty AI response body.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray()) {
                throw new IllegalStateException("[Kantara] ERROR: Invalid AI response format.");
            }

            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                if (!parts.isArray()) {
                    continue;
                }

                StringBuilder text = new StringBuilder();
                for (JsonNode part : parts) {
                    String value = part.path("text").asText("").trim();
                    if (!value.isEmpty()) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(value);
                    }
                }

                if (text.length() > 0) {
                    return text.toString();
                }
            }
            throw new IllegalStateException("[Kantara] ERROR: No generated text found in AI response.");
        } catch (JacksonException e) {
            throw new IllegalStateException("[Kantara] ERROR: Invalid AI response format.");
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

    private String extractJsonObject(String rawResponse) {
        int start = rawResponse.indexOf('{');
        if (start < 0) {
            throw new IllegalStateException("[Kantara] ERROR: AI response does not contain JSON.");
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

        throw new IllegalStateException("[Kantara] ERROR: AI response JSON is incomplete.");
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
            throw new IllegalStateException("[Kantara] ERROR: AI response validation failed: presentation is empty.");
        }

        for (int i = 0; i < response.presentation().size(); i++) {
            Slide slide = response.presentation().get(i);
            if (slide.title() == null || slide.title().isBlank()) {
                throw new IllegalStateException(
                        "[Kantara] ERROR: AI response validation failed: slide " + (i + 1) + " has no title."
                );
            }
            if (slide.bullets() == null || slide.bullets().isEmpty()) {
                throw new IllegalStateException(
                        "[Kantara] ERROR: AI response validation failed: slide " + (i + 1) + " has no bullets."
                );
            }
        }
    }
}
