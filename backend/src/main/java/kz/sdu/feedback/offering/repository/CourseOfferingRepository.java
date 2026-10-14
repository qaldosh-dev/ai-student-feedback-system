package kz.sdu.feedback.offering.repository;

import kz.sdu.feedback.offering.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    // Display-only DISTINCT: source offerings and their sections/Moodle IDs remain untouched.
    @Query("select distinct c.id as courseId, c.code as courseCode, c.name as courseName, o.semester as academicTerm "
            + "from CourseOffering o join o.course c where o.teacher.id = :teacherId "
            + "order by c.code asc, o.semester desc, c.id asc")
    List<ProfileCourseTerm> findProfileCourses(@Param("teacherId") Long teacherId);

    interface ProfileCourseTerm {
        Long getCourseId();
        String getCourseCode();
        String getCourseName();
        String getAcademicTerm();
    }

    // Preserve separate teachers and terms while hiding repeated source cards/sections.
    @Query("select distinct t.id as teacherId, t.name as teacherName, o.semester as academicTerm "
            + "from CourseOffering o join o.teacher t where o.course.id = :courseId "
            + "order by t.name asc, t.id asc, o.semester desc")
    List<CourseTeacherTerm> findCourseTeachers(@Param("courseId") Long courseId);

    interface CourseTeacherTerm {
        Long getTeacherId();
        String getTeacherName();
        String getAcademicTerm();
    }

    @Query("select o.id as courseOfferingId, t.name as teacherName, c.code as courseCode, c.name as courseName, "
            + "o.semester as academicTerm from CourseOffering o join o.teacher t join o.course c "
            + "where t.id = :teacherId and c.id = :courseId order by o.id")
    List<FeedbackOffering> findFeedbackOfferings(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);

    interface FeedbackOffering {
        Long getCourseOfferingId();
        String getTeacherName();
        String getCourseCode();
        String getCourseName();
        String getAcademicTerm();
    }

    @Override
    @EntityGraph(attributePaths = {"teacher", "course"})
    List<CourseOffering> findAll();
}
