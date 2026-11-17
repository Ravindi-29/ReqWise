package com.reqwise.controller;

import com.reqwise.dto.IssueResolveRequest;
import com.reqwise.dto.RequirementIssueResponse;
import com.reqwise.model.Requirement;
import com.reqwise.model.RequirementIssue;
import com.reqwise.repository.RequirementIssueRepository;
import com.reqwise.repository.RequirementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Requirement Issues", description = "Query, resolve, and auto-apply suggested rewrites for flagged issues")
public class IssueController {

    private final RequirementIssueRepository issueRepository;
    private final RequirementRepository requirementRepository;

    public IssueController(RequirementIssueRepository issueRepository, RequirementRepository requirementRepository) {
        this.issueRepository = issueRepository;
        this.requirementRepository = requirementRepository;
    }

    @GetMapping("/versions/{versionId}/issues")
    @Transactional(readOnly = true)
    @Operation(summary = "List flagged issues for a document version with filtering")
    public ResponseEntity<List<RequirementIssueResponse>> getIssues(
            @PathVariable UUID versionId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isResolved) {

        List<RequirementIssue> issues = issueRepository.filterIssues(versionId, severity, category, isResolved);
        List<RequirementIssueResponse> responses = issues.stream()
                .map(RequirementIssueResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/issues/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get issue by ID")
    public ResponseEntity<RequirementIssueResponse> getIssueById(@PathVariable UUID id) {
        return issueRepository.findById(id)
                .map(issue -> ResponseEntity.ok(RequirementIssueResponse.fromEntity(issue)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/issues/{id}/resolve")
    @Transactional
    @Operation(summary = "Mark an issue as resolved or unresolved")
    public ResponseEntity<RequirementIssueResponse> resolveIssue(
            @PathVariable UUID id,
            @RequestBody(required = false) IssueResolveRequest request) {

        RequirementIssue issue = issueRepository.findById(id).orElse(null);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }

        boolean resolved = (request == null || request.isResolved() == null) || request.isResolved();
        issue.setIsResolved(resolved);
        RequirementIssue updated = issueRepository.save(issue);

        return ResponseEntity.ok(RequirementIssueResponse.fromEntity(updated));
    }

    @PostMapping("/issues/{id}/apply-rewrite")
    @Transactional
    @Operation(summary = "Apply suggested rewrite directly to primary requirement and mark issue resolved")
    public ResponseEntity<RequirementIssueResponse> applyRewrite(@PathVariable UUID id) {
        RequirementIssue issue = issueRepository.findById(id).orElse(null);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }

        if (issue.getSuggestedRewrite() == null || issue.getSuggestedRewrite().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Requirement req = issue.getPrimaryReq();
        if (req != null) {
            req.setNormalizedText(issue.getSuggestedRewrite());
            // Boost clarity score now that suggested rewrite is applied
            if (req.getClarityScore() == null || req.getClarityScore().doubleValue() < 95.0) {
                req.setClarityScore(new BigDecimal("95.00"));
            }
            requirementRepository.save(req);
        }

        issue.setIsResolved(true);
        RequirementIssue updated = issueRepository.save(issue);

        return ResponseEntity.ok(RequirementIssueResponse.fromEntity(updated));
    }
}
