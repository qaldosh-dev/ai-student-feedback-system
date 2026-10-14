package kz.sdu.feedback.review.service;

import java.util.List;
import kz.sdu.feedback.review.dto.TeacherSentimentResponse;
import kz.sdu.feedback.review.dto.TeacherSentimentResponse.Status;
import kz.sdu.feedback.review.dto.TeacherSentimentResponse.OverallSentiment;
import kz.sdu.feedback.review.sentiment.SentimentProvider;
import kz.sdu.feedback.review.sentiment.SentimentProvider.Sentiment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SentimentAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SentimentAnalysisService.class);
    private final TeacherAnalyticsService analytics;
    private final SentimentProvider provider;

    public SentimentAnalysisService(TeacherAnalyticsService analytics, SentimentProvider provider) {
        this.analytics = analytics;
        this.provider = provider;
    }

    public TeacherSentimentResponse analyze(String email, Long courseId, String term) {
        var input = analytics.sentimentInput(email, courseId, term);
        var course = input.course();
        if (!input.privacyThresholdMet()) return unavailable(input, Status.PRIVACY_PROTECTED);
        try {
            List<Sentiment> labels = provider.classify(input.texts());
            if (labels == null || labels.size() != input.texts().size() || labels.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException();
            int[] counts = new int[3];
            for (var label : labels) counts[label.ordinal()]++;
            int total = labels.size();
            int[] percentages = percentages(counts, total);
            OverallSentiment overall = counts[0] > counts[1] && counts[0] > counts[2] ? OverallSentiment.MOSTLY_POSITIVE
                    : counts[1] > counts[0] && counts[1] > counts[2] ? OverallSentiment.MOSTLY_NEUTRAL
                    : counts[2] > counts[0] && counts[2] > counts[1] ? OverallSentiment.MOSTLY_NEGATIVE
                    : OverallSentiment.MIXED;
            return new TeacherSentimentResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.AVAILABLE, true, total, overall, counts[0], counts[1], counts[2],
                    percentages[0], percentages[1], percentages[2], true);
        } catch (RuntimeException failure) {
            // Never log exception messages: they may contain feedback, credentials or provider bodies.
            log.warn("Sentiment analysis unavailable: itemCount={}; see provider diagnostic if available.", input.texts().size());
            return unavailable(input, Status.UNAVAILABLE);
        }
    }

    private TeacherSentimentResponse unavailable(TeacherAnalyticsService.SentimentInput input, Status status) {
        var course = input.course();
        return new TeacherSentimentResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                status, false, null, null, null, null, null, null, null, null, status != Status.PRIVACY_PROTECTED);
    }

    // Largest remainder rounding totals 100. Ties use POSITIVE, NEUTRAL, NEGATIVE order.
    private int[] percentages(int[] counts, int total) {
        int[] result = new int[3];
        long[] remainder = new long[3];
        int assigned = 0;
        for (int index = 0; index < 3; index++) {
            result[index] = (int) (counts[index] * 100L / total);
            remainder[index] = counts[index] * 100L % total;
            assigned += result[index];
        }
        while (assigned++ < 100) {
            int best = 0;
            for (int index = 1; index < 3; index++) if (remainder[index] > remainder[best]) best = index;
            result[best]++;
            remainder[best] = -1;
        }
        return result;
    }
}
