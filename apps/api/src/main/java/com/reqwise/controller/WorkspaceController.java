package com.reqwise.controller;

import com.reqwise.dto.ProjectCreateRequest;
import com.reqwise.dto.ProjectResponse;
import com.reqwise.dto.WorkspaceCreateRequest;
import com.reqwise.dto.WorkspaceResponse;
import com.reqwise.model.Project;
import com.reqwise.model.Workspace;
import com.reqwise.repository.ProjectRepository;
import com.reqwise.repository.WorkspaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Workspaces & Projects", description = "Tenant Workspaces and SRS Project Groupings")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;

    public WorkspaceController(WorkspaceRepository workspaceRepository, ProjectRepository projectRepository) {
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/workspaces")
    @Transactional(readOnly = true)
    @Operation(summary = "List all workspaces")
    public ResponseEntity<List<WorkspaceResponse>> listWorkspaces() {
        List<WorkspaceResponse> list = workspaceRepository.findAll().stream()
                .map(WorkspaceResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/workspaces")
    @Transactional
    @Operation(summary = "Create a new organization workspace")
    public ResponseEntity<WorkspaceResponse> createWorkspace(@Valid @RequestBody WorkspaceCreateRequest request) {
        Workspace workspace = new Workspace(request.name());
        Workspace saved = workspaceRepository.save(workspace);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkspaceResponse.fromEntity(saved));
    }

    @GetMapping("/workspaces/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get workspace details by ID")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable UUID id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        return ResponseEntity.ok(WorkspaceResponse.fromEntity(workspace));
    }

    @GetMapping("/workspaces/{id}/projects")
    @Transactional(readOnly = true)
    @Operation(summary = "List all projects in a workspace")
    public ResponseEntity<List<ProjectResponse>> listProjects(@PathVariable UUID id) {
        if (!workspaceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        List<ProjectResponse> projects = projectRepository.findByWorkspaceIdOrderByCreatedAtDesc(id).stream()
                .map(ProjectResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/workspaces/{id}/projects")
    @Transactional
    @Operation(summary = "Create a project inside a workspace")
    public ResponseEntity<ProjectResponse> createProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectCreateRequest request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        Project project = new Project(workspace, request.name(), request.description());
        Project saved = projectRepository.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.fromEntity(saved));
    }

    @GetMapping("/projects/{projectId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return ResponseEntity.ok(ProjectResponse.fromEntity(project));
    }
}
