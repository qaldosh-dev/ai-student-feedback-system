package kz.sdu.feedback.schedule.controller;

import java.util.List;
import kz.sdu.feedback.schedule.dto.VerifiedCourseResponse;
import kz.sdu.feedback.schedule.service.VerifiedCourseService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/verified-courses")
public class VerifiedCourseController {
    private final VerifiedCourseService service;
    public VerifiedCourseController(VerifiedCourseService service) { this.service = service; }

    @GetMapping
    public List<VerifiedCourseResponse> list(Authentication authentication) {
        return service.list(authentication.getName());
    }
}
