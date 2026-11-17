package com.reqwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.RequirementUpdateRequest;
import com.reqwise.model.*;
import com.reqwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RequirementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private DocumentVersion testVersion;
    private Requirement testReq1;
    private Requirement testReq2;

    @BeforeEach
    void setUp() {
        diffRepository.deleteAll();
        issueRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        Workspace workspace = workspaceRepository.save(new Workspace("FinTech Division"));
        Project project = projectRepository.save(new Project(workspace, "Banking Core", "Requirements"));
        Document document = documentRepository.save(new Document(project, "SRS_Core.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(document, 1, "v1.0", "raw_documents/test.pdf", "PDF"));

        Requirement r1 = new Requirement();
        r1.setVersion(testVersion);
        r1.setReqCode("FR-001");
        r1.setSectionPath("3.1 Auth");
        r1.setRawText("The system shall authenticate users.");
        r1.setNormalizedText("The system shall authenticate users via OAuth 2.0.");
        r1.setReqType("FUNCTIONAL");
        r1.setEarsPattern("UBIQUITOUS");
        r1.setPriority("MUST");
        r1.setClarityScore(new BigDecimal("98.50"));
        r1.setReviewStatus("PENDING");
        testReq1 = requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(testVersion);
        r2.setReqCode("NFR-001");
        r2.setSectionPath("5.1 Performance");
        r2.setRawText("The system shall be fast.");
        r2.setNormalizedText("The system shall respond within 200 milliseconds.");
        r2.setReqType("NON_FUNCTIONAL");
        r2.setNfrCategory("PERFORMANCE");
        r2.setEarsPattern("UBIQUITOUS");
        r2.setPriority("MUST");
        r2.setClarityScore(new BigDecimal("75.00"));
        r2.setReviewStatus("NEEDS_REVISION");
        testReq2 = requirementRepository.save(r2);
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/requirements retrieves all requirements for version")
    void shouldGetRequirementsForVersion() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/requirements", testVersion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reqCode").value("FR-001"))
                .andExpect(jsonPath("$[1].reqCode").value("NFR-001"));
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/requirements filters by reqType")
    void shouldFilterRequirementsByType() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/requirements", testVersion.getId())
                        .param("reqType", "FUNCTIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reqCode").value("FR-001"));
    }

    @Test
    @DisplayName("GET /api/v1/requirements/{id} retrieves specific requirement")
    void shouldGetRequirementById() throws Exception {
        mockMvc.perform(get("/api/v1/requirements/{id}", testReq1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testReq1.getId().toString()))
                .andExpect(jsonPath("$.reqCode").value("FR-001"))
                .andExpect(jsonPath("$.normalizedText").value("The system shall authenticate users via OAuth 2.0."));
    }

    @Test
    @DisplayName("PATCH /api/v1/requirements/{id} updates review status and normalized text")
    void shouldUpdateRequirement() throws Exception {
        RequirementUpdateRequest updateRequest = new RequirementUpdateRequest(
                "The system shall authenticate users via OAuth 2.0 with MFA.",
                null,
                null,
                null,
                null,
                "APPROVED"
        );

        mockMvc.perform(patch("/api/v1/requirements/{id}", testReq1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.normalizedText").value("The system shall authenticate users via OAuth 2.0 with MFA."));
    }
}
