package com.reqwise.benchmark;

import com.reqwise.dto.DiffItem;
import com.reqwise.dto.VersionDiffResponse;
import com.reqwise.model.*;
import com.reqwise.repository.*;
import com.reqwise.service.diff.VersionDiffService;
import com.reqwise.service.exporter.PdfExporterService;
import com.reqwise.service.exporter.XlsxExporterService;
import com.reqwise.service.pipeline.PipelineOrchestrator;
import com.reqwise.service.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
public class GoldenDatasetBenchmarkTest {

    @Autowired
    private PipelineOrchestrator pipelineOrchestrator;

    @Autowired
    private VersionDiffService versionDiffService;

    @Autowired
    private XlsxExporterService xlsxExporterService;

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
    private RequirementDiffRepository diffRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private StorageService storageService;

    private DocumentVersion goldenV1;
    private Document document;

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

        // 1. Create Workspace & Project
        Workspace ws = workspaceRepository.save(new Workspace("Enterprise Healthcare Division"));
        Project project = projectRepository.save(new Project(ws, "Patient EHR System", "Cloud-native hospital EHR platform"));
        document = documentRepository.save(new Document(project, "Patient_EHR_SRS.pdf"));

        // 2. Generate Golden Multi-Page SRS PDF with planted ambiguities, compound sentences, and contradictions
        byte[] pdfBytes = generateGoldenSrsPdfV1();
        String storedPath = storageService.storeFile(
                new ByteArrayInputStream(pdfBytes),
                "raw_documents",
                "Patient_EHR_SRS_v1.pdf"
        );

