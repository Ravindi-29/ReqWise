package com.reqwise.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RtmNode(
        UUID id,
        String reqCode,
        String label,
        String sectionPath,
        String reqType,
        String nfrCategory,
        BigDecimal clarityScore,
        boolean isOrphan,
        boolean isUnverified
) {}
