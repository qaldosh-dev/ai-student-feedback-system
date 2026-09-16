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
    public List<CourseResponse> getCourses(String query) {
        String search = query == null ? "" : query.strip();
        Sort sort = Sort.by("code");
        List<Course> courses = search.isEmpty()
                ? courseRepository.findAll(sort)
                : courseRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                        search, search, sort);

        return courses.stream()
                .map(course -> new CourseResponse(
                        course.getId(), course.getCode(), course.getName()))
                .toList();
    }
}
