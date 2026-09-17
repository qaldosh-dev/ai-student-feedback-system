package kz.sdu.feedback.moodle;

public record MoodleCourseRecord(
        String teacherName, String courseCode, String courseName, String semester,
        Long moodleCourseId, int sourcePage, String sourceTitle, Long moodleTeacherId) {
    // Keep the Phase 2A constructor for callers without a source teacher ID.
    public MoodleCourseRecord(String teacherName, String courseCode, String courseName, String semester,
                              Long moodleCourseId, int sourcePage, String sourceTitle) {
        this(teacherName, courseCode, courseName, semester, moodleCourseId, sourcePage, sourceTitle, null);
    }
}
