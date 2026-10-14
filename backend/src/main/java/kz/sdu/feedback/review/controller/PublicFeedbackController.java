package kz.sdu.feedback.review.controller;

import java.util.Map;
import java.util.Set;
import kz.sdu.feedback.review.dto.PublicFeedbackResponse;
import kz.sdu.feedback.review.service.PublicFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teachers/{teacherId}/courses/{courseId}/feedback")
public class PublicFeedbackController {
    private final PublicFeedbackService service;
    public PublicFeedbackController(PublicFeedbackService service) { this.service = service; }

    @GetMapping
    public PublicFeedbackResponse feedback(@PathVariable String teacherId, @PathVariable String courseId,
                                            @RequestParam Map<String, String> parameters) {
        if (!Set.of("academicTerm", "courseOfferingId").containsAll(parameters.keySet()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feedback selection.");
        return service.feedback(id(teacherId), id(courseId), parameters.containsKey("courseOfferingId")
                ? id(parameters.get("courseOfferingId")) : null, parameters.get("academicTerm"));
    }

    private Long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback selection not found.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
