package com.reqwise.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RtmEdge(
        UUID id,
        UUID sourceId,
        UUID targetId,
        String sourceReqCode,
        String targetReqCode,
        String linkType,
        BigDecimal confidenceScore,
        boolean isHumanVerified
) {}
