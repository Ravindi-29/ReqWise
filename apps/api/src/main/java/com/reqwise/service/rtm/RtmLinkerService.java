package com.reqwise.service.rtm;

import com.reqwise.dto.RtmCreateLinkRequest;
import com.reqwise.dto.RtmEdge;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.dto.RtmNode;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Requirement;
import com.reqwise.model.TraceabilityLink;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementRepository;
import com.reqwise.repository.TraceabilityLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constructs and manages the Requirement Traceability Matrix (RTM) Directed Acyclic Graph (DAG).
 * Links: Business Goals -> Functional Requirements -> NFR Constraints -> Verification Rules.
 * Detects orphan and unverified specifications per ISO/IEC/IEEE 29148.
 */
@Service
public class RtmLinkerService {

    private static final Logger logger = LoggerFactory.getLogger(RtmLinkerService.class);

    private final TraceabilityLinkRepository linkRepository;
    private final RequirementRepository requirementRepository;
    private final DocumentVersionRepository versionRepository;

    private static final Pattern CODE_REF_PATTERN = Pattern.compile(
            "\\b((?:REQ|FR|NFR|BR|CON|SYS)-\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public RtmLinkerService(
            TraceabilityLinkRepository linkRepository,
            RequirementRepository requirementRepository,
            DocumentVersionRepository versionRepository) {
        this.linkRepository = linkRepository;
        this.requirementRepository = requirementRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * Discovers and persists traceability links for all requirements in a document version.
     */
    @Transactional
    public RtmGraphResponse autoLinkRequirements(DocumentVersion version, List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return new RtmGraphResponse(List.of(), List.of(), 0, 0, 0, 0, 0.0);
        }

        Map<String, Requirement> codeMap = new HashMap<>();
        for (Requirement req : requirements) {
            if (req.getReqCode() != null) {
                codeMap.put(req.getReqCode().toUpperCase(), req);
            }
        }

        List<TraceabilityLink> existingLinks = linkRepository.findByVersionId(version.getId());
        Set<String> existingLinkKeys = new HashSet<>();
        for (TraceabilityLink l : existingLinks) {
            existingLinkKeys.add(linkKey(l.getSourceNode().getId(), l.getTargetNode().getId(), l.getLinkType()));
        }

        List<TraceabilityLink> newLinks = new ArrayList<>();

        // 1. Explicit reference linking in requirement text
        for (Requirement req : requirements) {
            String text = (req.getNormalizedText() != null ? req.getNormalizedText() : req.getRawText());
            Matcher matcher = CODE_REF_PATTERN.matcher(text);
            while (matcher.find()) {
                String refCode = matcher.group(1).toUpperCase();
                if (codeMap.containsKey(refCode) && !refCode.equalsIgnoreCase(req.getReqCode())) {
                    Requirement target = codeMap.get(refCode);
                    String linkType = determineLinkType(req, target);
                    String key = linkKey(req.getId(), target.getId(), linkType);

                    if (existingLinkKeys.add(key)) {
                        TraceabilityLink link = new TraceabilityLink();
                        link.setVersion(version);
                        link.setSourceNode(req);
                        link.setTargetNode(target);
                        link.setLinkType(linkType);
                        link.setConfidenceScore(new BigDecimal("0.9500"));
                        link.setIsHumanVerified(false);
                        newLinks.add(link);
                    }
                }
            }
        }

        // 2. Functional & NFR affinity linking (e.g. NFRs constrain relevant Functional Requirements)
        List<Requirement> nfrs = requirements.stream()
                .filter(r -> "NON_FUNCTIONAL".equals(r.getReqType()) || "CONSTRAINT".equals(r.getReqType()))
                .toList();

        List<Requirement> frs = requirements.stream()
                .filter(r -> "FUNCTIONAL".equals(r.getReqType()))
                .toList();

        for (Requirement nfr : nfrs) {
            String nfrCategory = nfr.getNfrCategory() != null ? nfr.getNfrCategory().toLowerCase() : "";
            String nfrText = (nfr.getNormalizedText() != null ? nfr.getNormalizedText() : "").toLowerCase();

            for (Requirement fr : frs) {
                String frText = (fr.getNormalizedText() != null ? fr.getNormalizedText() : "").toLowerCase();
                boolean domainMatch = false;

                if (nfrCategory.equals("security") && (frText.contains("auth") || frText.contains("login") || frText.contains("password") || frText.contains("token"))) {
                    domainMatch = true;
                } else if (nfrCategory.equals("performance") && (frText.contains("search") || frText.contains("export") || frText.contains("api") || frText.contains("upload"))) {
                    domainMatch = true;
                } else if (nfrCategory.equals("usability") && (frText.contains("view") || frText.contains("ui") || frText.contains("dashboard") || frText.contains("screen"))) {
                    domainMatch = true;
                }

                if (domainMatch) {
                    String key = linkKey(nfr.getId(), fr.getId(), "CONSTRAINS");
                    if (existingLinkKeys.add(key)) {
                        TraceabilityLink link = new TraceabilityLink();
                        link.setVersion(version);
                        link.setSourceNode(nfr);
                        link.setTargetNode(fr);
                        link.setLinkType("CONSTRAINS");
                        link.setConfidenceScore(new BigDecimal("0.8500"));
                        link.setIsHumanVerified(false);
                        newLinks.add(link);
                    }
                }
            }
        }

        // 3. Hierarchical Section Linking (FRs refine top-level section objectives if present)
        Requirement topObjective = requirements.stream()
                .filter(r -> r.getSectionPath() != null && (r.getSectionPath().startsWith("1.") || r.getReqCode().startsWith("BR-")))
                .findFirst()
                .orElse(null);

        if (topObjective != null) {
            for (Requirement fr : frs) {
                if (!fr.getId().equals(topObjective.getId())) {
                    String key = linkKey(fr.getId(), topObjective.getId(), "REFINES");
                    if (existingLinkKeys.add(key)) {
                        TraceabilityLink link = new TraceabilityLink();
                        link.setVersion(version);
                        link.setSourceNode(fr);
                        link.setTargetNode(topObjective);
                        link.setLinkType("REFINES");
                        link.setConfidenceScore(new BigDecimal("0.7800"));
                        link.setIsHumanVerified(false);
                        newLinks.add(link);
                    }
                }
            }
        }

        if (!newLinks.isEmpty()) {
            linkRepository.saveAll(newLinks);
        }

        return getRtmGraph(version.getId());
    }

