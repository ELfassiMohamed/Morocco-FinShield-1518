package com.kantara.generator;

import com.kantara.ai.AiResponse;
import com.kantara.ai.Slide;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DocxGenerator {

    private static final int TITLE_FONT_SIZE = 24;
    private static final int HEADING_FONT_SIZE = 15;
    private static final int BODY_FONT_SIZE = 11;

    public void generateReport(AiResponse response, String outputPath) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        if (isBlank(outputPath)) {
            throw new IllegalArgumentException("outputPath must not be blank");
        }

        List<Slide> slides = response.presentation();
        if (slides == null || slides.isEmpty()) {
            return;
        }

        Path output = Path.of(outputPath);
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output file already exists: " + outputPath);
        }

        try (XWPFDocument document = new XWPFDocument();
             OutputStream stream = Files.newOutputStream(output)) {

            addMainTitle(document, "Business Analysis Report");

            Slide executiveSlide = slides.get(0);
            addHeading(document, "Executive Summary");
            addHeading(document, safeText(executiveSlide.title(), "Summary"), 13);
            addBullets(document, executiveSlide.bullets());
            addSectionSpacing(document);

            addHeading(document, "Key Insights");
            addBullets(document, response.keyInsights());
            addSectionSpacing(document);

            for (int i = 1; i < slides.size(); i++) {
                Slide slide = slides.get(i);
                addHeading(document, safeText(slide.title(), "Detailed Section"));
                addBullets(document, slide.bullets());
                addSectionSpacing(document);
            }

            document.write(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate DOCX report at: " + outputPath, e);
        }
    }

    private void addMainTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(280);

        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(TITLE_FONT_SIZE);
        run.setText(text);
    }

    private void addHeading(XWPFDocument document, String text) {
        addHeading(document, text, HEADING_FONT_SIZE);
    }

    private void addHeading(XWPFDocument document, String text, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(120);
        paragraph.setSpacingAfter(120);

        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(fontSize);
        run.setText(text);
    }

    private void addBullets(XWPFDocument document, List<String> items) {
        if (items == null || items.isEmpty()) {
            addBodyParagraph(document, "No content available.");
            return;
        }

        for (String item : items) {
            String bulletText = safeText(item, "");
            if (bulletText.isBlank()) {
                continue;
            }

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(360);
            paragraph.setSpacingAfter(80);

            XWPFRun run = paragraph.createRun();
            run.setFontSize(BODY_FONT_SIZE);
            run.setText("\u2022 " + bulletText);
        }
    }

    private void addBodyParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);

        XWPFRun run = paragraph.createRun();
        run.setFontSize(BODY_FONT_SIZE);
        run.setText(text);
    }

    private void addSectionSpacing(XWPFDocument document) {
        XWPFParagraph spacing = document.createParagraph();
        spacing.setSpacingAfter(140);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
