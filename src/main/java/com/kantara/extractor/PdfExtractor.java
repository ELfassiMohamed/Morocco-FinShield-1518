package com.kantara.extractor;

import com.kantara.exception.ExtractionException;
import com.kantara.exception.ValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts page-aware text, sections, and table-like blocks from PDF documents.
 */
public class PdfExtractor {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f ]+");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("\\s{2,}|\\|");
    private static final Pattern KEYWORD_HEADING_PATTERN =
            Pattern.compile(".*\\b(SUMMARY|ABSTRACT|INTRODUCTION|REVENUE|RESULTS|CONCLUSION|APPENDIX)\\b.*");

    public String extractText(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new ValidationException("filePath must not be null or blank.");
        }

        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new ValidationException("PDF file not found: " + filePath);
        }

        try {
            return extractText(new FileInputStream(pdfFile));
        } catch (FileNotFoundException e) {
            throw new ExtractionException("PDF file not found: " + filePath, e);
        }
    }

    public String extractText(InputStream stream) {
        return asString(extract(stream, "document.pdf").get("raw_text"));
    }

    public Map<String, Object> extract(InputStream stream, String fileName) {
        if (stream == null) {
            throw new ValidationException("InputStream must not be null.");
        }

        try (PDDocument document = Loader.loadPDF(stream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<Map<String, Object>> pages = new ArrayList<>();
            List<Map<String, Object>> sections = new ArrayList<>();
            List<Map<String, Object>> tables = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String pageText = cleanText(stripper.getText(document));
                if (!pageText.isBlank()) {
                    if (rawText.length() > 0) {
                        rawText.append("\n\n");
                    }
                    rawText.append(pageText);
                }

                Map<String, Object> page = new LinkedHashMap<>();
                page.put("page_number", pageNumber);
                page.put("token_estimate", estimateTokens(pageText));
                page.put("text", pageText);
                pages.add(page);

                sections.addAll(extractSectionsForPage(pageText, pageNumber));
                tables.addAll(extractTableLikeBlocks(pageText, pageNumber));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("metadata", pdfMetadata(document, fileName));
            result.put("pages", pages);
            result.put("sections", sections);
            result.put("tables", tables);
            result.put("raw_text", rawText.toString());
            return result;
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract PDF content from stream.", e);
        }
    }

    public List<String> extractSections(String text) {
        List<String> legacySections = new ArrayList<>();
        for (Map<String, Object> section : extractSectionsForPage(cleanText(text), 1)) {
            String title = asString(section.get("title"));
            String body = asString(section.get("text"));
            legacySections.add((title.isBlank() ? "" : title + "\n") + body);
        }
        return legacySections;
    }

    private List<Map<String, Object>> extractSectionsForPage(String text, int pageNumber) {
        List<Map<String, Object>> sections = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sections;
        }

        String[] lines = text.split("\n");
        String title = "";
        int confidence = 0;
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int headingConfidence = headingConfidence(trimmed);
            if (headingConfidence > 0 && body.length() > 0) {
                sections.add(sectionMap(title, body.toString(), pageNumber, confidence));
                body.setLength(0);
            }

            if (headingConfidence > 0) {
                title = trimmed;
                confidence = headingConfidence;
            } else {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(trimmed);
            }
        }

        if (!title.isBlank() || body.length() > 0) {
            sections.add(sectionMap(title, body.toString(), pageNumber, confidence));
        }

        return sections;
    }

    private Map<String, Object> sectionMap(String title, String text, int pageNumber, int confidence) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("page_start", pageNumber);
        section.put("page_end", pageNumber);
        section.put("heading_confidence", confidence);
        section.put("token_estimate", estimateTokens(text));
        section.put("text", text.trim());
        return section;
    }

    private List<Map<String, Object>> extractTableLikeBlocks(String text, int pageNumber) {
        List<Map<String, Object>> tables = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (looksLikeTableRow(trimmed)) {
                rows.add(splitTableRow(trimmed));
            } else if (rows.size() >= 2) {
                tables.add(tableMap(pageNumber, rows));
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
            }
        }

        if (rows.size() >= 2) {
            tables.add(tableMap(pageNumber, rows));
        }

        return tables;
    }

    private Map<String, Object> tableMap(int pageNumber, List<List<String>> rows) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", "PDF table candidate p" + pageNumber);
        table.put("page", pageNumber);
        table.put("confidence", rows.size() >= 4 ? "medium" : "low");
        table.put("rows", rows);
        return table;
    }

    private boolean looksLikeTableRow(String line) {
        if (line.length() < 6) {
            return false;
        }
        String[] cells = TABLE_SEPARATOR_PATTERN.split(line);
        return cells.length >= 3;
    }

    private List<String> splitTableRow(String line) {
        List<String> cells = new ArrayList<>();
        for (String cell : TABLE_SEPARATOR_PATTERN.split(line)) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty()) {
                cells.add(trimmed);
            }
        }
        return cells;
    }

    private Map<String, Object> pdfMetadata(PDDocument document, String fileName) {
        PDDocumentInformation info = document.getDocumentInformation();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", fileName);
        metadata.put("source_type", "pdf");
        metadata.put("page_count", document.getNumberOfPages());
        putIfPresent(metadata, "title", info.getTitle());
        putIfPresent(metadata, "author", info.getAuthor());
        putIfPresent(metadata, "subject", info.getSubject());
        putIfPresent(metadata, "keywords", info.getKeywords());
        putIfPresent(metadata, "created_at", calendarToInstant(info.getCreationDate()));
        putIfPresent(metadata, "modified_at", calendarToInstant(info.getModificationDate()));
        metadata.put("extracted_at", Instant.now().toString());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            metadata.put(key, value);
        }
    }

    private String calendarToInstant(Calendar calendar) {
        return calendar == null ? null : calendar.toInstant().toString();
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalizedLineBreaks = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedLineBreaks.split("\n");
        StringBuilder cleaned = new StringBuilder(normalizedLineBreaks.length());

        for (String line : lines) {
            String compact = WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (compact.isEmpty()) {
                continue;
            }

            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(compact);
        }

        return cleaned.toString();
    }

    private int headingConfidence(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (KEYWORD_HEADING_PATTERN.matcher(upper).matches()) {
            return 85;
        }

        if (line.length() >= 3
                && line.length() <= 100
                && line.matches(".*[A-Z].*")
                && line.equals(upper)
                && line.matches("[A-Z0-9\\s&:.,()/%\\-]+")) {
            return 75;
        }

        if (line.length() <= 80 && line.matches("^\\d+(\\.\\d+)*\\s+\\S.*")) {
            return 65;
        }

        return 0;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
