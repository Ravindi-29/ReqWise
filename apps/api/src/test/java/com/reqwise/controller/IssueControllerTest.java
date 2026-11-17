package com.reqwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.IssueResolveRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IssueControllerTest {

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
    private Requirement testReq;
    private RequirementIssue testIssue;

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

        Requirement r = new Requirement();
        r.setVersion(testVersion);
        r.setReqCode("FR-001");
        r.setSectionPath("3.1 Auth");
        r.setRawText("The system should be fast.");
        r.setNormalizedText("The system should be fast.");
        r.setReqType("FUNCTIONAL");
        r.setEarsPattern("UBIQUITOUS");
        r.setPriority("MUST");
        r.setClarityScore(new BigDecimal("70.00"));
        r.setReviewStatus("NEEDS_REVISION");
        testReq = requirementRepository.save(r);

        RequirementIssue issue = new RequirementIssue();
        issue.setVersion(testVersion);
        issue.setPrimaryReq(testReq);
        issue.setIssueCategory("AMBIGUOUS");
        issue.setSeverity("HIGH");
        issue.setFlaggedPhrase("fast");
        issue.setExplanation("Subjective term 'fast' lacks objective measurable criteria.");
        issue.setSuggestedRewrite("The system shall process requests within 200 milliseconds (p95).");
        issue.setIsResolved(false);
        testIssue = issueRepository.save(issue);
    }

    @Test
    @DisplayName("GET /api/v1/versions/{versionId}/issues retrieves flagged issues")
    void shouldGetIssuesForVersion() throws Exception {
        mockMvc.perform(get("/api/v1/versions/{versionId}/issues", testVersion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flaggedPhrase").value("fast"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].isResolved").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/issues/{id}/resolve marks issue as resolved")
    void shouldResolveIssue() throws Exception {
        IssueResolveRequest req = new IssueResolveRequest(true);

        mockMvc.perform(patch("/api/v1/issues/{id}/resolve", testIssue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isResolved").value(true));

        RequirementIssue updated = issueRepository.findById(testIssue.getId()).orElseThrow();
        assertThat(updated.getIsResolved()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/issues/{id}/apply-rewrite updates requirement statement and resolves issue")
    void shouldApplyRewriteToRequirement() throws Exception {
        mockMvc.perform(post("/api/v1/issues/{id}/apply-rewrite", testIssue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isResolved").value(true));

        Requirement updatedReq = requirementRepository.findById(testReq.getId()).orElseThrow();
        assertThat(updatedReq.getNormalizedText()).isEqualTo("The system shall process requests within 200 milliseconds (p95).");
        assertThat(updatedReq.getClarityScore().doubleValue()).isGreaterThanOrEqualTo(95.0);

        RequirementIssue updatedIssue = issueRepository.findById(testIssue.getId()).orElseThrow();
        assertThat(updatedIssue.getIsResolved()).isTrue();
    }
}
