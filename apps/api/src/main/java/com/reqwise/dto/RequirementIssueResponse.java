package com.reqwise.dto;

import com.reqwise.model.RequirementIssue;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RequirementIssueResponse(
        UUID id,
        UUID versionId,
        UUID primaryReqId,
        String primaryReqCode,
        String issueCategory,
        String severity,
        String flaggedPhrase,
        String explanation,
        String suggestedRewrite,
        Boolean isResolved,
        OffsetDateTime createdAt
) {
    public static RequirementIssueResponse fromEntity(RequirementIssue issue) {
        return new RequirementIssueResponse(
                issue.getId(),
                issue.getVersion() != null ? issue.getVersion().getId() : null,
                issue.getPrimaryReq() != null ? issue.getPrimaryReq().getId() : null,
                issue.getPrimaryReq() != null ? issue.getPrimaryReq().getReqCode() : null,
                issue.getIssueCategory(),
                issue.getSeverity(),
                issue.getFlaggedPhrase(),
                issue.getExplanation(),
                issue.getSuggestedRewrite(),
                issue.getIsResolved(),
                issue.getCreatedAt()
        );
    }
}
