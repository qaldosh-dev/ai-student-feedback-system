package kz.sdu.feedback.review.dto;

import java.time.Instant;
import java.util.List;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.entity.ReviewStatus;

/** Current content for the authenticated author only, without account/source identifiers. */
public record OwnReviewResponse(Long reviewId, String courseCode, String courseName, String teacherName,
        String academicTerm, int rating, String text, ReviewStatus status, Instant submittedAt,
        Instant moderatedAt, List<ReviewStatusResponse.ModerationReason> moderationReasons) {
    public static OwnReviewResponse from(Review review) {
        var safe = ReviewStatusResponse.from(review);
        return new OwnReviewResponse(safe.reviewId(), safe.courseCode(), safe.courseName(), safe.teacherName(),
                safe.academicTerm(), safe.rating(), review.getText(), safe.status(), safe.submittedAt(),
                safe.moderatedAt(), safe.moderationReasons());
    }
}
