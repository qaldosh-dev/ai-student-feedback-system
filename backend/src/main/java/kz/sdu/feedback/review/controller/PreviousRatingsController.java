package kz.sdu.feedback.review.controller;

import java.util.Map;
import kz.sdu.feedback.review.dto.PreviousRatingsResponse;
import kz.sdu.feedback.review.service.PreviousRatingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teachers/{teacherId}/courses/{courseId}/ratings/history")
public class PreviousRatingsController {
    private final PreviousRatingsService service;
    public PreviousRatingsController(PreviousRatingsService service) { this.service = service; }

    @GetMapping
    public PreviousRatingsResponse history(@PathVariable String teacherId, @PathVariable String courseId,
                                            @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating history does not accept query parameters.");
        return service.history(id(teacherId), id(courseId));
    }

    private Long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating history not found.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
