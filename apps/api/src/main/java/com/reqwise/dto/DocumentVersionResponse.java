package com.reqwise.dto;

import com.reqwise.model.DocumentVersion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentVersionResponse(
    UUID id,
    UUID documentId,
    Integer versionNumber,
    String versionLabel,
    String sourceFilePath,
    String previewPdfPath,
    String fileType,
    String processingStatus,
    BigDecimal overallQualityScore,
    String executiveSummary,
    OffsetDateTime createdAt,
    int requirementCount
) {
    public static DocumentVersionResponse fromEntity(DocumentVersion version) {
        return new DocumentVersionResponse(
            version.getId(),
            version.getDocument() != null ? version.getDocument().getId() : null,
            version.getVersionNumber(),
            version.getVersionLabel(),
            version.getSourceFilePath(),
            version.getPreviewPdfPath(),
            version.getFileType(),
            version.getProcessingStatus(),
            version.getOverallQualityScore(),
            version.getExecutiveSummary(),
            version.getCreatedAt(),
            version.getRequirements() != null ? version.getRequirements().size() : 0
        );
    }
}
