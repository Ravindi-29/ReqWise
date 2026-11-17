package com.reqwise.controller;

import com.reqwise.dto.RequirementResponse;
import com.reqwise.dto.RequirementUpdateRequest;
import com.reqwise.model.Requirement;
import com.reqwise.repository.RequirementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Requirements", description = "Query, filter, and modify atomic requirements")
public class RequirementController {

    private final RequirementRepository requirementRepository;

    public RequirementController(RequirementRepository requirementRepository) {
        this.requirementRepository = requirementRepository;
    }

    @GetMapping("/versions/{versionId}/requirements")
    @Transactional(readOnly = true)
    @Operation(summary = "List requirements for a document version with optional filtering")
    public ResponseEntity<List<RequirementResponse>> getRequirements(
            @PathVariable UUID versionId,
            @RequestParam(required = false) String reqType,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Double minClarity) {

        List<Requirement> requirements = requirementRepository.filterRequirements(versionId, reqType, reviewStatus);

        if (minClarity != null) {
            requirements = requirements.stream()
                    .filter(r -> r.getClarityScore() != null && r.getClarityScore().doubleValue() >= minClarity)
                    .toList();
        }

        List<RequirementResponse> responses = requirements.stream()
                .map(RequirementResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/requirements/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get requirement by ID")
    public ResponseEntity<RequirementResponse> getRequirementById(@PathVariable UUID id) {
        return requirementRepository.findById(id)
                .map(req -> ResponseEntity.ok(RequirementResponse.fromEntity(req)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/requirements/{id}")
    @Transactional
    @Operation(summary = "Update requirement content, type, or review status")
    public ResponseEntity<RequirementResponse> updateRequirement(
            @PathVariable UUID id,
            @RequestBody RequirementUpdateRequest request) {

        Requirement req = requirementRepository.findById(id).orElse(null);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.normalizedText() != null && !request.normalizedText().isBlank()) {
            req.setNormalizedText(request.normalizedText());
        }
        if (request.reqType() != null && !request.reqType().isBlank()) {
            req.setReqType(request.reqType());
        }
        if (request.nfrCategory() != null) {
            req.setNfrCategory(request.nfrCategory());
        }
        if (request.earsPattern() != null) {
            req.setEarsPattern(request.earsPattern());
        }
        if (request.priority() != null) {
            req.setPriority(request.priority());
        }
        if (request.reviewStatus() != null) {
            req.setReviewStatus(request.reviewStatus());
        }

        Requirement updated = requirementRepository.save(req);
        return ResponseEntity.ok(RequirementResponse.fromEntity(updated));
    }
}
