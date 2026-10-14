package kz.sdu.feedback.teacher.service;

import java.util.List;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.teacher.dto.TeacherSort;
import kz.sdu.feedback.teacher.dto.TeacherProfileResponse;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherService {
    private final TeacherRepository teacherRepository;
    private final CourseOfferingRepository offerings;

    public TeacherService(TeacherRepository teacherRepository, CourseOfferingRepository offerings) {
        this.teacherRepository = teacherRepository;
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getProfile(Long teacherId) {
        var teacher = teacherRepository.findById(teacherId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found."));
        var courses = offerings.findProfileCourses(teacherId).stream().map(row -> new TeacherProfileResponse.CourseTerm(
                row.getCourseId(), row.getCourseCode(), row.getCourseName(), row.getAcademicTerm())).toList();
        return new TeacherProfileResponse(teacher.getId(), teacher.getName(), courses);
    }

    @Transactional(readOnly = true)
    public List<TeacherResponse> getTeachers(String query, String courseQuery, TeacherSort order) {
        String search = query == null ? "" : query.strip();
        String courseSearch = courseQuery == null ? "" : courseQuery.strip();
        Sort sort = Sort.by(order == TeacherSort.NAME_DESC ? Sort.Direction.DESC : Sort.Direction.ASC, "name")
                .and(Sort.by("id"));
        List<Teacher> teachers;
        if (order != TeacherSort.NAME_ASC && order != TeacherSort.NAME_DESC) {
            teachers = teacherRepository.findByPublishedFeedbackOrder(search, courseSearch, order.name());
        } else if (!courseSearch.isEmpty()) {
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
