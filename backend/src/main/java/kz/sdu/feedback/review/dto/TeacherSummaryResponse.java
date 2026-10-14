package kz.sdu.feedback.review.dto;

public record TeacherSummaryResponse(Long courseId, String courseCode, String courseName, String academicTerm,
        Status status, Integer analyzedReviewCount, String summary, boolean analysisAvailable, boolean enoughFeedback, boolean privacyThresholdMet) {
    public enum Status { AVAILABLE, PRIVACY_PROTECTED, UNAVAILABLE }
}
