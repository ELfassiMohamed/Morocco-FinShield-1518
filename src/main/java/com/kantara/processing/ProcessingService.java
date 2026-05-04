package com.kantara.processing;

import com.kantara.exception.ExtractionException;
import com.kantara.extractor.DataExtractor;
import com.kantara.extractor.PdfExtractor;
import com.kantara.formatter.JsonFormatter;
import com.kantara.formatter.MarkdownFormatter;
import com.kantara.formatter.OutputFormatter;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessingService {

    private final DataExtractor dataExtractor;
    private final PdfExtractor pdfExtractor;
    private final TokenOptimizer tokenOptimizer;
    private final DocumentChunker documentChunker;
    private final Map<String, OutputFormatter> formatters;

    public ProcessingService() {
        this.dataExtractor = new DataExtractor();
        this.pdfExtractor = new PdfExtractor();
        this.tokenOptimizer = new TokenOptimizer();
        this.documentChunker = new DocumentChunker();

        this.formatters = new HashMap<>();
        OutputFormatter json = new JsonFormatter();
        OutputFormatter md = new MarkdownFormatter();
        this.formatters.put(json.formatName(), json);
        this.formatters.put(md.formatName(), md);
    }

    public ProcessingResult process(InputStream fileStream, String fileName, String format, String verbosity) {
        return processBatch(List.of(new SourceDocument(fileName, fileStream)), format, verbosity);
    }

    public ProcessingResult processBatch(List<SourceDocument> documents, String format, String verbosity) {
        if (documents == null || documents.isEmpty()) {
            throw new ExtractionException("No files uploaded.");
        }

        long start = System.currentTimeMillis();
        OutputFormatter formatter = formatterFor(format);
        List<Map<String, Object>> processedDocuments = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> chunkBlocks = new ArrayList<>();

        for (SourceDocument document : documents) {
            try {
                Map<String, Object> processed = processDocument(document, verbosity);
                processedDocuments.add(processed);
                chunkBlocks.addAll(blocksForChunking(processed));
            } catch (RuntimeException e) {
                errors.add(errorMap(document.fileName(), e));
            }
        }

        if (processedDocuments.isEmpty()) {
            throw new ExtractionException("Failed to process uploaded files.");
        }

        List<Map<String, Object>> chunks = documentChunker.chunk(chunkBlocks);
        Map<String, Object> data = rootData(processedDocuments, errors, chunks, verbosity);
        String sourceType = processedDocuments.size() == 1
                ? asString(processedDocuments.get(0).get("source_type"))
                : "batch";

        String formatted = formatter.format(data, sourceType);
        String optimized = tokenOptimizer.optimize(formatted);
        int originalTokenEstimate = estimateTokens(formatted);
        int tokenEstimate = estimateTokens(optimized);
        long duration = System.currentTimeMillis() - start;

        return new ProcessingResult(
                optimized,
                formatter.formatName(),
                resultName(processedDocuments, errors),
                sourceType,
                originalTokenEstimate,
                tokenEstimate,
                duration,
                chunks.size(),
                processedDocuments.size()
        );
    }

    private Map<String, Object> processDocument(SourceDocument sourceDocument, String verbosity) {
        String fileName = sourceDocument.fileName();
        String lowerName = fileName.toLowerCase();
        String sourceType;
        Map<String, Object> extracted;

        if (lowerName.endsWith(".pdf")) {
            sourceType = "pdf";
            extracted = pdfExtractor.extract(sourceDocument.stream(), fileName);
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv")) {
            sourceType = lowerName.endsWith(".xlsx") ? "xlsx" : "csv";
            extracted = dataExtractor.extractDocument(sourceDocument.stream(), fileName);
        } else {
            throw new ExtractionException("Unsupported file type: " + fileName);
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("filename", fileName);
        document.put("source_type", sourceType);
        document.putAll(extracted);
        pruneByVerbosity(document, verbosity);
        return document;
    }

    private Map<String, Object> rootData(
            List<Map<String, Object>> documents,
            List<Map<String, Object>> errors,
            List<Map<String, Object>> chunks,
            String verbosity
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schema_version", "1.0");
        Map<String, Object> processingMetadata = Map.of(
                "file_count", documents.size(),
                "chunk_count", chunks.size(),
                "generated_at", Instant.now().toString()
        );

        if (documents.size() == 1) {
            data.putAll(documents.get(0));
        } else {
            data.put("documents", documents);
        }

        data.put("processing_metadata", processingMetadata);
        data.put("chunks", chunks);
        if (!errors.isEmpty()) {
            data.put("errors", errors);
        }
        if ("minimal".equalsIgnoreCase(verbosity)) {
            data.remove("pages");
        }
        return data;
    }

    private void pruneByVerbosity(Map<String, Object> document, String verbosity) {
        if ("verbose".equalsIgnoreCase(verbosity)) {
            return;
        }

        document.remove("raw_text");
        if ("minimal".equalsIgnoreCase(verbosity)) {
            document.remove("pages");
            document.remove("metadata");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> blocksForChunking(Map<String, Object> document) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        String filename = asString(document.get("filename"));

        Object sectionsValue = document.get("sections");
        if (sectionsValue instanceof List<?> sections) {
            for (Object sectionValue : sections) {
                if (sectionValue instanceof Map<?, ?> section) {
                    String title = asString(section.get("title"));
                    String text = asString(section.get("text"));
                    String page = asString(section.get("page_start"));
                    addBlock(blocks, filename + (page.isBlank() ? "" : " p" + page), title, text);
                }
            }
        }

        Object tablesValue = document.get("tables");
        if (tablesValue instanceof List<?> tables) {
            for (Object tableValue : tables) {
                if (tableValue instanceof Map<?, ?> table) {
                    blocks.add(tableBlock(filename, (Map<String, Object>) table));
                }
            }
        }

        Object pagesValue = document.get("pages");
        if (blocks.isEmpty() && pagesValue instanceof List<?> pages) {
            for (Object pageValue : pages) {
                if (pageValue instanceof Map<?, ?> page) {
                    addBlock(blocks, filename + " p" + asString(page.get("page_number")), "", asString(page.get("text")));
                }
            }
        }

        return blocks;
    }

    private void addBlock(List<Map<String, Object>> blocks, String source, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("source", source);
        block.put("text", title == null || title.isBlank() ? text : title + "\n" + text);
        blocks.add(block);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tableBlock(String filename, Map<String, Object> table) {
        String name = asString(table.get("name"));
        StringBuilder text = new StringBuilder();
        text.append(name.isBlank() ? "Table" : name).append("\n");

        Object rows = table.get("data");
        if (rows instanceof List<?> dataRows) {
            for (Object rowValue : dataRows) {
                if (rowValue instanceof Map<?, ?> row) {
                    Object values = row.get("values");
                    text.append("Row ").append(asString(row.get("row_number"))).append(": ");
                    if (values instanceof Map<?, ?> valueMap) {
                        List<String> cells = new ArrayList<>();
                        for (Map.Entry<?, ?> entry : valueMap.entrySet()) {
                            cells.add(entry.getKey() + "=" + entry.getValue());
                        }
                        text.append(String.join("; ", cells));
                    }
                    text.append('\n');
                }
            }
        } else if (table.get("rows") instanceof List<?> pdfRows) {
            for (Object row : pdfRows) {
                text.append(row).append('\n');
            }
        }

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("source", filename + " " + name);
        block.put("text", text.toString());
        return block;
    }

    private OutputFormatter formatterFor(String format) {
        if (format == null || format.isBlank()) {
            return formatters.get("json");
        }
        OutputFormatter formatter = formatters.get(format.toLowerCase());
        return formatter == null ? formatters.get("json") : formatter;
    }

    private Map<String, Object> errorMap(String fileName, RuntimeException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("filename", fileName);
        error.put("error", e.getMessage());
        return error;
    }

    private String resultName(List<Map<String, Object>> documents, List<Map<String, Object>> errors) {
        if (documents.size() == 1 && errors.isEmpty()) {
            return asString(documents.get(0).get("filename"));
        }
        return documents.size() + " file" + (documents.size() == 1 ? "" : "s");
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    public record SourceDocument(String fileName, InputStream stream) {
    }
}
