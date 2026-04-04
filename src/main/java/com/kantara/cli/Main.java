package com.kantara.cli;

import com.kantara.ai.PayloadBuilder;
import com.kantara.extractor.DataExtractor;
import com.kantara.extractor.PdfExtractor;

import java.util.List;
import java.util.Map;

/**
 * CLI Entry point to test the full data pipeline from extraction to AI payload.
 */
public class Main {

    public static void main(String[] args) {
        String pdfPath = "data/report.pdf";
        String excelPath = "data/actors.xlsx";

        DataExtractor excelExtractor = new DataExtractor();
        PdfExtractor pdfExtractor = new PdfExtractor();
        PayloadBuilder payloadBuilder = new PayloadBuilder();

        System.out.println("--- Starting Data Pipeline Test ---");

        try {
            // 1. Extract Excel Data
            System.out.println("Extracting data from: " + excelPath);
            List<Map<String, String>> excelData = excelExtractor.extract(excelPath);
            System.out.println("Excel rows extracted: " + excelData.size());

            // 2. Extract PDF Sections
            System.out.println("Extracting sections from: " + pdfPath);
            String cleanedText = pdfExtractor.extractText(pdfPath);
            List<String> pdfSections = pdfExtractor.extractSections(cleanedText);
            System.out.println("PDF sections extracted: " + pdfSections.size());

            // 3. Build AI Payload
            System.out.println("Building AI Payload...");
            Map<String, Object> payload = payloadBuilder.buildPayload(excelData, pdfSections);

            // 4. Results
            System.out.println("\n--- GENERATED AI PAYLOAD ---");
            printPayload(payload, "");

        } catch (Exception e) {
            System.err.println("Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printPayload(Map<?, ?> map, String indent) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                System.out.println(indent + entry.getKey() + ":");
                printPayload((Map<?, ?>) value, indent + "  ");
            } else if (value instanceof List) {
                System.out.println(indent + entry.getKey() + ":");
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        printPayload((Map<?, ?>) item, indent + "    - ");
                    } else {
                        System.out.println(indent + "    - " + item);
                    }
                }
            } else {
                System.out.println(indent + entry.getKey() + ": " + value);
            }
        }
    }
}
