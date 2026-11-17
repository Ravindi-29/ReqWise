package com.reqwise.controller;

import com.reqwise.model.Project;
import com.reqwise.model.Workspace;
import com.reqwise.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private RequirementIssueRepository issueRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    private Project testProject;

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

        Workspace workspace = workspaceRepository.save(new Workspace("Engineering Division"));
        testProject = projectRepository.save(new Project(workspace, "Flight Control System", "SRS Analysis"));
    }

    @Test
    void shouldUploadPdfAndTriggerPipeline() throws Exception {
        // Create mock PDF bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 700);
                cs.showText("1.0 Navigation Specifications");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 650);
                cs.showText("The autopilot shall maintain pitch within 2 degrees.");
                cs.endText();
            }
            doc.save(baos);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "navigation_srs_v1.pdf",
                "application/pdf",
                baos.toByteArray()
        );

        mockMvc.perform(multipart("/api/v1/projects/" + testProject.getId() + "/documents/upload")
                        .file(file)
                        .param("title", "Flight Navigation SRS")
                        .param("versionLabel", "v1.0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.versionLabel").value("v1.0"))
                .andExpect(jsonPath("$.fileType").value("PDF"));
    }
}
