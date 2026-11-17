package com.reqwise.service.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that decomposes SRS blocks into atomic statements,
 * performs ISO 25010 classification, EARS pattern tagging,
 * runs INCOSE linguistic rules, calculates clarity scores,
 * and generates verifiable IEEE 29148 compliant suggested rewrites.
 */
@Service
public class RequirementAuditorService {

    private static final Logger logger = LoggerFactory.getLogger(RequirementAuditorService.class);

    private final LinguisticRuleEngine ruleEngine;

    @Value("${reqwise.llm.api-key:}")
    private String llmApiKey;

    @Value("${reqwise.llm.model:gpt-4o}")
    private String llmModel;

    private static final Pattern CODE_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:\\[?(?:REQ|FR|NFR|CON|CR|SYS|BR)-?\\s*([0-9A-Za-z.-]+)\\]?)|(?:Section\\s+)?(\\d+(?:\\.\\d+)*))[:.-]?\\s+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMPOUND_SPLIT_PATTERN = Pattern.compile(
            "(?i)\\s*(?:,\\s*)?(?:and|as well as|additionally)\\s+(?:the system|the platform|the application|the service|it|users?)\\s+(?:shall|must|should|will|can)\\s+"
    );

    // EARS Syntax Patterns
    private static final Pattern EARS_EVENT_PATTERN = Pattern.compile(
            "^\\s*(?:when|upon|on receiving|whenever|after)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EARS_STATE_PATTERN = Pattern.compile(
            "^\\s*(?:while|during|in the state of|in\\s+(?:[a-zA-Z]+\\s+)?mode)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EARS_UNWANTED_PATTERN = Pattern.compile(
            "^\\s*(?:if|in the event that|in case of error|in case of failure|should\\s+[a-zA-Z]+\\s+fail)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EARS_OPTIONAL_PATTERN = Pattern.compile(
            "^\\s*(?:where|wherever|if configured|optionally|where available)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // ISO 25010 NFR Keyword Classifiers
    private static final Map<String, List<String>> NFR_KEYWORDS = Map.of(
            "SECURITY", List.of("security", "encrypt", "aes", "tls", "ssl", "auth", "token", "jwt", "rbac", "password", "permission", "vulnerability", "sanitize", "xss", "csrf", "confidential"),
            "PERFORMANCE", List.of("performance", "latency", "response time", "throughput", "millisecond", "second", "tps", "qps", "cpu", "memory", "bandwidth", "fast", "speed", "load"),
            "RELIABILITY", List.of("reliability", "uptime", "99.9", "disaster recovery", "backup", "failover", "mtbf", "recovery", "redundant", "fault-tolerant", "availability", "survive"),
            "SCALABILITY", List.of("scalability", "horizontal scale", "elastic", "load balance", "concurrent users", "cluster", "peak load", "expand"),
            "USABILITY", List.of("usability", "wcag", "aria", "accessibility", "user interface", "dark mode", "screen reader", "intuitive", "user-friendly", "localization", "i18n"),
            "COMPLIANCE", List.of("compliance", "gdpr", "hipaa", "soc2", "pci-dss", "iso 27001", "regulatory", "audit trail", "legal")
    );

    public RequirementAuditorService(LinguisticRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * Audits an SRS block text and produces atomic requirements with quality metrics.
     */
    public BlockExtractionResult auditBlock(String sectionPath, String text, int reqCounterStart) {
        if (text == null || text.isBlank()) {
            return BlockExtractionResult.empty();
        }

        // If an external LLM API key is configured, could invoke LLM API here.
        // Defaults to robust deterministic linguistic and structural auditor.
        return auditBlockDeterministic(sectionPath, text, reqCounterStart);
    }

    public BlockExtractionResult auditBlockDeterministic(String sectionPath, String text, int reqCounterStart) {
        List<String> rawSentences = splitIntoSentences(text);
        List<ExtractedRequirement> extracted = new ArrayList<>();
        int currentCounter = reqCounterStart;

        for (String sentence : rawSentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 12) {
                continue;
            }

            // Check if sentence looks like a requirement or specification statement
            if (!isRequirementCandidate(trimmed, sectionPath)) {
                continue;
            }

            // Split compound requirement sentences into atomic clauses
            List<String> atomicClauses = splitCompoundClauses(trimmed);

            for (String clause : atomicClauses) {
                String atomicText = clause.trim();
                if (atomicText.length() < 10) {
                    continue;
                }

                // Extract or generate requirement code
                String reqCode = extractOrGenerateReqCode(atomicText, sectionPath, currentCounter);
                currentCounter++;

                // Detect requirement type & ISO 25010 category
                String reqType = detectReqType(atomicText, sectionPath);
                String nfrCategory = "NON_FUNCTIONAL".equals(reqType) ? detectNfrCategory(atomicText, sectionPath) : null;

                // Detect EARS pattern
                String earsPattern = detectEarsPattern(atomicText);

                // Detect Priority
                String priority = detectPriority(atomicText);

                // Run INCOSE Linguistic Rules
                List<LinguisticFlag> flags = ruleEngine.checkText(atomicText);

                boolean isAmbiguous = flags.stream().anyMatch(f -> "INCOSE-R07".equals(f.ruleId()) || "INCOSE-R12".equals(f.ruleId()));
                boolean isIncomplete = flags.stream().anyMatch(f -> "INCOSE-R03".equals(f.ruleId()));

                // Calculate Clarity Score
                double clarityScore = calculateClarityScore(flags);

                // Formulate Defect Explanation
                String defectExplanation = buildDefectExplanation(flags);

                // Generate Suggested Rewrite
                String suggestedRewrite = null;
                if (!flags.isEmpty() || clarityScore < 90.0) {
                    suggestedRewrite = generateSuggestedRewrite(atomicText, flags, earsPattern, nfrCategory);
                }

                extracted.add(new ExtractedRequirement(
                        reqCode,
                        trimmed, // raw quote
                        atomicText, // atomic statement
                        reqType,
                        nfrCategory,
                        earsPattern,
                        priority,
                        isAmbiguous,
                        isIncomplete,
                        defectExplanation,
                        suggestedRewrite,
                        clarityScore,
                        flags
                ));
            }
        }

        List<String> missingObservations = detectMissingSectionElements(sectionPath, extracted);

        return new BlockExtractionResult(extracted, missingObservations);
    }

    private List<String> splitIntoSentences(String text) {
        // Splits by sentence terminators or bullet points / numbered lists
        String normalized = text.replaceAll("\\r\\n|\\r", "\n");
        String[] lines = normalized.split("\n+");
        List<String> sentences = new ArrayList<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isBlank()) continue;

            // Split line by sentence boundary (. ! ?) while keeping abbreviations safe
            String[] parts = trimmedLine.split("(?<!\\b(?:e\\.g|i\\.e|etc|vs|Dr|Mr|Ms|v))\\.(?=\\s+[A-Z0-9])|(?<=[!?])\\s+");
            for (String p : parts) {
                String s = p.trim();
                if (!s.isBlank()) {
                    sentences.add(s);
                }
            }
        }
        return sentences;
    }

    private boolean isRequirementCandidate(String text, String sectionPath) {
        String lower = text.toLowerCase();
        String secLower = sectionPath != null ? sectionPath.toLowerCase() : "";

        // Check for modal or requirement keywords
        boolean hasModal = lower.contains("shall") || lower.contains("must") || lower.contains("should")
                || lower.contains("will") || lower.contains("can") || lower.contains("require")
                || lower.contains("support") || lower.contains("allow") || lower.contains("ensure");

        // Check for code prefix
        boolean hasCode = CODE_PREFIX_PATTERN.matcher(text).find();

        // Check if section implies requirements
        boolean isReqSection = secLower.contains("requirement") || secLower.contains("specification")
                || secLower.contains("feature") || secLower.contains("functional") || secLower.contains("security");

        return hasModal || hasCode || isReqSection;
    }

    private List<String> splitCompoundClauses(String sentence) {
        // If the sentence contains multiple "shall", "must", or "should" separated by conjunctions, split it
        Matcher matcher = COMPOUND_SPLIT_PATTERN.matcher(sentence);
        if (!matcher.find()) {
            return List.of(sentence);
        }

        List<String> clauses = new ArrayList<>();
        int lastIndex = 0;
        matcher.reset();

        while (matcher.find()) {
            String firstPart = sentence.substring(lastIndex, matcher.start()).trim();
            if (!firstPart.isBlank()) {
                clauses.add(firstPart);
            }
            // Construct the second atomic clause
            lastIndex = matcher.end();
            String matchedConjunction = matcher.group();
            // Extract the subject and modal from conjunction e.g. "and the system shall"
            String subjectModal = matchedConjunction.replaceFirst("(?i)^[\\s,]+(?:and|as well as|additionally)\\s+", "");
            String remainder = sentence.substring(lastIndex);
            clauses.add((subjectModal + " " + remainder).replaceAll("\\s+", " ").trim());
            return clauses;
        }

        clauses.add(sentence);
        return clauses;
    }

    private String extractOrGenerateReqCode(String text, String sectionPath, int counter) {
        Matcher codeMatcher = CODE_PREFIX_PATTERN.matcher(text);
        if (codeMatcher.find()) {
            String code = codeMatcher.group(1);
            if (code != null && !code.isBlank()) {
                return code.toUpperCase().trim();
            }
        }

        String secLower = sectionPath != null ? sectionPath.toLowerCase() : "";
        String prefix = "FR";
        if (secLower.contains("security") || secLower.contains("performance") || secLower.contains("non-functional")) {
            prefix = "NFR";
        } else if (secLower.contains("constraint")) {
            prefix = "CON";
        }

        return String.format("%s-%03d", prefix, counter);
    }

    private String detectReqType(String text, String sectionPath) {
        String lower = text.toLowerCase();
        String secLower = sectionPath != null ? sectionPath.toLowerCase() : "";

        if (secLower.contains("constraint") || lower.contains("constrained to") || lower.contains("must be developed in")) {
            return "CONSTRAINT";
        }

        if (secLower.contains("non-functional") || secLower.contains("nfr") || secLower.contains("performance")
                || secLower.contains("security") || secLower.contains("reliability") || secLower.contains("usability")) {
            return "NON_FUNCTIONAL";
        }

        for (Map.Entry<String, List<String>> entry : NFR_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    return "NON_FUNCTIONAL";
                }
            }
        }

        return "FUNCTIONAL";
    }

    private String detectNfrCategory(String text, String sectionPath) {
        String combined = (text + " " + (sectionPath != null ? sectionPath : "")).toLowerCase();
        for (Map.Entry<String, List<String>> entry : NFR_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (combined.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return "PERFORMANCE";
    }

    private String detectEarsPattern(String text) {
        if (EARS_EVENT_PATTERN.matcher(text).find()) {
            return "EVENT_DRIVEN";
        }
        if (EARS_STATE_PATTERN.matcher(text).find()) {
            return "STATE_DRIVEN";
        }
        if (EARS_UNWANTED_PATTERN.matcher(text).find()) {
            return "UNWANTED_BEHAVIOR";
        }
        if (EARS_OPTIONAL_PATTERN.matcher(text).find()) {
            return "OPTIONAL";
        }
        return "UBIQUITOUS";
    }

    private String detectPriority(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("shall not") || lower.contains("will not") || lower.contains("won't")) {
            return "WONT";
        }
        if (lower.contains("shall") || lower.contains("must")) {
            return "MUST";
        }
        if (lower.contains("should")) {
            return "SHOULD";
        }
        if (lower.contains("could") || lower.contains("may") || lower.contains("might")) {
            return "COULD";
        }
        return "MUST";
    }

    private double calculateClarityScore(List<LinguisticFlag> flags) {
        if (flags.isEmpty()) {
            return 98.0;
        }

        double score = 100.0;
        for (LinguisticFlag flag : flags) {
            switch (flag.severity()) {
                case "CRITICAL" -> score -= 25.0;
                case "HIGH" -> score -= 18.0;
                case "MEDIUM" -> score -= 12.0;
                case "LOW" -> score -= 6.0;
            }
        }
        return Math.max(10.0, Math.min(100.0, Math.round(score * 100.0) / 100.0));
    }

    private String buildDefectExplanation(List<LinguisticFlag> flags) {
        if (flags.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < flags.size(); i++) {
            LinguisticFlag f = flags.get(i);
            sb.append("[").append(f.ruleId()).append("] ").append(f.reason());
            if (i < flags.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String generateSuggestedRewrite(String originalText, List<LinguisticFlag> flags, String earsPattern, String nfrCategory) {
        String rewrite = originalText.trim();

        // Strip prefix code if present e.g. "FR-01: "
        rewrite = rewrite.replaceFirst("^(?:\\[?[A-Za-z0-9.-]+\\]?[:.-]?\\s+)", "");

        // 1. Replace weak modals with "shall"
        rewrite = rewrite.replaceAll("(?i)\\b(should|could|may|might|would)\\b", "shall");

        // 2. Resolve passive voice without actor by ensuring explicit subject
        if (flags.stream().anyMatch(f -> "INCOSE-R03".equals(f.ruleId()))) {
            if (!rewrite.toLowerCase().startsWith("the system shall") && !rewrite.toLowerCase().startsWith("the application shall")) {
                if (rewrite.toLowerCase().startsWith("the ") || rewrite.toLowerCase().startsWith("user ")) {
                    rewrite = "The system shall ensure that " + rewrite.substring(0, 1).toLowerCase() + rewrite.substring(1);
                } else {
                    rewrite = "The system shall " + rewrite.substring(0, 1).toLowerCase() + rewrite.substring(1);
                }
            }
        }

        // 3. Replace vague terms with quantifiable metrics
        Map<String, String> replacements = Map.of(
                "fast", "within 200 milliseconds (p95)",
                "quickly", "within 250 milliseconds",
                "user-friendly", "compliant with WCAG 2.1 AA and scoring >= 85 on the System Usability Scale (SUS)",
                "seamless", "without manual re-entry or secondary authentication prompts",
                "scalable", "supporting horizontal auto-scaling up to 10,000 concurrent sessions",
                "robust", "with automatic failover and data persistence guarantees",
                "efficient", "maintaining under 60% CPU and memory utilization under peak load",
                "adequate", "satisfying the agreed SLA thresholds",
                "minimal", "less than 0.05% error rate",
                "etc", "and related enumerated parameters"
        );

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            rewrite = rewrite.replaceAll("(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getValue());
        }

        // Ensure requirement ends with a period
        if (!rewrite.endsWith(".")) {
            rewrite = rewrite + ".";
        }

        // Ensure starts with a capitalized letter
        if (!rewrite.isEmpty()) {
            rewrite = Character.toUpperCase(rewrite.charAt(0)) + rewrite.substring(1);
        }

        return rewrite;
    }

    private List<String> detectMissingSectionElements(String sectionPath, List<ExtractedRequirement> extracted) {
        if (sectionPath == null) return List.of();
        String secLower = sectionPath.toLowerCase();
        List<String> observations = new ArrayList<>();

        if (secLower.contains("auth") || secLower.contains("login")) {
            boolean hasBruteForce = extracted.stream().anyMatch(r -> r.atomicStatement().toLowerCase().contains("lockout") || r.atomicStatement().toLowerCase().contains("rate limit"));
            if (!hasBruteForce) {
                observations.add("Missing brute-force protection and rate-limiting policy in authentication specification.");
            }
        }

        if (secLower.contains("payment") || secLower.contains("checkout")) {
            boolean hasRefundOrTimeout = extracted.stream().anyMatch(r -> r.atomicStatement().toLowerCase().contains("timeout") || r.atomicStatement().toLowerCase().contains("idempotency"));
            if (!hasRefundOrTimeout) {
                observations.add("Missing payment gateway idempotency and timeout handling requirements.");
            }
        }

        return observations;
    }
}
