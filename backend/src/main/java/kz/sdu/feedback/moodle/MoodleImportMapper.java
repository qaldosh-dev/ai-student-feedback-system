package kz.sdu.feedback.moodle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure in-memory planning: no Spring context, entities, repositories or database connection. */
public class MoodleImportMapper {
    public Result map(List<MoodleCourseRecord> records) {
        var reviews = new ArrayList<Review>();
        var valid = new ArrayList<MoodleCourseRecord>();
        for (var record : records) {
            if (!positive(record.moodleTeacherId()) || !positive(record.moodleCourseId())
                    || invalidText(record.teacherName()) || invalidText(record.courseCode())
                    || invalidText(record.courseName()) || !"Fall 2026".equals(record.semester())) {
                reviews.add(new Review(record, "Missing/invalid identity, required text or Fall 2026 semester"));
            } else {
                valid.add(record);
            }
        }

        var teachers = new LinkedHashMap<Long, TeacherCandidate>();
        var teacherConflicts = new LinkedHashMap<Long, List<MoodleCourseRecord>>();
        for (var entry : group(valid, MoodleCourseRecord::moodleTeacherId).entrySet()) {
            // Even capitalization differences in a person's name require review here.
            long names = entry.getValue().stream().map(r -> clean(r.teacherName())).distinct().count();
            if (names != 1) {
                teacherConflicts.put(entry.getKey(), List.copyOf(entry.getValue()));
            } else {
                teachers.put(entry.getKey(), new TeacherCandidate(entry.getKey(),
                        clean(entry.getValue().getFirst().teacherName()), null));
            }
        }

        var courses = new LinkedHashMap<String, CourseCandidate>();
        var courseConflicts = new LinkedHashMap<String, List<MoodleCourseRecord>>();
        for (var entry : group(valid, r -> code(r.courseCode())).entrySet()) {
            long names = entry.getValue().stream().map(r -> normalizedName(r.courseName())).distinct().count();
            if (names != 1) {
                courseConflicts.put(entry.getKey(), List.copyOf(entry.getValue()));
            } else {
                // Only casing/whitespace-equivalent names reach this branch.
                courses.put(entry.getKey(), new CourseCandidate(entry.getKey(),
                        clean(entry.getValue().getFirst().courseName())));
            }
        }

        var offerings = new ArrayList<OfferingCandidate>();
        int repeatedSourceRecords = 0;
        for (var entry : group(valid, r -> new OfferingKey(r.moodleCourseId(), r.moodleTeacherId(), r.semester())).entrySet()) {
            var group = entry.getValue();
            boolean sourceConflict = group.stream().map(r -> code(r.courseCode())).distinct().count() != 1;
            var first = group.getFirst();
            var teacher = teachers.get(first.moodleTeacherId());
            var course = courses.get(code(first.courseCode()));
            String reason = sourceConflict ? "Same source offering key has different course codes"
                    : teacher == null ? "Teacher ID has conflicting displayed names"
                    : course == null ? "Unresolved course-code/name conflict" : null;
            if (reason != null) {
                for (var record : group) reviews.add(new Review(record, reason));
            } else {
                offerings.add(new OfferingCandidate(first.moodleCourseId(), teacher, course, first.semester(), null));
                repeatedSourceRecords += group.size() - 1;
            }
        }
        return new Result(List.copyOf(teachers.values()), List.copyOf(courses.values()), List.copyOf(offerings),
                Map.copyOf(teacherConflicts), Map.copyOf(courseConflicts), List.copyOf(reviews), repeatedSourceRecords);
    }

    private static boolean positive(Long id) { return id != null && id > 0; }
    private static boolean invalidText(String text) { return text == null || clean(text).isEmpty() || clean(text).length() > 255; }
    static String clean(String text) { return text.strip().replaceAll("\\s+", " "); }
    static String code(String text) { return clean(text).toUpperCase(Locale.ROOT); }
    static String normalizedName(String text) { return clean(text).toLowerCase(Locale.ROOT); }

    private static <K> Map<K, List<MoodleCourseRecord>> group(List<MoodleCourseRecord> records,
                                                            Function<MoodleCourseRecord, K> key) {
        return records.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private record OfferingKey(Long moodleCourseId, Long moodleTeacherId, String semester) { }
    public record TeacherCandidate(Long moodleTeacherId, String name, String department) { }
    public record CourseCandidate(String code, String name) { }
    public record OfferingCandidate(Long moodleCourseId, TeacherCandidate teacher, CourseCandidate course,
                                    String semester, String section) { }
    public record Review(MoodleCourseRecord record, String reason) { }
    public record Result(List<TeacherCandidate> teachers, List<CourseCandidate> courses,
                         List<OfferingCandidate> offerings, Map<Long, List<MoodleCourseRecord>> teacherConflicts,
                         Map<String, List<MoodleCourseRecord>> courseConflicts, List<Review> reviews,
                         int repeatedSourceRecords) { }
}
