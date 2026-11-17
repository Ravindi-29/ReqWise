package com.reqwise.dto;

import com.reqwise.model.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID projectId,
    String title,
    OffsetDateTime createdAt,
    int versionCount,
    List<DocumentVersionResponse> versions
) {
    public static DocumentResponse fromEntity(Document doc) {
        List<DocumentVersionResponse> vers = doc.getVersions() != null
                ? doc.getVersions().stream().map(DocumentVersionResponse::fromEntity).toList()
                : List.of();

        return new DocumentResponse(
            doc.getId(),
            doc.getProject() != null ? doc.getProject().getId() : null,
            doc.getTitle(),
            doc.getCreatedAt(),
            vers.size(),
            vers
        );
    }
}
