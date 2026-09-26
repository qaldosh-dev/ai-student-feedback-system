package kz.sdu.feedback.schedule.dto;

import kz.sdu.feedback.schedule.entity.VerifiedCourse;

public record VerifiedCourseResponse(Long verifiedCourseId, Long courseOfferingId, Long uploadId,
        Long sourceEntryId, String courseCode, String courseName, String teacherName,
        String groupSection, String academicTerm) {
    public static VerifiedCourseResponse from(VerifiedCourse verified) {
        var source = verified.getSourceEntry();
        var offering = verified.getCourseOffering();
        return new VerifiedCourseResponse(verified.getId(), offering.getId(), source.getScheduleUpload().getId(),
                source.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getTeacher().getName(), source.getGroupSection(), source.getAcademicTerm());
    }
}
