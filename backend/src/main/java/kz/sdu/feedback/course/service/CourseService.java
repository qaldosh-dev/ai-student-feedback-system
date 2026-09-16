package kz.sdu.feedback.course.service;

import java.util.List;
import kz.sdu.feedback.course.dto.CourseResponse;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
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
