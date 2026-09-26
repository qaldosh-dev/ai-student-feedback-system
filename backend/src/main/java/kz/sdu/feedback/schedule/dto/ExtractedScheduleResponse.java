package kz.sdu.feedback.schedule.dto;

import java.util.List;
import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;
import kz.sdu.feedback.schedule.entity.PendingCatalogVerification.Reason;

public record ExtractedScheduleResponse(ScheduleUploadView upload, List<Entry> entries) {
    public enum VerificationStatus { VERIFIED, PENDING, NOT_CHECKED }

    public record Entry(Long id, String courseCode, String courseName, String teacherName,
                        String groupSection, String academicTerm,
                        VerificationStatus verificationStatus, Reason verificationReason) {
        public static Entry from(ExtractedScheduleEntry entry, boolean verified, Reason pendingReason) {
            var status = verified ? VerificationStatus.VERIFIED
                    : pendingReason != null ? VerificationStatus.PENDING : VerificationStatus.NOT_CHECKED;
            return new Entry(entry.getId(), entry.getCourseCode(), entry.getCourseName(),
                    entry.getTeacherName(), entry.getGroupSection(), entry.getAcademicTerm(),
                    status, verified ? null : pendingReason);
        }
    }
}
