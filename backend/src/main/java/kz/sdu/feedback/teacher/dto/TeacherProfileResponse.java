package kz.sdu.feedback.teacher.dto;

import java.util.List;

/** Public catalog only; rows represent distinct courses/terms, not offering identities. */
public record TeacherProfileResponse(Long id, String name, List<CourseTerm> courseOfferings) {
    public record CourseTerm(Long courseId, String courseCode, String courseName, String academicTerm) { }
}
