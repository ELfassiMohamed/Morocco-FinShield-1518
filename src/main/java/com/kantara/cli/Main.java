package com.kantara.cli;

import com.kantara.ai.AiService;
import com.kantara.generator.PptGenerator;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI Entry point to test the full data pipeline from extraction to AI payload.
 */
public class Main {

    public static void main(String[] args) {
        AiService aiService = new AiService(); // default constructor simulates API call
        PptGenerator pptGenerator = new PptGenerator();
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> payload = Map.of(
                "source", "Q1 Performance Report",
                "period", "2026-Q1",
                "kpis", Map.of(
                        "revenue", 1_250_000,
                        "cost", 830_000,
                        "customer_churn", 0.08
                ),
                "highlights", List.of(
                        "North region outperformed target by 12%",
                        "Digital channel CAC increased by 7%"
                )
        );

        try {
            String insightsJson = aiService.generateInsights(payload);
            //System.out.println("AI Insights:");
            System.out.println(insightsJson);

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResponse = objectMapper.readValue(insightsJson, Map.class);
            List<Map<String, Object>> slides = extractSlides(aiResponse.get("presentation"));
            pptGenerator.generatePresentation(slides, "output.pptx");
            System.out.println("PowerPoint generated successfully: output.pptx");
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid payload: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("Processing failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractSlides(Object presentationObject) {
        if (!(presentationObject instanceof List<?> rawSlides)) {
            return List.of();
        }
        List<Map<String, Object>> slides = new ArrayList<>();
        for (Object item : rawSlides) {
            if (item instanceof Map<?, ?> map) {
                slides.add((Map<String, Object>) map);
            }
        }
        return slides;
    }
}
