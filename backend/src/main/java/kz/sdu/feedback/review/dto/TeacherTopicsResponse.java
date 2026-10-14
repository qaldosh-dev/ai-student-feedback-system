package kz.sdu.feedback.review.dto;

import java.util.List;

public record TeacherTopicsResponse(Long courseId, String courseCode, String courseName, String academicTerm,
        Status status, Integer analyzedReviewCount, List<String> topics, List<String> concerns,
        boolean analysisAvailable, boolean enoughRecurringFeedback, boolean privacyThresholdMet) {
    public enum Status { AVAILABLE, PRIVACY_PROTECTED, UNAVAILABLE }
}
