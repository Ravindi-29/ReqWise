package com.reqwise.service.quality;

public record LinguisticFlag(
    String ruleId,
    String flaggedPhrase,
    String reason,
    String severity // CRITICAL, HIGH, MEDIUM, LOW
) {}
