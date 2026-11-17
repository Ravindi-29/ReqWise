package com.reqwise.service.rtm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.ExecutiveScorecardResponse;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Requirement;
import com.reqwise.model.RequirementIssue;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementIssueRepository;
import com.reqwise.repository.RequirementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes executive readiness index (0-100), defect density, ISO 25010 breakdown,
 * and section-level health diagnostics for SRS auditing.
 */
@Service
public class ExecutiveScorecardService {

    private static final Logger logger = LoggerFactory.getLogger(ExecutiveScorecardService.class);

    private final RequirementRepository requirementRepository;
    private final RequirementIssueRepository issueRepository;
    private final DocumentVersionRepository versionRepository;
    private final RtmLinkerService rtmLinkerService;
    private final ObjectMapper objectMapper;

    public ExecutiveScorecardService(
            RequirementRepository requirementRepository,
            RequirementIssueRepository issueRepository,
            DocumentVersionRepository versionRepository,
            RtmLinkerService rtmLinkerService,
            ObjectMapper objectMapper) {
        this.requirementRepository = requirementRepository;
        this.issueRepository = issueRepository;
        this.versionRepository = versionRepository;
        this.rtmLinkerService = rtmLinkerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExecutiveScorecardResponse generateAndPersistScorecard(UUID versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        List<Requirement> reqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(versionId);
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(versionId);
        RtmGraphResponse rtmGraph = rtmLinkerService.getRtmGraph(versionId);

        ExecutiveScorecardResponse scorecard = calculateScorecard(reqs, issues, rtmGraph);

        try {
            String json = objectMapper.writeValueAsString(scorecard);
            version.setExecutiveSummary(json);
            versionRepository.save(version);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize executive scorecard for version {}", versionId, e);
        }

        return scorecard;
    }

    @Transactional(readOnly = true)
    public ExecutiveScorecardResponse getScorecard(UUID versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        if (version.getExecutiveSummary() != null && !version.getExecutiveSummary().isBlank()) {
            try {
                return objectMapper.readValue(version.getExecutiveSummary(), ExecutiveScorecardResponse.class);
            } catch (JsonProcessingException e) {
                logger.warn("Could not deserialize saved executive scorecard, recalculating...", e);
            }
        }

        List<Requirement> reqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(versionId);
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(versionId);
        RtmGraphResponse rtmGraph = rtmLinkerService.getRtmGraph(versionId);

        return calculateScorecard(reqs, issues, rtmGraph);
    }

    private ExecutiveScorecardResponse calculateScorecard(
            List<Requirement> reqs,
            List<RequirementIssue> issues,
            RtmGraphResponse rtmGraph) {

        int totalReqs = reqs.size();
        int totalIssues = issues.size();

        double avgClarity = totalReqs > 0 ?
                reqs.stream().mapToDouble(r -> r.getClarityScore() != null ? r.getClarityScore().doubleValue() : 100.0).average().orElse(100.0) : 100.0;

        double defectDensity = totalReqs > 0 ? (double) totalIssues / totalReqs : 0.0;
        double coverage = rtmGraph.coveragePercentage();

        // Severity Breakdown
        Map<String, Long> severityBreakdown = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getSeverity() != null ? i.getSeverity() : "MEDIUM", Collectors.counting()));

        // Category Breakdown
        Map<String, Long> categoryBreakdown = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getIssueCategory() != null ? i.getIssueCategory() : "AMBIGUOUS", Collectors.counting()));

        // ISO 25010 Breakdown
        Map<String, Long> isoBreakdown = new LinkedHashMap<>();
        for (Requirement r : reqs) {
            String key = "NON_FUNCTIONAL".equals(r.getReqType()) && r.getNfrCategory() != null ?
                    r.getNfrCategory() : r.getReqType();
            isoBreakdown.put(key, isoBreakdown.getOrDefault(key, 0L) + 1);
        }

        // Section Health Breakdown
        Map<String, List<Requirement>> sectionReqs = reqs.stream()
                .collect(Collectors.groupingBy(r -> r.getSectionPath() != null ? r.getSectionPath() : "General"));

        Map<UUID, List<RequirementIssue>> reqIssueMap = issues.stream()
                .filter(i -> i.getPrimaryReq() != null)
                .collect(Collectors.groupingBy(i -> i.getPrimaryReq().getId()));

        List<ExecutiveScorecardResponse.SectionHealthSummary> sectionHealth = new ArrayList<>();
        for (Map.Entry<String, List<Requirement>> entry : sectionReqs.entrySet()) {
            String sec = entry.getKey();
            List<Requirement> sReqs = entry.getValue();

            int secIssues = 0;
            boolean hasCriticalConflict = false;
            double secClaritySum = 0.0;

            for (Requirement r : sReqs) {
                secClaritySum += (r.getClarityScore() != null ? r.getClarityScore().doubleValue() : 100.0);
                List<RequirementIssue> rIssues = reqIssueMap.getOrDefault(r.getId(), List.of());
                secIssues += rIssues.size();
                for (RequirementIssue iss : rIssues) {
                    if ("CRITICAL".equalsIgnoreCase(iss.getSeverity()) || "CONFLICT".equalsIgnoreCase(iss.getIssueCategory())) {
                        hasCriticalConflict = true;
                    }
                }
            }

            double secAvgClarity = sReqs.isEmpty() ? 100.0 : secClaritySum / sReqs.size();
            sectionHealth.add(new ExecutiveScorecardResponse.SectionHealthSummary(
                    sec,
                    sReqs.size(),
                    secIssues,
                    Math.round(secAvgClarity * 10.0) / 10.0,
                    hasCriticalConflict
            ));
        }

        // Calculate Overall Readiness Index (0 - 100)
        long criticalCount = severityBreakdown.getOrDefault("CRITICAL", 0L);
        long highCount = severityBreakdown.getOrDefault("HIGH", 0L);
        long mediumCount = severityBreakdown.getOrDefault("MEDIUM", 0L);

        double baseScore = (avgClarity * 0.40) + (coverage * 0.30) + 30.0;
        baseScore -= (criticalCount * 15.0);
        baseScore -= (highCount * 5.0);
        baseScore -= (mediumCount * 2.0);

        double readinessIndex = Math.max(5.0, Math.min(100.0, Math.round(baseScore * 10.0) / 10.0));

        return new ExecutiveScorecardResponse(
                readinessIndex,
                Math.round(avgClarity * 10.0) / 10.0,
                totalReqs,
                totalIssues,
                Math.round(defectDensity * 100.0) / 100.0,
                Math.round(coverage * 10.0) / 10.0,
                severityBreakdown,
                categoryBreakdown,
                isoBreakdown,
                sectionHealth
        );
    }
}
