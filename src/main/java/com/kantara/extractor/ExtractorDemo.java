package com.kantara.extractor;

import java.util.List;
import java.util.Map;

/**
 * Example Main class showing how to use the ExcelExtractor.
 */
public class ExtractorDemo {

    public static void main(String[] args) {
        // Path to your Excel file (ensure this file exists when running the demo)
        String filePath = "sample-data.xlsx"; 

        DataExtractor extractor = new DataExtractor();

        try {
            System.out.println("Starting extraction from: " + filePath);
            List<Map<String, String>> extractedData = extractor.extract(filePath);
            
            if (extractedData.isEmpty()) {
                System.out.println("No data found or file is empty.");
                return;
            }

            System.out.println("--- Extracted Data (" + extractedData.size() + " rows) ---");
            for (int i = 0; i < extractedData.size(); i++) {
                Map<String, String> row = extractedData.get(i);
                System.out.printf("Row %d: %s%n", i + 1, row);
            }
            
        } catch (Exception e) {
            System.err.println("Extraction failed! Error: " + e.getMessage());
        }
    }
}
