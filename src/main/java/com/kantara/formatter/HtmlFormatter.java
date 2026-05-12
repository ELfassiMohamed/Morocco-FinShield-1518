package com.kantara.formatter;

import java.util.List;
import java.util.Map;

public class HtmlFormatter implements OutputFormatter {

    @Override
    public String format(Map<String, Object> extractedData, String sourceType) {
        StringBuilder sb = new StringBuilder();
        sb.append("<article class=\"kantara-output\">\n");
        sb.append("<h1>Kantara Output</h1>\n");

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
        sb.append("</article>\n");
        return sb.toString();
    }

    private void formatDocument(StringBuilder sb, Map<?, ?> document) {
        String filename = asString(document.get("filename"));
        String sourceType = asString(document.get("source_type"));

        sb.append("<section class=\"kantara-document\">\n");
        if (!filename.isBlank()) {
            sb.append("<h2>").append(escapeHtml(filename)).append("</h2>\n");
            if (!sourceType.isBlank()) {
                sb.append("<p><em>").append(escapeHtml(sourceType.toUpperCase())).append("</em></p>\n");
            }
        }

        formatMetadata(sb, document.get("metadata"));
        formatSections(sb, document.get("sections"));
        formatTables(sb, document.get("tables"));
        sb.append("</section>\n");
    }

    private void formatMetadata(StringBuilder sb, Object metadataValue) {
        if (!(metadataValue instanceof Map<?, ?> metadata) || metadata.isEmpty()) {
            return;
        }

        sb.append("<section class=\"kantara-metadata\">\n");
        sb.append("<h3>Metadata</h3>\n");
        sb.append("<dl>\n");
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            sb.append("<dt>").append(escapeHtml(asString(entry.getKey()))).append("</dt>\n");
            sb.append("<dd>").append(escapeHtml(asString(entry.getValue()))).append("</dd>\n");
        }
        sb.append("</dl>\n");
        sb.append("</section>\n");
    }

    private void formatSections(StringBuilder sb, Object sectionsValue) {
        if (!(sectionsValue instanceof List<?> sections) || sections.isEmpty()) {
            return;
        }

        sb.append("<section class=\"kantara-sections\">\n");
        sb.append("<h3>Sections</h3>\n");
        for (Object sectionValue : sections) {
            if (!(sectionValue instanceof Map<?, ?> section)) {
                continue;
            }

            String title = asString(section.get("title"));
            String text = asString(section.get("text"));
            String pageStart = asString(section.get("page_start"));
            String slideNumber = asString(section.get("slide_number"));

            sb.append("<section class=\"kantara-section\">\n");
            if (!title.isBlank()) {
                sb.append("<h4>").append(escapeHtml(title)).append("</h4>\n");
            }
            if (!slideNumber.isBlank()) {
                sb.append("<p><em>Slide ").append(escapeHtml(slideNumber)).append("</em></p>\n");
            } else if (!pageStart.isBlank()) {
                sb.append("<p><em>Page ").append(escapeHtml(pageStart)).append("</em></p>\n");
            }
            if (!text.isBlank()) {
                sb.append("<p>").append(escapeHtml(text).replace("\n", "<br>")).append("</p>\n");
            }
            sb.append("</section>\n");
        }
        sb.append("</section>\n");
    }

    private void formatTables(StringBuilder sb, Object tablesValue) {
        if (!(tablesValue instanceof List<?> tables) || tables.isEmpty()) {
            return;
        }

        sb.append("<section class=\"kantara-tables\">\n");
        sb.append("<h3>Tables</h3>\n");
        for (Object tableValue : tables) {
            if (!(tableValue instanceof Map<?, ?> table)) {
                continue;
            }

            String name = asString(table.get("name"));
            if (!name.isBlank()) {
                sb.append("<h4>").append(escapeHtml(name)).append("</h4>\n");
            }

            if (table.get("data") instanceof List<?> dataRows) {
                formatStructuredRows(sb, table.get("headers"), dataRows);
            } else if (table.get("rows") instanceof List<?> pdfRows) {
                formatPdfRows(sb, pdfRows);
            }
        }
        sb.append("</section>\n");
    }

    private void formatStructuredRows(StringBuilder sb, Object headersValue, List<?> rows) {
        if (!(headersValue instanceof List<?> headers) || headers.isEmpty()) {
            sb.append("<p><em>No data</em></p>\n");
            return;
        }

        sb.append("<table>\n<thead>\n<tr><th>Row</th>");
        for (Object header : headers) {
            sb.append("<th>").append(escapeHtml(asString(header))).append("</th>");
        }
        sb.append("</tr>\n</thead>\n<tbody>\n");

        for (Object rowValue : rows) {
            if (!(rowValue instanceof Map<?, ?> row)) {
                continue;
            }
            sb.append("<tr><td>").append(escapeHtml(asString(row.get("row_number")))).append("</td>");
            Object values = row.get("values");
            for (Object header : headers) {
                String cell = "";
                if (values instanceof Map<?, ?> valueMap) {
                    cell = asString(valueMap.get(header));
                }
                sb.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n");
    }

    private void formatPdfRows(StringBuilder sb, List<?> rows) {
        sb.append("<ul>\n");
        for (Object row : rows) {
            if (row instanceof List<?> cells) {
                String joined = String.join(" | ", cells.stream().map(this::asString).toList());
                sb.append("<li>").append(escapeHtml(joined)).append("</li>\n");
            }
        }
        sb.append("</ul>\n");
    }

    private void formatChunks(StringBuilder sb, Object chunksValue) {
        if (!(chunksValue instanceof List<?> chunks) || chunks.isEmpty()) {
            return;
        }

        sb.append("<section class=\"kantara-chunks\">\n");
        sb.append("<h2>LLM Chunks</h2>\n");
        for (Object chunkValue : chunks) {
            if (!(chunkValue instanceof Map<?, ?> chunk)) {
                continue;
            }

            sb.append("<section class=\"kantara-chunk\">\n");
            sb.append("<h3>").append(escapeHtml(asString(chunk.get("id")))).append("</h3>\n");
            sb.append("<p><em>~").append(escapeHtml(asString(chunk.get("token_estimate")))).append(" tokens");
            Object sources = chunk.get("sources");
            if (sources instanceof List<?> sourceList && !sourceList.isEmpty()) {
                sb.append(" | ").append(escapeHtml(String.join(", ", sourceList.stream().map(this::asString).toList())));
            }
            sb.append("</em></p>\n");
            sb.append("<pre>").append(escapeHtml(asString(chunk.get("text")))).append("</pre>\n");
            sb.append("</section>\n");
        }
        sb.append("</section>\n");
    }

    private void formatErrors(StringBuilder sb, Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors) || errors.isEmpty()) {
            return;
        }

        sb.append("<section class=\"kantara-errors\">\n");
        sb.append("<h2>Errors</h2>\n");
        sb.append("<ul>\n");
        for (Object errorValue : errors) {
            if (errorValue instanceof Map<?, ?> error) {
                sb.append("<li><strong>")
                        .append(escapeHtml(asString(error.get("filename"))))
                        .append(":</strong> ")
                        .append(escapeHtml(asString(error.get("error"))))
                        .append("</li>\n");
            }
        }
        sb.append("</ul>\n");
        sb.append("</section>\n");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String formatName() {
        return "html";
    }
}
