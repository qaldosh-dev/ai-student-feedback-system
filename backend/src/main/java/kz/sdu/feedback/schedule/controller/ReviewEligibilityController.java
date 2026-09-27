package kz.sdu.feedback.schedule.controller;

import java.util.List;
import kz.sdu.feedback.schedule.dto.ReviewEligibilityResponse;
import kz.sdu.feedback.schedule.service.ReviewEligibilityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/review-eligibilities")
public class ReviewEligibilityController {
    private final ReviewEligibilityService service;

    public ReviewEligibilityController(ReviewEligibilityService service) { this.service = service; }

    @GetMapping
    public List<ReviewEligibilityResponse> list(Authentication authentication) {
        return service.list(authentication.getName());
    }
}
