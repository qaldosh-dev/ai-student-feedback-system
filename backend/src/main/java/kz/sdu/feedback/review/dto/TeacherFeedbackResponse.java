package kz.sdu.feedback.review.dto;

import java.time.Instant;
import java.util.List;

/** Offering ID identifies a group, never an author or an individual review. */
public record TeacherFeedbackResponse(Long courseOfferingId, String courseCode, String courseName,
        String academicTerm, List<Feedback> feedback) {
    public record Feedback(int rating, String text, Instant publishedAt) { }
}
