package kz.sdu.feedback.moodle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoodleDuplicateAnalysisTest {
    @Test
    void countsAllFiveGroupingLevelsWithoutRemovingAnyRecords() {
        var records = new ArrayList<>(List.of(
                record("Teacher A", "CSS 114", "Programming", 10L, 0),
                record("Teacher A", "CSS 114", "Programming", 11L, 1),
                record("Teacher B", "CSS 114", "Programming", 10L, 0),
                record("Teacher A", "CSS 212", "Databases", 12L, 2),
                record("Teacher C", "MAT 151", "Calculus", null, 3)));
        var original = List.copyOf(records);
        var result = new MoodleDuplicateAnalysis().analyze(records);
        assertEquals(original, records);
        assertEquals(5, result.totalRecords());
        assertEquals(3, result.uniqueMoodleIds());
        assertEquals(3, result.uniqueTeachers());
        assertEquals(3, result.uniqueCourseCodes());
        assertEquals(4, result.uniqueTeacherCourses());
        assertEquals(1, result.recordsWithoutId());
        assertEquals(1, result.exactDuplicates().size());
        assertEquals(2, result.exactDuplicates().getFirst().size());
        assertEquals(1, result.multipleIds().size());
        assertEquals(1, result.multipleTeachersPerId().size());
        assertEquals(1, result.multipleTeachersPerCode().size());
        assertEquals(1, result.multipleCoursesPerTeacher().size());
    }

    @Test
    void exactDuplicatesIncludeNameAndSemesterButRepeatedIdIsNotMultipleIds() {
        var same = record("Teacher A", "CSS 114", "Programming", 10L, 0);
        var result = new MoodleDuplicateAnalysis().analyze(List.of(same, same,
                record("Teacher A", "CSS 114", "Different title", 10L, 1),
                new MoodleCourseRecord("Teacher A", "CSS 114", "Programming", "Spring 2027", 10L, 1, "title")));
        assertEquals(4, result.totalRecords());
        assertEquals(1, result.exactDuplicates().size());
        assertEquals(2, result.exactDuplicates().getFirst().size());
        assertEquals(0, result.multipleIds().size());
        assertEquals(0, result.multipleTeachersPerId().size());
    }

    private MoodleCourseRecord record(String teacher, String code, String name, Long id, int page) {
        return new MoodleCourseRecord(teacher, code, name, "Fall 2026", id, page, code + " " + name);
    }

    @Test
    void sourceIdentityDetectsSameNamesDifferentIdsAndSameIdDifferentFormatting() {
        var records = List.of(
                new MoodleCourseRecord("Teacher A", "CSS 114", "Programming", "Fall 2026", 10L, 0, "CSS 114 Programming (Teacher A)KZ", 41L),
                new MoodleCourseRecord("Teacher A", "CSS 114", "Programming", "Fall 2026", 11L, 1, "CSS 114 Programming (Teacher A)RU", 41L),
                new MoodleCourseRecord("Teacher A", "CSS 114", "Programming", "Fall 2026", 12L, 2, "CSS 114 Programming", 42L),
                new MoodleCourseRecord("TEACHER  A", "CSS 212", "Databases", "Fall 2026", 13L, 3, "CSS 212 Databases", 41L));
        var result = new MoodleDuplicateAnalysis().analyzeSource(records, List.of());
        assertEquals(1, result.groups().get("TYPE 1 Exact logical duplicates with different Moodle IDs").size());
        assertEquals(1, result.groups().get("TYPE 2 Explicit KZ/RU variants with different Moodle IDs").size());
        assertEquals(1, result.groups().get("TYPE 5 Same displayed teacher name, different teacher IDs").size());
        assertEquals(1, result.groups().get("TYPE 6 Same teacher ID, different displayed names").size());
        assertEquals(1, result.groups().get("Teacher name formatting candidates (case, whitespace)").size());
        assertEquals(2L, result.totals().get("uniqueMoodleTeacherIds"));
        assertEquals(4, records.size());
    }

    @Test
    void missingTeacherIdsDoNotBecomeCrossCardIdentityAndNoSuffixDoesNotMeanEnglish() {
        var records = List.of(record("Teacher A", "CSS 114", "Programming", 10L, 0),
                record("Teacher A", "CSS 114", "Programming", 11L, 1));
        var result = new MoodleDuplicateAnalysis().analyzeSource(records, List.of());
        assertEquals(0, result.groups().get("TYPE 1 Exact logical duplicates with different Moodle IDs").size());
        assertEquals(2L, result.totals().get("recordsWithoutTeacherId"));
        assertEquals("NONE", MoodleDuplicateAnalysis.suffix("CSS 114 Programming (Teacher A)"));
        assertEquals("EN", MoodleDuplicateAnalysis.suffix("CSS 114 Programming (Teacher A)EN"));
        assertEquals(true, MoodleDuplicateAnalysis.hasNameLikeSuffix("CSS 114 Programming (Aida Sarsenova)"));
        assertEquals(false, MoodleDuplicateAnalysis.hasNameLikeSuffix("CSS 114 Programming (B1)"));
    }
}
