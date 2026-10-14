package kz.sdu.feedback.review.service;

public final class AnalyticsPrivacyPolicy {
    public static final int MIN_ANALYTICS_REVIEW_COUNT = 3;

    private AnalyticsPrivacyPolicy() { }

    public static boolean thresholdMet(long publishedReviewCount) {
        return publishedReviewCount >= MIN_ANALYTICS_REVIEW_COUNT;
    }
}
