package com.reqwise.service.conflicts;

import com.reqwise.model.Requirement;
import com.reqwise.service.vector.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictEngineTest {

    private ConflictEngine conflictEngine;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService();
        conflictEngine = new ConflictEngine(embeddingService);
    }

    private Requirement createReq(String code, String section, String text) {
        Requirement req = new Requirement();
        req.setId(UUID.randomUUID());
        req.setReqCode(code);
        req.setSectionPath(section);
        req.setRawText(text);
        req.setNormalizedText(text);
        req.setReqType("FUNCTIONAL");
        return req;
    }

    @Test
    @DisplayName("Detects numerical threshold contradictions across distant sections (e.g. 15 mins vs 60 mins session timeout)")
    void shouldDetectNumericTimeoutContradiction() {
        Requirement r1 = createReq("SEC-01", "2.4 Session Management",
                "User session shall timeout after 15 minutes of inactivity.");
        Requirement r2 = createReq("SEC-09", "8.2 Admin Console",
                "User session shall timeout after 60 minutes of inactivity.");

        List<ConflictVerdict> verdicts = conflictEngine.detectDuplicatesAndConflicts(List.of(r1, r2));

        assertThat(verdicts).hasSize(1);
        ConflictVerdict v = verdicts.get(0);
        assertThat(v.relationship()).isEqualTo("DIRECT_CONTRADICTION");
        assertThat(v.severity()).isEqualTo("CRITICAL");
        assertThat(v.issueCategory()).isEqualTo("CONFLICT");
        assertThat(v.explanation()).contains("15 minutes").contains("60 minutes");
    }

    @Test
    @DisplayName("Detects password expiration polarity contradiction (never expire vs expire every 90 days)")
    void shouldDetectPasswordExpiryPolarityContradiction() {
        Requirement r1 = createReq("SEC-02", "3.1 Security Policy",
                "Passwords shall never expire for regular user accounts.");
        Requirement r2 = createReq("SEC-14", "7.5 Compliance",
                "All user passwords shall expire every 90 days.");

        List<ConflictVerdict> verdicts = conflictEngine.detectDuplicatesAndConflicts(List.of(r1, r2));

        assertThat(verdicts).hasSize(1);
        ConflictVerdict v = verdicts.get(0);
        assertThat(v.relationship()).isEqualTo("DIRECT_CONTRADICTION");
        assertThat(v.severity()).isEqualTo("CRITICAL");
        assertThat(v.explanation()).contains("password expiration policy");
    }

    @Test
    @DisplayName("Detects 24/7 availability vs scheduled nightly downtime contradiction")
    void shouldDetectAvailabilityContradiction() {
        Requirement r1 = createReq("NFR-REL-01", "5.1 Availability",
                "The system shall provide 24/7 continuous availability with zero scheduled outages.");
        Requirement r2 = createReq("NFR-OPS-04", "9.3 Maintenance",
                "The system shall be offline every night for maintenance between 02:00 and 04:00 UTC.");

        List<ConflictVerdict> verdicts = conflictEngine.detectDuplicatesAndConflicts(List.of(r1, r2));

        assertThat(verdicts).hasSize(1);
        ConflictVerdict v = verdicts.get(0);
        assertThat(v.relationship()).isEqualTo("DIRECT_CONTRADICTION");
        assertThat(v.severity()).isEqualTo("CRITICAL");
        assertThat(v.explanation()).contains("system availability");
    }

    @Test
    @DisplayName("Detects exact semantic duplicate requirements with high similarity")
    void shouldDetectExactDuplicates() {
        Requirement r1 = createReq("FR-01", "3.1 Reports",
                "The system shall allow users to export financial reports in PDF format.");
        Requirement r2 = createReq("FR-18", "6.2 Billing",
                "The system shall allow users to export financial reports in PDF format.");

        List<ConflictVerdict> verdicts = conflictEngine.detectDuplicatesAndConflicts(List.of(r1, r2));

        assertThat(verdicts).hasSize(1);
        ConflictVerdict v = verdicts.get(0);
        assertThat(v.relationship()).isEqualTo("EXACT_DUPLICATE");
        assertThat(v.issueCategory()).isEqualTo("DUPLICATE");
    }

    @Test
    @DisplayName("Does not flag independent and orthogonal requirements")
    void shouldNotFlagIndependentRequirements() {
        Requirement r1 = createReq("FR-01", "3.1 Auth",
                "The system shall authenticate users using OAuth 2.0 with MFA.");
        Requirement r2 = createReq("FR-02", "4.1 Payment",
                "The payment gateway shall process credit card transactions via Stripe API.");

        List<ConflictVerdict> verdicts = conflictEngine.detectDuplicatesAndConflicts(List.of(r1, r2));
        assertThat(verdicts).isEmpty();
    }
}
