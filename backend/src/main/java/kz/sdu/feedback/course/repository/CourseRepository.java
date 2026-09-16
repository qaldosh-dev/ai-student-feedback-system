package kz.sdu.feedback.course.repository;

import kz.sdu.feedback.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
