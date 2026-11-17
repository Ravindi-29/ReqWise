package com.reqwise.service.quality;

import java.util.List;

public record ExtractedRequirement(
        String reqCode,
        String rawQuote,
        String atomicStatement,
        String reqType,          // FUNCTIONAL, NON_FUNCTIONAL, CONSTRAINT
        String nfrCategory,      // PERFORMANCE, SECURITY, RELIABILITY, SCALABILITY, USABILITY, COMPLIANCE (or null)
        String earsPattern,      // UBIQUITOUS, EVENT_DRIVEN, STATE_DRIVEN, UNWANTED_BEHAVIOR, OPTIONAL
        String priority,         // MUST, SHOULD, COULD, WONT
        boolean isAmbiguous,
        boolean isIncomplete,
        String defectExplanation,
        String suggestedRewrite,
        double clarityScore,     // 0.0 - 100.0
        List<LinguisticFlag> linguisticFlags
) {}
