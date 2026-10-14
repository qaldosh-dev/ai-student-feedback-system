package kz.sdu.feedback.review.moderation;

/** In-process notification, handled synchronously only after the review commits. */
public record ReviewSubmitted(Long reviewId, String feedback, java.time.Instant attemptAt) { }
