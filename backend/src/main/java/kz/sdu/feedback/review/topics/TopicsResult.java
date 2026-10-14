package kz.sdu.feedback.review.topics;

import java.util.*;

/** Validated short labels only; no per-review evidence or narrative. */
public record TopicsResult(List<String> topics, List<String> concerns) {
    public static final int MAX_ITEMS = 5;
    public static final int MAX_LABEL_LENGTH = 80;

    public TopicsResult {
        Set<String> seen = new HashSet<>();
        topics = validate(topics, seen);
        concerns = validate(concerns, seen);
    }

    private static List<String> validate(List<String> labels, Set<String> seen) {
        if (labels == null || labels.size() > MAX_ITEMS) throw invalid();
        var result = new ArrayList<String>();
        for (String label : labels) {
            if (label == null || label.codePoints().anyMatch(Character::isISOControl)) throw invalid();
            String normalized = label.strip().replaceAll("(?U)\\s+", " ");
            if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > MAX_LABEL_LENGTH
                    || !seen.add(normalized.toLowerCase(Locale.ROOT))) throw invalid();
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid topic labels.");
    }
}
