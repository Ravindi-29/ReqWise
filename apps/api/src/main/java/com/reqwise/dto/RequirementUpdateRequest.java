package com.reqwise.dto;

public record RequirementUpdateRequest(
        String normalizedText,
        String reqType,
        String nfrCategory,
        String earsPattern,
        String priority,
        String reviewStatus
) {}
