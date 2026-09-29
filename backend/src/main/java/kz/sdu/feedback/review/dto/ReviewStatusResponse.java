package kz.sdu.feedback.review.dto;

import java.time.Instant;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.entity.ReviewStatus;

/** Deliberately excludes the author and schedule/source identifiers. */
public record ReviewStatusResponse(Long reviewId, ReviewStatus status, Instant submittedAt,
        Long courseOfferingId, String courseCode, String courseName, String teacherName,
        String academicTerm, int rating) {
    public static ReviewStatusResponse from(Review review) {
        var offering = review.getCourseOffering();
        return new ReviewStatusResponse(review.getId(), review.getStatus(), review.getCreatedAt(),
                offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getTeacher().getName(), review.getAcademicTerm(), review.getRating());
    }
}
