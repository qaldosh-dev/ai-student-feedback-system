package kz.sdu.feedback.review.service;

import kz.sdu.feedback.review.dto.TeacherSummaryResponse;
import kz.sdu.feedback.review.dto.TeacherSummaryResponse.Status;
import kz.sdu.feedback.review.summary.SummaryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SummaryAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SummaryAnalysisService.class);
    private final TeacherAnalyticsService analytics;
    private final SummaryProvider provider;

    public SummaryAnalysisService(TeacherAnalyticsService analytics, SummaryProvider provider) {
        this.analytics = analytics;
        this.provider = provider;
    }

    public TeacherSummaryResponse analyze(String email, Long courseId, String term) {
        // Reuse the validated published-text read, not sentiment/topics analysis or previous AI outputs.
        // The database transaction finishes before calling the summary provider.
        var input = analytics.sentimentInput(email, courseId, term);
        var course = input.course();
        if (!input.privacyThresholdMet())
            return new TeacherSummaryResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.PRIVACY_PROTECTED, null, null, false, false, false);
        try {
            var result = provider.summarize(input.texts());
            if (result == null) throw new IllegalArgumentException();
            return new TeacherSummaryResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.AVAILABLE, input.texts().size(), result.summary(), true, true, true);
        } catch (RuntimeException failure) {
            // Exception messages and stacks can contain feedback, credentials or provider output.
            log.warn("AI summary unavailable: itemCount={}; see provider diagnostic if available.", input.texts().size());
            return new TeacherSummaryResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.UNAVAILABLE, null, null, false, true, true);
        }
    }
}
