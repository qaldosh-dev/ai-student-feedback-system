package kz.sdu.feedback.review.summary;

/** Validated prose only, without provider metadata or individual review references. */
public record SummaryResult(String summary) {
    public static final int MAX_LENGTH = 1000;

    public SummaryResult {
        if (summary == null || summary.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))
                || summary.contains("```") || summary.matches("(?s).*(?m)^[ \\t]*(?:#{1,6}\\s|[-*+]\\s|\\d+[.)]\\s).*"))
            throw invalid();
        summary = summary.strip().replaceAll("(?U)\\s+", " ");
        if (summary.isBlank() || summary.codePointCount(0, summary.length()) > MAX_LENGTH) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid summary text.");
    }
}
