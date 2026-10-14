package kz.sdu.feedback.course.controller;

import java.util.List;
import java.util.Map;
import kz.sdu.feedback.course.dto.CourseDetailsResponse;
import kz.sdu.feedback.course.dto.CourseResponse;
import kz.sdu.feedback.course.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/{courseId}")
    public CourseDetailsResponse getDetails(@PathVariable String courseId) {
        long id;
        try {
            id = Long.parseLong(courseId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        }
        return courseService.getDetails(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @GetMapping
    public List<CourseResponse> getCourses(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "teacherQ", required = false) String teacherQuery) {
        return courseService.getCourses(query, teacherQuery);
    }
}
