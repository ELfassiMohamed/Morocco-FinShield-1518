package com.kantara.cli;

import com.kantara.extractor.DataExtractor;
import java.util.List;
import java.util.Map;

public class Main {

	public static void main(String[] args) {
		System.out.println("Kantara MVP started...");

		// 1. Specify the path to your Excel file
		String filePath = "data/actors.csv";

		try {
			// 2. Instantiate the extractor
			DataExtractor extractor = new DataExtractor();

			// 3. Call the extract method
			System.out.println("Starting extraction from: " + filePath);
			List<Map<String, String>> extractedData = extractor.extract(filePath);

			// 4. Output/process the results
			if (extractedData.isEmpty()) {
				System.out.println("No data found or file is empty.");
			} else {
				System.out.println("--- Extracted Data (" + extractedData.size() + " rows) ---");
				for (int i = 0; i < extractedData.size(); i++) {
					Map<String, String> row = extractedData.get(i);
					System.out.printf("Row %d: %s%n", i + 1, row);
				}
			}
		} catch (Exception e) {
			System.err.println("Extraction failed! Error: " + e.getMessage());
		}
	}

}
