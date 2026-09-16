package kz.sdu.feedback.course.repository;

import java.util.List;
import kz.sdu.feedback.course.entity.Course;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Sort sort);

    // EXISTS avoids duplicates from multiple offerings; LOCATE matches literal text.
    @Query("""
            select c from Course c
            where (locate(lower(:course), lower(c.code)) > 0
                   or locate(lower(:course), lower(c.name)) > 0)
              and exists (
                  select o.id from CourseOffering o
                  where o.course = c
                    and locate(lower(:teacher), lower(o.teacher.name)) > 0
              )
            """)
    List<Course> findMatchingCourses(@Param("course") String course,
                                     @Param("teacher") String teacher, Sort sort);
}
