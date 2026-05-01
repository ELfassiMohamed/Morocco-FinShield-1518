package com.kantara.formatter;

import java.util.List;
import java.util.Map;

public class MarkdownFormatter implements OutputFormatter {

    @Override
    public String format(Map<String, Object> extractedData, String sourceType) {
        StringBuilder sb = new StringBuilder();
        
        if ("pdf".equals(sourceType)) {
            formatPdf(sb, extractedData);
        } else {
            formatTabular(sb, extractedData);
        }
        
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void formatPdf(StringBuilder sb, Map<String, Object> data) {
        List<String> sections = (List<String>) data.get("sections");
        if (sections != null) {
            for (String section : sections) {
                // If it looks like a heading (e.g. single line, uppercase)
                String[] lines = section.split("\n", 2);
                if (lines.length > 0 && lines[0].length() < 100 && lines[0].toUpperCase().equals(lines[0])) {
                    sb.append("## ").append(lines[0].trim()).append("\n\n");
                    if (lines.length > 1) {
                        sb.append(lines[1].trim()).append("\n\n");
                    }
                } else {
                    sb.append(section.trim()).append("\n\n");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void formatTabular(StringBuilder sb, Map<String, Object> data) {
        List<Map<String, String>> rows = (List<Map<String, String>>) data.get("rows");
        if (rows == null || rows.isEmpty()) {
            sb.append("_No data_\n");
            return;
        }

        Map<String, String> firstRow = rows.get(0);
        List<String> headers = firstRow.keySet().stream().toList();

        // Header
        sb.append("|");
        for (String header : headers) {
            sb.append(" ").append(header).append(" |");
        }
        sb.append("\n|");
        for (String header : headers) {
            sb.append("---|");
        }
        sb.append("\n");

        // Rows
        for (Map<String, String> row : rows) {
            sb.append("|");
            for (String header : headers) {
                String val = row.getOrDefault(header, "");
                sb.append(" ").append(val).append(" |");
            }
            sb.append("\n");
        }
    }

    @Override
    public String formatName() {
        return "markdown";
    }
}
