package kz.sdu.feedback.course.service;

import java.util.List;
import kz.sdu.feedback.course.dto.CourseResponse;
import kz.sdu.feedback.course.dto.CourseDetailsResponse;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offerings;

    public CourseService(CourseRepository courseRepository, CourseOfferingRepository offerings) {
        this.courseRepository = courseRepository;
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public CourseDetailsResponse getDetails(Long courseId) {
        var course = courseRepository.findById(courseId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        var teachers = offerings.findCourseTeachers(courseId).stream()
                .map(row -> new CourseDetailsResponse.TeacherTerm(
                        row.getTeacherId(), row.getTeacherName(), row.getAcademicTerm())).toList();
        return new CourseDetailsResponse(course.getId(), course.getCode(), course.getName(), teachers);
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses(String query, String teacherQuery) {
        String search = query == null ? "" : query.strip();
        String teacherSearch = teacherQuery == null ? "" : teacherQuery.strip();
        Sort sort = Sort.by("code");
        List<Course> courses;
        if (!teacherSearch.isEmpty()) {
            courses = courseRepository.findMatchingCourses(search, teacherSearch, sort);
        } else if (!search.isEmpty()) {
            courses = courseRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                    search, search, sort);
        } else {
            courses = courseRepository.findAll(sort);
        }

        return courses.stream()
                .map(course -> new CourseResponse(
                        course.getId(), course.getCode(), course.getName()))
                .toList();
    }
}
