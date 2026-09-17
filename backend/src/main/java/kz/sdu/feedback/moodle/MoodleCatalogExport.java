package kz.sdu.feedback.moodle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import kz.sdu.feedback.moodle.MoodleCatalogParser.SourceCard;

/** Offline analysis entry point. Reads cache files only; has no HTTP or database client. */
public class MoodleCatalogExport {
    public static CachedCatalog readCache(Path directory) throws IOException {
        var status = new Properties();
        try (var reader = Files.newBufferedReader(directory.resolve("crawl.properties"))) {
            status.load(reader);
        }
        if (!"true".equals(status.getProperty("complete"))) {
            throw new IOException("Crawl is incomplete. Check crawl.properties; no complete-catalog report was generated.");
        }
        int pageCount = Integer.parseInt(status.getProperty("pageCount"));
        String first = Files.readString(MoodleCatalogCrawl.pagePath(directory, 0));
        if (MoodleCatalogCrawl.discoverPageCount(first) != pageCount) {
            throw new IOException("Cached first-page pagination disagrees with crawl manifest.");
        }
        List<MoodleCourseRecord> records = new ArrayList<>();
        List<SourceCard> cards = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        var parser = new MoodleCatalogParser();
        for (int page = 0; page < pageCount; page++) {
            String html = page == 0 ? first : Files.readString(MoodleCatalogCrawl.pagePath(directory, page));
            try {
                var result = parser.parse(html, page);
                records.addAll(result.records());
                cards.addAll(result.sourceCards());
                diagnostics.addAll(result.skipped());
                diagnostics.addAll(result.warnings());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Cannot parse cached page " + page + "; analysis stopped.", exception);
            }
        }
        return new CachedCatalog(List.copyOf(records), List.copyOf(cards), List.copyOf(diagnostics), status);
    }

    public static void export(CachedCatalog catalog, Path directory) throws IOException {
        Files.createDirectories(directory);
        try (var writer = Files.newBufferedWriter(directory.resolve("fall-2026-records.csv"))) {
            writer.write("\ufeffmoodleCourseId,moodleTeacherId,teacherName,courseCode,courseName,semester,sourcePage,sourceTitle\n");
            for (var r : catalog.records()) writer.write(csv(r.moodleCourseId(), r.moodleTeacherId(), r.teacherName(),
                    r.courseCode(), r.courseName(), r.semester(), r.sourcePage(), r.sourceTitle()) + "\n");
        }
        try (var writer = Files.newBufferedWriter(directory.resolve("fall-2026-cards.csv"))) {
            writer.write("\ufeffmoodleCourseId,sourcePage,sourceTitle,rawCourseCode,courseCode,courseName,teacherContactCount,skipReason\n");
            for (var c : catalog.cards()) writer.write(csv(c.moodleCourseId(), c.sourcePage(), c.sourceTitle(),
                    c.rawCourseCode(), c.courseCode(), c.courseName(), c.teacherContactCount(), c.skipReason()) + "\n");
        }
        Files.write(directory.resolve("fall-2026-diagnostics.txt"), catalog.diagnostics());
        var analysis = new MoodleDuplicateAnalysis().analyzeSource(catalog.records(), catalog.cards());
        var report = new StringBuilder("# Fall 2026 public Moodle source analysis\n\n");
        report.append("Read-only snapshot. No merging, database writes or inferred teacher assignments.\n\n");
        for (String key : List.of("source", "startedAt", "finishedAt", "complete", "pageCount", "successfulPages", "requests", "retries")) {
            report.append("- ").append(key).append(": ").append(catalog.status().getProperty(key)).append('\n');
        }
        report.append("\n## Totals\n\n");
        analysis.totals().forEach((key, count) -> report.append("- ").append(key).append(": ").append(count).append('\n'));
        report.append("\nNormalization: case folding and whitespace collapse only. No name-based person merges.\n")
                .append("Teacher identity uses Moodle user ID; unresolved IDs stay source-local.\n")
                .append("Contactless name-like suffixes use a 2–4 capitalized-word heuristic, not verified teacher extraction.\n")
                .append("Suffixes below count source cards, not relationships; NONE never implies EN.\n\n");
        analysis.suffixes().forEach((suffix, count) -> report.append("- suffix ").append(suffix).append(": ").append(count).append('\n'));
        for (var entry : analysis.groups().entrySet()) {
            report.append("\n## ").append(entry.getKey()).append("\n\nGroups: ").append(entry.getValue().size())
                    .append("; records involved: ").append(entry.getValue().stream().mapToInt(List::size).sum()).append("\n\n");
            int limit = entry.getKey().startsWith("TYPE") ? 10 : 5;
            for (var group : entry.getValue().stream().limit(limit).toList()) {
                report.append("| Course ID | Teacher ID | Teacher | Code | Course name | Page | Semester | Source title |\n")
                        .append("|---|---|---|---|---|---|---|---|\n");
                for (var r : group.stream().limit(10).toList()) report.append(markdown(r.moodleCourseId(), r.moodleTeacherId(),
                        r.teacherName(), r.courseCode(), r.courseName(), r.sourcePage(), r.semester(), r.sourceTitle()));
                if (group.size() > 10) report.append("\nRemaining records are in the complete CSV.\n");
                report.append('\n');
            }
        }
        for (var entry : analysis.cardGroups().entrySet()) {
            report.append("\n## ").append(entry.getKey()).append("\n\nCards involved: ")
                    .append(entry.getValue().stream().mapToInt(List::size).sum()).append("\n\n");
            report.append("| Course ID | Page | Source title | Code | Name | Contacts | Reason |\n|---|---|---|---|---|---|---|\n");
            for (var group : entry.getValue().stream().limit(10).toList()) {
                for (var c : group.stream().limit(10).toList()) report.append(markdown(c.moodleCourseId(), c.sourcePage(),
                        c.sourceTitle(), c.courseCode(), c.courseName(), c.teacherContactCount(), c.skipReason()));
            }
        }
        Files.writeString(directory.resolve("fall-2026-summary.md"), report);
        analysis.totals().forEach((key, value) -> System.out.println(key + "=" + value));
        analysis.groups().forEach((key, groups) -> System.out.println(key + "=" + groups.size()));
        System.out.println("suffixes=" + analysis.suffixes());
    }

    private static String csv(Object... fields) {
        return java.util.Arrays.stream(fields).map(value -> "\"" + (value == null ? "" : value.toString())
                .replace("\"", "\"\"") + "\"").collect(Collectors.joining(","));
    }

    private static String markdown(Object... fields) {
        return "| " + java.util.Arrays.stream(fields).map(value -> value == null ? "" : value.toString()
                .replace("|", "\\|").replace("\n", " ")).collect(Collectors.joining(" | ")) + " |\n";
    }

    public record CachedCatalog(List<MoodleCourseRecord> records, List<SourceCard> cards,
                                 List<String> diagnostics, Properties status) { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: MoodleCatalogExport <cache-directory> <analysis-directory>");
        export(readCache(Path.of(args[0])), Path.of(args[1]));
    }
}
