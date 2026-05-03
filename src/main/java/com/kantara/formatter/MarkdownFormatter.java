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
        List<Map<String, String>> sections = (List<Map<String, String>>) data.get("sections");
        if (sections != null) {
            for (Map<String, String> section : sections) {
                String title = section.get("title");
                String text = section.get("text");
                
                if (title != null && !title.isEmpty()) {
                    sb.append("## ").append(title).append("\n\n");
                }
                if (text != null && !text.isEmpty()) {
                    sb.append(text).append("\n\n");
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
