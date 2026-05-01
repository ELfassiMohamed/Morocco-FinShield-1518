package com.kantara.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import com.kantara.exception.*;

/**
 * Extracts and structures text content from PDF documents.
 */
public class PdfExtractor implements TextExtractable {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f ]+");
    private static final Pattern KEYWORD_HEADING_PATTERN =
            Pattern.compile(".*\\b(SUMMARY|REVENUE|CONCLUSION)\\b.*");

    /**
     * Extracts the full document text from a PDF and applies basic cleanup.
     *
     * @param filePath absolute or relative path to the PDF file
     * @return cleaned text as a single string
     */
    public String extractText(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new ValidationException("filePath must not be null or blank.");
        }

        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new ValidationException("PDF file not found: " + filePath);
        }

        try {
            return extractText(new FileInputStream(pdfFile));
        } catch (FileNotFoundException e) {
            throw new ExtractionException("PDF file not found: " + filePath, e);
        }
    }

    public String extractText(InputStream stream) {
        if (stream == null) {
            throw new ValidationException("InputStream must not be null.");
        }

        try (PDDocument document = Loader.loadPDF(stream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            String rawText = stripper.getText(document);
            return cleanText(rawText);
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract PDF text from stream.", e);
        }
    }

    /**
     * Splits text into logical sections using heading-like lines.
     *
     * @param text cleaned or raw text
     * @return list of non-empty text sections
     */
    public List<String> extractSections(String text) {
        List<String> sections = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sections;
        }

        String normalizedText = cleanText(text);
        String[] lines = normalizedText.split("\n");
        StringBuilder currentSection = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (isHeading(trimmed) && currentSection.length() > 0) {
                sections.add(currentSection.toString().trim());
                currentSection.setLength(0);
            }

            if (currentSection.length() > 0) {
                currentSection.append('\n');
            }
            currentSection.append(trimmed);
        }

        if (currentSection.length() > 0) {
            sections.add(currentSection.toString().trim());
        }

        return sections;
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalizedLineBreaks = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedLineBreaks.split("\n");
        StringBuilder cleaned = new StringBuilder(normalizedLineBreaks.length());

        for (String line : lines) {
            String compact = WHITESPACE_PATTERN.matcher(line).replaceAll(" ").trim();
            if (compact.isEmpty()) {
                continue;
            }

            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(compact);
        }

        return cleaned.toString();
    }

    private boolean isHeading(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (KEYWORD_HEADING_PATTERN.matcher(upper).matches()) {
            return true;
        }

        return line.length() >= 3
                && line.matches(".*[A-Z].*")
                && line.equals(upper)
                && line.matches("[A-Z0-9\\s&:.,()/%\\-]+");
    }
}
