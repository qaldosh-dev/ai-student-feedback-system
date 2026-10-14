package kz.sdu.feedback.review.controller;

import java.util.Map;
import kz.sdu.feedback.review.dto.OverallRatingResponse;
import kz.sdu.feedback.review.service.OverallRatingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teachers/{teacherId}/ratings/overall")
public class OverallRatingController {
    private final OverallRatingService service;
    public OverallRatingController(OverallRatingService service) { this.service = service; }

    @GetMapping
    public OverallRatingResponse overall(@PathVariable String teacherId, @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overall ratings do not accept query parameters.");
        long id;
        try {
            id = Long.parseLong(teacherId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found.");
        }
        return service.overall(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
