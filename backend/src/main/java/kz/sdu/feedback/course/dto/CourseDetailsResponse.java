package kz.sdu.feedback.course.dto;

import java.util.List;

/** Public catalog only; repeated source offerings are grouped for display. */
public record CourseDetailsResponse(Long courseId, String courseCode, String courseName,
                                    List<TeacherTerm> teacherOfferings) {
    public record TeacherTerm(Long teacherId, String teacherName, String academicTerm) { }
}
