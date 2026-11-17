package com.reqwise.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PipelineProgressEvent(
    UUID versionId,
    String stage, // QUEUED, PARSING, EXTRACTING, ANALYZING_QUALITY, DETECTING_CONFLICTS, BUILDING_RTM, COMPLETED, FAILED
    double progress, // 0.0 to 100.0
    String message,
    Object data,
    OffsetDateTime timestamp
) {
    public static PipelineProgressEvent of(UUID versionId, String stage, double progress, String message) {
        return new PipelineProgressEvent(versionId, stage, progress, message, null, OffsetDateTime.now());
    }

    public static PipelineProgressEvent of(UUID versionId, String stage, double progress, String message, Object data) {
        return new PipelineProgressEvent(versionId, stage, progress, message, data, OffsetDateTime.now());
    }
}
