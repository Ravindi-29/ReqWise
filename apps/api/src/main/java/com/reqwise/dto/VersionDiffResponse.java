package com.reqwise.dto;

import java.util.List;
import java.util.UUID;

public record VersionDiffResponse(
        UUID baseVersionId,
        String baseVersionLabel,
        UUID targetVersionId,
        String targetVersionLabel,
        int totalUnchanged,
        int totalEditorial,
        int totalBreaking,
        int totalAdded,
        int totalDeleted,
        int totalImpacted,
        List<DiffItem> diffs
) {}
