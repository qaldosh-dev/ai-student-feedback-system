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

    // Aggregate once per teacher, across ALL courses/terms. EXISTS filters teachers without
    // narrowing the aggregates or multiplying reviews when several offerings match a course.
    @Query(value = """
            select t.* from teachers t
            left join (
                select o.teacher_id, avg(cast(r.rating as double precision)) as average_rating,
                       count(r.id) as review_count
                from reviews r join course_offerings o on o.id = r.course_offering_id
                where r.status = 'PUBLISHED'
                group by o.teacher_id
            ) feedback on feedback.teacher_id = t.id
            where position(lower(:name) in lower(t.name)) > 0
              and (:course = '' or exists (
                  select 1 from course_offerings o join courses c on c.id = o.course_id
                  where o.teacher_id = t.id
                    and (position(lower(:course) in lower(c.code)) > 0
                         or position(lower(:course) in lower(c.name)) > 0)
              ))
            order by
                case when feedback.review_count is null then 1 else 0 end,
                case when :sort = 'RATING_DESC' then feedback.average_rating end desc,
                case when :sort = 'RATING_ASC' then feedback.average_rating end asc,
                case when :sort = 'REVIEW_COUNT_DESC' then feedback.review_count end desc,
                t.name asc, t.id asc
            """, nativeQuery = true)
    List<Teacher> findByPublishedFeedbackOrder(@Param("name") String name,
                                              @Param("course") String course,
                                              @Param("sort") String sort);

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
