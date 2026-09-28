package kz.sdu.feedback.review.dto;

import kz.sdu.feedback.review.entity.Review;

/** Author remains internal. Never serialize the entity graph to a client. */
public record ReviewSubmissionResponse(Long reviewId, boolean submitted, Long courseOfferingId, String academicTerm, int rating) {
    public static ReviewSubmissionResponse from(Review review) {
        return new ReviewSubmissionResponse(review.getId(), true, review.getCourseOffering().getId(), review.getAcademicTerm(), review.getRating());
    }
}
