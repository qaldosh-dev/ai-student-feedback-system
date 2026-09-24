package kz.sdu.feedback.teacher.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import kz.sdu.feedback.teacher.entity.Teacher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.id = :id")
    Optional<Teacher> findByIdForUpdate(@Param("id") Long id);

    List<Teacher> findByNameContainingIgnoreCase(String name, Sort sort);

    // EXISTS avoids duplicates from multiple offerings; LOCATE matches literal text.
    @Query("""
            select t from Teacher t
            where locate(lower(:name), lower(t.name)) > 0
              and exists (
                  select o.id from CourseOffering o
                  where o.teacher = t
                    and (locate(lower(:course), lower(o.course.code)) > 0
                         or locate(lower(:course), lower(o.course.name)) > 0)
              )
            """)
    List<Teacher> findMatchingTeachers(@Param("name") String name,
                                       @Param("course") String course, Sort sort);
}