    /**
     * Fetches current RTM DAG nodes and edges with orphan and unverified gap detection.
     */
    @Transactional(readOnly = true)
    public RtmGraphResponse getRtmGraph(UUID versionId) {
        List<Requirement> requirements = requirementRepository.findByVersionIdOrderByReqCodeAsc(versionId);
        List<TraceabilityLink> links = linkRepository.findByVersionId(versionId);

        Set<UUID> nodesWithUpstream = new HashSet<>();
        Set<UUID> nodesWithDownstream = new HashSet<>();

        List<RtmEdge> edges = new ArrayList<>();
        for (TraceabilityLink link : links) {
            nodesWithDownstream.add(link.getSourceNode().getId());
            nodesWithUpstream.add(link.getTargetNode().getId());

            edges.add(new RtmEdge(
                    link.getId(),
                    link.getSourceNode().getId(),
                    link.getTargetNode().getId(),
                    link.getSourceNode().getReqCode(),
                    link.getTargetNode().getReqCode(),
                    link.getLinkType(),
                    link.getConfidenceScore(),
                    link.getIsHumanVerified()
            ));
        }

        List<RtmNode> nodes = new ArrayList<>();
        int orphanCount = 0;
        int unverifiedCount = 0;

        for (Requirement req : requirements) {
            // A node is an orphan if it has no upstream parent/business link (unless it is a top-level business goal)
            boolean isTopGoal = req.getSectionPath() != null && req.getSectionPath().startsWith("1.");
            boolean isOrphan = !isTopGoal && !nodesWithUpstream.contains(req.getId());
            if (isOrphan) {
                orphanCount++;
            }

            // A node is unverified if it has no downstream test/verification or dependent link
            boolean isUnverified = !nodesWithDownstream.contains(req.getId());
            if (isUnverified) {
                unverifiedCount++;
            }

            nodes.add(new RtmNode(
                    req.getId(),
                    req.getReqCode(),
                    req.getNormalizedText() != null ? req.getNormalizedText() : req.getRawText(),
                    req.getSectionPath(),
                    req.getReqType(),
                    req.getNfrCategory(),
                    req.getClarityScore(),
                    isOrphan,
                    isUnverified
            ));
        }

        Set<UUID> linkedNodeIds = new HashSet<>();
        linkedNodeIds.addAll(nodesWithUpstream);
        linkedNodeIds.addAll(nodesWithDownstream);

        int total = requirements.size();
        int linked = linkedNodeIds.size();
        double coverage = total > 0 ? (double) linked / total * 100.0 : 0.0;

        return new RtmGraphResponse(
                nodes,
                edges,
                total,
                linked,
                orphanCount,
                unverifiedCount,
                Math.round(coverage * 10.0) / 10.0
        );
    }

    @Transactional
    public RtmEdge createManualLink(UUID versionId, RtmCreateLinkRequest request) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
        Requirement source = requirementRepository.findById(request.sourceReqId())
                .orElseThrow(() -> new IllegalArgumentException("Source requirement not found"));
        Requirement target = requirementRepository.findById(request.targetReqId())
                .orElseThrow(() -> new IllegalArgumentException("Target requirement not found"));

        TraceabilityLink link = new TraceabilityLink();
        link.setVersion(version);
        link.setSourceNode(source);
        link.setTargetNode(target);
        link.setLinkType(request.linkType() != null ? request.linkType() : "DEPENDS_ON");
        link.setConfidenceScore(new BigDecimal("1.0000"));
        link.setIsHumanVerified(true);

        TraceabilityLink saved = linkRepository.save(link);
        return new RtmEdge(
                saved.getId(),
                source.getId(),
                target.getId(),
                source.getReqCode(),
                target.getReqCode(),
                saved.getLinkType(),
                saved.getConfidenceScore(),
                saved.getIsHumanVerified()
        );
    }

    @Transactional
    public void deleteLink(UUID linkId) {
        linkRepository.deleteById(linkId);
    }

    private String determineLinkType(Requirement source, Requirement target) {
        if ("NON_FUNCTIONAL".equals(source.getReqType()) || "CONSTRAINT".equals(source.getReqType())) {
            return "CONSTRAINS";
        }
        if (target.getSectionPath() != null && target.getSectionPath().startsWith("1.")) {
            return "REFINES";
        }
        return "DEPENDS_ON";
    }

    private String linkKey(UUID sourceId, UUID targetId, String linkType) {
        return sourceId + "->" + targetId + ":" + linkType;
    }
}
