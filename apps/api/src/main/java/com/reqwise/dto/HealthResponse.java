package com.reqwise.dto;

import java.time.OffsetDateTime;

public record HealthResponse(
    String status,
    String service,
    String version,
    OffsetDateTime timestamp,
    String storagePath
) {}
