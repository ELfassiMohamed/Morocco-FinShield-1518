package com.kantara.formatter;

import java.util.List;
import java.util.Map;

public class MarkdownFormatter implements OutputFormatter {

    @Override
    public String format(Map<String, Object> extractedData, String sourceType) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Kantara Output\n\n");

        Object documents = extractedData.get("documents");
        if (documents instanceof List<?> documentList) {
            for (Object document : documentList) {
                if (document instanceof Map<?, ?> documentMap) {
                    formatDocument(sb, documentMap);
                }
            }
        } else {
            formatDocument(sb, extractedData);
        }

        formatChunks(sb, extractedData.get("chunks"));
        formatErrors(sb, extractedData.get("errors"));
        return sb.toString();
    }

    private void formatDocument(StringBuilder sb, Map<?, ?> document) {
        String filename = asString(document.get("filename"));
        String sourceType = asString(document.get("source_type"));
        if (!filename.isBlank()) {
            sb.append("## ").append(escapeMarkdown(filename)).append("\n\n");
            if (!sourceType.isBlank()) {
                sb.append("_").append(sourceType.toUpperCase()).append("_\n\n");
            }
        }

        formatMetadata(sb, document.get("metadata"));
        formatPdfSections(sb, document.get("sections"));
        formatTables(sb, document.get("tables"));
    }

    private void formatMetadata(StringBuilder sb, Object metadataValue) {
        if (!(metadataValue instanceof Map<?, ?> metadata) || metadata.isEmpty()) {
            return;
        }

        sb.append("### Metadata\n\n");
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            sb.append("- **")
                    .append(escapeMarkdown(asString(entry.getKey())))
                    .append(":** ")
                    .append(escapeMarkdown(asString(entry.getValue())))
                    .append("\n");
        }
        sb.append("\n");
    }

    private void formatPdfSections(StringBuilder sb, Object sectionsValue) {
        if (!(sectionsValue instanceof List<?> sections) || sections.isEmpty()) {
            return;
        }

        sb.append("### Sections\n\n");
        for (Object sectionValue : sections) {
            if (!(sectionValue instanceof Map<?, ?> section)) {
                continue;
            }

            String title = asString(section.get("title"));
            String text = asString(section.get("text"));
            String pageStart = asString(section.get("page_start"));

            if (!title.isBlank()) {
                sb.append("#### ").append(escapeMarkdown(title)).append("\n\n");
            }
            if (!pageStart.isBlank()) {
                sb.append("_Page ").append(pageStart).append("_\n\n");
            }
            if (!text.isBlank()) {
                sb.append(text).append("\n\n");
            }
        }
    }

    private void formatTables(StringBuilder sb, Object tablesValue) {
        if (!(tablesValue instanceof List<?> tables) || tables.isEmpty()) {
            return;
        }

        sb.append("### Tables\n\n");
        for (Object tableValue : tables) {
            if (!(tableValue instanceof Map<?, ?> table)) {
                continue;
            }

            String name = asString(table.get("name"));
            if (!name.isBlank()) {
                sb.append("#### ").append(escapeMarkdown(name)).append("\n\n");
            }

            if (table.get("data") instanceof List<?> dataRows) {
                formatStructuredRows(sb, table.get("headers"), dataRows);
            } else if (table.get("rows") instanceof List<?> pdfRows) {
                formatPdfRows(sb, pdfRows);
            }
        }
    }

    private void formatStructuredRows(StringBuilder sb, Object headersValue, List<?> rows) {
        if (!(headersValue instanceof List<?> headers) || headers.isEmpty()) {
            sb.append("_No data_\n\n");
            return;
        }

        sb.append("| Row |");
        for (Object header : headers) {
            sb.append(" ").append(escapeTableCell(asString(header))).append(" |");
        }
        sb.append("\n|---|");
        for (int i = 0; i < headers.size(); i++) {
            sb.append("---|");
        }
        sb.append("\n");

        for (Object rowValue : rows) {
            if (!(rowValue instanceof Map<?, ?> row)) {
                continue;
            }
            sb.append("| ").append(escapeTableCell(asString(row.get("row_number")))).append(" |");
            Object values = row.get("values");
            for (Object header : headers) {
                String cell = "";
                if (values instanceof Map<?, ?> valueMap) {
                    cell = asString(valueMap.get(header));
                }
                sb.append(" ").append(escapeTableCell(cell)).append(" |");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void formatPdfRows(StringBuilder sb, List<?> rows) {
        for (Object row : rows) {
            if (row instanceof List<?> cells) {
                sb.append("- ").append(String.join(" | ", cells.stream().map(this::asString).toList())).append("\n");
            }
        }
        sb.append("\n");
    }

    private void formatChunks(StringBuilder sb, Object chunksValue) {
        if (!(chunksValue instanceof List<?> chunks) || chunks.isEmpty()) {
            return;
        }

        sb.append("## LLM Chunks\n\n");
        for (Object chunkValue : chunks) {
            if (!(chunkValue instanceof Map<?, ?> chunk)) {
                continue;
            }
            sb.append("### ").append(asString(chunk.get("id"))).append("\n\n");
            sb.append("_~").append(asString(chunk.get("token_estimate"))).append(" tokens");
            Object sources = chunk.get("sources");
            if (sources instanceof List<?> sourceList && !sourceList.isEmpty()) {
                sb.append(" | ").append(String.join(", ", sourceList.stream().map(this::asString).toList()));
            }
            sb.append("_\n\n");
            sb.append(asString(chunk.get("text"))).append("\n\n");
        }
    }

    private void formatErrors(StringBuilder sb, Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors) || errors.isEmpty()) {
            return;
        }

        sb.append("## Errors\n\n");
        for (Object errorValue : errors) {
            if (errorValue instanceof Map<?, ?> error) {
                sb.append("- **")
                        .append(escapeMarkdown(asString(error.get("filename"))))
                        .append(":** ")
                        .append(escapeMarkdown(asString(error.get("error"))))
                        .append("\n");
            }
        }
        sb.append("\n");
    }

    private String escapeMarkdown(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String escapeTableCell(String value) {
        return escapeMarkdown(value).replace("\n", " ");
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String formatName() {
        return "markdown";
    }
}
