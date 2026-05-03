package com.kantara.web;

import com.kantara.exception.ValidationException;
import com.kantara.processing.ProcessingResult;
import com.kantara.processing.ProcessingService;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UploadedFile;

public class ProcessHandler implements Handler {
    
    private final ProcessingService processingService;

    public ProcessHandler(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @Override
    public void handle(Context ctx) {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            throw new ValidationException("No file uploaded.");
        }

        String format = ctx.formParam("format");
        if (format == null || format.isBlank()) {
            format = "json";
        }

        String verbosity = ctx.formParam("verbosity");
        if (verbosity == null || verbosity.isBlank()) {
            verbosity = "standard";
        }

        ProcessingResult result = processingService.process(
            file.content(), file.filename(), format, verbosity
        );

        ctx.json(result);
    }
}
