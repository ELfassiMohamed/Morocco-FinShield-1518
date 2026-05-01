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

    public ProcessingResult process(InputStream fileStream, String fileName, String format) {
        long start = System.currentTimeMillis();
        
        OutputFormatter formatter = formatters.get(format.toLowerCase());
        if (formatter == null) {
            formatter = formatters.get("json");
        }

        String lowerName = fileName.toLowerCase();
        Map<String, Object> extractedData = new LinkedHashMap<>();
        String sourceType;

        try {
            if (lowerName.endsWith(".pdf")) {
                sourceType = "pdf";
                String text = pdfExtractor.extractText(fileStream);
                List<String> sections = pdfExtractor.extractSections(text);
                extractedData.put("sections", sections);
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv")) {
                sourceType = lowerName.endsWith(".xlsx") ? "xlsx" : "csv";
                List<Map<String, String>> rows = dataExtractor.extract(fileStream, fileName);
                extractedData.put("rows", rows);
            } else {
                throw new ExtractionException("Unsupported file type: " + fileName);
            }
        } catch (Exception e) {
            throw new ExtractionException("Failed to process file: " + fileName, e);
        }

        String formatted = formatter.format(extractedData, sourceType);
        String optimized = tokenOptimizer.optimize(formatted);

        int tokenEstimate = optimized.length() / 4;
        long duration = System.currentTimeMillis() - start;

        return new ProcessingResult(
            optimized, 
            formatter.formatName(), 
            fileName, 
            sourceType, 
            tokenEstimate, 
            duration
        );
    }
}
