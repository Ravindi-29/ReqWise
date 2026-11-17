package com.reqwise.service.quality;

import java.util.List;

public record BlockExtractionResult(
        List<ExtractedRequirement> requirements,
        List<String> missingSectionObservations
) {
    public static BlockExtractionResult empty() {
        return new BlockExtractionResult(List.of(), List.of());
    }
}
