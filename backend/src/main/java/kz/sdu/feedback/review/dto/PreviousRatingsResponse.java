package kz.sdu.feedback.review.dto;

import java.util.List;

public record PreviousRatingsResponse(Long teacherId, String teacherName, Long courseId, String courseCode,
                                      String courseName, List<TermRating> ratings) {
    public record TermRating(Long courseOfferingId, String academicTerm, double averageRating, long publishedReviewCount) { }
}
