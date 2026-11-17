package com.reqwise.service.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAuditorServiceTest {

    private RequirementAuditorService auditorService;

    @BeforeEach
    void setUp() {
        LinguisticRuleEngine ruleEngine = new LinguisticRuleEngine();
        auditorService = new RequirementAuditorService(ruleEngine);
    }

    @Test
    @DisplayName("Splits compound requirement sentence into atomic statements")
    void shouldSplitCompoundRequirementSentence() {
        String compound = "The system shall authenticate the user using OAuth 2.0, and the system shall log all access attempts to the audit store.";
        BlockExtractionResult result = auditorService.auditBlock("3.1 Security", compound, 1);

        assertThat(result.requirements()).hasSize(2);
        assertThat(result.requirements().get(0).atomicStatement())
                .contains("The system shall authenticate");
        assertThat(result.requirements().get(1).atomicStatement())
                .contains("the system shall log all access attempts");
    }

    @Test
    @DisplayName("Correctly tags EARS syntax patterns across various requirement structures")
    void shouldTagEarsPatterns() {
        // Event-driven
        BlockExtractionResult res1 = auditorService.auditBlock(
                "4.1 Core",
                "When the user clicks submit, the system shall validate form inputs.",
                1
        );
        assertThat(res1.requirements().get(0).earsPattern()).isEqualTo("EVENT_DRIVEN");

        // State-driven
        BlockExtractionResult res2 = auditorService.auditBlock(
                "4.1 Core",
                "While in maintenance mode, the system shall reject incoming API transactions.",
                1
        );
        assertThat(res2.requirements().get(0).earsPattern()).isEqualTo("STATE_DRIVEN");

        // Unwanted behavior
        BlockExtractionResult res3 = auditorService.auditBlock(
                "4.1 Core",
                "If the database connection fails, then the system shall retry up to three times.",
                1
        );
        assertThat(res3.requirements().get(0).earsPattern()).isEqualTo("UNWANTED_BEHAVIOR");

        // Optional
        BlockExtractionResult res4 = auditorService.auditBlock(
                "4.1 Core",
                "Where biometric hardware is available, the system shall offer fingerprint login.",
                1
        );
        assertThat(res4.requirements().get(0).earsPattern()).isEqualTo("OPTIONAL");

        // Ubiquitous
        BlockExtractionResult res5 = auditorService.auditBlock(
                "4.1 Core",
                "The system shall retain all audit logs for a minimum duration of 7 years.",
                1
        );
        assertThat(res5.requirements().get(0).earsPattern()).isEqualTo("UBIQUITOUS");
    }

    @Test
    @DisplayName("Classifies ISO 25010 Non-Functional Requirement categories accurately")
    void shouldClassifyIso25010Categories() {
        // Performance
        BlockExtractionResult perf = auditorService.auditBlock(
                "5.1 Quality",
                "The system shall process checkout requests with a response time latency under 200 milliseconds.",
                1
        );
        assertThat(perf.requirements().get(0).reqType()).isEqualTo("NON_FUNCTIONAL");
        assertThat(perf.requirements().get(0).nfrCategory()).isEqualTo("PERFORMANCE");

        // Security
        BlockExtractionResult sec = auditorService.auditBlock(
                "5.2 Quality",
                "The system shall encrypt all customer credit card numbers using AES-256 encryption.",
                1
        );
        assertThat(sec.requirements().get(0).reqType()).isEqualTo("NON_FUNCTIONAL");
        assertThat(sec.requirements().get(0).nfrCategory()).isEqualTo("SECURITY");

        // Reliability
        BlockExtractionResult rel = auditorService.auditBlock(
                "5.3 Quality",
                "The system shall maintain 99.9% uptime with automated database failover.",
                1
        );
        assertThat(rel.requirements().get(0).reqType()).isEqualTo("NON_FUNCTIONAL");
        assertThat(rel.requirements().get(0).nfrCategory()).isEqualTo("RELIABILITY");

        // Usability
        BlockExtractionResult usa = auditorService.auditBlock(
                "5.4 Quality",
                "The user interface shall adhere to WCAG 2.1 AA accessibility guidelines.",
                1
        );
        assertThat(usa.requirements().get(0).reqType()).isEqualTo("NON_FUNCTIONAL");
        assertThat(usa.requirements().get(0).nfrCategory()).isEqualTo("USABILITY");
    }

    @Test
    @DisplayName("Generates verifiable IEEE 29148 suggested rewrite for ambiguous requirement")
    void shouldGenerateVerifiableSuggestedRewrite() {
        String ambiguous = "The system should be fast and user-friendly.";
        BlockExtractionResult result = auditorService.auditBlock("3.0 General", ambiguous, 1);

        assertThat(result.requirements()).hasSize(1);
        ExtractedRequirement req = result.requirements().get(0);

        assertThat(req.isAmbiguous()).isTrue();
        assertThat(req.clarityScore()).isLessThan(80.0);
        assertThat(req.suggestedRewrite()).isNotNull();
        // Rewrite should replace weak modal 'should' with 'shall'
        assertThat(req.suggestedRewrite()).containsIgnoringCase("shall");
        // Rewrite should quantify 'fast' and 'user-friendly'
        assertThat(req.suggestedRewrite()).contains("200 milliseconds");
        assertThat(req.suggestedRewrite()).contains("WCAG 2.1 AA");
    }
}
