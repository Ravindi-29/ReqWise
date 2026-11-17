package com.reqwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.RtmCreateLinkRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RtmAndScorecardControllerTest {

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
    private TraceabilityLinkRepository linkRepository;

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
        linkRepository.deleteAll();
        issueRepository.deleteAll();
        requirementRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        workspaceRepository.deleteAll();

        Workspace ws = workspaceRepository.save(new Workspace("FinTech"));
        Project proj = projectRepository.save(new Project(ws, "Trading Platform", "SRS"));
        Document doc = documentRepository.save(new Document(proj, "Trading_SRS.pdf"));
        testVersion = versionRepository.save(new DocumentVersion(doc, 1, "v1.0", "path.pdf", "PDF"));

        Requirement r1 = new Requirement();
        r1.setVersion(testVersion);
        r1.setReqCode("FR-001");
        r1.setSectionPath("3.1 Order Execution");
        r1.setRawText("The system shall execute market orders within 10ms.");
        r1.setNormalizedText("The system shall execute market orders within 10ms.");
        r1.setReqType("FUNCTIONAL");
        r1.setClarityScore(new BigDecimal("96.00"));
        testReq1 = requirementRepository.save(r1);

        Requirement r2 = new Requirement();
        r2.setVersion(testVersion);
        r2.setReqCode("NFR-001");
        r2.setSectionPath("5.1 Low Latency");
        r2.setRawText("The trading engine latency shall not exceed 15ms.");
        r2.setNormalizedText("The trading engine latency shall not exceed 15ms.");
        r2.setReqType("NON_FUNCTIONAL");
        r2.setNfrCategory("PERFORMANCE");
        r2.setClarityScore(new BigDecimal("94.00"));
        testReq2 = requirementRepository.save(r2);
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/rtm returns graph nodes and edges")
    void shouldGetRtmGraph() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/rtm", testVersion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.totalRequirements").value(2));
    }

    @Test
    @DisplayName("POST and DELETE /api/v1/versions/{versionId}/rtm/links creates and removes manual link")
    void shouldCreateAndDeleteManualLink() throws Exception {
        RtmCreateLinkRequest req = new RtmCreateLinkRequest(testReq2.getId(), testReq1.getId(), "CONSTRAINS");

        String response = mockMvc.perform(post("/api/v1/versions/{versionId}/rtm/links", testVersion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkType").value("CONSTRAINS"))
                .andExpect(jsonPath("$.isHumanVerified").value(true))
                .andReturn().getResponse().getContentAsString();

        String linkId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/v1/rtm/links/{linkId}", linkId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/scorecard returns executive readiness metrics")
    void shouldGetScorecard() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/scorecard", testVersion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequirements").value(2))
                .andExpect(jsonPath("$.readinessIndex").isNumber())
                .andExpect(jsonPath("$.overallClarityScore").isNumber());
    }
}
