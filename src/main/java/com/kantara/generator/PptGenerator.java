package com.kantara.generator;

import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PptGenerator {

    private static final int MAX_SLIDES = 6;
    private static final int MAX_BULLETS_PER_SLIDE = 5;
    private static final double TITLE_FONT_SIZE = 32.0;
    private static final double BULLET_FONT_SIZE = 22.0;

    public void generatePresentation(List<Map<String, Object>> slides, String outputPath) {
        if (slides == null || slides.isEmpty()) {
            throw new IllegalArgumentException("slides must not be null or empty");
        }
        if (isBlank(outputPath)) {
            throw new IllegalArgumentException("outputPath must not be blank");
        }

        try (XMLSlideShow ppt = new XMLSlideShow()) {
            int createdSlides = 0;
            for (Map<String, Object> slideData : slides) {
                if (createdSlides >= MAX_SLIDES) {
                    break;
                }
                if (isEmptySlide(slideData)) {
                    continue;
                }
                addSlide(ppt, slideData);
                createdSlides++;
            }

            if (createdSlides == 0) {
                throw new IllegalArgumentException("No valid slide content to generate.");
            }

            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                ppt.write(outputStream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate presentation: " + outputPath, e);
        }
    }

    private void addSlide(XMLSlideShow ppt, Map<String, Object> slideData) {
        XSLFSlide slide = ppt.createSlide();
        Dimension pageSize = ppt.getPageSize();

        String title = safeString(slideData.get("title"), "Untitled Slide");
        List<String> bullets = extractBullets(slideData.get("bullets"));

        addTitle(slide, title, pageSize);
        addBullets(slide, bullets, pageSize);
    }

    private void addTitle(XSLFSlide slide, String title, Dimension pageSize) {
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(40, 20, pageSize.width - 80, 60));
        titleBox.setVerticalAlignment(VerticalAlignment.MIDDLE);
        titleBox.setWordWrap(true);

        XSLFTextParagraph paragraph = titleBox.addNewTextParagraph();
        paragraph.setBullet(false);
        paragraph.setSpaceAfter(6.0);

        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(TITLE_FONT_SIZE);
        run.setFontColor(new Color(33, 37, 41));
    }

    private void addBullets(XSLFSlide slide, List<String> bullets, Dimension pageSize) {
        XSLFTextBox bulletBox = slide.createTextBox();
        bulletBox.setAnchor(new Rectangle(60, 100, pageSize.width - 120, pageSize.height - 140));
        bulletBox.setWordWrap(true);
        bulletBox.setVerticalAlignment(VerticalAlignment.TOP);

        if (bullets.isEmpty()) {
            return;
        }

        for (String bullet : bullets) {
            XSLFTextParagraph paragraph = bulletBox.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setLeftMargin(18.0);
            paragraph.setIndent(-8.0);
            paragraph.setSpaceAfter(10.0);

            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(bullet);
            run.setFontSize(BULLET_FONT_SIZE);
            run.setFontColor(new Color(44, 62, 80));
        }
    }

    private boolean isEmptySlide(Map<String, Object> slideData) {
        if (slideData == null || slideData.isEmpty()) {
            return true;
        }
        String title = safeString(slideData.get("title"), "");
        List<String> bullets = extractBullets(slideData.get("bullets"));
        return isBlank(title) && bullets.isEmpty();
    }

    private List<String> extractBullets(Object bulletsObject) {
        if (!(bulletsObject instanceof List<?> rawList) || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> bullets = new ArrayList<>();
        for (Object item : rawList) {
            String text = safeString(item, "");
            if (!isBlank(text)) {
                bullets.add(text);
            }
            if (bullets.size() >= MAX_BULLETS_PER_SLIDE) {
                break;
            }
        }
        return bullets;
    }

    private String safeString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}

