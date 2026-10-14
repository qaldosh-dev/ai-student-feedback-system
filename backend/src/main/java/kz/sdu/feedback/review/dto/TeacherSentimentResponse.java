package kz.sdu.feedback.review.dto;

public record TeacherSentimentResponse(Long courseId, String courseCode, String courseName, String academicTerm,
        Status status, boolean analysisAvailable, Integer analyzedReviewCount, OverallSentiment overallSentiment,
        Integer positiveCount, Integer neutralCount, Integer negativeCount,
        Integer positivePercentage, Integer neutralPercentage, Integer negativePercentage, boolean privacyThresholdMet) {
    public enum Status { AVAILABLE, PRIVACY_PROTECTED, UNAVAILABLE }
    public enum OverallSentiment { MOSTLY_POSITIVE, MOSTLY_NEUTRAL, MOSTLY_NEGATIVE, MIXED }
}
