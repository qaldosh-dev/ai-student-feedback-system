package kz.sdu.feedback.review.service;

public class ModerationCooldownException extends RuntimeException {
    private final long retryAfterSeconds;
    public ModerationCooldownException(long retryAfterSeconds) {
        super("Moderation was recently attempted. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
