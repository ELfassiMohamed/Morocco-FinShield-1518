package com.kantara.extractor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kantara.exception.*;

/**
 * Extracts structured data from multiple file formats (.xlsx, .csv).
 */
public class DataExtractor implements Extractable {

    /**
     * Reads a file and extracts its content as a list of maps.
     * Automatically detects file format based on extension.
     *
     * @param filePath The absolute or relative path to the file
     * @return A list of rows represented as key-value pairs
     */
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
        if (inputStream == null || fileName == null || fileName.isEmpty()) {
            throw new ValidationException("Stream and file name cannot be null or empty");
        }

        String lowerPath = fileName.toLowerCase();
        if (lowerPath.endsWith(".xlsx")) {
            return extractExcel(inputStream, fileName);
        } else if (lowerPath.endsWith(".csv")) {
            return extractCsv(inputStream, fileName);
        } else {
            throw new ExtractionException("Unsupported file format: " + fileName);
        }
    }

    /**
     * Logic for extracting data from Excel (.xlsx) files.
     */
    private List<Map<String, String>> extractExcel(InputStream fis, String fileName) {
        List<Map<String, String>> result = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return result;
            }

            // Extract headers from the first row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return result;
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                headers.add(getCellValueAsString(cell).trim());
            }

            // Extract data starting from the second row
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue; 
                }

                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String cellValue = getCellValueAsString(cell).trim();
                    
                    if (!cellValue.isEmpty()) {
                        hasData = true;
                    }
                    
                    String header = headers.get(colIndex);
                    String key = header.isEmpty() ? "Column" + (colIndex + 1) : header;
                    rowData.put(key, cellValue);
                }

                if (hasData) {
                    result.add(rowData);
                }
            }

        } catch (IOException e) {
            throw new ExtractionException("Error reading Excel file: " + fileName, e);
        }

        return result;
    }

    /**
     * Logic for extracting data from CSV files.
     */
    private List<Map<String, String>> extractCsv(InputStream is, String fileName) {
        List<Map<String, String>> result = new ArrayList<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null) {
                return result;
            }

            List<String> headers = csvParser.getHeaderNames();

            for (CSVRecord record : csvParser) {
                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (String header : headers) {
                    String value = record.get(header).trim();
                    if (!value.isEmpty()) {
                        hasData = true;
                    }
                    rowData.put(header, value);
                }

                if (hasData) {
                    result.add(rowData);
                }
            }

        } catch (IOException e) {
            throw new ExtractionException("Error reading CSV file: " + fileName, e);
        }

        return result;
    }

    /**
     * Safely converts different cell types to a String representation.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    long longValue = (long) numericValue;
                    if (numericValue == longValue) {
                        return String.valueOf(longValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            case ERROR:
            default:
                return "";
        }
    }
}
