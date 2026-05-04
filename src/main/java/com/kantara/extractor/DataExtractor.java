package com.kantara.extractor;

import com.kantara.exception.ExtractionException;
import com.kantara.exception.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

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
 * Extracts structured table data from XLSX and CSV files.
 */
public class DataExtractor {

    private final org.apache.poi.ss.usermodel.DataFormatter cellFormatter =
            new org.apache.poi.ss.usermodel.DataFormatter();

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

        String lowerPath = fileName.toLowerCase();
        if (lowerPath.endsWith(".xlsx")) {
            return extractExcel(inputStream, fileName);
        } else if (lowerPath.endsWith(".csv")) {
            return extractCsv(inputStream, fileName);
        }
        throw new ExtractionException("Unsupported file format: " + fileName);
    }

    private Map<String, Object> extractExcel(InputStream inputStream, String fileName) {
        List<Map<String, Object>> tables = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }

                Map<String, Object> table = extractSheet(sheet, sheetIndex, evaluator);
                if (!castRows(table.get("data")).isEmpty()) {
                    tables.add(table);
                }
            }
        } catch (IOException e) {
            throw new ExtractionException("Error reading Excel file: " + fileName, e);
        }

        return documentMap(fileName, "xlsx", tables, Map.of("sheet_count", tables.size()));
    }

    private Map<String, Object> extractSheet(Sheet sheet, int sheetIndex, FormulaEvaluator evaluator) {
        Row headerRow = firstNonEmptyRow(sheet);
        List<String> headers = headersFromRow(headerRow, evaluator);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (headerRow != null) {
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Map<String, Object> rowData = rowMap(rowIndex + 1, headers, row, evaluator);
                if (Boolean.TRUE.equals(rowData.get("has_data"))) {
                    rowData.remove("has_data");
                    rows.add(rowData);
                }
            }
        }

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", sheet.getSheetName());
        table.put("sheet_index", sheetIndex);
        table.put("header_row", headerRow == null ? null : headerRow.getRowNum() + 1);
        table.put("row_count", rows.size());
        table.put("column_count", headers.size());
        table.put("headers", headers);
        table.put("data", rows);
        return table;
    }

    private Row firstNonEmptyRow(Sheet sheet) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && !cellFormatter.formatCellValue(cell).trim().isEmpty()) {
                    return row;
                }
            }
        }
        return null;
    }

    private List<String> headersFromRow(Row headerRow, FormulaEvaluator evaluator) {
        List<String> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }

        for (int colIndex = 0; colIndex < headerRow.getLastCellNum(); colIndex++) {
            Cell cell = headerRow.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String header = cellFormatter.formatCellValue(cell, evaluator).trim();
            headers.add(header.isEmpty() ? "Column" + (colIndex + 1) : uniqueHeader(headers, header));
        }
        return headers;
    }

    private Map<String, Object> rowMap(
            int rowNumber,
            List<String> headers,
            Row row,
            FormulaEvaluator evaluator
    ) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        boolean hasData = false;

        for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
            Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String cellValue = cellFormatter.formatCellValue(cell, evaluator).trim();
            if (!cellValue.isEmpty()) {
                hasData = true;
            }
            values.put(headers.get(colIndex), cellValue);
        }

        rowData.put("row_number", rowNumber);
        rowData.put("values", values);
        rowData.put("has_data", hasData);
        return rowData;
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
