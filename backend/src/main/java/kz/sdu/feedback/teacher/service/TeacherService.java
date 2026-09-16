package kz.sdu.feedback.teacher.service;

import java.util.List;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.teacher.entity.Teacher;
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
    public List<TeacherResponse> getTeachers(String query, String courseQuery) {
        String search = query == null ? "" : query.strip();
        String courseSearch = courseQuery == null ? "" : courseQuery.strip();
        Sort sort = Sort.by("name").and(Sort.by("id"));
        List<Teacher> teachers;
        if (!courseSearch.isEmpty()) {
            teachers = teacherRepository.findMatchingTeachers(search, courseSearch, sort);
        } else if (!search.isEmpty()) {
            teachers = teacherRepository.findByNameContainingIgnoreCase(search, sort);
        } else {
            teachers = teacherRepository.findAll(sort);
        }

        return teachers.stream()
                .map(teacher -> new TeacherResponse(
                        teacher.getId(), teacher.getName(), teacher.getDepartment()))
                .toList();
    }
}
