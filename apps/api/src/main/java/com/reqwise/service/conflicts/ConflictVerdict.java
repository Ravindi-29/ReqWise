package com.reqwise.service.conflicts;

import com.reqwise.model.Requirement;

public record ConflictVerdict(
        Requirement primaryReq,
        Requirement relatedReq,
        String relationship, // DIRECT_CONTRADICTION, EXACT_DUPLICATE, SEMANTIC_OVERLAP, INDEPENDENT
        String severity,     // CRITICAL, HIGH, MEDIUM, LOW
        String issueCategory,// CONFLICT, DUPLICATE
        String explanation,
        double similarityScore
) {}
