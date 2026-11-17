package com.reqwise.service.exporter;

import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class XlsxExporterServiceTest {

    @Autowired
    private XlsxExporterService xlsxExporterService;

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

        Workspace ws = workspaceRepository.save(new Workspace("Enterprise Corp"));
        Project proj = projectRepository.save(new Project(ws, "Portal", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Portal_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path.pdf", "PDF"));

        Requirement r = new Requirement();
        r.setVersion(testVersion);
        r.setReqCode("FR-001");
        r.setSectionPath("3.1 Auth");
        r.setRawText("The system shall authenticate users.");
        r.setNormalizedText("The system shall authenticate users.");
        r.setReqType("FUNCTIONAL");
        r.setClarityScore(new BigDecimal("95.00"));
        Requirement req = requirementRepository.save(r);

        RequirementIssue issue = new RequirementIssue();
        issue.setVersion(testVersion);
        issue.setPrimaryReq(req);
        issue.setIssueCategory("AMBIGUOUS");
        issue.setSeverity("MEDIUM");
        issue.setFlaggedPhrase("users");
        issue.setExplanation("Ambiguous user scope.");
        issue.setIsResolved(false);
        issueRepository.save(issue);
    }

    @Test
    @DisplayName("Generates valid multi-sheet Excel workbook with RTM, Defects, and Scorecard sheets")
    void shouldGenerateMultiSheetExcelWorkbook() throws Exception {
        byte[] xlsxBytes = xlsxExporterService.generateRtmWorkbookBytes(testVersion.getId());

        assertThat(xlsxBytes).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Traceability Matrix");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("Defects & Ambiguities");
            assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("Executive Scorecard");

            // Verify headers in sheet 1
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Req ID");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("FR-001");
        }
    }
}
