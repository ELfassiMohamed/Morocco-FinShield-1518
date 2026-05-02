package com.kantara.processing;

public record ProcessingResult(
    String output,
    String format,
    String sourceFileName,
    String sourceType,
    int originalTokenEstimate,
    int tokenEstimate,
    long processingTimeMs
) {}
