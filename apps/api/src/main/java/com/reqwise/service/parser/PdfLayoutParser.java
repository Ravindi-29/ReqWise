package com.reqwise.service.parser;

import com.reqwise.dto.BoundingBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfLayoutParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(PdfLayoutParser.class);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+([A-Z][^\\n]{2,100})$");

    @Override
    public boolean supports(String fileType) {
        return "PDF".equalsIgnoreCase(fileType);
    }

    @Override
    public List<ParsedBlock> parse(Path filePath) {
        List<ParsedBlock> blocks = new ArrayList<>();
        File file = filePath.toFile();

        try (PDDocument document = Loader.loadPDF(file)) {
            List<String> sectionStack = new ArrayList<>();

            int numPages = document.getNumberOfPages();
            for (int pageIdx = 1; pageIdx <= numPages; pageIdx++) {
                PDPage page = document.getPage(pageIdx - 1);
                float pageHeight = page.getCropBox().getHeight();

                CoordinateTrackingStripper stripper = new CoordinateTrackingStripper(pageHeight, pageIdx, sectionStack);
                stripper.setStartPage(pageIdx);
                stripper.setEndPage(pageIdx);
                stripper.setSortByPosition(true);

                // Run extraction
                stripper.extractTextBlocks(document);
                blocks.addAll(stripper.getExtractedBlocks());
            }
        } catch (IOException e) {
            logger.error("Failed to parse PDF document at {}", filePath, e);
            throw new RuntimeException("Error parsing PDF layout", e);
        }

        return blocks;
    }

    public static int calculateSectionDepth(String secNum) {
        String[] parts = secNum.split("\\.");
        if (parts.length <= 1) {
            return 0;
        }
        if (parts.length == 2 && "0".equals(parts[1])) {
            return 0;
        }
        return parts.length - 1;
    }

    private static class CoordinateTrackingStripper extends PDFTextStripper {
        private final float pageHeight;
        private final int pageNumber;
        private final List<String> sectionStack;
        private final List<ParsedBlock> extractedBlocks = new ArrayList<>();

        public CoordinateTrackingStripper(float pageHeight, int pageNumber, List<String> sectionStack) throws IOException {
            super();
            this.pageHeight = pageHeight;
            this.pageNumber = pageNumber;
            this.sectionStack = sectionStack;
        }

        public void extractTextBlocks(PDDocument doc) throws IOException {
            StringWriter dummy = new StringWriter();
            writeText(doc, dummy);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.trim().isEmpty() || textPositions == null || textPositions.isEmpty()) {
                return;
            }

            // Calculate bounding box across glyphs
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE;
            float maxY = Float.MIN_VALUE;

            for (TextPosition tp : textPositions) {
                float x0 = tp.getXDirAdj();
                float y0 = tp.getYDirAdj() - tp.getHeightDir();
                float x1 = x0 + tp.getWidthDirAdj();
                float y1 = tp.getYDirAdj();

                if (x0 < minX) minX = x0;
                if (y0 < minY) minY = y0;
                if (x1 > maxX) maxX = x1;
                if (y1 > maxY) maxY = y1;
            }

            // Filter out running headers (top 6%) and running footers (bottom 6%)
            if (minY < pageHeight * 0.06f || maxY > pageHeight * 0.94f) {
                return;
            }

            String cleanedText = text.trim().replaceAll("\\s+", " ");
            if (cleanedText.length() < 5) {
                return;
            }

            // Check if line is a hierarchical heading (e.g., "1.0 Architecture", "1.1 Security")
            Matcher headingMatch = HEADING_PATTERN.matcher(cleanedText);
            if (headingMatch.matches()) {
                String secNum = headingMatch.group(1);
                String secTitle = headingMatch.group(2);
                int depth = calculateSectionDepth(secNum);

                while (sectionStack.size() > depth) {
                    sectionStack.remove(sectionStack.size() - 1);
                }
                sectionStack.add(secNum + " " + secTitle);
                return;
            }

            String currentPath = sectionStack.isEmpty() ? "1.0 General" : String.join(" > ", sectionStack);
            extractedBlocks.add(new ParsedBlock(
                currentPath,
                cleanedText,
                pageNumber,
                new BoundingBox(
                    round(minX),
                    round(minY),
                    round(maxX),
                    round(maxY)
                )
            ));
        }

        public List<ParsedBlock> getExtractedBlocks() {
            return extractedBlocks;
        }

        private float round(float val) {
            return Math.round(val * 100.0f) / 100.0f;
        }
    }
}
