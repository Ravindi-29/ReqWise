package com.reqwise.service.rtm;

import com.reqwise.dto.ExecutiveScorecardResponse;
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
class ExecutiveScorecardServiceTest {

    @Autowired
    private ExecutiveScorecardService scorecardService;

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
    private RequirementIssueRepository issueRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

    private DocumentVersion testVersion;

    @BeforeEach
    void setUp() {
        diffRepository.deleteAll();
        linkRepository.deleteAll();
        issueRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        Workspace ws = workspaceRepository.save(new Workspace("Healthcare IT"));
        Project proj = projectRepository.save(new Project(ws, "EHR Platform", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "EHR_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path.pdf", "PDF"));

        Requirement r1 = new Requirement();
        r1.setVersion(testVersion);
        r1.setReqCode("FR-001");
        r1.setSectionPath("3.1 Patient Records");
        r1.setRawText("The system shall store electronic medical records.");
        r1.setNormalizedText("The system shall store electronic medical records.");
        r1.setReqType("FUNCTIONAL");
        r1.setClarityScore(new BigDecimal("95.00"));
        Requirement req1 = requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(testVersion);
        r2.setReqCode("NFR-001");
        r2.setSectionPath("5.1 HIPAA Security");
        r2.setRawText("The system shall encrypt all PHI with AES-256.");
        r2.setNormalizedText("The system shall encrypt all PHI with AES-256.");
        r2.setReqType("NON_FUNCTIONAL");
        r2.setNfrCategory("SECURITY");
        r2.setClarityScore(new BigDecimal("98.00"));
        Requirement req2 = requirementRepository.save(r2);

        RequirementIssue issue = new RequirementIssue();
        issue.setVersion(testVersion);
        issue.setPrimaryReq(req1);
        issue.setIssueCategory("AMBIGUOUS");
        issue.setSeverity("HIGH");
        issue.setFlaggedPhrase("store");
        issue.setExplanation("Vague storage retention criteria.");
        issue.setIsResolved(false);
        issueRepository.save(issue);
    }

    @Test
    @DisplayName("Generates and persists executive scorecard with readiness index and ISO 25010 breakdown")
    void shouldGenerateAndPersistExecutiveScorecard() {
        ExecutiveScorecardResponse scorecard = scorecardService.generateAndPersistScorecard(testVersion.getId());

        assertThat(scorecard).isNotNull();
        assertThat(scorecard.totalRequirements()).isEqualTo(2);
        assertThat(scorecard.totalIssues()).isEqualTo(1);
        assertThat(scorecard.readinessIndex()).isGreaterThan(0.0);
        assertThat(scorecard.overallClarityScore()).isGreaterThan(90.0);
        assertThat(scorecard.iso25010Breakdown()).containsKeys("FUNCTIONAL", "SECURITY");
        assertThat(scorecard.sectionHealth()).isNotEmpty();

        DocumentVersion updatedVersion = versionRepository.findById(testVersion.getId()).orElseThrow();
        assertThat(updatedVersion.getExecutiveSummary()).isNotNull();
        assertThat(updatedVersion.getExecutiveSummary()).contains("readinessIndex");
    }
}
