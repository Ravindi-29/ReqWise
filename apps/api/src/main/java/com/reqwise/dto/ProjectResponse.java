package com.reqwise.dto;

import com.reqwise.model.Project;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String description,
    OffsetDateTime createdAt,
    int documentCount
) {
    public static ProjectResponse fromEntity(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getWorkspace() != null ? project.getWorkspace().getId() : null,
            project.getName(),
            project.getDescription(),
            project.getCreatedAt(),
            0
        );
    }

    public static ProjectResponse fromEntity(Project project, int documentCount) {
        return new ProjectResponse(
            project.getId(),
            project.getWorkspace() != null ? project.getWorkspace().getId() : null,
            project.getName(),
            project.getDescription(),
            project.getCreatedAt(),
            documentCount
        );
    }
}
