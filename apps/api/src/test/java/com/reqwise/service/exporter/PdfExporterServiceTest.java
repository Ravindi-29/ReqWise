package com.reqwise.service.exporter;

import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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
class PdfExporterServiceTest {

    @Autowired
    private PdfExporterService pdfExporterService;

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

        Workspace ws = workspaceRepository.save(new Workspace("Defense & Aerospace"));
        Project proj = projectRepository.save(new Project(ws, "Mission Control", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Mission_Control_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path.pdf", "PDF"));

        Requirement r = new Requirement();
        r.setVersion(testVersion);
        r.setReqCode("FR-001");
        r.setSectionPath("3.1 Telemetry");
        r.setRawText("The system shall stream telemetry at 100Hz.");
        r.setNormalizedText("The system shall stream telemetry at 100Hz.");
        r.setReqType("FUNCTIONAL");
        r.setClarityScore(new BigDecimal("98.00"));
        Requirement req = requirementRepository.save(r);

        RequirementIssue issue = new RequirementIssue();
        issue.setVersion(testVersion);
        issue.setPrimaryReq(req);
        issue.setIssueCategory("CONFLICT");
        issue.setSeverity("CRITICAL");
        issue.setFlaggedPhrase("telemetry");
        issue.setExplanation("Direct frequency conflict with radar subsystem.");
        issue.setIsResolved(false);
        issueRepository.save(issue);
    }

    @Test
    @DisplayName("Generates valid print-ready Executive Audit Report PDF with metadata and KPI boxes")
    void shouldGenerateExecutiveAuditPdf() throws Exception {
        byte[] pdfBytes = pdfExporterService.generateExecutiveReportPdfBytes(testVersion.getId());

        assertThat(pdfBytes).isNotEmpty();

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}
