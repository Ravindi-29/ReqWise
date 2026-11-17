package com.reqwise.service.parser;

import com.reqwise.dto.BoundingBox;

public record ParsedBlock(
    String sectionPath,
    String text,
    int pageNumber,
    BoundingBox boundingBox,
    boolean isTableRow
) {
    public ParsedBlock(String sectionPath, String text, int pageNumber, BoundingBox boundingBox) {
        this(sectionPath, text, pageNumber, boundingBox, false);
    }
}
