package com.reqwise.dto;

import java.util.List;

public record RtmGraphResponse(
        List<RtmNode> nodes,
        List<RtmEdge> edges,
        int totalRequirements,
        int linkedRequirements,
        int orphanCount,
        int unverifiedCount,
        double coveragePercentage
) {}
