package com.reqwise.dto;

import com.reqwise.model.Workspace;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id,
    String name,
    OffsetDateTime createdAt,
    int projectCount
) {
    public static WorkspaceResponse fromEntity(Workspace workspace) {
        return new WorkspaceResponse(
            workspace.getId(),
            workspace.getName(),
            workspace.getCreatedAt(),
            0
        );
    }

    public static WorkspaceResponse fromEntity(Workspace workspace, int projectCount) {
        return new WorkspaceResponse(
            workspace.getId(),
            workspace.getName(),
            workspace.getCreatedAt(),
            projectCount
        );
    }
}
