package kz.sdu.feedback.schedule.dto;

import java.util.List;
import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;

public record ExtractedScheduleResponse(ScheduleUploadView upload, List<Entry> entries) {
    public record Entry(Long id, String courseCode, String courseName, String teacherName,
                        String groupSection, String academicTerm) {
        public static Entry from(ExtractedScheduleEntry entry) {
            return new Entry(entry.getId(), entry.getCourseCode(), entry.getCourseName(),
                    entry.getTeacherName(), entry.getGroupSection(), entry.getAcademicTerm());
        }
    }
}
