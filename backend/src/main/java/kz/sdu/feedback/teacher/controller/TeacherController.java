package kz.sdu.feedback.teacher.controller;

import java.util.List;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.teacher.service.TeacherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {
    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    public List<TeacherResponse> getTeachers() {
        return teacherService.getTeachers();
    }
}
