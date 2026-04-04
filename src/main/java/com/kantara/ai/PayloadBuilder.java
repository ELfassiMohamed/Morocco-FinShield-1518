package com.kantara.ai;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Senior-level Data Pipeline component for structuring AI payloads.
 * Aggregates Excel data and cleans PDF sections.
 */
public class PayloadBuilder {

    /**
     * Builds a structured payload for AI processing.
     *
     * @param excelData   List of maps representing tabular data.
     * @param pdfSections List of raw text sections from a PDF.
     * @return A structured Map containing sales summary and report sections.
     */
    public Map<String, Object> buildPayload(
            List<Map<String, String>> excelData,
            List<String> pdfSections
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // 1. Process Excel Data for Sales Summary
        payload.put("sales_summary", processExcelData(excelData));

        // 2. Process PDF Sections
        payload.put("report_sections", processPdfSections(pdfSections));

        return payload;
    }

    private Map<String, Object> processExcelData(List<Map<String, String>> excelData) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (excelData == null || excelData.isEmpty()) {
            return summary;
        }

        // Mapping column keys to their values for numeric/categorical analysis
        Map<String, List<Double>> numericValues = new HashMap<>();
        Map<String, Map<String, Integer>> categoricalCounts = new HashMap<>();

        for (Map<String, String> row : excelData) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.isBlank()) continue;

                Optional<Double> numericOpt = tryParseDouble(value);
                if (numericOpt.isPresent()) {
                    numericValues.computeIfAbsent(key, k -> new ArrayList<>()).add(numericOpt.get());
                } else {
                    categoricalCounts.computeIfAbsent(key, k -> new HashMap<>())
                            .merge(value, 1, Integer::sum);
                }
            }
        }

        // Metrics: numeric aggregates
        List<Map<String, Object>> metrics = new ArrayList<>();
        numericValues.forEach((key, values) -> {
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            double avg = sum / values.size();
            
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("column", key);
            metric.put("total_value", sum);
            metric.put("average_value", avg);
            metric.put("count", values.size());
            metrics.add(metric);
        });
        summary.put("metrics", metrics);

        // Insights: top categories
        List<Map<String, Object>> insights = new ArrayList<>();
        categoricalCounts.forEach((key, counts) -> {
            counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(topEntry -> {
                        Map<String, Object> insight = new LinkedHashMap<>();
                        insight.put("category_column", key);
                        insight.put("top_value", topEntry.getKey());
                        insight.put("frequency", topEntry.getValue());
                        insights.add(insight);
                    });
        });
        summary.put("top_categories", insights);

        return summary;
    }

    private List<String> processPdfSections(List<String> pdfSections) {
        if (pdfSections == null) return Collections.emptyList();
        
        return pdfSections.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private Optional<Double> tryParseDouble(String value) {
        try {
            // Remove currency symbols if present for basic numeric detection
            String cleaned = value.replaceAll("[^0-9.\\-]", "");
            if (cleaned.isEmpty()) return Optional.empty();
            return Optional.of(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
