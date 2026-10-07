package kz.sdu.feedback.review.dto;

import java.time.Instant;
import java.util.List;
import kz.sdu.feedback.review.moderation.ModerationCategory;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.entity.ReviewStatus;

/** Deliberately excludes the author and schedule/source identifiers. */
public record ReviewStatusResponse(Long reviewId, ReviewStatus status, Instant submittedAt,
        Long courseOfferingId, String courseCode, String courseName, String teacherName,
        String academicTerm, int rating, Instant moderatedAt, List<ModerationReason> moderationReasons, String text) {
    public record ModerationReason(ModerationCategory category, String message) { }
    public static ReviewStatusResponse from(Review review) {
        var offering = review.getCourseOffering();
        return new ReviewStatusResponse(review.getId(), review.getStatus(), review.getCreatedAt(),
                offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getTeacher().getName(), review.getAcademicTerm(), review.getRating(), review.getModeratedAt(),
                review.getModerationCategories().stream().sorted()
                        .map(category -> new ModerationReason(category, category.message())).toList(),
                review.getStatus() == ReviewStatus.NEEDS_CORRECTION ? review.getText() : null);
    }
}
