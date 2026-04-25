package com.kantara.cli;

import com.kantara.config.Config;
import com.kantara.config.LocalTestConfigLoader;
import com.kantara.pipeline.KantaraPipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(
        name = "compose",
        description = "Run the document-to-presentation pipeline."
)
public class ComposeCommand implements Callable<Integer> {

    @Option(names = "--data", required = true, description = "Path to Excel file containing business data (.xlsx)")
    private String dataPath;

    @Option(names = "--report", required = true, description = "Path to PDF report file (.pdf)")
    private String reportPath;

    @Option(
            names = "--output",
            required = true,
            description = "Path where the generated PowerPoint (.pptx) will be saved"
    )
    private String outputPath;

    @Option(names = "--config", description = "Path to optional JSON config file")
    private String configPath;

    @Option(names = "--verbose", description = "Enable detailed logs")
    boolean verbose;

    private final KantaraPipeline pipeline;

    public ComposeCommand() {
        this(new KantaraPipeline());
    }

    ComposeCommand(KantaraPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public Integer call() {
        if (isBlank(dataPath)) {
            System.err.println("[Kantara] ERROR: --data must not be empty.");
            return 1;
        }
        if (isBlank(reportPath)) {
            System.err.println("[Kantara] ERROR: --report must not be empty.");
            return 1;
        }
        if (isBlank(outputPath)) {
            System.err.println("[Kantara] ERROR: --output must not be empty.");
            return 1;
        }

        dataPath = dataPath.trim();
        reportPath = reportPath.trim();
        outputPath = outputPath.trim();

        File excel = new File(dataPath);
        if (!excel.exists()) {
            System.err.println("[Kantara] ERROR: Excel file not found: " + dataPath);
            return 1;
        }
        if (!isExtension(excel.getName(), ".xlsx")) {
            System.err.println("[Kantara] ERROR: Excel file must have .xlsx extension: " + dataPath);
            return 1;
        }

        File pdf = new File(reportPath);
        if (!pdf.exists()) {
            System.err.println("[Kantara] ERROR: PDF file not found: " + reportPath);
            return 1;
        }
        if (!isExtension(pdf.getName(), ".pdf")) {
            System.err.println("[Kantara] ERROR: PDF file must have .pdf extension: " + reportPath);
            return 1;
        }

        File output = new File(outputPath);
        if (output.exists()) {
            System.err.println("[Kantara] ERROR: Output file already exists: " + outputPath);
            return 1;
        }

        System.out.println("[Kantara] Starting pipeline...");
        pipeline.setVerbose(verbose);
        long startTimeMs = System.currentTimeMillis();
        try {
            //for local testing
            Config config = LocalTestConfigLoader.load();
            //production 
            //Config config = ConfigLoader.load(configPath);
            pipeline.runPipeline(dataPath, reportPath, outputPath, config);
            double elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            System.out.println("[Kantara] Presentation generated successfully: " + outputPath);
            System.out.println("[Kantara] Completed in " + String.format(Locale.ROOT, "%.1f", elapsedSeconds) + " seconds");
            return 0;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("[Kantara] ERROR:")) {
                System.err.println(message);
            } else {
                System.err.println("[Kantara] ERROR: " + (message == null ? "Unexpected failure." : message));
            }
            return 1;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isExtension(String fileName, String extension) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(extension);
    }
}
