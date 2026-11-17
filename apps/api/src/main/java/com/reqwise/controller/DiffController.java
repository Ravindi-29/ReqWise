package com.reqwise.controller;

import com.reqwise.dto.VersionDiffResponse;
import com.reqwise.service.diff.VersionDiffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Revision Diff Engine", description = "Hungarian algorithm bipartite revision tracking and breaking change impact analysis")
public class DiffController {

    private final VersionDiffService diffService;

    public DiffController(VersionDiffService diffService) {
        this.diffService = diffService;
    }

    @GetMapping("/diff")
    @Operation(summary = "Compare two document versions using Hungarian bipartite matching on embeddings")
    public ResponseEntity<VersionDiffResponse> getVersionDiff(
            @RequestParam UUID baseVersionId,
            @RequestParam UUID targetVersionId) {

        VersionDiffResponse response = diffService.computeAndPersistDiff(baseVersionId, targetVersionId);
        return ResponseEntity.ok(response);
    }
}
