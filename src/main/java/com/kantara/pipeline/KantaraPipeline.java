package com.kantara.pipeline;

import com.kantara.ai.AiResponse;
import com.kantara.ai.AiService;
import com.kantara.ai.PayloadBuilder;
import com.kantara.config.Config;
import com.kantara.extractor.DataExtractor;
import com.kantara.extractor.PdfExtractor;
import com.kantara.generator.PptGenerator;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class KantaraPipeline {

    private static final Logger LOGGER = Logger.getLogger(KantaraPipeline.class.getName());

    private final DataExtractor dataExtractor;
    private final PdfExtractor pdfExtractor;
    private final PayloadBuilder payloadBuilder;
    private final AiService aiService;
    private final PptGenerator pptGenerator;
    private boolean verbose;

    public KantaraPipeline() {
        this(new DataExtractor(), new PdfExtractor(), new PayloadBuilder(), new AiService(), new PptGenerator());
    }

    public KantaraPipeline(
            DataExtractor dataExtractor,
            PdfExtractor pdfExtractor,
            PayloadBuilder payloadBuilder,
            AiService aiService,
            PptGenerator pptGenerator
    ) {
        this.dataExtractor = dataExtractor;
        this.pdfExtractor = pdfExtractor;
        this.payloadBuilder = payloadBuilder;
        this.aiService = aiService;
        this.pptGenerator = pptGenerator;
    }

    public void runPipeline(String excelPath, String pdfPath, String outputPath, Config config) {
        logInfo("Extracting Excel...");
        List<Map<String, String>> excelData = dataExtractor.extract(excelPath);
        if (excelData.isEmpty()) {
            throw new IllegalStateException("Excel data is empty: " + excelPath);
        }

        logInfo("Extracting PDF...");
        String pdfText = pdfExtractor.extractText(pdfPath);
        List<String> pdfSections = pdfExtractor.extractSections(pdfText);
        if (pdfSections.isEmpty()) {
            LOGGER.warning("[Kantara] PDF content is empty. Continuing without PDF sections.");
        }

        logInfo("Building payload...");
        Map<String, Object> payload = payloadBuilder.buildPayload(excelData, pdfSections);

        logInfo("Calling AI...");
        String rawAiResponse = aiService.generateInsights(payload, config);

        logInfo("Parsing AI response...");
        AiResponse aiResponse = aiService.parseAiResponse(rawAiResponse);

        logInfo("Generating PPT...");
        pptGenerator.generatePresentation(aiResponse.presentation(), outputPath);
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void logInfo(String message) {
        if (verbose) {
            LOGGER.info("[Kantara] " + message);
        }
    }
}
