package com.kantara.extractor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured data from an Excel (.xlsx) file.
 */
public class ExcelExtractor {

    /**
     * Reads an Excel file and extracts its content as a list of maps.
     * Each map represents a row, where keys are column headers and values are cell contents.
     *
     * @param filePath The absolute or relative path to the .xlsx file
     * @return A list of rows represented as key-value pairs
     */
    public List<Map<String, String>> extract(String filePath) {
        List<Map<String, String>> result = new ArrayList<>();

        try (InputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

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
                    continue; // Skip entirely empty rows
                }

                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String cellValue = getCellValueAsString(cell).trim();
                    
                    if (!cellValue.isEmpty()) {
                        hasData = true;
                    }
                    
                    // Fallback key if the header is missing/blank
                    String header = headers.get(colIndex);
                    String key = header.isEmpty() ? "Column" + (colIndex + 1) : header;
                    rowData.put(key, cellValue);
                }

                // Only add the row if it wasn't completely empty
                if (hasData) {
                    result.add(rowData);
                }
            }

        } catch (IOException e) {
            // Re-throw as unchecked exception per requirements to include proper exception handling
            // without needing try-catch in every method, or we could handle it via a custom business exception.
            throw new RuntimeException("Error reading Excel file: " + filePath, e);
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
                    // Check if the number is essentially an integer
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
