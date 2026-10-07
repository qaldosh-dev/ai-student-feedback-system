package kz.sdu.feedback.review.moderation;

import java.util.Set;

/** Only consistent, known decisions can reach the persistence layer. */
public record ModerationResult(Decision decision, Set<ModerationCategory> categories) {
    public enum Decision { ACCEPT, NEEDS_CORRECTION }

    public ModerationResult {
        if (decision == null || categories == null)
            throw new IllegalArgumentException("Incomplete moderation result.");
        categories = Set.copyOf(categories);
        if ((decision == Decision.ACCEPT) != categories.isEmpty())
            throw new IllegalArgumentException("Inconsistent moderation result.");
    }
}
