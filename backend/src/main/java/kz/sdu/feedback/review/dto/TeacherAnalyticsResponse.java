package kz.sdu.feedback.review.dto;

import java.util.List;

public record TeacherAnalyticsResponse(List<CourseOption> courses, Long selectedCourseId,
                                       String selectedAcademicTerm, boolean hasPublishedFeedback, boolean privacyThresholdMet) {
    public record CourseOption(Long courseId, String courseCode, String courseName, List<String> academicTerms) { }
}
