package kz.sdu.feedback.schedule.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScheduleParser {
    private static final Pattern TERM = Pattern.compile(
            "\\b20\\d{2}\\s*[-–—/]\\s*20\\d{2}\\s+(?:\\d{1,2}\\s*term|term\\s*\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    // A new course block begins at the start of a PDF text line; its code may wrap.
    private static final Pattern CODE = Pattern.compile("(?m)^\\h*([A-Z]{2,8}[\\s\\p{Z}]+\\d{3,4}[A-Z]?)\\b");
    private static final Pattern SECTION = Pattern.compile("\\[\\s*(\\d{1,4}\\s*[-–—]\\s*[A-Za-z0-9]{1,4})\\s*\\]");
    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’.-]*(?:\\s+\\p{L}[\\p{L}\\p{M}'’.-]*){1,7}");
    private static final Pattern CREDITS = Pattern.compile("\\s*\\[\\s*\\d+\\s*cr\\s*/\\s*\\d+\\s*ECTS\\s*\\]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOAD = Pattern.compile("\\s*\\(\\s*\\d+(?:\\s*\\+\\s*\\d+){1,3}\\s*\\)\\s*$");
    private static final Pattern QUALIFICATION = Pattern.compile(
            ",\\s*Doctor of Science\\s*$", Pattern.CASE_INSENSITIVE);
    // Browser print footers can interrupt a block across pages. They are discarded, never persisted.
    private static final Pattern FOOTER = Pattern.compile(
            "(?im)^\\h*\\d{1,2}[./]\\d{1,2}[./]\\d{4},?\\h+\\d{1,2}:\\d{2}\\h+Schedule\\h*$"
            + "|^\\h*about:blank\\h+\\d+\\s*/\\s*\\d+\\h*$");

    public record Entry(String courseCode, String courseName, String teacherName,
                        String groupSection, String academicTerm) { }
    public record Summary(int totalCandidates, int extractedCount, int skippedCount, int duplicateCount) { }
    public record Result(List<Entry> entries, Summary summary) { }

    public Result parse(String rawText) {
        String text = FOOTER.matcher(rawText).replaceAll("");
        var terms = new LinkedHashSet<String>();
        TERM.matcher(text).results().forEach(match -> terms.add(normalize(match.group())));
        String term = terms.size() == 1 ? terms.iterator().next() : null;
        var starts = CODE.matcher(text).results().toList();
        var unique = new LinkedHashSet<Entry>();
        int candidates = 0, skipped = 0, duplicates = 0;
        for (int i = 0; i < starts.size(); i++) {
            var start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1).start() : text.length();
            String block = normalize(text.substring(start.end(), end));
            // An isolated code-like token with no following content is not an entry block.
            if (block.isEmpty()) continue;
            var section = SECTION.matcher(block);
            if (term == null || !section.find()) { candidates++; skipped++; continue; }
            String courseName = block.substring(0, section.start()).replaceFirst("^[\\s\\uFFFD:–—-]+", "");
            courseName = normalize(LOAD.matcher(CREDITS.matcher(courseName).replaceFirst("")).replaceFirst(""));
            int teacherEnd = block.indexOf(':', section.end());
            if (courseName.isBlank() || courseName.length() > 512
                    || !courseName.matches("\\p{L}[\\p{L}\\p{M}\\p{N}\\s'’.,&()/+–—-]*") || teacherEnd < 0) {
                candidates++; skipped++; continue;
            }
            String teacherText = block.substring(section.end(), teacherEnd);
            // PDF reading order can insert a parenthesized room fragment across a page break.
            int roomFragment = teacherText.indexOf('(');
            if (roomFragment >= 0) teacherText = teacherText.substring(0, roomFragment);
            teacherText = QUALIFICATION.matcher(teacherText).replaceFirst("");
            // Explicit comma-separated full names represent separate teachers, not academic qualifications.
            for (String teacher : teacherText.split(",", -1)) {
                candidates++;
                teacher = normalize(teacher);
                if (!NAME.matcher(teacher).matches() || teacher.length() > 255) { skipped++; continue; }
                var entry = new Entry(normalize(start.group(1)), courseName, teacher,
                        normalize(section.group(1)), term);
                if (!unique.add(entry)) duplicates++;
            }
        }
        return new Result(List.copyOf(unique), new Summary(candidates, unique.size(), skipped, duplicates));
    }

    private static String normalize(String text) { return text.replaceAll("[\\s\\p{Z}]+", " ").strip(); }
}
