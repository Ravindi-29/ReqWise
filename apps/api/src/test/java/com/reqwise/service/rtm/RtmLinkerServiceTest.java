package com.reqwise.service.rtm;

import com.reqwise.dto.RtmCreateLinkRequest;
import com.reqwise.dto.RtmEdge;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class RtmLinkerServiceTest {

    @Autowired
    private RtmLinkerService rtmLinkerService;

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
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

    private DocumentVersion testVersion;
    private Requirement reqGoal;
    private Requirement reqAuth;
    private Requirement reqSec;

    @BeforeEach
    void setUp() {
        diffRepository.deleteAll();
        linkRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        Workspace ws = workspaceRepository.save(new Workspace("Enterprise Banking"));
        Project proj = projectRepository.save(new Project(ws, "Core Payments", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Payments_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path.pdf", "PDF"));

        Requirement r1 = new Requirement();
        r1.setVersion(testVersion);
        r1.setReqCode("BR-001");
        r1.setSectionPath("1.0 Business Vision");
        r1.setRawText("The platform shall provide secure, multi-tenant digital banking.");
        r1.setNormalizedText("The platform shall provide secure, multi-tenant digital banking.");
        r1.setReqType("FUNCTIONAL");
        r1.setClarityScore(new BigDecimal("98.00"));
        reqGoal = requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(testVersion);
        r2.setReqCode("FR-001");
        r2.setSectionPath("3.1 Authentication");
        r2.setRawText("The user login service shall authenticate credentials, satisfying BR-001.");
        r2.setNormalizedText("The user login service shall authenticate credentials, satisfying BR-001.");
        r2.setReqType("FUNCTIONAL");
        r2.setClarityScore(new BigDecimal("95.00"));
        reqAuth = requirementRepository.save(r2);

        Requirement r3 = new Requirement();
        r3.setVersion(testVersion);
        r3.setReqCode("NFR-SEC-01");
        r3.setSectionPath("5.1 Security Constraints");
        r3.setRawText("All auth passwords and credentials shall be hashed with BCrypt.");
        r3.setNormalizedText("All auth passwords and credentials shall be hashed with BCrypt.");
        r3.setReqType("NON_FUNCTIONAL");
        r3.setNfrCategory("SECURITY");
        r3.setClarityScore(new BigDecimal("92.00"));
        reqSec = requirementRepository.save(r3);
    }

    @Test
    @DisplayName("Auto-linker connects requirements by explicit code reference and NFR domain affinity")
    void shouldAutoLinkRequirements() {
        RtmGraphResponse graph = rtmLinkerService.autoLinkRequirements(
                testVersion,
                List.of(reqGoal, reqAuth, reqSec)
        );

        assertThat(graph.nodes()).hasSize(3);
        assertThat(graph.edges()).isNotEmpty();

        // Check that explicit link from FR-001 to BR-001 was discovered
        boolean hasExplicitLink = graph.edges().stream()
                .anyMatch(e -> e.sourceReqCode().equals("FR-001") && e.targetReqCode().equals("BR-001"));
        assertThat(hasExplicitLink).isTrue();

        // Check that NFR-SEC-01 constrains FR-001 due to domain affinity (auth)
        boolean hasNfrConstraint = graph.edges().stream()
                .anyMatch(e -> e.sourceReqCode().equals("NFR-SEC-01") && e.linkType().equals("CONSTRAINS"));
        assertThat(hasNfrConstraint).isTrue();
    }

    @Test
    @DisplayName("Supports manual traceability link creation and deletion")
    void shouldCreateAndManualLink() {
        RtmCreateLinkRequest req = new RtmCreateLinkRequest(reqSec.getId(), reqAuth.getId(), "CONSTRAINS");
        RtmEdge edge = rtmLinkerService.createManualLink(testVersion.getId(), req);

        assertThat(edge.id()).isNotNull();
        assertThat(edge.isHumanVerified()).isTrue();
        assertThat(edge.linkType()).isEqualTo("CONSTRAINS");

        rtmLinkerService.deleteLink(edge.id());
        assertThat(linkRepository.findById(edge.id())).isEmpty();
    }
}
