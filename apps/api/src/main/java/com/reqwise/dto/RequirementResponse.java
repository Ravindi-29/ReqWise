package com.reqwise.dto;

import com.reqwise.model.Requirement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        UUID versionId,
        String reqCode,
        String sectionPath,
        String rawText,
        String normalizedText,
        String reqType,
        String nfrCategory,
        String earsPattern,
        String priority,
        BigDecimal clarityScore,
        Integer pageNumber,
        String boundingBox,
        String reviewStatus,
        int issueCount,
        OffsetDateTime createdAt
) {
    public static RequirementResponse fromEntity(Requirement req) {
        return new RequirementResponse(
                req.getId(),
                req.getVersion() != null ? req.getVersion().getId() : null,
                req.getReqCode(),
                req.getSectionPath(),
                req.getRawText(),
                req.getNormalizedText(),
                req.getReqType(),
                req.getNfrCategory(),
                req.getEarsPattern(),
                req.getPriority(),
                req.getClarityScore(),
                req.getPageNumber(),
                req.getBoundingBox(),
                req.getReviewStatus(),
                req.getIssuesPrimary() != null ? req.getIssuesPrimary().size() : 0,
                req.getCreatedAt()
        );
    }
}
