package kz.sdu.feedback.review.controller;

import java.util.Map;
import java.util.Set;
import kz.sdu.feedback.review.dto.TeacherAnalyticsResponse;
import kz.sdu.feedback.review.service.TeacherAnalyticsService;
import kz.sdu.feedback.review.service.SentimentAnalysisService;
import kz.sdu.feedback.review.dto.TeacherSentimentResponse;
import kz.sdu.feedback.review.dto.TeacherTopicsResponse;
import kz.sdu.feedback.review.service.TopicsAnalysisService;
import kz.sdu.feedback.review.dto.TeacherSummaryResponse;
import kz.sdu.feedback.review.service.SummaryAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/analytics")
public class TeacherAnalyticsController {
    private final TeacherAnalyticsService service;
    private final SentimentAnalysisService sentiment;
    private final TopicsAnalysisService topics;
    private final SummaryAnalysisService summary;
    public TeacherAnalyticsController(TeacherAnalyticsService service, SentimentAnalysisService sentiment,
            TopicsAnalysisService topics, SummaryAnalysisService summary) {
        this.service = service;
        this.sentiment = sentiment;
        this.topics = topics;
        this.summary = summary;
    }

    @GetMapping("/scope")
    public TeacherAnalyticsResponse scope(Authentication authentication, @RequestParam MultiValueMap<String, String> parameters) {
        return service.scope(authentication.getName(), courseId(parameters), parameters.getFirst("term"));
    }

    @GetMapping("/sentiment")
    public TeacherSentimentResponse sentiment(Authentication authentication, @RequestParam MultiValueMap<String, String> parameters) {
        return sentiment.analyze(authentication.getName(), courseId(parameters), parameters.getFirst("term"));
    }

    @GetMapping("/topics")
    public TeacherTopicsResponse topics(Authentication authentication, @RequestParam MultiValueMap<String, String> parameters) {
        return topics.analyze(authentication.getName(), courseId(parameters), parameters.getFirst("term"));
    }

    @GetMapping("/summary")
    public TeacherSummaryResponse summary(Authentication authentication, @RequestParam MultiValueMap<String, String> parameters) {
        return summary.analyze(authentication.getName(), courseId(parameters), parameters.getFirst("term"));
    }

    private Long courseId(MultiValueMap<String, String> parameters) {
        if (!Set.of("courseId", "term").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.size() != 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported analytics parameters.");
        Long courseId = null;
        if (parameters.containsKey("courseId")) {
            try {
                courseId = Long.parseLong(parameters.getFirst("courseId"));
                if (courseId <= 0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytics selection not found.");
            }
        }
        return courseId;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
