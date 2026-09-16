package kz.sdu.feedback.course.repository;

import java.util.List;
import kz.sdu.feedback.course.entity.Course;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Sort sort);
}
