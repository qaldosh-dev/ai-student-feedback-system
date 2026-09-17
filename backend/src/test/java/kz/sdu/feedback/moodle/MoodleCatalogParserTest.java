package kz.sdu.feedback.moodle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoodleCatalogParserTest {
    private final MoodleCatalogParser parser = new MoodleCatalogParser();

    @Test
    void extractsCodeNameTeacherAndSemesterWithoutRemovingSubjectQualifier() {
        var result = parser.parse(page(card("MDE 151 Module of Social and Political Knowledge "
                + "(Political Science) (Yerkebulan Akberdiyev) KZ", "Yerkebulan Akberdiyev")));
        assertEquals(List.of(new MoodleCourseRecord("Yerkebulan Akberdiyev", "MDE 151",
                "Module of Social and Political Knowledge (Political Science)", "Fall 2026", 1L, 0,
                "MDE 151 Module of Social and Political Knowledge (Political Science) (Yerkebulan Akberdiyev) KZ")), result.records());
        assertEquals(0, result.skipped().size());
    }

    @Test
    void acceptsLongPrefixesAndKeepsMeaningfulParenthesesWithoutTeacherSuffix() {
        var result = parser.parse(page(card("IBCTL 202 IB MYP (Middle Year Program)", "Teacher A")));
        assertEquals("IBCTL 202", result.records().getFirst().courseCode());
        assertEquals("IB MYP (Middle Year Program)", result.records().getFirst().courseName());
    }

    @Test
    void createsSeparateRelationshipsForMultipleTeachersAndIgnoresRepeatedContacts() {
        var result = parser.parse(page(card("CSS 114 Programming Fundamentals", "Teacher A", "Teacher B", "Teacher A")));
        assertEquals(List.of(
                new MoodleCourseRecord("Teacher A", "CSS 114", "Programming Fundamentals", "Fall 2026",
                        1L, 0, "CSS 114 Programming Fundamentals"),
                new MoodleCourseRecord("Teacher B", "CSS 114", "Programming Fundamentals", "Fall 2026",
                        1L, 0, "CSS 114 Programming Fundamentals")), result.records());
    }

    @Test
    void skipsMalformedAndTeacherlessCardsButContinuesWithValidCards() {
        var result = parser.parse(page(
                card("Unstructured title", "Teacher A")
                + card("CSS 114 Programming Fundamentals")
                + card("CSS 114 (Teacher A)", "Teacher A")
                + card("CSS 114 Programming Fundamentals", " ")
                + card("CSS 212 Database Systems", "Teacher B")));
        assertEquals(5, result.courseCount());
        assertEquals(4, result.skipped().size());
        assertEquals(1, result.records().size());
        assertEquals("Teacher B", result.records().getFirst().teacherName());
    }

    @Test
    void neverBorrowsContactsFromANeighboringCard() {
        var result = parser.parse(page(card("CSS 114 Programming Fundamentals", "Teacher A")
                + "<div data-region='course-content' data-course-id='2'><h4 class='coursename'>CSS 212 Databases</h4></div>"));
        assertEquals(1, result.records().size());
        assertEquals(1, result.skipped().size());
    }

    @Test
    void rejectsWrongSemesterLoginPagesAndChangedLayouts() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("<h1>Log in</h1>"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(page("").replace("Fall 2026", "Spring 2027")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(page("<div>Different layout</div>")));
    }

    @Test
    void preservesDistinctMoodleIdsPageAndLanguageSuffixForDuplicateLookingCards() {
        String first = card("MDE 172 Philosophy (Teacher A)KZ", "Teacher A").replace("data-course-id='1'", "data-course-id='1764'");
        String second = card("MDE 172 Philosophy (Teacher A)RU", "Teacher A").replace("data-course-id='1'", "data-course-id='1765'");
        var result = parser.parse(page(first + second), 3);
        assertEquals(List.of(1764L, 1765L), result.records().stream().map(MoodleCourseRecord::moodleCourseId).toList());
        assertEquals(List.of(3, 3), result.records().stream().map(MoodleCourseRecord::sourcePage).toList());
        assertEquals(List.of("Philosophy", "Philosophy"), result.records().stream().map(MoodleCourseRecord::courseName).toList());
        assertEquals("MDE 172 Philosophy (Teacher A)RU", result.records().get(1).sourceTitle());
    }

    @Test
    void malformedMissingAndOverflowIdsKeepValidRelationshipsWithWarnings() {
        for (String attribute : List.of("", "data-course-id='oops'", "data-course-id='-1'",
                "data-course-id='0'", "data-course-id='999999999999999999999999'")) {
            String html = page(card("CSS 114 Programming Fundamentals", "Teacher A")
                    .replace("data-course-id='1'", attribute));
            var result = parser.parse(html, 2);
            assertEquals(1, result.records().size());
            assertEquals(0, result.skipped().size());
            assertEquals(1, result.warnings().size());
            assertNull(result.records().getFirst().moodleCourseId());
        }
    }

    @Test
    void checksCourseLinkAgainstIdWithoutInventingAnIdentityOnConflict() {
        String html = page(card("CSS 114 Programming Fundamentals", "Teacher A")
                .replace("</h4>", "</h4><a href='https://moodle.sdu.edu.kz/course/view.php?id=1'>Open</a>"));
        assertEquals(1L, parser.parse(html).records().getFirst().moodleCourseId());
        var conflict = parser.parse(html.replace("view.php?id=1", "view.php?id=2"));
        assertNull(conflict.records().getFirst().moodleCourseId());
        assertEquals(1, conflict.warnings().size());
    }

    @Test
    void countsSourceIdsEvenWhenCardCannotProduceATeacherRelationship() {
        var result = parser.parse(page(card("CSS 114 Programming Fundamentals")));
        assertEquals(1, result.courseCount());
        assertEquals(java.util.Set.of(1L), result.courseIds());
        assertEquals(0, result.records().size());
        assertEquals(1, result.skipped().size());
    }

    @Test
    void rejectsNegativePagesBeforeFetching() {
        for (int page : List.of(-1, -5)) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse("", page));
            assertThrows(IllegalArgumentException.class, () -> new MoodleCatalogClient().fetchPage(page));
        }
        assertEquals(87, parser.parse(page(card("CSS 114 Programming", "Teacher A")), 87).records().getFirst().sourcePage());
    }

    @Test
    void extractsTeacherIdsAndDoesNotCollapseEqualNamesWithDifferentIds() {
        String html = page(card("CSS 114 Programming", "Teacher A")
                .replace("<a class=", "<a href='/user/profile.php?id=41' class=")
                .replace("</a>", "</a><a class='rui-card-contact rui-user-Учитель' href='/user/view.php?course=1&amp;id=42'>"
                        + "<div class='rui-user-name'><span class='w-100'>Teacher A</span></div></a>"));
        var result = parser.parse(html);
        assertEquals(List.of(41L, 42L), result.records().stream().map(MoodleCourseRecord::moodleTeacherId).toList());
        assertEquals(2, result.records().size());
    }

    @Test
    void keepsNamedContactsWithMissingMalformedOrUnrelatedProfileLinks() {
        for (String href : List.of("", "/user/profile.php?id=oops", "/user/profile.php?id=0",
                "/user/profile.php?id=99999999999999999999999", "https://example.com/user/profile.php?id=42", "http://[")) {
            var result = parser.parse(page(card("CSS 114 Programming", "Teacher A").replace("<a class=", "<a href='" + href + "' class=")));
            assertEquals(1, result.records().size());
            assertEquals("Teacher A", result.records().getFirst().teacherName());
            assertNull(result.records().getFirst().moodleTeacherId());
        }
    }

    @Test
    void removesOnlyThreeReviewedPersonalSuffixesAndKeepsAcademicQualifiers() {
        Object[][] examples = {
                {916L, 1146L, "LAW 190", "Legal thinking and methodology (Aruzhan Ruslankyzy Seitmagambet)",
                        "Legal thinking and methodology", "Aruzhan Seitmagambet"},
                {722L, 1052L, "TFL 371", "Second FL(5)-Chinese (B1) (Maksat Musabek+Gulera Makhat)",
                        "Second FL(5)-Chinese (B1)", "Maksat Musabek"},
                {51L, 1181L, "CSS 416", "Parallel and Distributed computing (Dr. Mohammed Ala'anzy)",
                        "Parallel and Distributed computing", "MohammedAlaaFadhil Al-Hadeethi"}
        };
        for (var example : examples) {
            String title = example[2] + " " + example[3];
            String html = page(card(title, (String) example[5])
                    .replace("data-course-id='1'", "data-course-id='" + example[0] + "'")
                    .replace("<a class=", "<a href='/user/profile.php?id=" + example[1] + "' class="));
            var result = parser.parse(html);
            assertEquals(1, result.records().size());
            assertEquals(example[4], result.records().getFirst().courseName());
            assertEquals(example[1], result.records().getFirst().moodleTeacherId());
            assertEquals(title, result.records().getFirst().sourceTitle());
            assertEquals(example[3], parser.parse(html.replace("data-course-id='" + example[0] + "'", "data-course-id='9999'"))
                    .records().getFirst().courseName());
            assertEquals(example[3], parser.parse(html.replace("profile.php?id=" + example[1], "profile.php?id=9999"))
                    .records().getFirst().courseName());
            assertEquals(example[3] + " revised", parser.parse(html.replace(title, title + " revised"))
                    .records().getFirst().courseName());
        }
    }

    private String page(String cards) {
        return "<header id='page-header'><h1>Fall 2026 (2026-2027)</h1></header>"
                + "<div class='courses category-browse-4'>" + cards + "</div>";
    }

    // Small, offline fixture reflecting the inspected public Alpha-theme structure.
    private String card(String title, String... teachers) {
        StringBuilder contacts = new StringBuilder();
        for (String teacher : teachers) {
            contacts.append("<a class='rui-card-contact rui-user-Учитель'><div class='rui-user-name'>")
                    .append("<span class='w-100'>").append(teacher)
                    .append("</span><span>Учитель</span></div></a>");
        }
        return "<div class='position-relative'><div class='rui-course-card-text'>"
                + "<div class='rui-card-course-contacts'>" + contacts + "</div></div>"
                + "<div data-region='course-content' data-course-id='1'><h4 class='coursename'>"
                + "<span class='sr-only'>Название курса</span>" + title + "</h4></div></div>";
    }
}
