package kz.sdu.feedback.teacher.service;

import java.util.List;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {
    private final TeacherRepository teacherRepository;

    public TeacherService(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    @Transactional(readOnly = true)
    public List<TeacherResponse> getTeachers() {
        return teacherRepository.findAll(Sort.by("name").and(Sort.by("id"))).stream()
                .map(teacher -> new TeacherResponse(
                        teacher.getId(), teacher.getName(), teacher.getDepartment()))
                .toList();
    }
}
