package com.reqwise.service.parser;

import com.reqwise.dto.BoundingBox;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocxLayoutParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(DocxLayoutParser.class);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+([A-Z][^\\n]{2,100})$");

    @Override
    public boolean supports(String fileType) {
        return "DOCX".equalsIgnoreCase(fileType);
    }

    @Override
    public List<ParsedBlock> parse(Path filePath) {
        List<ParsedBlock> blocks = new ArrayList<>();
        List<String> sectionStack = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            int approxPage = 1;
            int paragraphCounter = 0;

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    paragraphCounter++;
                    if (paragraphCounter % 15 == 0) {
                        approxPage++;
                    }

                    String text = paragraph.getText().trim();
                    if (text.isEmpty() || text.length() < 5) {
                        continue;
                    }

                    String style = paragraph.getStyle();
                    boolean isHeadingStyle = style != null && style.toLowerCase().startsWith("heading");

                    Matcher headingMatch = HEADING_PATTERN.matcher(text);
                    if (isHeadingStyle || headingMatch.matches()) {
                        updateSectionStack(sectionStack, text, headingMatch, style);
                        continue;
                    }

                    String currentPath = sectionStack.isEmpty() ? "1.0 General" : String.join(" > ", sectionStack);
                    blocks.add(new ParsedBlock(
                        currentPath,
                        text,
                        approxPage,
                        BoundingBox.empty(),
                        false
                    ));
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cellTexts = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cText = cell.getText().trim();
                            if (!cText.isEmpty()) {
                                cellTexts.add(cText);
                            }
                        }
                        if (!cellTexts.isEmpty()) {
                            String tableRowText = String.join(" | ", cellTexts);
                            String currentPath = sectionStack.isEmpty() ? "1.0 General" : String.join(" > ", sectionStack);
                            blocks.add(new ParsedBlock(
                                currentPath,
                                tableRowText,
                                approxPage,
                                BoundingBox.empty(),
                                true
                            ));
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to parse DOCX document at {}", filePath, e);
            throw new RuntimeException("Error parsing DOCX layout", e);
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

    private void updateSectionStack(List<String> sectionStack, String text, Matcher headingMatch, String style) {
        if (headingMatch.matches()) {
            String secNum = headingMatch.group(1);
            String secTitle = headingMatch.group(2);
            int depth = calculateSectionDepth(secNum);

            while (sectionStack.size() > depth) {
                sectionStack.remove(sectionStack.size() - 1);
            }
            sectionStack.add(secNum + " " + secTitle);
        } else if (style != null && style.toLowerCase().startsWith("heading")) {
            try {
                int level = Integer.parseInt(style.replaceAll("\\D+", ""));
                int depth = Math.max(0, level - 1);
                while (sectionStack.size() > depth) {
                    sectionStack.remove(sectionStack.size() - 1);
                }
                sectionStack.add(text);
            } catch (NumberFormatException ignored) {
                sectionStack.add(text);
            }
        }
    }
}
