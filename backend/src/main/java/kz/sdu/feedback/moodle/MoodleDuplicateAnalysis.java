package kz.sdu.feedback.moodle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import kz.sdu.feedback.moodle.MoodleCatalogParser.SourceCard;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class MoodleDuplicateAnalysis {
    public SourceAnalysis analyzeSource(List<MoodleCourseRecord> records, List<SourceCard> cards) {
        Map<String, List<List<MoodleCourseRecord>>> groups = new LinkedHashMap<>();
        groups.put("TYPE 1 Exact logical duplicates with different Moodle IDs", groupRecords(records,
                r -> List.of(teacherIdentity(r), r.courseCode(), r.courseName(), r.semester()),
                g -> different(g, MoodleCourseRecord::moodleCourseId) > 1));
        groups.put("TYPE 2 Explicit KZ/RU variants with different Moodle IDs", groupRecords(records,
                r -> List.of(teacherIdentity(r), r.courseCode(), r.courseName(), r.semester()), this::hasLanguageVariants));
        groups.put("TYPE 3 Same course code, different teacher identities", groupRecords(records,
                MoodleCourseRecord::courseCode, g -> different(g, MoodleDuplicateAnalysis::teacherIdentity) > 1));
        groups.put("TYPE 4 Same teacher identity and code, different course names", groupRecords(records,
                r -> List.of(teacherIdentity(r), r.courseCode()), g -> different(g, MoodleCourseRecord::courseName) > 1));
        groups.put("TYPE 5 Same displayed teacher name, different teacher IDs", groupRecords(records,
                MoodleCourseRecord::teacherName, g -> different(g, MoodleCourseRecord::moodleTeacherId) > 1));
        groups.put("TYPE 6 Same teacher ID, different displayed names", groupRecords(
                records.stream().filter(r -> r.moodleTeacherId() != null).toList(),
                MoodleCourseRecord::moodleTeacherId, g -> different(g, MoodleCourseRecord::teacherName) > 1));
        groups.put("Same teacher identity and code, multiple Moodle IDs", groupRecords(records,
                r -> List.of(teacherIdentity(r), r.courseCode()), g -> different(g, MoodleCourseRecord::moodleCourseId) > 1));
        var identifiedCourses = records.stream().filter(r -> r.moodleCourseId() != null).toList();
        groups.put("Same Moodle course ID, multiple teacher identities", groupRecords(identifiedCourses,
                MoodleCourseRecord::moodleCourseId, g -> different(g, MoodleDuplicateAnalysis::teacherIdentity) > 1));
        groups.put("Same Moodle course ID, inconsistent names", groupRecords(identifiedCourses,
                MoodleCourseRecord::moodleCourseId, g -> different(g, MoodleCourseRecord::courseName) > 1));
        groups.put("Same Moodle course ID, inconsistent codes", groupRecords(identifiedCourses,
                MoodleCourseRecord::moodleCourseId, g -> different(g, MoodleCourseRecord::courseCode) > 1));
        groups.put("Same teacher name and ID on multiple records", groupRecords(
                records.stream().filter(r -> r.moodleTeacherId() != null).toList(),
                r -> List.of(r.teacherName(), r.moodleTeacherId()), g -> g.size() > 1));
        groups.put("Teacher name formatting candidates (case, whitespace)", groupRecords(records,
                r -> normalize(r.teacherName()), g -> different(g, MoodleCourseRecord::teacherName) > 1));
        groups.put("Teacher name punctuation candidates (not merged)", groupRecords(records,
                r -> normalize(r.teacherName()).replaceAll("[^\\p{L}\\p{N}]", ""),
                g -> different(g, MoodleCourseRecord::teacherName) > 1));
        groups.put("One course code, multiple normalized names", groupRecords(records,
                MoodleCourseRecord::courseCode, g -> different(g, r -> normalize(r.courseName())) > 1));
        groups.put("One normalized course name, multiple codes", groupRecords(records,
                r -> normalize(r.courseName()), g -> different(g, MoodleCourseRecord::courseCode) > 1));
        groups.put("One course code, multiple Moodle IDs", groupRecords(records,
                MoodleCourseRecord::courseCode, g -> different(g, MoodleCourseRecord::moodleCourseId) > 1));
        groups.put("One teacher identity, multiple course codes", groupRecords(records,
                MoodleDuplicateAnalysis::teacherIdentity, g -> different(g, MoodleCourseRecord::courseCode) > 1));

        Map<String, List<List<SourceCard>>> cardGroups = new LinkedHashMap<>();
        cardGroups.put("Repeated Moodle IDs across source cards", cards.stream().filter(c -> c.moodleCourseId() != null)
                .collect(groupingBy(SourceCard::moodleCourseId, LinkedHashMap::new, toList()))
                .values().stream().filter(g -> g.size() > 1).toList());
        cardGroups.put("Raw code capitalization variants", cards.stream().filter(c -> !c.rawCourseCode().isEmpty())
                .collect(groupingBy(c -> normalize(c.rawCourseCode()), LinkedHashMap::new, toList()))
                .values().stream().filter(g -> g.stream().map(SourceCard::rawCourseCode).distinct().count() > 1).toList());
        cardGroups.put("TYPE 7 Cards without named public teacher contacts", List.of(
                cards.stream().filter(c -> c.teacherContactCount() == 0).toList()));

        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("cards", (long) cards.size());
        totals.put("records", (long) records.size());
        totals.put("skippedCards", cards.stream().filter(c -> !c.skipReason().isEmpty()).count());
        totals.put("uniqueMoodleCourseIdsAllCards", cards.stream().map(SourceCard::moodleCourseId).filter(Objects::nonNull).distinct().count());
        totals.put("uniqueMoodleCourseIdsValidRecords", different(records, MoodleCourseRecord::moodleCourseId));
        totals.put("uniqueMoodleTeacherIds", different(records, MoodleCourseRecord::moodleTeacherId));
        totals.put("uniqueDisplayedTeacherNames", different(records, MoodleCourseRecord::teacherName));
        totals.put("uniqueCourseCodes", different(records, MoodleCourseRecord::courseCode));
        totals.put("uniqueNormalizedCodeNamePairs", different(records, r -> List.of(normalize(r.courseCode()), normalize(r.courseName()))));
        totals.put("uniqueTeacherIdentityCourseCodePairs", different(records, r -> List.of(teacherIdentity(r), r.courseCode())));
        totals.put("uniqueTeacherIdentityCourseCodeSemester", different(records, r -> List.of(teacherIdentity(r), r.courseCode(), r.semester())));
        totals.put("uniqueTeacherIdentityCodeNameSemester", different(records, r -> List.of(teacherIdentity(r), r.courseCode(), r.courseName(), r.semester())));
        totals.put("uniqueDisplayedNameCourseCodePairs", different(records, r -> List.of(r.teacherName(), r.courseCode())));
        totals.put("recordsWithoutTeacherId", records.stream().filter(r -> r.moodleTeacherId() == null).count());
        totals.put("cardsWithoutNamedTeacherContacts", cards.stream().filter(c -> c.teacherContactCount() == 0).count());
        totals.put("contactlessCardsWithNameLikeTitleSuffix", cards.stream().filter(c -> c.teacherContactCount() == 0 && hasNameLikeSuffix(c.sourceTitle())).count());
        totals.put("cardsWithMultipleNamedContacts", cards.stream().filter(c -> c.teacherContactCount() > 1).count());
        Map<String, Long> suffixes = cards.stream().collect(groupingBy(c -> suffix(c.sourceTitle()), LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return new SourceAnalysis(totals, groups, cardGroups, suffixes);
    }

    public static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String teacherIdentity(MoodleCourseRecord record) {
        // Missing IDs never establish identity between different source cards by name alone.
        return record.moodleTeacherId() == null
                ? "unresolved:" + record.sourcePage() + ":" + record.moodleCourseId() + ":" + record.teacherName()
                : "id:" + record.moodleTeacherId();
    }

    public static String suffix(String title) {
        var match = Pattern.compile("\\)\\s*([A-Za-z]{2,3})\\s*$").matcher(title);
        return match.find() ? match.group(1).toUpperCase(Locale.ROOT) : "NONE";
    }

    public static boolean hasNameLikeSuffix(String title) {
        // A review heuristic, not teacher extraction: 2–4 capitalized words in the final parentheses.
        return Pattern.compile("\\(([\\p{Lu}][\\p{L}'’-]+)(?:\\s+[\\p{Lu}][\\p{L}'’-]+){1,3}\\)\\s*(?:[A-Za-z]{2,3})?$")
                .matcher(title).find();
    }

    private boolean hasLanguageVariants(List<MoodleCourseRecord> group) {
        return group.stream().anyMatch(a -> a.moodleCourseId() != null && suffix(a.sourceTitle()).equals("KZ")
                && group.stream().anyMatch(b -> b.moodleCourseId() != null && !a.moodleCourseId().equals(b.moodleCourseId())
                && suffix(b.sourceTitle()).equals("RU")));
    }

    private static long different(List<MoodleCourseRecord> records, Function<MoodleCourseRecord, ?> field) {
        return records.stream().map(field).filter(Objects::nonNull).distinct().count();
    }

    private List<List<MoodleCourseRecord>> groupRecords(List<MoodleCourseRecord> records,
            Function<MoodleCourseRecord, ?> key, Predicate<List<MoodleCourseRecord>> keep) {
        return records.stream().collect(groupingBy(key, LinkedHashMap::new, toList())).values().stream().filter(keep).toList();
    }

    public record SourceAnalysis(Map<String, Long> totals,
            Map<String, List<List<MoodleCourseRecord>>> groups,
            Map<String, List<List<SourceCard>>> cardGroups, Map<String, Long> suffixes) { }

    public Analysis analyze(List<MoodleCourseRecord> records) {
        // Exact parsed values, without fuzzy matching or changing the source records.
        var logicalGroups = records.stream().collect(groupingBy(
                r -> new LogicalKey(r.teacherName(), r.courseCode(), r.courseName(), r.semester()),
                LinkedHashMap::new, toList()));
        var teacherCourseGroups = records.stream().collect(groupingBy(
                r -> new TeacherCourseKey(r.teacherName(), r.courseCode()), LinkedHashMap::new, toList()));
        var idGroups = records.stream().filter(r -> r.moodleCourseId() != null).collect(groupingBy(
                MoodleCourseRecord::moodleCourseId, LinkedHashMap::new, toList()));
        var courseGroups = records.stream().collect(groupingBy(
                MoodleCourseRecord::courseCode, LinkedHashMap::new, toList()));
        var teacherGroups = records.stream().collect(groupingBy(
                MoodleCourseRecord::teacherName, LinkedHashMap::new, toList()));

        var exactDuplicates = logicalGroups.values().stream().filter(group -> group.size() > 1).toList();
        var multipleIds = teacherCourseGroups.values().stream().filter(group -> group.stream()
                .map(MoodleCourseRecord::moodleCourseId).filter(Objects::nonNull).distinct().count() > 1).toList();
        var multipleTeachersPerId = idGroups.values().stream().filter(group -> group.stream()
                .map(MoodleCourseRecord::teacherName).distinct().count() > 1).toList();
        var multipleTeachersPerCode = courseGroups.values().stream().filter(group -> group.stream()
                .map(MoodleCourseRecord::teacherName).distinct().count() > 1).toList();
        var multipleCoursesPerTeacher = teacherGroups.values().stream().filter(group -> group.stream()
                .map(MoodleCourseRecord::courseCode).distinct().count() > 1).toList();
        return new Analysis(records.size(), idGroups.size(), teacherGroups.size(), courseGroups.size(),
                teacherCourseGroups.size(), (int) records.stream().filter(r -> r.moodleCourseId() == null).count(),
                exactDuplicates, multipleIds, multipleTeachersPerId, multipleTeachersPerCode, multipleCoursesPerTeacher);
    }

    public void printReport(List<MoodleCourseRecord> records) {
        var result = analyze(records);
        System.out.printf("Records: %d; unique Moodle IDs in records: %d; teachers: %d; course codes: %d; teacher/course pairs: %d; missing IDs: %d%n",
                result.totalRecords(), result.uniqueMoodleIds(), result.uniqueTeachers(), result.uniqueCourseCodes(),
                result.uniqueTeacherCourses(), result.recordsWithoutId());
        System.out.printf("A exact logical duplicate groups: %d; records involved: %d%n",
                result.exactDuplicates().size(), result.exactDuplicates().stream().mapToInt(List::size).sum());
        System.out.printf("B teacher/course groups with multiple IDs: %d%n", result.multipleIds().size());
        System.out.printf("C Moodle IDs with multiple teachers: %d%n", result.multipleTeachersPerId().size());
        System.out.printf("D course codes with multiple teachers: %d%n", result.multipleTeachersPerCode().size());
        System.out.printf("E teachers with multiple course codes: %d%n", result.multipleCoursesPerTeacher().size());
        System.out.println("Duplicate examples (up to 10 teacher/course groups; nothing merged):");
        result.multipleIds().stream().limit(10).forEach(group -> {
            System.out.println("Group: " + group.getFirst().teacherName() + " / " + group.getFirst().courseCode());
            group.forEach(this::printRecord);
        });
        System.out.println("Representative records (up to 4 from each page):");
        for (int page = 0; page <= 4; page++) {
            int sourcePage = page;
            records.stream().filter(r -> r.sourcePage() == sourcePage).limit(4).forEach(this::printRecord);
        }
    }

    private void printRecord(MoodleCourseRecord record) {
        System.out.printf("ID %s | page %d | %s | %s | %s | %s | source title: %s%n",
                record.moodleCourseId(), record.sourcePage(), record.teacherName(), record.courseCode(),
                record.courseName(), record.semester(), record.sourceTitle());
    }

    private record LogicalKey(String teacher, String code, String name, String semester) { }
    private record TeacherCourseKey(String teacher, String code) { }

    public record Analysis(int totalRecords, int uniqueMoodleIds, int uniqueTeachers, int uniqueCourseCodes,
                           int uniqueTeacherCourses, int recordsWithoutId,
                           List<List<MoodleCourseRecord>> exactDuplicates,
                           List<List<MoodleCourseRecord>> multipleIds,
                           List<List<MoodleCourseRecord>> multipleTeachersPerId,
                           List<List<MoodleCourseRecord>> multipleTeachersPerCode,
                           List<List<MoodleCourseRecord>> multipleCoursesPerTeacher) { }
}
