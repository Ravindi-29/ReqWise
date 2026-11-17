package com.reqwise.service.parser;

import com.reqwise.dto.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfLayoutParserTest {

    @TempDir
    Path tempDir;

    private PdfLayoutParser parser;

    @BeforeEach
    void setUp() {
        parser = new PdfLayoutParser();
    }

    @Test
    void shouldExtractHierarchicalSectionsAndBoundingBoxes() throws IOException {
        Path pdfPath = tempDir.resolve("sample_srs.pdf");

        // Generate synthetic PDF with headings and content
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Section 1.0 Heading
                content.beginText();
                content.setFont(fontBold, 16);
                content.newLineAtOffset(50, 700);
                content.showText("1.0 System Architecture");
                content.endText();

                // Section 1.1 Heading
                content.beginText();
                content.setFont(fontBold, 14);
                content.newLineAtOffset(50, 650);
                content.showText("1.1 Security Constraints");
                content.endText();

                // Requirement Paragraph
                content.beginText();
                content.setFont(fontRegular, 11);
                content.newLineAtOffset(50, 600);
                content.showText("The system shall encrypt all sensitive customer data at rest using AES-256.");
                content.endText();
            }

            doc.save(pdfPath.toFile());
        }

        // Parse generated PDF
        List<ParsedBlock> blocks = parser.parse(pdfPath);

        assertNotNull(blocks);
        assertFalse(blocks.isEmpty(), "Blocks should be extracted from PDF");

        ParsedBlock reqBlock = blocks.get(0);
        assertTrue(reqBlock.text().contains("encrypt all sensitive customer data"));
        assertEquals("1.0 System Architecture > 1.1 Security Constraints", reqBlock.sectionPath());
        assertEquals(1, reqBlock.pageNumber());

        BoundingBox bbox = reqBlock.boundingBox();
        assertNotNull(bbox);
        assertTrue(bbox.x0() > 0, "x0 should be greater than 0");
        assertTrue(bbox.x1() > bbox.x0(), "x1 should be greater than x0");
        assertTrue(bbox.y1() > bbox.y0(), "y1 should be greater than y0");
    }

    @Test
    void shouldSupportPdfOnly() {
        assertTrue(parser.supports("PDF"));
        assertTrue(parser.supports("pdf"));
        assertFalse(parser.supports("DOCX"));
    }
}
