package com.reqwise.controller;

import com.reqwise.dto.ExecutiveScorecardResponse;
import com.reqwise.service.rtm.ExecutiveScorecardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Executive Scorecard", description = "Quality scorecard, defect density, ISO 25010 breakdown, and readiness index")
public class ScorecardController {

    private final ExecutiveScorecardService scorecardService;

    public ScorecardController(ExecutiveScorecardService scorecardService) {
        this.scorecardService = scorecardService;
    }

    @GetMapping("/versions/{versionId}/scorecard")
    @Operation(summary = "Get executive readiness index, defect density, ISO 25010 and section health metrics")
    public ResponseEntity<ExecutiveScorecardResponse> getScorecard(@PathVariable UUID versionId) {
        ExecutiveScorecardResponse response = scorecardService.getScorecard(versionId);
        return ResponseEntity.ok(response);
    }
}
