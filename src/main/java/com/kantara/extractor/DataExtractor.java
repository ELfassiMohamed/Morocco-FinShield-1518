package com.kantara.extractor;

import com.kantara.exception.ExtractionException;
import com.kantara.exception.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured table data from CSV files.
 */
public class DataExtractor {

    public List<Map<String, String>> extract(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new ValidationException("File path cannot be null or empty");
        }

        try {
            return extract(new FileInputStream(filePath), filePath);
        } catch (FileNotFoundException e) {
            throw new ExtractionException("File not found: " + filePath, e);
        }
    }

    public List<Map<String, String>> extract(InputStream inputStream, String fileName) {
        Map<String, Object> document = extractDocument(inputStream, fileName);
        List<Map<String, Object>> tables = castTables(document.get("tables"));
        if (tables.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = castRows(tables.get(0).get("data"));
        List<Map<String, String>> legacyRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> legacyRow = new LinkedHashMap<>();
            Object values = row.get("values");
            if (values instanceof Map<?, ?> valueMap) {
                for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                    legacyRow.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            legacyRows.add(legacyRow);
        }
        return legacyRows;
    }

    public Map<String, Object> extractDocument(InputStream inputStream, String fileName) {
        if (inputStream == null || fileName == null || fileName.isEmpty()) {
            throw new ValidationException("Stream and file name cannot be null or empty");
        }

        if (!fileName.toLowerCase().endsWith(".csv")) {
            throw new ExtractionException("Unsupported file format: " + fileName);
        }

        return extractCsv(inputStream, fileName);
    }

    private Map<String, Object> extractCsv(InputStream inputStream, String fileName) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            char delimiter = detectDelimiter(new String(bytes, StandardCharsets.UTF_8));
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(false)
                    .setTrim(true)
                    .build();

            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> headers;
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
                 CSVParser csvParser = new CSVParser(reader, csvFormat)) {
                headers = csvParser.getHeaderNames().stream()
                        .map(String::trim)
                        .map(header -> header.isEmpty() ? "Column" : header)
                        .toList();

                for (CSVRecord record : csvParser) {
                    Map<String, String> values = new LinkedHashMap<>();
                    boolean hasData = false;
                    for (String header : headers) {
                        String value = record.isMapped(header) ? record.get(header).trim() : "";
                        if (!value.isEmpty()) {
                            hasData = true;
                        }
                        values.put(header, value);
                    }

                    if (hasData) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("row_number", record.getRecordNumber() + 1);
                        row.put("values", values);
                        rows.add(row);
                    }
                }
            }

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("name", "CSV");
            table.put("delimiter", String.valueOf(delimiter));
            table.put("header_row", 1);
            table.put("row_count", rows.size());
            table.put("column_count", headers.size());
            table.put("headers", headers);
            table.put("data", rows);
            return documentMap(fileName, "csv", List.of(table), Map.of("delimiter", String.valueOf(delimiter)));
        } catch (IOException e) {
            throw new ExtractionException("Error reading CSV file: " + fileName, e);
        }
    }

    private char detectDelimiter(String sample) {
        char[] candidates = {',', ';', '\t', '|'};
        char best = ',';
        int bestScore = -1;
        String[] lines = sample.lines().limit(10).toArray(String[]::new);

        for (char candidate : candidates) {
            int score = 0;
            int populatedLines = 0;
            for (String line : lines) {
                int count = 0;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == candidate) {
                        count++;
                    }
                }
                if (count > 0) {
                    populatedLines++;
                    score += count;
                }
            }

            score += populatedLines * 3;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private Map<String, Object> documentMap(
            String fileName,
            String sourceType,
            List<Map<String, Object>> tables,
            Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", fileName);
        metadata.put("source_type", sourceType);
        metadata.put("table_count", tables.size());
        metadata.put("extracted_at", Instant.now().toString());
        metadata.putAll(extraMetadata);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metadata", metadata);
        result.put("tables", tables);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castTables(Object value) {
        if (value instanceof List<?>) {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRows(Object value) {
        if (value instanceof List<?>) {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }
}
