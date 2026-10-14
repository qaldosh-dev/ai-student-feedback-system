package kz.sdu.feedback.review.dto;

import java.time.Instant;
import java.util.List;

public record PublicFeedbackResponse(Long teacherId, String teacherName, Long courseId, String courseCode,
        String courseName, Long courseOfferingId, String academicTerm, List<Target> availableTargets,
        List<Feedback> feedback) {
    public record Target(Long courseOfferingId, String academicTerm) { }
    public record Feedback(int rating, String text, Instant publishedAt) { }
}
