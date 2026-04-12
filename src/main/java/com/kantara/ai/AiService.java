package com.kantara.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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

    private String buildPrompt(String payloadJson) {
        return """
                You are a senior business analyst.

                Analyze the business payload below and return insights.

                Payload (JSON):
                %s

                Return JSON format ONLY with this exact shape:
                {
                  "key_insights": [
                    "...",
                    "...",
                    "..."
                  ],
                  "presentation": [
                    {
                      "title": "Slide title",
                      "bullets": ["point 1", "point 2"]
                    }
                  ]
                }

                Rules:
                - No free text outside JSON.
                - Keep output clean and valid JSON.
                - Do not include markdown fences.
                """.formatted(payloadJson);
    }

    private String buildSimulatedResponse(Map<String, Object> payload) throws JacksonException {
        Object sourceName = payload.getOrDefault("source", "uploaded dataset");
        Map<String, Object> simulated = Map.of(
                "key_insights", List.of(
                        "Revenue growth is strongest in top-performing segments.",
                        "Customer retention improved, but acquisition costs remain high.",
                        "Operational bottlenecks are concentrated in late-stage delivery."
                ),
                "presentation", List.of(
                        Map.of(
                                "title", "Executive Summary - " + sourceName,
                                "bullets", List.of(
                                        "Focus investment on high-margin segments.",
                                        "Reduce acquisition cost through channel optimization."
                                )
                        )
                )
        );
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simulated);
    }
}

