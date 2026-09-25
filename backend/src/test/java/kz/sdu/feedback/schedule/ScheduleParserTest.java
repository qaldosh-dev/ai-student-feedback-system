package kz.sdu.feedback.schedule;

import static org.junit.jupiter.api.Assertions.*;

import kz.sdu.feedback.schedule.service.ScheduleParser;
import org.junit.jupiter.api.Test;

class ScheduleParserTest {
    private final ScheduleParser parser = new ScheduleParser();
    private static final String HEADER = "Schedule\n2038-2039 2 term\nDay/Hour\n";

    private String block(String teacher, String section) {
        return "ABC 789\u00a0Experimental\nDesign (2+1+0) [3cr / 5ECTS]\n[" + section + "]\n"
                + teacher + "\n: Building Z: (R 300) Room 300\n";
    }

    @Test void extractsOnlyRequiredFieldsAcrossLines() {
        var result = parser.parse(HEADER + block("Morgan\nRivera", "04-N"));
        assertEquals(new ScheduleParser.Entry("ABC 789", "Experimental Design", "Morgan Rivera", "04-N", "2038-2039 2 term"),
                result.entries().getFirst());
        assertEquals(new ScheduleParser.Summary(1, 1, 0, 0), result.summary());
    }

    @Test void sameCourseDifferentTeachersAndSectionsRemainSeparate() {
        var result = parser.parse(HEADER + block("Morgan Rivera", "04-N")
                + block("Jamie Quinn", "04-N") + block("Morgan Rivera", "15-P"));
        assertEquals(3, result.entries().size());
    }

    @Test void exactNormalizedDuplicatesAreDeduplicatedOnlyWithinResult() {
        var result = parser.parse(HEADER + block("Morgan Rivera", "04-N") + block("Morgan   Rivera", "04-N"));
        assertEquals(new ScheduleParser.Summary(2, 1, 0, 1), result.summary());
        assertEquals(1, parser.parse(HEADER + block("Morgan Rivera", "04-N")).entries().size());
    }

    @Test void meaningfulNamesAndCapitalizationArePreserved() {
        var result = parser.parse(HEADER + "XYZ 456 Introduction to Java (Advanced) [9-Z]\nЖан-Люк О'Коннор : Room A 900\n");
        assertEquals("Introduction to Java (Advanced)", result.entries().getFirst().courseName());
        assertEquals("Жан-Люк О'Коннор", result.entries().getFirst().teacherName());
        assertEquals("9-Z", result.entries().getFirst().groupSection());
    }

    @Test void malformedBlockDoesNotConsumeNextValidBlock() {
        var result = parser.parse(HEADER + "BAD 123 Missing teacher [02-Q] : R 123\n"
                + "DEF 234 Missing section\n: R 456\n" + block("Morgan Rivera", "04-N"));
        assertEquals(new ScheduleParser.Summary(3, 1, 2, 0), result.summary());
    }

    @Test void explicitMultipleTeachersBecomeSeparateEntries() {
        var result = parser.parse(HEADER + block("Morgan Rivera, Jamie Quinn", "04-N"));
        assertEquals(new ScheduleParser.Summary(2, 2, 0, 0), result.summary());
        assertEquals("Jamie Quinn", result.entries().get(1).teacherName());
    }

    @Test void malformedTeacherDoesNotRemoveOtherTeacherInBlock() {
        var result = parser.parse(HEADER + block("Morgan Rivera, 123", "04-N"));
        assertEquals(new ScheduleParser.Summary(2, 1, 1, 0), result.summary());
    }

    @Test void missingOrAmbiguousTermSkipsRatherThanGuesses() {
        assertEquals(new ScheduleParser.Summary(1, 0, 1, 0), parser.parse(block("Morgan Rivera", "04-N")).summary());
        assertEquals(new ScheduleParser.Summary(1, 0, 1, 0),
                parser.parse(HEADER + "2039-2040 1 term\n" + block("Morgan Rivera", "04-N")).summary());
    }

    @Test void alternateTermFormatAndWrappedCodeAreSupported() {
        var result = parser.parse("2045 - 2046 term 3\n" + block("Morgan Rivera", "04-N").replace("ABC 789", "ABC\n789"));
        assertEquals("ABC 789", result.entries().getFirst().courseCode());
        assertEquals("2045 - 2046 term 3", result.entries().getFirst().academicTerm());
    }

    @Test void printFooterDoesNotPolluteNameAcrossPageBreak() {
        var result = parser.parse(HEADER + block("Morgan\n25.09.2038, 10:00 Schedule\nabout:blank 1/2\nRivera", "04-N"));
        assertEquals("Morgan Rivera", result.entries().getFirst().teacherName());
    }

    @Test void compactHasNoCompleteEntry() {
        assertEquals(new ScheduleParser.Summary(1, 0, 1, 0), parser.parse(HEADER + "ABC 789 [04-N] : R 300").summary());
    }

    @Test void academicQualificationIsNotAnotherTeacher() {
        var result = parser.parse(HEADER + block("Morgan Rivera, Doctor of Science", "04-N"));
        assertEquals(new ScheduleParser.Summary(1, 1, 0, 0), result.summary());
        assertEquals("Morgan Rivera", result.entries().getFirst().teacherName());
    }

    @Test void pageBreakRoomFragmentDoesNotPolluteTeacherOrAddAnEntry() {
        var result = parser.parse(HEADER + "ABC 789 Course Name [04-N]\nMorgan Rivera\n"
                + "25.09.2038, 10:00 Schedule\nabout:blank 1/2\n(Cabinet XX 99)\nXX 99\n: Building Z\nVR 100\n");
        assertEquals(new ScheduleParser.Summary(1, 1, 0, 0), result.summary());
        assertEquals("Morgan Rivera", result.entries().getFirst().teacherName());
    }
}
