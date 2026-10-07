package kz.sdu.feedback.review.moderation;

public interface ModerationProvider {
    // Complete feedback only: no rating, account, schedule or catalog identifiers.
    ModerationResult moderate(String feedback);
}
