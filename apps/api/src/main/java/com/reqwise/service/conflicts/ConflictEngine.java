package com.reqwise.service.conflicts;

import com.reqwise.model.Requirement;
import com.reqwise.service.vector.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-stage candidate retrieval and logical contradiction engine.
 * Stage 1: Dense embedding cosine similarity candidate retrieval (threshold >= 0.70).
 * Stage 2: Deep pairwise logical conflict adjudication (numeric, negation, polarity, and duplicates).
 */
@Service
public class ConflictEngine {

    private static final Logger logger = LoggerFactory.getLogger(ConflictEngine.class);

    private final EmbeddingService embeddingService;

    private static final double SIMILARITY_THRESHOLD = 0.70;
    private static final double EXACT_DUPLICATE_THRESHOLD = 0.95;

    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?)\\s*(days?|hours?|hrs?|minutes?|mins?|seconds?|secs?|ms|milliseconds?|%|percent|mb|gb|users?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ConflictEngine(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * Scans a collection of requirements and detects all pairwise duplicates and contradictions.
     */
    public List<ConflictVerdict> detectDuplicatesAndConflicts(List<Requirement> requirements) {
        if (requirements == null || requirements.size() < 2) {
            return List.of();
        }

        // Cache vector representations
        Map<UUID, float[]> vectorMap = new HashMap<>();
        for (Requirement req : requirements) {
            float[] vec;
            if (req.getEmbedding() != null && !req.getEmbedding().isBlank()) {
                vec = embeddingService.stringToVector(req.getEmbedding());
            } else {
                vec = embeddingService.generateEmbedding(req.getNormalizedText() != null ? req.getNormalizedText() : req.getRawText());
                req.setEmbedding(embeddingService.vectorToString(vec));
            }
            vectorMap.put(req.getId(), vec);
        }

        List<ConflictVerdict> verdicts = new ArrayList<>();

        for (int i = 0; i < requirements.size(); i++) {
            Requirement r1 = requirements.get(i);
            float[] vec1 = vectorMap.get(r1.getId());
            String t1 = (r1.getNormalizedText() != null ? r1.getNormalizedText() : r1.getRawText()).toLowerCase();

            for (int j = i + 1; j < requirements.size(); j++) {
                Requirement r2 = requirements.get(j);
                float[] vec2 = vectorMap.get(r2.getId());
                String t2 = (r2.getNormalizedText() != null ? r2.getNormalizedText() : r2.getRawText()).toLowerCase();

                double similarity = embeddingService.cosineSimilarity(vec1, vec2);

                // Check for candidate conflict or duplication
                ConflictVerdict verdict = adjudicatePair(r1, t1, r2, t2, similarity);
                if (verdict != null && !"INDEPENDENT".equals(verdict.relationship())) {
                    verdicts.add(verdict);
                }
            }
        }

        return verdicts;
    }

    private ConflictVerdict adjudicatePair(
            Requirement r1, String t1,
            Requirement r2, String t2,
            double similarity) {

        // 1. Direct Contradiction: Opposing numerical / temporal threshold constraints
        ConflictVerdict numericConflict = checkNumericContradiction(r1, t1, r2, t2, similarity);
        if (numericConflict != null) {
            return numericConflict;
        }

        // 2. Direct Contradiction: Polarity / Negation conflict (e.g. never expire vs shall expire, 24/7 vs offline)
        ConflictVerdict polarityConflict = checkPolarityContradiction(r1, t1, r2, t2, similarity);
        if (polarityConflict != null) {
            return polarityConflict;
        }

        // 3. Exact Duplicate: Near-identical wording or similarity >= 0.95
        if (similarity >= EXACT_DUPLICATE_THRESHOLD || t1.equals(t2)) {
            return new ConflictVerdict(
                    r1,
                    r2,
                    "EXACT_DUPLICATE",
                    "HIGH",
                    "DUPLICATE",
                    String.format("Requirement %s is an exact semantic duplicate of %s with %.1f%% similarity.",
                            r1.getReqCode(), r2.getReqCode(), similarity * 100.0),
                    similarity
            );
        }

        // 4. Semantic Overlap: Shared scope without contradiction
        if (similarity >= SIMILARITY_THRESHOLD) {
            return new ConflictVerdict(
                    r1,
                    r2,
                    "SEMANTIC_OVERLAP",
                    "MEDIUM",
                    "DUPLICATE",
                    String.format("Requirement %s shares high semantic overlap with %s (similarity: %.1f%%). Consider consolidating.",
                            r1.getReqCode(), r2.getReqCode(), similarity * 100.0),
                    similarity
            );
        }

        return null;
    }

    private ConflictVerdict checkNumericContradiction(
            Requirement r1, String t1,
            Requirement r2, String t2,
            double similarity) {

        // Check if both discuss a common topic domain
        List<String> domains = List.of(
                "session", "timeout", "password", "expire", "expiration", "retention",
                "latency", "response time", "upload", "file size", "uptime", "availability"
        );

        String commonDomain = null;
        for (String domain : domains) {
            if (t1.contains(domain) && t2.contains(domain)) {
                commonDomain = domain;
                break;
            }
        }

        if (commonDomain == null && similarity < 0.65) {
            return null;
        }

        Matcher m1 = METRIC_PATTERN.matcher(t1);
        Matcher m2 = METRIC_PATTERN.matcher(t2);

        if (m1.find() && m2.find()) {
            String val1 = m1.group(1);
            String unit1 = normalizeUnit(m1.group(2));

            String val2 = m2.group(1);
            String unit2 = normalizeUnit(m2.group(2));

            if (unit1.equals(unit2) && !val1.equals(val2)) {
                String domainDesc = commonDomain != null ? commonDomain : "metric specification";
                return new ConflictVerdict(
                        r1,
                        r2,
                        "DIRECT_CONTRADICTION",
                        "CRITICAL",
                        "CONFLICT",
                        String.format("Contradictory %s limits: %s specifies '%s %s' while %s specifies '%s %s'.",
                                domainDesc, r1.getReqCode(), val1, unit1, r2.getReqCode(), val2, unit2),
                        Math.max(similarity, 0.85)
                );
            }
        }

        return null;
    }

    private ConflictVerdict checkPolarityContradiction(
            Requirement r1, String t1,
            Requirement r2, String t2,
            double similarity) {

        // Check password expiration polarity: "never expire" vs "expire every / after"
        boolean r1NeverExpires = t1.contains("never expire") || t1.contains("no expiration") || t1.contains("not expire");
        boolean r2NeverExpires = t2.contains("never expire") || t2.contains("no expiration") || t2.contains("not expire");

        boolean r1Expires = (t1.contains("expire") || t1.contains("expiration")) && !r1NeverExpires;
        boolean r2Expires = (t2.contains("expire") || t2.contains("expiration")) && !r2NeverExpires;

        if ((r1NeverExpires && r2Expires) || (r2NeverExpires && r1Expires)) {
            return new ConflictVerdict(
                    r1,
                    r2,
                    "DIRECT_CONTRADICTION",
                    "CRITICAL",
                    "CONFLICT",
                    String.format("Direct contradiction regarding password expiration policy between %s ('%s') and %s ('%s').",
                            r1.getReqCode(), r1.getNormalizedText(), r2.getReqCode(), r2.getNormalizedText()),
                    Math.max(similarity, 0.88)
            );
        }

        // Check availability polarity: "24/7" / "continuous" vs "offline" / "nightly maintenance"
        boolean r1Continuous = t1.contains("24/7") || t1.contains("continuous availability") || t1.contains("100% uptime");
        boolean r2Continuous = t2.contains("24/7") || t2.contains("continuous availability") || t2.contains("100% uptime");

        boolean r1Downtime = t1.contains("offline") || t1.contains("nightly maintenance") || t1.contains("down for maintenance");
        boolean r2Downtime = t2.contains("offline") || t2.contains("nightly maintenance") || t2.contains("down for maintenance");

        if ((r1Continuous && r2Downtime) || (r2Continuous && r1Downtime)) {
            return new ConflictVerdict(
                    r1,
                    r2,
                    "DIRECT_CONTRADICTION",
                    "CRITICAL",
                    "CONFLICT",
                    String.format("Direct contradiction regarding system availability between %s ('%s') and %s ('%s').",
                            r1.getReqCode(), r1.getNormalizedText(), r2.getReqCode(), r2.getNormalizedText()),
                    Math.max(similarity, 0.88)
            );
        }

        // Check generic modal negation: "shall allow" / "shall permit" vs "shall not allow" / "must not"
        boolean r1Permits = (t1.contains("shall allow") || t1.contains("shall permit") || t1.contains("shall support")) && !t1.contains("not");
        boolean r2Prohibits = (t2.contains("shall not allow") || t2.contains("shall prohibit") || t2.contains("must not") || t2.contains("shall reject"));

        if (r1Permits && r2Prohibits && similarity >= 0.60) {
            return new ConflictVerdict(
                    r1,
                    r2,
                    "DIRECT_CONTRADICTION",
                    "CRITICAL",
                    "CONFLICT",
                    String.format("Direct behavioral contradiction: %s permits action while %s prohibits it.",
                            r1.getReqCode(), r2.getReqCode()),
                    similarity
            );
        }

        return null;
    }

    private String normalizeUnit(String unit) {
        String u = unit.toLowerCase();
        if (u.startsWith("day")) return "days";
        if (u.startsWith("hour") || u.equals("hrs") || u.equals("hr")) return "hours";
        if (u.startsWith("min")) return "minutes";
        if (u.startsWith("sec")) return "seconds";
        if (u.equals("ms") || u.startsWith("milli")) return "ms";
        if (u.equals("%") || u.startsWith("percent")) return "percent";
        if (u.startsWith("user")) return "users";
        return u;
    }
}
