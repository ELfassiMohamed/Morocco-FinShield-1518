package com.kantara.processing;

import com.kantara.exception.ExtractionException;
import com.kantara.extractor.PdfExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingServiceTest {

    private final ProcessingService processingService = new ProcessingService();

    @Test
    void rejectsXlsxFiles() {
        assertThrows(ExtractionException.class, () -> processingService.process(
                new ByteArrayInputStream(new byte[0]),
                "legacy.xlsx",
                "json",
                "standard"
        ));
    }

    @Test
    void processesCsvFiles() {
        byte[] csv = "Name,Value\nAlpha,42\n".getBytes(StandardCharsets.UTF_8);

        ProcessingResult result = processingService.process(
                new ByteArrayInputStream(csv),
                "sample.csv",
                "json",
                "standard"
        );

        assertEquals("csv", result.sourceType());
        assertTrue(result.output().contains("\"source_type\" : \"csv\""));
        assertTrue(result.output().contains("Alpha"));
        assertTrue(result.output().contains("42"));
    }

    @Test
    void processesCsvFilesAsHtml() {
        byte[] csv = "Name,Value\nAlpha,<script>alert('x')</script>\n".getBytes(StandardCharsets.UTF_8);

        ProcessingResult result = processingService.process(
                new ByteArrayInputStream(csv),
                "sample.csv",
                "html",
                "standard"
        );

        assertEquals("html", result.format());
        assertTrue(result.output().contains("<h1>Kantara Output</h1>"));
        assertTrue(result.output().contains("<table>"));
        assertTrue(result.output().contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertFalse(result.output().contains("<script>"));
    }

    @Test
    void extractsDocxParagraphsAndTables() throws Exception {
        ProcessingResult result = processingService.process(
                new ByteArrayInputStream(docxBytes()),
                "brief.docx",
                "json",
                "standard"
        );

        assertEquals("docx", result.sourceType());
        assertTrue(result.output().contains("\"source_type\" : \"docx\""));
        assertTrue(result.output().contains("Document intro text"));
        assertTrue(result.output().contains("Alice"));
        assertTrue(result.output().contains("Score"));
    }

    @Test
    void extractsPptxSlideTextAndTables() throws Exception {
        ProcessingResult result = processingService.process(
                new ByteArrayInputStream(pptxBytes()),
                "deck.pptx",
                "json",
                "standard"
        );

        assertEquals("pptx", result.sourceType());
        assertTrue(result.output().contains("\"source_type\" : \"pptx\""));
        assertTrue(result.output().contains("Quarterly results"));
        assertTrue(result.output().contains("Revenue"));
        assertTrue(result.output().contains("$10M"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pdfTableCandidateUsesStructuredRows() throws Exception {
        Method method = PdfExtractor.class.getDeclaredMethod("extractTableLikeBlocks", String.class, int.class);
        method.setAccessible(true);

        String text = """
                Name     Score     Rank
                Alice    99        1
                Bob      95        2
                """;
        List<Map<String, Object>> tables = (List<Map<String, Object>>) method.invoke(new PdfExtractor(), text, 1);

        assertFalse(tables.isEmpty());
        Map<String, Object> table = tables.get(0);
        assertEquals(List.of("Name", "Score", "Rank"), table.get("headers"));
        assertEquals(2, table.get("row_count"));
        assertTrue(table.containsKey("data"));
    }

    private byte[] docxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Document intro text");

            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Name");
            table.getRow(0).getCell(1).setText("Score");
            table.getRow(1).getCell(0).setText("Alice");
            table.getRow(1).getCell(1).setText("99");

            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pptxBytes() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();

            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new Rectangle2D.Double(50, 40, 500, 80));
            textBox.setText("Quarterly results");

            XSLFTable table = slide.createTable();
            table.setAnchor(new Rectangle2D.Double(50, 150, 400, 120));
            addPptxRow(table, "Metric", "Value");
            addPptxRow(table, "Revenue", "$10M");

            slideShow.write(output);
            return output.toByteArray();
        }
    }

    private void addPptxRow(XSLFTable table, String first, String second) {
        XSLFTableRow row = table.addRow();
        row.setHeight(24);
        XSLFTableCell firstCell = row.addCell();
        firstCell.setText(first);
        XSLFTableCell secondCell = row.addCell();
        secondCell.setText(second);
    }
}
