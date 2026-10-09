package kz.sdu.feedback.review.controller;

import java.util.List;
import java.util.Map;
import kz.sdu.feedback.review.dto.TeacherFeedbackResponse;
import kz.sdu.feedback.review.service.TeacherFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher/feedback")
public class TeacherFeedbackController {
    private final TeacherFeedbackService service;
    public TeacherFeedbackController(TeacherFeedbackService service) { this.service = service; }

    @GetMapping
    public List<TeacherFeedbackResponse> feedback(Authentication authentication, @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback does not accept query parameters.");
        return service.feedback(authentication.getName());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
