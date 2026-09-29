package kz.sdu.feedback.review.dto;

import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.entity.ReviewStatus;

/** Author remains internal. Never serialize the entity graph to a client. */
public record ReviewSubmissionResponse(Long reviewId, boolean submitted, Long courseOfferingId, String academicTerm, int rating, ReviewStatus status) {
    public static ReviewSubmissionResponse from(Review review) {
        return new ReviewSubmissionResponse(review.getId(), true, review.getCourseOffering().getId(), review.getAcademicTerm(), review.getRating(), review.getStatus());
    }
}
