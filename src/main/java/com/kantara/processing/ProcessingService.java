package com.kantara.processing;

import com.kantara.exception.ExtractionException;
import com.kantara.extractor.DataExtractor;
import com.kantara.extractor.Extractable;
import com.kantara.extractor.PdfExtractor;
import com.kantara.extractor.TextExtractable;
import com.kantara.formatter.JsonFormatter;
import com.kantara.formatter.MarkdownFormatter;
import com.kantara.formatter.OutputFormatter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessingService {

    private final Extractable dataExtractor;
    private final TextExtractable pdfExtractor;
    private final TokenOptimizer tokenOptimizer;
    private final Map<String, OutputFormatter> formatters;

    public ProcessingService() {
        this.dataExtractor = new DataExtractor();
        this.pdfExtractor = new PdfExtractor();
        this.tokenOptimizer = new TokenOptimizer();
        
        this.formatters = new HashMap<>();
        OutputFormatter json = new JsonFormatter();
        OutputFormatter md = new MarkdownFormatter();
        this.formatters.put(json.formatName(), json);
        this.formatters.put(md.formatName(), md);
    }

    public ProcessingResult process(InputStream fileStream, String fileName, String format, String verbosity) {
        long start = System.currentTimeMillis();
        
        OutputFormatter formatter = formatters.get(format.toLowerCase());
        if (formatter == null) {
            formatter = formatters.get("json");
        }

        String lowerName = fileName.toLowerCase();
        Map<String, Object> extractedData = new LinkedHashMap<>();
        String sourceType;
        String rawText = "";

        try {
            if (lowerName.endsWith(".pdf")) {
                sourceType = "pdf";
                rawText = pdfExtractor.extractText(fileStream);
                List<String> rawSections = pdfExtractor.extractSections(rawText);
                
                List<Map<String, String>> sections = new ArrayList<>();
                for (String section : rawSections) {
                    String[] lines = section.split("\n", 2);
                    Map<String, String> secMap = new LinkedHashMap<>();
                    if (lines.length > 0 && lines[0].length() < 100 && lines[0].toUpperCase().equals(lines[0])) {
                        secMap.put("title", lines[0].trim());
                        secMap.put("text", lines.length > 1 ? lines[1].trim() : "");
                    } else {
                        secMap.put("title", "");
                        secMap.put("text", section.trim());
                    }
                    sections.add(secMap);
                }
                
                extractedData.put("sections", sections);
                if (!"minimal".equalsIgnoreCase(verbosity)) {
                    extractedData.put("tables", new ArrayList<>());
                }
                
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv")) {
                sourceType = lowerName.endsWith(".xlsx") ? "xlsx" : "csv";
                List<Map<String, String>> rows = dataExtractor.extract(fileStream, fileName);
                
                if ("minimal".equalsIgnoreCase(verbosity)) {
                    extractedData.put("rows", rows);
                } else {
                    extractedData.put("tables", List.of(Map.of("name", "Sheet1", "data", rows)));
                    extractedData.put("sections", new ArrayList<>());
                }
            } else {
                throw new ExtractionException("Unsupported file type: " + fileName);
            }

            if ("verbose".equalsIgnoreCase(verbosity)) {
                extractedData.put("raw_text", rawText);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("filename", fileName);
                metadata.put("source_type", sourceType);
                metadata.put("extracted_at", System.currentTimeMillis());
                extractedData.put("metadata", metadata);
            }

        } catch (Exception e) {
            throw new ExtractionException("Failed to process file: " + fileName, e);
        }

        String formatted = formatter.format(extractedData, sourceType);
        String optimized = tokenOptimizer.optimize(formatted);

        int originalTokenEstimate = formatted.length() / 4;
        int tokenEstimate = optimized.length() / 4;
        long duration = System.currentTimeMillis() - start;

        return new ProcessingResult(
            optimized, 
            formatter.formatName(), 
            fileName, 
            sourceType, 
            originalTokenEstimate,
            tokenEstimate, 
            duration
        );
    }
}
