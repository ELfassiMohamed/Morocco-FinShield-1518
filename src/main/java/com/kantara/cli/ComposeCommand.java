package com.kantara.cli;

import com.kantara.pipeline.KantaraPipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
        name = "compose",
        description = "Run the document-to-presentation pipeline."
)
public class ComposeCommand implements Callable<Integer> {

    @Option(names = "--data", required = true, description = "Path to Excel input file.")
    private String dataPath;

    @Option(names = "--report", required = true, description = "Path to PDF report file.")
    private String reportPath;

    @Option(names = "--output", required = true, description = "Path for generated PPT output.")
    private String outputPath;

    private final KantaraPipeline pipeline;

    public ComposeCommand() {
        this(new KantaraPipeline());
    }

    ComposeCommand(KantaraPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public Integer call() {
        validatePath(dataPath, "--data");
        validatePath(reportPath, "--report");
        validatePath(outputPath, "--output");

        dataPath = dataPath.trim();
        reportPath = reportPath.trim();
        outputPath = outputPath.trim();

        File excel = new File(dataPath);
        if (!excel.exists()) {
            System.err.println("[Kantara] ERROR: Excel file not found: " + dataPath);
            return 1;
        }

        File pdf = new File(reportPath);
        if (!pdf.exists()) {
            System.err.println("[Kantara] ERROR: PDF file not found: " + reportPath);
            return 1;
        }

        System.out.println("[Kantara] Starting pipeline...");
        try {
            pipeline.runPipeline(dataPath, reportPath, outputPath);
            System.out.println("[Kantara] Done.");
            return 0;
        } catch (Exception e) {
            System.err.println("[Kantara] ERROR: " + e.getMessage());
            return 1;
        }
    }

    private void validatePath(String value, String optionName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(optionName + " must not be empty.");
        }
    }
}
