package com.reqwise.service.pipeline;

import com.reqwise.model.*;
import com.reqwise.repository.*;
import com.reqwise.service.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class PipelineOrchestratorExtractionTest {

    @Autowired
    private PipelineOrchestrator pipelineOrchestrator;

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
    private RequirementDiffRepository diffRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private StorageService storageService;

    private DocumentVersion testVersion;

    @BeforeEach
    void setUp() throws Exception {
        diffRepository.deleteAll();
        linkRepository.deleteAll();
        issueRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        // Generate mock PDF with a structured section and requirements
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(50, 700);
                cs.showText("3.0 Security Requirements");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 650);
                cs.showText("The system should authenticate users using OAuth 2.0, and the system shall log all attempts.");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 600);
                cs.showText("The application must be fast and user-friendly.");
                cs.endText();
            }
            doc.save(baos);
        }

        String storedPath = storageService.storeFile(
                new ByteArrayInputStream(baos.toByteArray()),
                "raw_documents",
                "test_srs.pdf"
        );

        Workspace ws = workspaceRepository.save(new Workspace("Defense & Aerospace"));
        Project proj = projectRepository.save(new Project(ws, "Avionics SRS", "Flight systems"));
        Document doc = documentRepository.save(new Document(proj, "Avionics_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", storedPath, "PDF"));
    }

    @Test
    @DisplayName("End-to-end Pipeline: Ingests PDF, decomposes into atomic statements, flags INCOSE issues, and scores version quality")
    void shouldExecutePipelineEndToEnd() throws Exception {
        pipelineOrchestrator.executePipeline(testVersion.getId());

        // Wait up to 5 seconds for async processing to reach terminal state
        DocumentVersion updatedVersion = null;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            updatedVersion = versionRepository.findById(testVersion.getId()).orElseThrow();
            if ("COMPLETED".equals(updatedVersion.getProcessingStatus()) || "FAILED".equals(updatedVersion.getProcessingStatus())) {
                break;
            }
        }

        assertThat(updatedVersion).isNotNull();
        assertThat(updatedVersion.getProcessingStatus()).isEqualTo("COMPLETED");
        assertThat(updatedVersion.getOverallQualityScore()).isNotNull();
        assertThat(updatedVersion.getExecutiveSummary()).isNotNull();

        List<Requirement> extractedReqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(testVersion.getId());
        assertThat(extractedReqs).isNotEmpty();

        // Verifies compound sentence splitting occurred
        assertThat(extractedReqs.size()).isGreaterThanOrEqualTo(3);

        // Verifies issues were flagged for weak modals ('should') and vague terms ('fast', 'user-friendly')
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(testVersion.getId());
        assertThat(issues).isNotEmpty();

        boolean hasWeakModalIssue = issues.stream().anyMatch(i -> "should".equalsIgnoreCase(i.getFlaggedPhrase()));
        boolean hasVagueTermIssue = issues.stream().anyMatch(i -> "fast".equalsIgnoreCase(i.getFlaggedPhrase()) || "user-friendly".equalsIgnoreCase(i.getFlaggedPhrase()));

        assertThat(hasWeakModalIssue || hasVagueTermIssue).isTrue();
    }
}
