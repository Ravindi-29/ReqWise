package com.reqwise.dto;

import java.util.List;
import java.util.Map;

public record ExecutiveScorecardResponse(
        double readinessIndex,
        double overallClarityScore,
        int totalRequirements,
        int totalIssues,
        double defectDensity,
        double traceabilityCoverage,
        Map<String, Long> severityBreakdown,
        Map<String, Long> categoryBreakdown,
        Map<String, Long> iso25010Breakdown,
        List<SectionHealthSummary> sectionHealth
) {
    public record SectionHealthSummary(
            String sectionPath,
            int requirementCount,
            int issueCount,
            double averageClarity,
            boolean hasCriticalConflict
    ) {}
}
