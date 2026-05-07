package com.kantara.extractor;

import com.kantara.exception.ExtractionException;
import com.kantara.exception.ValidationException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts text sections and real table objects from modern Office documents.
 */
public class OfficeDocumentExtractor {

    private static final int DOCX_PARAGRAPHS_PER_SECTION = 8;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f ]+");

    public Map<String, Object> extractDocument(InputStream inputStream, String fileName) {
        if (inputStream == null || fileName == null || fileName.isEmpty()) {
            throw new ValidationException("Stream and file name cannot be null or empty");
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) {
            return extractDocx(inputStream, fileName);
        }
        if (lowerName.endsWith(".pptx")) {
            return extractPptx(inputStream, fileName);
        }
        throw new ExtractionException("Unsupported file format: " + fileName);
    }

    private Map<String, Object> extractDocx(InputStream inputStream, String fileName) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<Map<String, Object>> sections = extractDocxSections(document.getParagraphs());
            List<Map<String, Object>> tables = extractDocxTables(document.getTables());
            Map<String, Object> metadata = Map.of(
                    "paragraph_count", countNonBlankParagraphs(document.getParagraphs())
            );
            return documentMap(fileName, "docx", sections, tables, metadata);
        } catch (IOException e) {
            throw new ExtractionException("Error reading DOCX file: " + fileName, e);
        }
    }

    private List<Map<String, Object>> extractDocxSections(List<XWPFParagraph> paragraphs) {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<String> body = new ArrayList<>();
        String currentTitle = "";
        int paragraphIndex = 0;
        int sectionStart = 0;
        int sectionEnd = 0;

        for (XWPFParagraph paragraph : paragraphs) {
            String text = cleanText(paragraph.getText());
            if (text.isBlank()) {
                continue;
            }

            paragraphIndex++;
            if (isHeading(paragraph, text)) {
                flushDocxSection(sections, currentTitle, body, sectionStart, sectionEnd);
                body.clear();
                currentTitle = text;
                sectionStart = paragraphIndex;
                sectionEnd = paragraphIndex;
                continue;
            }

            if (body.isEmpty()) {
                sectionStart = paragraphIndex;
            }
            body.add(text);
            sectionEnd = paragraphIndex;

            if (body.size() >= DOCX_PARAGRAPHS_PER_SECTION) {
                flushDocxSection(sections, currentTitle, body, sectionStart, sectionEnd);
                body.clear();
                sectionStart = 0;
                sectionEnd = 0;
            }
        }

        flushDocxSection(sections, currentTitle, body, sectionStart, sectionEnd);
        return sections;
    }

    private void flushDocxSection(
            List<Map<String, Object>> sections,
            String title,
            List<String> body,
            int paragraphStart,
            int paragraphEnd
    ) {
        String text = String.join("\n\n", body).trim();
        String sectionTitle = title == null ? "" : title.trim();
        if (text.isBlank() && sectionTitle.isBlank()) {
            return;
        }

        if (text.isBlank()) {
            text = sectionTitle;
            sectionTitle = "";
        }

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", sectionTitle);
        section.put("paragraph_start", paragraphStart == 0 ? null : paragraphStart);
        section.put("paragraph_end", paragraphEnd == 0 ? null : paragraphEnd);
        section.put("token_estimate", estimateTokens(text));
        section.put("text", text);
        sections.add(section);
    }

    private List<Map<String, Object>> extractDocxTables(List<XWPFTable> documentTables) {
        List<Map<String, Object>> tables = new ArrayList<>();
        int tableIndex = 1;

        for (XWPFTable table : documentTables) {
            List<List<String>> rows = new ArrayList<>();
            for (XWPFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XWPFTableCell cell : row.getTableCells()) {
                    cells.add(cleanText(cell.getText()));
                }
                rows.add(cells);
            }

            Map<String, Object> structuredTable = structuredTable(
                    "DOCX table " + tableIndex,
                    rows,
                    Map.of("table_index", tableIndex)
            );
            if (structuredTable != null) {
                tables.add(structuredTable);
                tableIndex++;
            }
        }

        return tables;
    }

    private Map<String, Object> extractPptx(InputStream inputStream, String fileName) {
        try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            List<Map<String, Object>> sections = new ArrayList<>();
            List<Map<String, Object>> tables = new ArrayList<>();
            List<XSLFSlide> slides = slideShow.getSlides();

            for (int slideIndex = 0; slideIndex < slides.size(); slideIndex++) {
                int slideNumber = slideIndex + 1;
                XSLFSlide slide = slides.get(slideIndex);
                String slideText = extractSlideText(slide);
                if (!slideText.isBlank()) {
                    sections.add(slideSection(slideNumber, slideText));
                }
                extractPptxTables(slide, slideNumber, tables);
            }

            return documentMap(fileName, "pptx", sections, tables, Map.of("slide_count", slides.size()));
        } catch (IOException e) {
            throw new ExtractionException("Error reading PPTX file: " + fileName, e);
        }
    }

    private String extractSlideText(XSLFSlide slide) {
        StringBuilder text = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            appendTextShape(shape, text);
        }
        return text.toString().trim();
    }

    private void appendTextShape(XSLFShape shape, StringBuilder text) {
        if (shape instanceof XSLFTable) {
            return;
        }

        if (shape instanceof XSLFTextShape textShape) {
            appendBlock(text, cleanText(textShape.getText()));
        } else if (shape instanceof XSLFGroupShape groupShape) {
            for (XSLFShape child : groupShape.getShapes()) {
                appendTextShape(child, text);
            }
        }
    }

    private void extractPptxTables(XSLFSlide slide, int slideNumber, List<Map<String, Object>> tables) {
        int[] tableIndex = {tables.size() + 1};
        for (XSLFShape shape : slide.getShapes()) {
            collectPptxTables(shape, slideNumber, tables, tableIndex);
        }
    }

    private void collectPptxTables(
            XSLFShape shape,
            int slideNumber,
            List<Map<String, Object>> tables,
            int[] tableIndex
    ) {
        if (shape instanceof XSLFTable table) {
            List<List<String>> rows = new ArrayList<>();
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) {
                    cells.add(cleanText(cell.getText()));
                }
                rows.add(cells);
            }

            int index = tableIndex[0]++;
            Map<String, Object> structuredTable = structuredTable(
                    "PPTX table s" + slideNumber + "." + index,
                    rows,
                    Map.of("slide_number", slideNumber, "table_index", index)
            );
            if (structuredTable != null) {
                tables.add(structuredTable);
            }
        } else if (shape instanceof XSLFGroupShape groupShape) {
            for (XSLFShape child : groupShape.getShapes()) {
                collectPptxTables(child, slideNumber, tables, tableIndex);
            }
        }
    }

    private Map<String, Object> slideSection(int slideNumber, String text) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "Slide " + slideNumber);
        section.put("slide_number", slideNumber);
        section.put("page_start", slideNumber);
        section.put("page_end", slideNumber);
        section.put("token_estimate", estimateTokens(text));
        section.put("text", text);
        return section;
    }

    private Map<String, Object> structuredTable(
            String name,
            List<List<String>> rawRows,
            Map<String, Object> extraFields
    ) {
        List<List<String>> rows = normalizeRows(rawRows);
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columnCount == 0 || rows.isEmpty()) {
            return null;
        }

        boolean hasHeader = rows.size() > 1;
        List<String> headers = hasHeader ? headersFromRow(rows.get(0), columnCount) : defaultHeaders(columnCount);
        int dataStartIndex = hasHeader ? 1 : 0;
        List<Map<String, Object>> dataRows = new ArrayList<>();

        for (int rowIndex = dataStartIndex; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<String, String> values = new LinkedHashMap<>();
            boolean hasData = false;
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String value = columnIndex < row.size() ? row.get(columnIndex) : "";
                if (!value.isBlank()) {
                    hasData = true;
                }
                values.put(headers.get(columnIndex), value);
            }

            if (hasData) {
                Map<String, Object> rowData = new LinkedHashMap<>();
                rowData.put("row_number", rowIndex + 1);
                rowData.put("values", values);
                dataRows.add(rowData);
            }
        }

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", name);
        table.putAll(extraFields);
        table.put("header_row", hasHeader ? 1 : null);
        table.put("row_count", dataRows.size());
        table.put("column_count", headers.size());
        table.put("headers", headers);
        table.put("data", dataRows);
        return table;
    }

    private List<List<String>> normalizeRows(List<List<String>> rawRows) {
        List<List<String>> rows = new ArrayList<>();
        for (List<String> rawRow : rawRows) {
            List<String> row = rawRow.stream()
                    .map(this::cleanText)
                    .toList();
            if (row.stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> headersFromRow(List<String> row, int columnCount) {
        List<String> headers = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String header = columnIndex < row.size() ? row.get(columnIndex).trim() : "";
            headers.add(uniqueHeader(headers, header.isBlank() ? "Column" + (columnIndex + 1) : header));
        }
        return headers;
    }

    private List<String> defaultHeaders(int columnCount) {
        List<String> headers = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            headers.add("Column" + (columnIndex + 1));
        }
        return headers;
    }

    private boolean isHeading(XWPFParagraph paragraph, String text) {
        String style = paragraph.getStyle();
        if (style != null && style.toLowerCase(Locale.ROOT).contains("heading")) {
            return true;
        }

        return text.length() <= 100
                && (text.matches("^\\d+(\\.\\d+)*\\s+\\S.*")
                || (text.equals(text.toUpperCase(Locale.ROOT)) && text.matches(".*[A-Z].*")));
    }

    private int countNonBlankParagraphs(List<XWPFParagraph> paragraphs) {
        int count = 0;
        for (XWPFParagraph paragraph : paragraphs) {
            if (!cleanText(paragraph.getText()).isBlank()) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> documentMap(
            String fileName,
            String sourceType,
            List<Map<String, Object>> sections,
            List<Map<String, Object>> tables,
            Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", fileName);
        metadata.put("source_type", sourceType);
        metadata.put("section_count", sections.size());
        metadata.put("table_count", tables.size());
        metadata.put("extracted_at", Instant.now().toString());
        metadata.putAll(extraMetadata);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metadata", metadata);
        result.put("sections", sections);
        result.put("tables", tables);
        return result;
    }

    private void appendBlock(StringBuilder builder, String text) {
        if (text.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text);
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");
        StringBuilder cleaned = new StringBuilder(normalized.length());
        for (String line : lines) {
            String compact = WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (compact.isBlank()) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(compact);
        }
        return cleaned.toString();
    }

    private String uniqueHeader(List<String> existing, String header) {
        if (!existing.contains(header)) {
            return header;
        }

        int suffix = 2;
        while (existing.contains(header + "_" + suffix)) {
            suffix++;
        }
        return header + "_" + suffix;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
