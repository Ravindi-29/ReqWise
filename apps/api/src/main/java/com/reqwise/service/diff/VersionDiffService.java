package com.reqwise.service.diff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.DiffItem;
import com.reqwise.dto.VersionDiffResponse;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Requirement;
import com.reqwise.model.RequirementDiff;
import com.reqwise.model.TraceabilityLink;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementDiffRepository;
import com.reqwise.repository.RequirementRepository;
import com.reqwise.repository.TraceabilityLinkRepository;
import com.reqwise.service.vector.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Solves optimal bipartite matching over dense vector embeddings using the Hungarian Algorithm,
 * categorizing requirement drift into UNCHANGED, MODIFIED_EDITORIAL, MODIFIED_BREAKING, ADDED, and DELETED.
 * Propagates breaking changes downstream through the Requirement Traceability Matrix (RTM).
 */
@Service
public class VersionDiffService {

    private static final Logger logger = LoggerFactory.getLogger(VersionDiffService.class);

    private static final double MATCH_THRESHOLD = 0.78;
    private static final double EDITORIAL_THRESHOLD = 0.96;

    private final DocumentVersionRepository versionRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementDiffRepository diffRepository;
    private final TraceabilityLinkRepository linkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public VersionDiffService(
            DocumentVersionRepository versionRepository,
            RequirementRepository requirementRepository,
            RequirementDiffRepository diffRepository,
            TraceabilityLinkRepository linkRepository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.requirementRepository = requirementRepository;
        this.diffRepository = diffRepository;
        this.linkRepository = linkRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VersionDiffResponse computeAndPersistDiff(UUID baseVersionId, UUID targetVersionId) {
        DocumentVersion baseVersion = versionRepository.findById(baseVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Base version not found: " + baseVersionId));
        DocumentVersion targetVersion = versionRepository.findById(targetVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Target version not found: " + targetVersionId));

        // Fetch requirements
        List<Requirement> baseReqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(baseVersionId);
        List<Requirement> targetReqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(targetVersionId);

        // Fetch downstream links for impact propagation
        List<TraceabilityLink> baseLinks = linkRepository.findByVersionId(baseVersionId);
        Map<UUID, List<Requirement>> downstreamImpactMap = new HashMap<>();
        for (TraceabilityLink link : baseLinks) {
            downstreamImpactMap.computeIfAbsent(link.getTargetNode().getId(), k -> new ArrayList<>())
                    .add(link.getSourceNode());
        }

        // Cache vector arrays
        float[][] baseVectors = extractVectors(baseReqs);
        float[][] targetVectors = extractVectors(targetReqs);

        int n = baseReqs.size();
        int m = targetReqs.size();

        double[][] costMatrix = new double[n][m];
        double[][] simMatrix = new double[n][m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double sim = embeddingService.cosineSimilarity(baseVectors[i], targetVectors[j]);
                simMatrix[i][j] = sim;
                costMatrix[i][j] = 1.0 - sim;
            }
        }

        // Run Kuhn-Munkres (Hungarian) optimal bipartite assignment
        HungarianAlgorithm hungarian = new HungarianAlgorithm(costMatrix);
        int[] matchedTargetIndices = hungarian.execute();

        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedTargetSet = new HashSet<>();

        List<RequirementDiff> persistedDiffs = new ArrayList<>();
        List<DiffItem> diffItems = new ArrayList<>();

        int unchangedCount = 0;
        int editorialCount = 0;
        int breakingCount = 0;
        int addedCount = 0;
        int deletedCount = 0;
        int impactedCount = 0;

        // Process matched pairs
        for (int i = 0; i < n; i++) {
            int j = matchedTargetIndices[i];
            if (j >= 0 && j < m) {
                double sim = simMatrix[i][j];
                if (sim >= MATCH_THRESHOLD) {
                    matchedBaseIndices.add(i);
                    matchedTargetSet.add(j);

                    Requirement bReq = baseReqs.get(i);
                    Requirement tReq = targetReqs.get(j);

                    String changeType;
                    String bText = bReq.getNormalizedText() != null ? bReq.getNormalizedText().trim() : "";
                    String tText = tReq.getNormalizedText() != null ? tReq.getNormalizedText().trim() : "";

                    if (bText.equalsIgnoreCase(tText)) {
                        changeType = "UNCHANGED";
                        unchangedCount++;
                    } else if (sim >= EDITORIAL_THRESHOLD) {
                        changeType = "MODIFIED_EDITORIAL";
                        editorialCount++;
                    } else {
                        changeType = "MODIFIED_BREAKING";
                        breakingCount++;
                    }

                    // Downstream impact calculation
                    List<Requirement> impactedReqs = List.of();
                    String impactSummary = null;
                    if ("MODIFIED_BREAKING".equals(changeType)) {
                        impactedReqs = downstreamImpactMap.getOrDefault(bReq.getId(), List.of());
                        if (!impactedReqs.isEmpty()) {
                            impactedCount += impactedReqs.size();
                            List<String> codes = impactedReqs.stream().map(Requirement::getReqCode).toList();
                            impactSummary = String.format("Breaking change propagates to %d downstream requirements: %s",
                                    impactedReqs.size(), String.join(", ", codes));
                        }
                    }

                    RequirementDiff diff = createDiffEntity(baseVersion, targetVersion, bReq, tReq, changeType, sim, impactSummary, impactedReqs);
                    persistedDiffs.add(diff);

                    diffItems.add(new DiffItem(
                            diff.getId(),
                            bReq.getId(),
                            bReq.getReqCode(),
                            bReq.getNormalizedText(),
                            tReq.getId(),
                            tReq.getReqCode(),
                            tReq.getNormalizedText(),
                            changeType,
                            Math.round(sim * 10000.0) / 10000.0,
                            impactSummary,
                            impactedReqs.stream().map(Requirement::getReqCode).toList()
                    ));
                }
            }
        }

        // Process Unmatched Base Requirements (DELETED)
        for (int i = 0; i < n; i++) {
            if (!matchedBaseIndices.contains(i)) {
                Requirement bReq = baseReqs.get(i);
                deletedCount++;

                List<Requirement> impactedReqs = downstreamImpactMap.getOrDefault(bReq.getId(), List.of());
                String impactSummary = null;
                if (!impactedReqs.isEmpty()) {
                    impactedCount += impactedReqs.size();
                    List<String> codes = impactedReqs.stream().map(Requirement::getReqCode).toList();
                    impactSummary = String.format("Deleted requirement orphaned %d downstream requirements: %s",
                            impactedReqs.size(), String.join(", ", codes));
                }

                RequirementDiff diff = createDiffEntity(baseVersion, targetVersion, bReq, null, "DELETED", 0.0, impactSummary, impactedReqs);
                persistedDiffs.add(diff);

                diffItems.add(new DiffItem(
                        diff.getId(),
                        bReq.getId(),
                        bReq.getReqCode(),
                        bReq.getNormalizedText(),
                        null,
                        null,
                        null,
                        "DELETED",
                        0.0,
                        impactSummary,
                        impactedReqs.stream().map(Requirement::getReqCode).toList()
                ));
            }
        }

        // Process Unmatched Target Requirements (ADDED)
        for (int j = 0; j < m; j++) {
            if (!matchedTargetSet.contains(j)) {
                Requirement tReq = targetReqs.get(j);
                addedCount++;

                RequirementDiff diff = createDiffEntity(baseVersion, targetVersion, null, tReq, "ADDED", 0.0, null, List.of());
                persistedDiffs.add(diff);

                diffItems.add(new DiffItem(
                        diff.getId(),
                        null,
                        null,
                        null,
                        tReq.getId(),
                        tReq.getReqCode(),
                        tReq.getNormalizedText(),
                        "ADDED",
                        0.0,
                        null,
                        List.of()
                ));
            }
        }

        diffRepository.saveAll(persistedDiffs);

        return new VersionDiffResponse(
                baseVersion.getId(),
                baseVersion.getVersionLabel(),
                targetVersion.getId(),
                targetVersion.getVersionLabel(),
                unchangedCount,
                editorialCount,
                breakingCount,
                addedCount,
                deletedCount,
                impactedCount,
                diffItems
        );
    }

    private float[][] extractVectors(List<Requirement> reqs) {
        float[][] vectors = new float[reqs.size()][EmbeddingService.VECTOR_DIMENSION];
        for (int i = 0; i < reqs.size(); i++) {
            Requirement req = reqs.get(i);
            if (req.getEmbedding() != null && !req.getEmbedding().isBlank()) {
                vectors[i] = embeddingService.stringToVector(req.getEmbedding());
            } else {
                float[] v = embeddingService.generateEmbedding(req.getNormalizedText() != null ? req.getNormalizedText() : req.getRawText());
                req.setEmbedding(embeddingService.vectorToString(v));
                vectors[i] = v;
            }
        }
        return vectors;
    }

    private RequirementDiff createDiffEntity(
            DocumentVersion baseVersion,
            DocumentVersion targetVersion,
            Requirement bReq,
            Requirement tReq,
            String changeType,
            double sim,
            String impactSummary,
            List<Requirement> impactedReqs) {

        RequirementDiff diff = new RequirementDiff();
        diff.setDocument(baseVersion.getDocument());
        diff.setBaseVersion(baseVersion);
        diff.setTargetVersion(targetVersion);
        diff.setBaseReq(bReq);
        diff.setTargetReq(tReq);
        diff.setChangeType(changeType);
        diff.setSimilarityScore(BigDecimal.valueOf(Math.round(sim * 10000.0) / 10000.0));
        diff.setImpactSummary(impactSummary);

        if (!impactedReqs.isEmpty()) {
            List<String> ids = impactedReqs.stream().map(r -> r.getId().toString()).toList();
            try {
                diff.setImpactedReqIds(objectMapper.writeValueAsString(ids));
            } catch (JsonProcessingException e) {
                diff.setImpactedReqIds("[]");
            }
        } else {
            diff.setImpactedReqIds("[]");
        }

        return diff;
    }
}
