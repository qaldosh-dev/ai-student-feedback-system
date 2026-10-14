package kz.sdu.feedback.review.service;

import java.util.List;
import kz.sdu.feedback.review.dto.TeacherTopicsResponse;
import kz.sdu.feedback.review.dto.TeacherTopicsResponse.Status;
import kz.sdu.feedback.review.topics.TopicsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TopicsAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(TopicsAnalysisService.class);
    private final TeacherAnalyticsService analytics;
    private final TopicsProvider provider;

    public TopicsAnalysisService(TeacherAnalyticsService analytics, TopicsProvider provider) {
        this.analytics = analytics;
        this.provider = provider;
    }

    public TeacherTopicsResponse analyze(String email, Long courseId, String term) {
        // Reuse US31/33's validated, read-only published-text selection, not its sentiment provider.
        // Its database transaction finishes before this external call.
        var input = analytics.sentimentInput(email, courseId, term);
        var course = input.course();
        if (!input.privacyThresholdMet())
            return new TeacherTopicsResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.PRIVACY_PROTECTED, null, List.of(), List.of(), false, false, false);
        try {
            var result = provider.analyze(input.texts());
            if (result == null) throw new IllegalArgumentException();
            return new TeacherTopicsResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.AVAILABLE, input.texts().size(), result.topics(), result.concerns(), true,
                    !result.topics().isEmpty() || !result.concerns().isEmpty(), true);
        } catch (RuntimeException failure) {
            // Provider exceptions may contain sensitive text; never log their message or stack trace.
            log.warn("Topic analysis unavailable: itemCount={}; see provider diagnostic if available.", input.texts().size());
            return new TeacherTopicsResponse(course.courseId(), course.courseCode(), course.courseName(), input.term(),
                    Status.UNAVAILABLE, null, List.of(), List.of(), false, false, true);
        }
    }
}
