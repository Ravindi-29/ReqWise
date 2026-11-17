package com.reqwise.dto;

import java.util.List;
import java.util.UUID;

public record DiffItem(
        UUID id,
        UUID baseReqId,
        String baseReqCode,
        String baseText,
        UUID targetReqId,
        String targetReqCode,
        String targetText,
        String changeType, // UNCHANGED, MODIFIED_EDITORIAL, MODIFIED_BREAKING, ADDED, DELETED
        double similarityScore,
        String impactSummary,
        List<String> impactedReqCodes
) {}
