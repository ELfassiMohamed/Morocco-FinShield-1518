package com.kantara.cli;

import com.kantara.ai.AiService;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        AiService aiService = new AiService(); // default constructor simulates API call

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
            System.out.println("AI Insights:");
            System.out.println(insightsJson);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid payload: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println("AI generation failed: " + e.getMessage());
        }
    }
}
