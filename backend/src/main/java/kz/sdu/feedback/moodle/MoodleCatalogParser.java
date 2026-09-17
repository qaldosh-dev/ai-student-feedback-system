package kz.sdu.feedback.moodle;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class MoodleCatalogParser {
    private static final Pattern COURSE_TITLE = Pattern.compile(
            "^([A-Z]+)\\s+(\\d+[A-Z]?)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    // Reviewed against the complete Fall 2026 cache. Exact guards avoid treating
    // arbitrary subject qualifiers or similar-looking names as teacher aliases.
    private static final List<ReviewedSuffix> REVIEWED_SUFFIXES = List.of(
            new ReviewedSuffix(916L, 1146L, "LAW 190",
                    "Legal thinking and methodology (Aruzhan Ruslankyzy Seitmagambet)",
                    "Legal thinking and methodology"),
            new ReviewedSuffix(722L, 1052L, "TFL 371",
                    "Second FL(5)-Chinese (B1) (Maksat Musabek+Gulera Makhat)",
                    "Second FL(5)-Chinese (B1)"),
            new ReviewedSuffix(51L, 1181L, "CSS 416",
                    "Parallel and Distributed computing (Dr. Mohammed Ala'anzy)",
                    "Parallel and Distributed computing"));

    public ParseResult parse(String html) {
        return parse(html, 0);
    }

    public ParseResult parse(String html, int sourcePage) {
        if (sourcePage < 0) {
            throw new IllegalArgumentException("Page number must be non-negative.");
        }
        var document = Jsoup.parse(html);
        Element heading = document.selectFirst("#page-header h1");
        if (heading == null || !heading.text().equals("Fall 2026 (2026-2027)")) {
            throw new IllegalArgumentException("Expected the public Fall 2026 category heading.");
        }
        var cards = document.select(
                ".courses.category-browse-4 [data-region=course-content]");
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("No course cards found; the Moodle layout may have changed.");
        }

        List<MoodleCourseRecord> records = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<SourceCard> sourceCards = new ArrayList<>();
        Set<Long> courseIds = new HashSet<>();
        for (Element card : cards) {
            String entry = "Page " + sourcePage + ", course " + card.attr("data-course-id") + ": ";
            Long moodleId = readCourseId(card, entry, warnings);
            if (moodleId != null) courseIds.add(moodleId);
            Element title = card.selectFirst(".coursename");
            // In the public Alpha theme, contacts are in the card's preceding sibling.
            Element summary = card.previousElementSibling();
            String sourceTitle = "";
            if (title != null) {
                Element cleanTitle = title.clone();
                cleanTitle.select(".sr-only").remove();
                sourceTitle = cleanTitle.text();
            }
            var match = COURSE_TITLE.matcher(sourceTitle);
            String rawCode = match.matches() ? match.group(1) + " " + match.group(2) : "";
            String code = rawCode.toUpperCase(Locale.ROOT);
            String name = match.matches() ? match.group(3) : "";
            if (title == null || summary == null || !summary.hasClass("rui-course-card-text")) {
                skipped.add(entry + "missing title or contact block");
                sourceCards.add(new SourceCard(moodleId, sourcePage, sourceTitle, rawCode, code, name, 0, "missing title or contact block"));
                continue;
            }
            var teachers = summary.select(
                            ".rui-card-course-contacts a.rui-card-contact.rui-user-Учитель")
                    .stream().map(contact -> readTeacher(contact, entry, warnings))
                    .filter(contact -> !contact.name().isBlank()).distinct().toList();
            if (teachers.isEmpty()) {
                skipped.add(entry + "no named teacher in public contacts");
                sourceCards.add(new SourceCard(moodleId, sourcePage, sourceTitle, rawCode, code, name, 0, "no named teacher in public contacts"));
                continue;
            }
            if (!match.matches()) {
                skipped.add(entry + "unrecognized course code/name: " + sourceTitle);
                sourceCards.add(new SourceCard(moodleId, sourcePage, sourceTitle, rawCode, code, name, teachers.size(), "unrecognized course code/name"));
                continue;
            }
            // Strip only suffixes matching actual contacts, not subject qualifiers in parentheses.
            String teacherNames = teachers.stream().map(TeacherContact::name).map(Pattern::quote).collect(Collectors.joining("|"));
            name = name.replaceFirst("(?i)(?:\\s*\\((?:" + teacherNames
                    + ")\\))+(?:\\s*(?:KZ|RU|EN))?$", "").strip();
            for (var correction : REVIEWED_SUFFIXES) {
                if (Long.valueOf(correction.courseId()).equals(moodleId)
                        && correction.code().equals(code) && correction.originalName().equals(name)
                        && teachers.size() == 1
                        && Long.valueOf(correction.teacherId()).equals(teachers.getFirst().id())) {
                    name = correction.academicName();
                    warnings.add(entry + "reviewed personal suffix removed; original sourceTitle retained");
                }
            }
            if (name.isEmpty()) {
                skipped.add(entry + "missing course name after removing teacher suffix");
                sourceCards.add(new SourceCard(moodleId, sourcePage, sourceTitle, rawCode, code, name, teachers.size(), "missing course name"));
                continue;
            }
            sourceCards.add(new SourceCard(moodleId, sourcePage, sourceTitle, rawCode, code, name, teachers.size(), ""));
            for (TeacherContact teacher : teachers) {
                records.add(new MoodleCourseRecord(teacher.name(), code, name, "Fall 2026",
                        moodleId, sourcePage, sourceTitle, teacher.id()));
            }
        }
        return new ParseResult(List.copyOf(records), List.copyOf(skipped),
                List.copyOf(warnings), cards.size(), Set.copyOf(courseIds), List.copyOf(sourceCards));
    }

    private TeacherContact readTeacher(Element contact, String entry, List<String> warnings) {
        Element nameElement = contact.selectFirst(".rui-user-name > span.w-100");
        String name = nameElement == null ? "" : nameElement.text();
        Long id = null;
        try {
            URI uri = URI.create(contact.attr("href"));
            if ((uri.getHost() == null || uri.getHost().equals("moodle.sdu.edu.kz"))
                    && ("/user/profile.php".equals(uri.getPath()) || "/user/view.php".equals(uri.getPath()))) {
                var match = Pattern.compile("(?:^|&)id=([^&]*)").matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
                if (match.find()) id = positiveId(match.group(1));
            }
        } catch (IllegalArgumentException exception) {
            // A malformed public link does not erase an otherwise usable displayed name.
        }
        if (id == null && !name.isBlank() && !contact.attr("href").isBlank()) {
            warnings.add(entry + "teacher '" + name + "' has no usable Moodle teacher ID");
        }
        return new TeacherContact(name, id);
    }

    private record TeacherContact(String name, Long id) { }

    private record ReviewedSuffix(long courseId, long teacherId, String code,
                                  String originalName, String academicName) { }

    public record SourceCard(Long moodleCourseId, int sourcePage, String sourceTitle,
                             String rawCourseCode, String courseCode, String courseName,
                             int teacherContactCount, String skipReason) { }

    private Long readCourseId(Element card, String entry, List<String> warnings) {
        Long id = positiveId(card.attr("data-course-id"));
        if (id == null) {
            warnings.add(entry + "missing or malformed Moodle ID; relationship kept with null ID");
            return null;
        }
        Element link = card.selectFirst("a[href*='/course/view.php?id=']");
        if (link != null) {
            var match = Pattern.compile("[?&]id=([^&#]*)").matcher(link.attr("href"));
            if (!match.find() || !id.equals(positiveId(match.group(1)))) {
                warnings.add(entry + "course link disagrees with data-course-id; relationship kept with null ID");
                return null;
            }
        }
        return id;
    }

    private Long positiveId(String value) {
        try {
            if (!value.matches("[0-9]+")) return null;
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record ParseResult(List<MoodleCourseRecord> records, List<String> skipped,
                              List<String> warnings, int courseCount, Set<Long> courseIds, List<SourceCard> sourceCards) {
    }
}
