package com.kantara.web;

import com.kantara.exception.ValidationException;
import com.kantara.processing.ProcessingResult;
import com.kantara.processing.ProcessingService;
import com.kantara.processing.ProcessingService.SourceDocument;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UploadedFile;

import java.util.ArrayList;
import java.util.List;

public class ProcessHandler implements Handler {
    
    private final ProcessingService processingService;

    public ProcessHandler(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @Override
    public void handle(Context ctx) {
        List<UploadedFile> uploadedFiles = ctx.uploadedFiles("file");
        if (uploadedFiles.isEmpty()) {
            UploadedFile singleFile = ctx.uploadedFile("file");
            if (singleFile != null) {
                uploadedFiles = List.of(singleFile);
            }
        }

        if (uploadedFiles.isEmpty()) {
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

        List<SourceDocument> documents = new ArrayList<>();
        for (UploadedFile file : uploadedFiles) {
            documents.add(new SourceDocument(file.filename(), file.content()));
        }

        ProcessingResult result = processingService.processBatch(documents, format, verbosity);

        ctx.json(result);
    }
}
