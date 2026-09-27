package kz.sdu.feedback.schedule.dto;

import java.util.List;

public record ReviewEligibilityResponse(Long courseOfferingId, Long courseId, Long teacherId,
        String courseCode, String courseName, String teacherName, String academicTerm,
        List<String> groupSections) {}
