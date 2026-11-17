package com.reqwise.service.diff;

import com.reqwise.dto.DiffItem;
import com.reqwise.dto.VersionDiffResponse;
import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class VersionDiffServiceTest {

    @Autowired
    private VersionDiffService diffService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    private DocumentVersion v1;
    private DocumentVersion v2;

    @BeforeEach
    void setUp() {
        diffRepository.deleteAll();
        linkRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        Workspace ws = workspaceRepository.save(new Workspace("FinTech"));
        Project proj = projectRepository.save(new Project(ws, "Payment Core", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Payments.pdf"));

        v1 = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "v1.pdf", "PDF"));
        v2 = versionRepository.save(new DocumentVersion(doc, 2, "v2.0", "v2.docx", "DOCX"));

        // V1 Requirements
        Requirement r1 = new Requirement();
        r1.setVersion(v1);
        r1.setReqCode("FR-001");
        r1.setSectionPath("3.1 Auth");
        r1.setRawText("The system shall authenticate users via OAuth 2.0.");
        r1.setNormalizedText("The system shall authenticate users via OAuth 2.0.");
        r1.setReqType("FUNCTIONAL");
        r1.setClarityScore(new BigDecimal("98.00"));
        Requirement v1Req1 = requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(v1);
        r2.setReqCode("FR-002");
        r2.setSectionPath("3.2 Payments");
        r2.setRawText("The payment service shall timeout after 5 seconds.");
        r2.setNormalizedText("The payment service shall timeout after 5 seconds.");
        r2.setReqType("FUNCTIONAL");
        r2.setClarityScore(new BigDecimal("95.00"));
        Requirement v1Req2 = requirementRepository.save(r2);

        Requirement r3 = new Requirement();
        r3.setVersion(v1);
        r3.setReqCode("FR-003");
        r3.setSectionPath("3.3 Deprecated Feature");
        r3.setRawText("The system shall support legacy SMS authentication.");
        r3.setNormalizedText("The system shall support legacy SMS authentication.");
        r3.setReqType("FUNCTIONAL");
        r3.setClarityScore(new BigDecimal("90.00"));
        Requirement v1Req3 = requirementRepository.save(r3);

        // V2 Requirements
        // 1. Unchanged from FR-001
        Requirement r2_1 = new Requirement();
        r2_1.setVersion(v2);
        r2_1.setReqCode("FR-101"); // Shifted ID!
        r2_1.setSectionPath("3.1 Auth");
        r2_1.setRawText("The system shall authenticate users via OAuth 2.0.");
        r2_1.setNormalizedText("The system shall authenticate users via OAuth 2.0.");
        r2_1.setReqType("FUNCTIONAL");
        r2_1.setClarityScore(new BigDecimal("98.00"));
        requirementRepository.save(r2_1);

        // 2. Breaking change from FR-002: timeout changed from 5 seconds to 30 seconds
        Requirement r2_2 = new Requirement();
        r2_2.setVersion(v2);
        r2_2.setReqCode("FR-102");
        r2_2.setSectionPath("3.2 Payments");
        r2_2.setRawText("The payment service shall timeout after 30 seconds with automatic retry.");
        r2_2.setNormalizedText("The payment service shall timeout after 30 seconds with automatic retry.");
        r2_2.setReqType("FUNCTIONAL");
        r2_2.setClarityScore(new BigDecimal("95.00"));
        requirementRepository.save(r2_2);

        // 3. New requirement added in V2 (independent domain)
        Requirement r2_new = new Requirement();
        r2_new.setVersion(v2);
        r2_new.setReqCode("FR-104");
        r2_new.setSectionPath("4.1 Inventory");
        r2_new.setRawText("The warehouse service shall track inventory SKU stock levels in real time.");
        r2_new.setNormalizedText("The warehouse service shall track inventory SKU stock levels in real time.");
        r2_new.setReqType("FUNCTIONAL");
        r2_new.setClarityScore(new BigDecimal("96.00"));
        requirementRepository.save(r2_new);

        // Traceability link in V1: FR-002 is constrained by an audit requirement
        Requirement rAudit = new Requirement();
        rAudit.setVersion(v1);
        rAudit.setReqCode("NFR-AUDIT-01");
        rAudit.setSectionPath("5.1 Audit");
        rAudit.setRawText("All payment transactions must be logged to immutable ledger.");
        rAudit.setNormalizedText("All payment transactions must be logged to immutable ledger.");
        rAudit.setReqType("NON_FUNCTIONAL");
        rAudit.setClarityScore(new BigDecimal("99.00"));
        Requirement v1Audit = requirementRepository.save(rAudit);

        TraceabilityLink link = new TraceabilityLink();
        link.setVersion(v1);
        link.setSourceNode(v1Audit);
        link.setTargetNode(v1Req2);
        link.setLinkType("CONSTRAINS");
        link.setConfidenceScore(new BigDecimal("0.9000"));
        linkRepository.save(link);
    }

    @Test
    @DisplayName("Hungarian algorithm aligns shifted IDs and detects UNCHANGED, BREAKING, ADDED, and DELETED changes")
    void shouldComputeSemanticVersionDiff() {
        VersionDiffResponse diff = diffService.computeAndPersistDiff(v1.getId(), v2.getId());

        assertThat(diff).isNotNull();
        assertThat(diff.totalUnchanged()).isEqualTo(1);
        assertThat(diff.totalBreaking()).isEqualTo(1);
        assertThat(diff.totalAdded()).isEqualTo(1);
        assertThat(diff.totalDeleted()).isGreaterThanOrEqualTo(1);

        // Verify UNCHANGED matched FR-001 to shifted FR-101
        DiffItem unchanged = diff.diffs().stream()
                .filter(d -> "UNCHANGED".equals(d.changeType()))
                .findFirst()
                .orElseThrow();
        assertThat(unchanged.baseReqCode()).isEqualTo("FR-001");
        assertThat(unchanged.targetReqCode()).isEqualTo("FR-101");

        // Verify BREAKING change on FR-002 -> FR-102 with downstream impact
        DiffItem breaking = diff.diffs().stream()
                .filter(d -> "MODIFIED_BREAKING".equals(d.changeType()))
                .findFirst()
                .orElseThrow();
        assertThat(breaking.baseReqCode()).isEqualTo("FR-002");
        assertThat(breaking.targetReqCode()).isEqualTo("FR-102");
        assertThat(breaking.impactSummary()).contains("NFR-AUDIT-01");

        // Verify ADDED item is FR-104
        DiffItem added = diff.diffs().stream()
                .filter(d -> "ADDED".equals(d.changeType()))
                .findFirst()
                .orElseThrow();
        assertThat(added.targetReqCode()).isEqualTo("FR-104");
    }
}
