package com.kantara;

import com.kantara.ai.PayloadBuilder;
import java.util.*;

/**
 * Main application for demonstration.
 */
public class Main {

    public static void main(String[] args) {
        PayloadBuilder builder = new PayloadBuilder();

        // 1. Mock Excel Data
        List<Map<String, String>> excelData = Arrays.asList(
            Map.of("region", "North", "sales", "12500", "product", "Electronics"),
            Map.of("region", "South", "sales", "8400.50", "product", "Home Appliance"),
            Map.of("region", "North", "sales", "5000", "product", "Electronics"),
            Map.of("region", "West", "sales", "11000", "product", "Furniture"),
            Map.of("region", "North", "sales", "7200", "product", "Office Supplies")
        );

        // 2. Mock PDF Sections
        List<String> pdfSections = Arrays.asList(
            "   Executive Summary  ",
            "This report analyzes sales performance for Q1 and Q2.   ",
            "\n" +
            "Infrastructure and Digital Transformation findings suggest strong growth potential in Northern regions.\n"
        );

        // 3. Build Payload
        Map<String, Object> payload = builder.buildPayload(excelData, pdfSections);

        // 4. Output Results (Simple map print for demo)
        System.out.println("--- Generated AI Payload ---");
        printMap(payload, "");
    }

    /**
     * Minimal utility to print map hierarchically for readability.
     */
    private static void printMap(Map<?, ?> map, String indent) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                System.out.println(indent + entry.getKey() + ":");
                printMap((Map<?, ?>) value, indent + "  ");
            } else if (value instanceof List) {
                System.out.println(indent + entry.getKey() + ":");
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        printMap((Map<?, ?>) item, indent + "    - ");
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
