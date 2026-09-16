package kz.sdu.feedback.course.controller;

import java.util.List;
import kz.sdu.feedback.course.dto.CourseResponse;
import kz.sdu.feedback.course.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> getCourses(@RequestParam(name = "q", required = false) String query) {
        return courseService.getCourses(query);
    }
}
