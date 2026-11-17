package com.reqwise.service.quality;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic linguistic rule matcher implementing:
 * - INCOSE-R12: Weak Modals (should, could, may, might, would)
 * - INCOSE-R07: Vague / Subjective Terms (fast, scalable, user-friendly, etc.)
 * - INCOSE-R03: Passive Voice without Explicit Actor
 */
@Component
public class LinguisticRuleEngine {

    private static final Set<String> WEAK_MODALS = Set.of(
            "should", "could", "may", "might", "would"
    );

    private static final Set<String> VAGUE_TERMS = Set.of(
            "fast", "faster", "fastest",
            "quick", "quickly", "quicker",
            "user-friendly", "user friendly",
            "intuitive",
            "seamless", "seamlessly",
            "scalable", "scalability",
            "flexible", "flexibility",
            "robust", "robustness",
            "efficient", "efficiently",
            "minimal", "minimally",
            "adequate", "adequately",
            "etc", "etc.",
            "and/or",
            "asap", "as soon as possible",
            "appropriate", "appropriately",
            "reasonable", "reasonably",
            "state-of-the-art",
            "easy", "easily",
            "sufficient", "sufficiently",
            "instant", "instantly",
            "modern"
    );

    private static final Pattern WEAK_MODAL_PATTERN = Pattern.compile(
            "\\b(should|could|may|might|would)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Passive voice: auxiliary + past participle not immediately followed by "by <actor>"
    private static final Pattern PASSIVE_VOICE_PATTERN = Pattern.compile(
            "\\b(shall|must|should|will|can|could|is|are|was|were|be|been|being)\\s+([a-zA-Z]+(?:ed|en|written|sent|run|built|done|set|chosen|found|stored|transmitted|received))\\b(?!\\s+by\\s+[a-zA-Z]+)",
            Pattern.CASE_INSENSITIVE
    );

    public List<LinguisticFlag> checkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<LinguisticFlag> flags = new ArrayList<>();
        Set<String> seenFlags = new HashSet<>();

        // Check INCOSE-R12 (Weak modals)
        Matcher modalMatcher = WEAK_MODAL_PATTERN.matcher(text);
        while (modalMatcher.find()) {
            String modal = modalMatcher.group(1);
            String key = "R12:" + modal.toLowerCase();
            if (seenFlags.add(key)) {
                flags.add(new LinguisticFlag(
                        "INCOSE-R12",
                        modal,
                        String.format("Weak modal verb '%s' implies optionality or uncertainty; use 'shall' or 'must' for mandatory requirements per ISO/IEC/IEEE 29148.", modal),
                        "MEDIUM"
                ));
            }
        }

        // Check INCOSE-R07 (Vague terms)
        for (String term : VAGUE_TERMS) {
            Pattern termPattern = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher termMatcher = termPattern.matcher(text);
            if (termMatcher.find()) {
                String key = "R07:" + term.toLowerCase();
                if (seenFlags.add(key)) {
                    flags.add(new LinguisticFlag(
                            "INCOSE-R07",
                            termMatcher.group(),
                            String.format("Subjective or unbounded term '%s' lacks objective, measurable acceptance criteria.", termMatcher.group()),
                            "HIGH"
                    ));
                }
            }
        }

        // Check INCOSE-R03 (Passive voice without explicit actor)
        Matcher passiveMatcher = PASSIVE_VOICE_PATTERN.matcher(text);
        while (passiveMatcher.find()) {
            String verbPhrase = passiveMatcher.group(1) + " " + passiveMatcher.group(2);
            String key = "R03:" + verbPhrase.toLowerCase();
            if (seenFlags.add(key)) {
                flags.add(new LinguisticFlag(
                        "INCOSE-R03",
                        verbPhrase,
                        String.format("Passive construction '%s' omits the system actor responsible for executing the action. State explicitly who or what performs the action.", verbPhrase),
                        "MEDIUM"
                ));
            }
        }

        return flags;
    }
}
