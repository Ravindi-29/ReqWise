package com.reqwise.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqwise.dto.ProjectCreateRequest;
import com.reqwise.dto.WorkspaceCreateRequest;
import com.reqwise.model.Workspace;
import com.reqwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WorkspaceControllerTest {

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
    private RequirementIssueRepository issueRepository;

    @Autowired
    private TraceabilityLinkRepository linkRepository;

    @Autowired
    private RequirementDiffRepository diffRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    }

    @Test
    void shouldCreateAndListWorkspaces() throws Exception {
        WorkspaceCreateRequest req = new WorkspaceCreateRequest("Enterprise Systems Workspace");

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Enterprise Systems Workspace"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Enterprise Systems Workspace"));
    }

    @Test
    void shouldCreateAndListProjectsUnderWorkspace() throws Exception {
        Workspace workspace = workspaceRepository.save(new Workspace("Avionics Team"));

        ProjectCreateRequest projectReq = new ProjectCreateRequest("Flight Navigation SRS", "Critical DO-178C specification");

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.getId() + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Flight Navigation SRS"))
                .andExpect(jsonPath("$.workspaceId").value(workspace.getId().toString()));

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.getId() + "/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Flight Navigation SRS"));
    }
}
