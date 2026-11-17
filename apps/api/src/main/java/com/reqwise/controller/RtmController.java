package com.reqwise.controller;

import com.reqwise.dto.RtmCreateLinkRequest;
import com.reqwise.dto.RtmEdge;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.service.rtm.RtmLinkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Traceability Matrix (RTM)", description = "Requirement Traceability Matrix DAG, orphan/gap detection, and manual linking")
public class RtmController {

    private final RtmLinkerService rtmLinkerService;

    public RtmController(RtmLinkerService rtmLinkerService) {
        this.rtmLinkerService = rtmLinkerService;
    }

    @GetMapping("/versions/{versionId}/rtm")
    @Operation(summary = "Get RTM Graph nodes, edges, orphan/unverified gap metrics, and coverage rate")
    public ResponseEntity<RtmGraphResponse> getRtmGraph(@PathVariable UUID versionId) {
        RtmGraphResponse response = rtmLinkerService.getRtmGraph(versionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/versions/{versionId}/rtm/links")
    @Operation(summary = "Create a manual human-verified traceability link between requirements")
    public ResponseEntity<RtmEdge> createLink(
            @PathVariable UUID versionId,
            @RequestBody RtmCreateLinkRequest request) {
        RtmEdge edge = rtmLinkerService.createManualLink(versionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(edge);
    }

    @DeleteMapping("/rtm/links/{linkId}")
    @Operation(summary = "Delete a traceability link by ID")
    public ResponseEntity<Void> deleteLink(@PathVariable UUID linkId) {
        rtmLinkerService.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}
