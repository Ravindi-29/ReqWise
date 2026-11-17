package com.reqwise.service.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocxLayoutParserTest {

    @TempDir
    Path tempDir;

    private DocxLayoutParser parser;

    @BeforeEach
    void setUp() {
        parser = new DocxLayoutParser();
    }

    @Test
    void shouldExtractDocxHeadingsAndTables() throws IOException {
        Path docxPath = tempDir.resolve("sample_spec.docx");

        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(docxPath.toFile())) {

            // Heading 1
            XWPFParagraph h1 = doc.createParagraph();
            h1.createRun().setText("2.0 User Management");

            // Heading 2
            XWPFParagraph h2 = doc.createParagraph();
            h2.createRun().setText("2.1 Authentication Rules");

            // Requirement Paragraph
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("The platform shall lock user accounts after five consecutive failed login attempts.");

            // Table
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Parameter");
            headerRow.getCell(1).setText("Threshold");

            XWPFTableRow dataRow = table.getRow(1);
            dataRow.getCell(0).setText("Session Inactivity Timeout");
            dataRow.getCell(1).setText("15 minutes");

            doc.write(fos);
        }

        List<ParsedBlock> blocks = parser.parse(docxPath);

        assertNotNull(blocks);
        assertTrue(blocks.size() >= 3, "Should extract paragraph and both table rows");

        ParsedBlock reqBlock = blocks.stream()
                .filter(b -> b.text().contains("lock user accounts"))
                .findFirst()
                .orElseThrow();

        assertEquals("2.0 User Management > 2.1 Authentication Rules", reqBlock.sectionPath());
        assertFalse(reqBlock.isTableRow());

        boolean foundHeader = blocks.stream()
                .anyMatch(b -> b.isTableRow() && b.text().contains("Parameter | Threshold"));
        assertTrue(foundHeader, "Should contain header table row");

        boolean foundDataRow = blocks.stream()
                .anyMatch(b -> b.isTableRow() && b.text().contains("Session Inactivity Timeout | 15 minutes"));
        assertTrue(foundDataRow, "Should contain data table row");
    }

    @Test
    void shouldSupportDocxOnly() {
        assertTrue(parser.supports("DOCX"));
        assertTrue(parser.supports("docx"));
        assertFalse(parser.supports("PDF"));
    }
}
