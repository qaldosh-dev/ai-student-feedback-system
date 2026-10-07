package kz.sdu.feedback.review.controller;

import java.util.Map;
import kz.sdu.feedback.review.dto.*;
import kz.sdu.feedback.review.service.ReviewService;
import tools.jackson.databind.JsonNode;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/reviews")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }
    @GetMapping
    public java.util.List<OwnReviewResponse> history(Authentication authentication, @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Review history does not accept query parameters.");
        return service.history(authentication.getName());
    }
    @GetMapping("/{reviewId}/status")
    public ReviewStatusResponse status(@PathVariable Long reviewId, Authentication authentication) {
        return service.status(authentication.getName(), reviewId);
    }
    @PostMapping
    public ResponseEntity<ReviewSubmissionResponse> submit(@RequestBody JsonNode input, Authentication authentication) {
        return ResponseEntity.status(201).body(service.submit(authentication.getName(), SubmitReviewRequest.fromJson(input)));
    }
    @PutMapping("/{reviewId}/correction")
    public ReviewSubmissionResponse correct(@PathVariable Long reviewId, @RequestBody JsonNode input, Authentication authentication) {
        return service.correct(authentication.getName(), reviewId, CorrectReviewRequest.fromJson(input));
    }
    @PutMapping("/{reviewId}")
    public ReviewSubmissionResponse updatePublished(@PathVariable Long reviewId, @RequestBody JsonNode input, Authentication authentication) {
        return service.updatePublished(authentication.getName(), reviewId, UpdateReviewRequest.fromJson(input));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request fields or JSON."));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> persistenceFailed() {
        return ResponseEntity.internalServerError().body(Map.of("message", "Review could not be saved. Please try again."));
    }
}
