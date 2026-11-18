package com.reqwise.service.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.ExecutiveScorecardResponse;
import com.reqwise.dto.PipelineProgressEvent;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Requirement;
import com.reqwise.model.RequirementIssue;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementIssueRepository;
import com.reqwise.repository.RequirementRepository;
import com.reqwise.service.conflicts.ConflictEngine;
import com.reqwise.service.conflicts.ConflictVerdict;
import com.reqwise.service.parser.DocumentParser;
import com.reqwise.service.parser.DocumentParserFactory;
import com.reqwise.service.parser.ParsedBlock;
import com.reqwise.service.quality.BlockExtractionResult;
import com.reqwise.service.quality.ExtractedRequirement;
import com.reqwise.service.quality.LinguisticFlag;
import com.reqwise.service.quality.RequirementAuditorService;
import com.reqwise.service.rtm.ExecutiveScorecardService;
import com.reqwise.service.rtm.RtmLinkerService;
import com.reqwise.service.storage.StorageService;
import com.reqwise.service.vector.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PipelineOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final DocumentVersionRepository versionRepository;
    private final DocumentParserFactory parserFactory;
    private final StorageService storageService;
    private final SseNotificationService sseService;
    private final ObjectMapper objectMapper;
    private final RequirementAuditorService auditorService;
    private final RequirementRepository requirementRepository;
    private final RequirementIssueRepository requirementIssueRepository;
    private final EmbeddingService embeddingService;
    private final ConflictEngine conflictEngine;
    private final RtmLinkerService rtmLinkerService;
    private final ExecutiveScorecardService scorecardService;

    public PipelineOrchestrator(
            DocumentVersionRepository versionRepository,
            DocumentParserFactory parserFactory,
            StorageService storageService,
            SseNotificationService sseService,
            ObjectMapper objectMapper,
            RequirementAuditorService auditorService,
            RequirementRepository requirementRepository,
            RequirementIssueRepository requirementIssueRepository,
            EmbeddingService embeddingService,
            ConflictEngine conflictEngine,
            RtmLinkerService rtmLinkerService,
            ExecutiveScorecardService scorecardService) {
        this.versionRepository = versionRepository;
        this.parserFactory = parserFactory;
        this.storageService = storageService;
        this.sseService = sseService;
        this.objectMapper = objectMapper;
        this.auditorService = auditorService;
        this.requirementRepository = requirementRepository;
        this.requirementIssueRepository = requirementIssueRepository;
        this.embeddingService = embeddingService;
        this.conflictEngine = conflictEngine;
        this.rtmLinkerService = rtmLinkerService;
        this.scorecardService = scorecardService;
    }

    @Async("pipelineTaskExecutor")
    @Transactional
    public void executePipeline(UUID versionId) {
        logger.info("Starting processing pipeline for document version: {}", versionId);

        // Wait up to 3 seconds for database transaction commit from HTTP thread
        DocumentVersion version = null;
        for (int i = 0; i < 30; i++) {
            version = versionRepository.findById(versionId).orElse(null);
            if (version != null) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }

        if (version == null) {
            logger.error("Document version {} not found after timeout", versionId);
            sseService.fail(versionId, "Version not found: " + versionId);
            return;
        }

        // Brief delay allowing client EventSource to attach before initial events stream
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}

        try {
            // ==========================================
            // Stage 1: Document Layout Parsing (PDF/DOCX)
            // ==========================================
            version.setProcessingStatus("PARSING");
            versionRepository.save(version);
            sseService.emitProgress(PipelineProgressEvent.of(versionId, "PARSING", 15.0, "Parsing document layout and section hierarchy..."));

            Path filePath = storageService.loadFile(version.getSourceFilePath());
            DocumentParser parser = parserFactory.getParser(version.getFileType());
            List<ParsedBlock> blocks = parser.parse(filePath);

            sseService.emitProgress(PipelineProgressEvent.of(
                    versionId,
                    "PARSING",
                    40.0,
                    "Extracted " + blocks.size() + " structural blocks across document pages",
                    Map.of("blockCount", blocks.size())
            ));

            // Persist parsed blocks as preview artifact
            byte[] blocksJson = objectMapper.writeValueAsBytes(blocks);
            String previewKey = storageService.storeFile(
                    new ByteArrayInputStream(blocksJson),
                    "previews",
                    "blocks_" + versionId + ".json"
            );
            version.setPreviewPdfPath(previewKey);
            versionRepository.save(version);

            // ==========================================
            // Stage 2: Atomic Extraction & Quality Audit
            // ==========================================
            version.setProcessingStatus("EXTRACTING");
            versionRepository.save(version);
            sseService.emitProgress(PipelineProgressEvent.of(
                    versionId,
                    "EXTRACTING",
                    50.0,
                    "Decomposing text blocks into atomic ISO/IEC/IEEE 29148 requirements..."
            ));

            List<Requirement> savedRequirements = new ArrayList<>();
            List<RequirementIssue> savedIssues = new ArrayList<>();
            int reqCounter = 1;

            for (ParsedBlock block : blocks) {
                BlockExtractionResult result = auditorService.auditBlock(
                        block.sectionPath(),
                        block.text(),
                        reqCounter
                );

                for (ExtractedRequirement extracted : result.requirements()) {
                    Requirement req = new Requirement();
                    req.setVersion(version);
                    req.setReqCode(extracted.reqCode());
                    req.setSectionPath(block.sectionPath());
                    req.setRawText(extracted.rawQuote());
                    req.setNormalizedText(extracted.atomicStatement());
                    req.setReqType(extracted.reqType());
                    req.setNfrCategory(extracted.nfrCategory());
                    req.setEarsPattern(extracted.earsPattern());
                    req.setPriority(extracted.priority());
                    req.setClarityScore(BigDecimal.valueOf(extracted.clarityScore()));
                    req.setPageNumber(block.pageNumber());
                    req.setBoundingBox(block.boundingBox() != null ? objectMapper.writeValueAsString(block.boundingBox()) : null);

                    // Generate 1536-dimensional embedding
                    float[] embedding = embeddingService.generateEmbedding(extracted.atomicStatement());
                    req.setEmbedding(embeddingService.vectorToString(embedding));
                    req.setReviewStatus("PENDING");

                    Requirement persisted = requirementRepository.save(req);
                    savedRequirements.add(persisted);
                    reqCounter++;

                    // Create issues for flagged ambiguities, weak modals, and passive omissions
                    for (LinguisticFlag flag : extracted.linguisticFlags()) {
                        String category = "AMBIGUOUS";
                        if ("INCOSE-R03".equals(flag.ruleId())) {
                            category = "INCOMPLETE";
                        }

                        RequirementIssue issue = new RequirementIssue();
                        issue.setVersion(version);
                        issue.setPrimaryReq(persisted);
                        issue.setIssueCategory(category);
                        issue.setSeverity(flag.severity());
                        issue.setFlaggedPhrase(flag.flaggedPhrase());
                        issue.setExplanation(flag.reason());
                        issue.setSuggestedRewrite(extracted.suggestedRewrite());
                        issue.setIsResolved(false);

                        RequirementIssue persistedIssue = requirementIssueRepository.save(issue);
                        savedIssues.add(persistedIssue);
                    }
                }
            }

            // ==========================================
            // Stage 3: Vector Deduplication & Conflict Detection
            // ==========================================
            version.setProcessingStatus("DETECTING_CONFLICTS");
            versionRepository.save(version);
            sseService.emitProgress(PipelineProgressEvent.of(
                    versionId,
                    "DETECTING_CONFLICTS",
                    70.0,
                    "Executing pairwise cosine similarity retrieval and contradiction verification..."
            ));

            List<ConflictVerdict> conflictVerdicts = conflictEngine.detectDuplicatesAndConflicts(savedRequirements);
            for (ConflictVerdict cv : conflictVerdicts) {
                RequirementIssue issue = new RequirementIssue();
                issue.setVersion(version);
                issue.setPrimaryReq(cv.primaryReq());
                issue.setRelatedReq(cv.relatedReq());
                issue.setIssueCategory(cv.issueCategory());
                issue.setSeverity(cv.severity());
                issue.setFlaggedPhrase(cv.relationship());
                issue.setExplanation(cv.explanation());
                issue.setSimilarityScore(BigDecimal.valueOf(Math.round(cv.similarityScore() * 10000.0) / 10000.0));
                issue.setIsResolved(false);

                RequirementIssue savedConflict = requirementIssueRepository.save(issue);
                savedIssues.add(savedConflict);
            }

            // ==========================================
            // Stage 4: Requirement Traceability Matrix (RTM) & Scorecard
            // ==========================================
            version.setProcessingStatus("BUILDING_RTM");
            versionRepository.save(version);
            sseService.emitProgress(PipelineProgressEvent.of(
                    versionId,
                    "BUILDING_RTM",
                    85.0,
                    "Constructing Traceability DAG and evaluating executive readiness scorecard..."
            ));

            RtmGraphResponse rtmGraph = rtmLinkerService.autoLinkRequirements(version, savedRequirements);
            ExecutiveScorecardResponse scorecard = scorecardService.generateAndPersistScorecard(versionId);

            // ==========================================
            // Stage 5: Final Quality Score & Completion
            // ==========================================
            double avgClarity = savedRequirements.isEmpty() ? 100.0 :
                    savedRequirements.stream()
                            .mapToDouble(r -> r.getClarityScore().doubleValue())
                            .average()
                            .orElse(100.0);

            version.setOverallQualityScore(BigDecimal.valueOf(Math.round(avgClarity * 100.0) / 100.0));
            version.setProcessingStatus("COMPLETED");
            versionRepository.save(version);

            sseService.complete(
                    versionId,
                    String.format("Audit pipeline completed. Requirements: %d | Issues: %d | Conflicts: %d | Readiness Index: %.1f | Traceability: %.1f%%",
                            savedRequirements.size(),
                            savedIssues.size(),
                            conflictVerdicts.size(),
                            scorecard.readinessIndex(),
                            rtmGraph.coveragePercentage())
            );

            logger.info("Pipeline completed successfully for version {}: {} reqs, {} issues, {} conflicts, readiness: {}",
                    versionId, savedRequirements.size(), savedIssues.size(), conflictVerdicts.size(), scorecard.readinessIndex());

        } catch (Exception e) {
            logger.error("Pipeline failure for version {}: {}", versionId, e.getMessage(), e);
            version.setProcessingStatus("FAILED");
            versionRepository.save(version);
            sseService.fail(versionId, "Pipeline execution error: " + e.getMessage());
        }
    }

    public List<ParsedBlock> getParsedBlocks(UUID versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        if (version.getPreviewPdfPath() == null) {
            return List.of();
        }

        try {
            Path previewFile = storageService.loadFile(version.getPreviewPdfPath());
            byte[] bytes = Files.readAllBytes(previewFile);
            return objectMapper.readValue(
                    bytes,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ParsedBlock.class)
            );
        } catch (Exception e) {
            logger.error("Failed to load parsed blocks for version {}", versionId, e);
            throw new RuntimeException("Could not retrieve parsed blocks", e);
        }
    }
}
