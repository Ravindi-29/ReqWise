package com.reqwise.controller;

import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DiffAndExportControllerTest {

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
    private RequirementRepository requirementRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

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
        Project proj = projectRepository.save(new Project(ws, "Banking Core", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Core_SRS.pdf"));

        v1 = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path1.pdf", "PDF"));
        v2 = versionRepository.save(new DocumentVersion(doc, 2, "v2.0", "path2.docx", "DOCX"));

        Requirement r1 = new Requirement();
        r1.setVersion(v1);
        r1.setReqCode("FR-001");
        r1.setSectionPath("3.1 Auth");
        r1.setRawText("The system shall authenticate users via OAuth 2.0.");
        r1.setNormalizedText("The system shall authenticate users via OAuth 2.0.");
        r1.setReqType("FUNCTIONAL");
        r1.setClarityScore(new BigDecimal("98.00"));
        requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(v2);
        r2.setReqCode("FR-001");
        r2.setSectionPath("3.1 Auth");
        r2.setRawText("The system shall authenticate users via OAuth 2.0.");
        r2.setNormalizedText("The system shall authenticate users via OAuth 2.0.");
        r2.setReqType("FUNCTIONAL");
        r2.setClarityScore(new BigDecimal("98.00"));
        requirementRepository.save(r2);
    }

    @Test
    @DisplayName("GET /api/v1/diff returns bipartite matching diff response between two versions")
    void shouldGetVersionDiff() throws Exception {
        mockMvc.perform(get("/api/v1/diff")
                        .param("baseVersionId", v1.getId().toString())
                        .param("targetVersionId", v2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVersionId").value(v1.getId().toString()))
                .andExpect(jsonPath("$.targetVersionId").value(v2.getId().toString()))
                .andExpect(jsonPath("$.totalUnchanged").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/export/xlsx downloads multi-tab Excel workbook")
    void shouldExportXlsx() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/export/xlsx", v1.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"RTM_Audit_" + v1.getId() + ".xlsx\""))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/export/pdf downloads Executive Audit PDF")
    void shouldExportPdf() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/export/pdf", v1.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Executive_Audit_" + v1.getId() + ".pdf\""))
                .andExpect(content().contentType("application/pdf"));
    }
}
