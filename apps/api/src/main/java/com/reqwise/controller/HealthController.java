package com.reqwise.controller;

import com.reqwise.dto.HealthResponse;
import com.reqwise.service.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service Health and Diagnostics")
public class HealthController {

    private final StorageService storageService;

    @Value("${spring.application.name:reqwise-api}")
    private String applicationName;

    public HealthController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    @Operation(summary = "Check service health and storage accessibility")
    public ResponseEntity<HealthResponse> checkHealth() {
        HealthResponse response = new HealthResponse(
                "healthy",
                applicationName,
                "0.1.0",
                OffsetDateTime.now(),
                storageService.getStorageRoot().toString()
        );
        return ResponseEntity.ok(response);
    }
}