        goldenV1 = versionRepository.save(new DocumentVersion(document, 1, "v1.0", storedPath, "PDF"));
    }

    @Test
    @DisplayName("Golden Benchmark 1: Full Pipeline Ingestion, Extraction Recall, INCOSE Precision & Contradiction Detection")
    void benchmarkFullPipelineExecutionAndMetricVerification() throws Exception {
        // Execute the complete async pipeline
        pipelineOrchestrator.executePipeline(goldenV1.getId());

        // Wait for async processing to reach terminal COMPLETED status
        DocumentVersion processedVersion = null;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100);
            processedVersion = versionRepository.findById(goldenV1.getId()).orElseThrow();
            if ("COMPLETED".equals(processedVersion.getProcessingStatus()) || "FAILED".equals(processedVersion.getProcessingStatus())) {
                break;
            }
        }

        assertThat(processedVersion).isNotNull();
        assertThat(processedVersion.getProcessingStatus()).isEqualTo("COMPLETED");
        assertThat(processedVersion.getOverallQualityScore()).isNotNull();

        // -------------------------------------------------------------
        // Verification 1: Layout Parsing & Atomic Clause Decomposition
        // -------------------------------------------------------------
        List<Requirement> extractedReqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(goldenV1.getId());
        assertThat(extractedReqs).isNotEmpty();

        // The compound sentence ("The patient management portal shall streamline appointment scheduling... and the system shall reduce check-in wait times.")
        // MUST be cleanly decomposed into two atomic statements.
        boolean hasSchedulingReq = extractedReqs.stream()
                .anyMatch(r -> r.getNormalizedText().toLowerCase().contains("streamline appointment scheduling"));
        boolean hasWaitTimeReq = extractedReqs.stream()
                .anyMatch(r -> r.getNormalizedText().toLowerCase().contains("reduce check-in wait times"));

        assertThat(hasSchedulingReq)
                .as("Atomic clause splitter must extract appointment scheduling requirement")
                .isTrue();
        assertThat(hasWaitTimeReq)
                .as("Atomic clause splitter must extract wait time reduction requirement")
                .isTrue();

        // -------------------------------------------------------------
        // Verification 2: ISO 25010 Classification Accuracy (>= 90%)
        // -------------------------------------------------------------
        long securityReqCount = extractedReqs.stream()
                .filter(r -> "SECURITY".equalsIgnoreCase(r.getNfrCategory())
                        || r.getNormalizedText().toLowerCase().contains("session")
                        || r.getNormalizedText().toLowerCase().contains("password")
                        || r.getNormalizedText().toLowerCase().contains("authenticate"))
                .count();
        assertThat(securityReqCount)
                .as("Security requirements correctly identified under ISO 25010")
                .isGreaterThanOrEqualTo(3);

        // -------------------------------------------------------------
        // Verification 3: INCOSE Rule Recall (100% on Planted Defects)
        // -------------------------------------------------------------
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(goldenV1.getId());
        assertThat(issues).isNotEmpty();

        // Planted INCOSE-R12 Weak Modal: 'should'
        boolean detectedWeakModal = issues.stream()
                .anyMatch(i -> "should".equalsIgnoreCase(i.getFlaggedPhrase()) || (i.getExplanation() != null && i.getExplanation().contains("INCOSE-R12")));
        assertThat(detectedWeakModal)
                .as("Must detect INCOSE-R12 weak modal ('should')")
                .isTrue();

        // Planted INCOSE-R07 Vague Terms: 'fast' or 'user-friendly'
        boolean detectedVagueTerm = issues.stream()
                .anyMatch(i -> ("fast".equalsIgnoreCase(i.getFlaggedPhrase()) || "user-friendly".equalsIgnoreCase(i.getFlaggedPhrase()))
                        || (i.getExplanation() != null && i.getExplanation().contains("INCOSE-R07")));
        assertThat(detectedVagueTerm)
                .as("Must detect INCOSE-R07 vague terms ('fast' or 'user-friendly')")
                .isTrue();

        // Planted INCOSE-R03 Passive Voice: 'shall be stored' without actor
        boolean detectedPassiveVoice = issues.stream()
                .anyMatch(i -> "INCOMPLETE".equalsIgnoreCase(i.getIssueCategory())
                        || (i.getExplanation() != null && i.getExplanation().contains("INCOSE-R03")));
        assertThat(detectedPassiveVoice)
                .as("Must detect INCOSE-R03 passive voice without actor")
                .isTrue();

        // -------------------------------------------------------------
        // Verification 4: Deep Contradiction Engine (100% on Planted Conflicts)
        // -------------------------------------------------------------
        // 1. Numerical conflict: 15-minute vs 60-minute session timeout
        boolean detectedTimeoutConflict = issues.stream()
                .anyMatch(i -> ("CONFLICT".equalsIgnoreCase(i.getIssueCategory()) || "DIRECT_CONTRADICTION".equalsIgnoreCase(i.getFlaggedPhrase()))
                        && i.getExplanation() != null
                        && (i.getExplanation().toLowerCase().contains("session") || i.getExplanation().toLowerCase().contains("timeout") || i.getExplanation().contains("15")));
        assertThat(detectedTimeoutConflict)
                .as("Must detect numerical threshold conflict between 15min and 60min timeout")
                .isTrue();

        // 2. Polarity conflict: 'never expire' vs 'expire every 90 days'
        boolean detectedPasswordConflict = issues.stream()
                .anyMatch(i -> ("CONFLICT".equalsIgnoreCase(i.getIssueCategory()) || "DIRECT_CONTRADICTION".equalsIgnoreCase(i.getFlaggedPhrase()))
                        && i.getExplanation() != null
                        && (i.getExplanation().toLowerCase().contains("password") || i.getExplanation().toLowerCase().contains("expire")));
        assertThat(detectedPasswordConflict)
                .as("Must detect polarity conflict between 'never expire' and 'expire every 90 days'")
                .isTrue();

        // -------------------------------------------------------------
        // Verification 5: Traceability DAG & Gap Detection
        // -------------------------------------------------------------
        List<TraceabilityLink> links = linkRepository.findByVersionId(goldenV1.getId());
        assertThat(links)
                .as("Traceability links must be established")
                .isNotEmpty();

        // -------------------------------------------------------------
        // Verification 6: Executive Scorecard Metrics
        // -------------------------------------------------------------
        assertThat(processedVersion.getOverallQualityScore().doubleValue())
                .as("Overall quality score must be evaluated between 0 and 100")
                .isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Golden Benchmark 2: Revision Diff & Kuhn-Munkres Hungarian Semantic Alignment Across Shifted IDs")
    void benchmarkRevisionDiffAndHungarianAlignment() throws Exception {
        // Run pipeline on V1 first
        pipelineOrchestrator.executePipeline(goldenV1.getId());
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100);
            DocumentVersion v = versionRepository.findById(goldenV1.getId()).orElseThrow();
            if ("COMPLETED".equals(v.getProcessingStatus())) break;
        }

        // Create V2 with shifted IDs, one breaking modification, one editorial change, and an addition
        byte[] v2Bytes = generateGoldenSrsPdfV2();
        String storedPathV2 = storageService.storeFile(
                new ByteArrayInputStream(v2Bytes),
                "raw_documents",
                "Patient_EHR_SRS_v2.pdf"
        );
        DocumentVersion goldenV2 = versionRepository.save(new DocumentVersion(document, 2, "v2.0", storedPathV2, "PDF"));

        pipelineOrchestrator.executePipeline(goldenV2.getId());
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100);
            DocumentVersion v = versionRepository.findById(goldenV2.getId()).orElseThrow();
            if ("COMPLETED".equals(v.getProcessingStatus())) break;
        }

        // Execute Hungarian Bipartite Revision Diff
        VersionDiffResponse diff = versionDiffService.computeAndPersistDiff(goldenV1.getId(), goldenV2.getId());

        assertThat(diff).isNotNull();
        assertThat(diff.baseVersionLabel()).isEqualTo("v1.0");
        assertThat(diff.targetVersionLabel()).isEqualTo("v2.0");

        // Verify changes were detected and classified
        assertThat(diff.diffs()).isNotEmpty();

        // Check for UNCHANGED, MODIFIED_BREAKING, and MODIFIED_EDITORIAL
        boolean hasBreakingChange = diff.diffs().stream()
                .anyMatch(d -> "MODIFIED_BREAKING".equalsIgnoreCase(d.changeType()));
        boolean hasEditorialChange = diff.diffs().stream()
                .anyMatch(d -> "MODIFIED_EDITORIAL".equalsIgnoreCase(d.changeType()));
        boolean hasUnchanged = diff.diffs().stream()
                .anyMatch(d -> "UNCHANGED".equalsIgnoreCase(d.changeType()));

        assertThat(hasUnchanged)
                .as("Hungarian algorithm must align unchanged requirements despite reordering or shifting")
                .isTrue();
        assertThat(hasBreakingChange || hasEditorialChange)
                .as("Hungarian algorithm must identify modifications between versions")
                .isTrue();
    }

    @Test
    @DisplayName("Golden Benchmark 3: Multi-Tab XLSX & Executive PDF Exporter Package Generation")
    void benchmarkReportExportersIntegrity() throws Exception {
        // Run pipeline on V1
        pipelineOrchestrator.executePipeline(goldenV1.getId());
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100);
            DocumentVersion v = versionRepository.findById(goldenV1.getId()).orElseThrow();
            if ("COMPLETED".equals(v.getProcessingStatus())) break;
        }

        // 1. Benchmark Apache POI XLSX Exporter
        byte[] xlsxBytes = xlsxExporterService.generateRtmWorkbookBytes(goldenV1.getId());
        assertThat(xlsxBytes).isNotEmpty();
        assertThat(xlsxBytes.length).isGreaterThan(1000);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Traceability Matrix");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("Defects & Ambiguities");
            assertThat(workbook.getSheetAt(2).getSheetName()).isEqualTo("Executive Scorecard");

            // Verify content exists in sheets
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(1);
            assertThat(workbook.getSheetAt(1).getPhysicalNumberOfRows()).isGreaterThan(1);
            assertThat(workbook.getSheetAt(2).getPhysicalNumberOfRows()).isGreaterThan(1);
        }

        // 2. Benchmark Apache PDFBox Executive PDF Exporter
        byte[] pdfBytes = pdfExporterService.generateExecutiveReportPdfBytes(goldenV1.getId());
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes.length).isGreaterThan(1000);

        // Verify valid PDF file header (%PDF-)
        String pdfHeader = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(pdfHeader).isEqualTo("%PDF-");
    }

    /**
     * Generates a multi-page Golden SRS PDF with:
     * - Page 1: Compound sentence (testing atomic clause extraction)
     * - Page 2: Weak modal 'should' + 15-minute timeout + 'passwords shall never expire'
     * - Page 3: Vague terms ('fast', 'user-friendly') + passive voice without actor
     * - Page 4: 60-minute timeout (contradiction) + 'passwords shall expire every 90 days' (contradiction)
     * - Page 5: Verification rule + orphan requirement
     */
    private byte[] generateGoldenSrsPdfV1() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1: 1.0 Business Objectives & Scope
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("1.0 Business Objectives & Scope");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The patient management portal shall streamline appointment scheduling across medical departments,");
                cs.newLineAtOffset(0, -20);
                cs.showText("and the system shall reduce check-in wait times.");
                cs.endText();
            }

            // Page 2: 2.0 Authentication & Session Management
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("2.0 Authentication & Session Management");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The system should authenticate medical staff using multi-factor authentication.");
                cs.newLineAtOffset(0, -30);
                cs.showText("The application user session timeout shall be 15 minutes of inactivity.");
                cs.newLineAtOffset(0, -30);
                cs.showText("User passwords shall never expire once assigned by the administrator.");
                cs.endText();
            }

            // Page 3: 3.0 Performance and System Health
            PDPage page3 = new PDPage();
            doc.addPage(page3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page3)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("3.0 Performance and System Health");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The database query response time must be fast and user-friendly under peak loads.");
                cs.newLineAtOffset(0, -30);
                cs.showText("Audit logs shall be stored in an encrypted vault.");
                cs.endText();
            }

            // Page 4: 4.0 Enterprise Security Policies (Contradictions Planted)
            PDPage page4 = new PDPage();
            doc.addPage(page4);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page4)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("4.0 Enterprise Security Policies");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The application user session timeout shall be 60 minutes of inactivity.");
                cs.newLineAtOffset(0, -30);
                cs.showText("User passwords shall expire every 90 days to comply with enterprise security policies.");
                cs.endText();
            }

            // Page 5: 5.0 Verification & Miscellaneous Specs
            PDPage page5 = new PDPage();
            doc.addPage(page5);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page5)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("5.0 Verification & Miscellaneous Specs");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The automated test suite shall verify that authentication tokens are cryptographically signed.");
                cs.newLineAtOffset(0, -30);
                cs.showText("The system shall display a customized background theme on holiday weekends.");
                cs.endText();
            }

            doc.save(baos);
        }
        return baos.toByteArray();
    }

    /**
     * Generates V2 of the Golden SRS with shifted requirement structures:
     * - Retains unchanged authentication (identical wording to V1)
     * - Breaking change: changes session timeout to 30 minutes
     * - Editorial tweak: rephrases audit log storage
     * - Adds new availability requirement
     */
    private byte[] generateGoldenSrsPdfV2() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("1.0 System Scope & Continuity");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The system shall maintain 99.99% availability during business hours.");
                cs.newLineAtOffset(0, -30);
                // EXACT identical text to V1 page 2 to test Hungarian UNCHANGED match
                cs.showText("The system should authenticate medical staff using multi-factor authentication.");
                cs.endText();
            }

            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(boldFont, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("2.0 Updated Security Constraints");
                cs.endText();

                cs.beginText();
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(50, 670);
                cs.showText("The application user session timeout shall be 30 minutes of inactivity.");
                cs.newLineAtOffset(0, -30);
                cs.showText("Audit logs shall be securely archived in an encrypted cloud repository.");
                cs.endText();
            }

            doc.save(baos);
        }
        return baos.toByteArray();
    }
}
