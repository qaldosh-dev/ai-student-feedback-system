package kz.sdu.feedback.teacher.controller;

import java.util.List;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.teacher.dto.TeacherSort;
import kz.sdu.feedback.teacher.dto.TeacherProfileResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import kz.sdu.feedback.teacher.service.TeacherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {
    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/{teacherId}")
    public TeacherProfileResponse getProfile(@PathVariable String teacherId) {
        long id;
        try {
            id = Long.parseLong(teacherId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found.");
        }
        return teacherService.getProfile(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @GetMapping
    public List<TeacherResponse> getTeachers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "courseQ", required = false) String courseQuery,
            @RequestParam(name = "sort", required = false) String sort) {
        TeacherSort order;
        try {
            order = sort == null ? TeacherSort.NAME_ASC : TeacherSort.valueOf(sort);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported teacher sort.");
        }
        return teacherService.getTeachers(query, courseQuery, order);
    }
}
