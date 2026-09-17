package kz.sdu.feedback.moodle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.sdu.feedback.moodle.MoodleCatalogExport.CachedCatalog;

/** Offline entry point, deliberately independent of Spring Boot and persistence. */
public class MoodleImportDryRun {
    public static MoodleImportMapper.Result run(Path cache, Path reportFile) throws IOException {
        var catalog = MoodleCatalogExport.readCache(cache);
        var result = new MoodleImportMapper().map(catalog.records());
        Files.createDirectories(reportFile.toAbsolutePath().getParent());
        Files.writeString(reportFile, report(catalog, result));
        System.out.printf("Cards=%d; records=%d; teachers=%d; courses=%d; offerings=%d; review records=%d%n",
                catalog.cards().size(), catalog.records().size(), result.teachers().size(),
                result.courses().size(), result.offerings().size(), result.reviews().size());
        System.out.println("Offline report: " + reportFile.toAbsolutePath());
        return result;
    }

    private static String report(CachedCatalog catalog, MoodleImportMapper.Result result) {
        var analysis = new MoodleDuplicateAnalysis().analyzeSource(catalog.records(), catalog.cards());
        var skipped = catalog.cards().stream().filter(c -> !c.skipReason().isEmpty()).toList();
        var noContacts = skipped.stream().filter(c -> c.teacherContactCount() == 0).count();
        var logicalGroups = result.offerings().stream().collect(Collectors.groupingBy(
                o -> List.of(o.teacher().moodleTeacherId(), o.course().code(), o.semester()), LinkedHashMap::new, Collectors.toList()))
                .values().stream().filter(g -> g.size() > 1).toList();
        var multiTeacher = result.offerings().stream().collect(Collectors.groupingBy(
                MoodleImportMapper.OfferingCandidate::moodleCourseId, LinkedHashMap::new, Collectors.toList()))
                .values().stream().filter(g -> g.size() > 1).toList();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("Cached pages (no new HTTP requests)", catalog.status().getProperty("pageCount"));
        totals.put("Source cards", catalog.cards().size());
        totals.put("Valid parsed Teacher-Course records", catalog.records().size());
        totals.put("Source cards skipped", skipped.size());
        totals.put("No named public teacher contact", noContacts);
        totals.put("Malformed/unparsed source cards", skipped.size() - noContacts);
        totals.put("Unique source teacher IDs", analysis.totals().get("uniqueMoodleTeacherIds"));
        totals.put("Teacher candidates (department=null)", result.teachers().size());
        totals.put("Teacher identity conflicts", result.teacherConflicts().size());
        totals.put("Course candidates / resolved codes", result.courses().size());
        totals.put("Unresolved course codes", result.courseConflicts().size());
        totals.put("Offering candidates (section=null)", result.offerings().size());
        totals.put("Moodle course IDs represented by offerings", result.offerings().stream().map(MoodleImportMapper.OfferingCandidate::moodleCourseId).distinct().count());
        totals.put("Parsed records requiring review", result.reviews().size());
        totals.put("Exact repeated source-key records (one candidate per key)", result.repeatedSourceRecords());
        totals.put("Logical duplicate groups retained with different Moodle IDs", logicalGroups.size());
        totals.put("Offerings in those groups", logicalGroups.stream().mapToInt(List::size).sum());
        totals.put("Extra offerings retained beyond one per logical group", logicalGroups.stream().mapToInt(g -> g.size() - 1).sum());
        totals.put("Multi-teacher cards mapped", multiTeacher.size());
        totals.put("Offerings from multi-teacher cards", multiTeacher.stream().mapToInt(List::size).sum());
        totals.put("KZ/RU groups retained", analysis.groups().get("TYPE 2 Explicit KZ/RU variants with different Moodle IDs").size());

        var text = new StringBuilder("# Phase 3 — full offline dry-run\n\n");
        text.append("Cache snapshot: ").append(catalog.status().getProperty("finishedAt")).append(".\n\n")
                .append("No network, Spring context, repositories, JDBC, schema changes or database writes in this command. ")
                .append("Candidates describe source mapping, not inserts or reconciliation with existing database rows.\n\n");
        totals.forEach((label, value) -> text.append("- ").append(label).append(": **").append(value).append("**\n"));
        text.append("\nSkipped source cards and rejected parsed relationships are different units; no-contact cards produce no records.\n\n")
                .append("## Identity and safety rules\n\n")
                .append("Teacher: positive Moodle teacher ID, one whitespace-normalized display name; differing names require review. ")
                .append("Equal names with different IDs stay separate. Department is null.\n\n")
                .append("Course: uppercase, whitespace-normalized code; names must agree after case/whitespace normalization. ")
                .append("Only equivalent formatting variants use the first source spelling. Unresolved codes yield no Course or Offering candidates.\n\n")
                .append("Offering: Moodle course ID + Moodle teacher ID + semester in memory; the database uses the corresponding internal teacher_id. ")
                .append("Different course IDs remain separate. Section is null. Source-key/code contradictions require review.\n\n")
                .append("Teachers may remain candidates even when their particular offering is blocked by a course conflict. ")
                .append("No teacher is guessed from title text. Missing source IDs, required text or oversized text require review.\n\n")
                .append("## Four reviewed course-code conflicts\n\n");
        var classifications = Map.of(
                "IBCTL 201", "D — genuine semantic difference: IB PYP (Primary Year Program) versus IB MYP (Middle Year Program). UNRESOLVED; hold the whole code.",
                "LAW 190", "C — expanded personal suffix (Aruzhan Ruslankyzy Seitmagambet), card 916. Academic name agrees with other public cards after removing this suffix.",
                "TFL 371", "C — combined personal suffix (Maksat Musabek+Gulera Makhat), card 722. Retain (B1) and only the actual public contact Maksat; do not infer Gulera as a second contact.",
                "CSS 416", "C — personal honorific/alias suffix (Dr. Mohammed Ala'anzy), card 51. Academic name agrees with card 50. This does not establish a general teacher alias rule.");
        for (String code : List.of("IBCTL 201", "LAW 190", "TFL 371", "CSS 416")) {
            text.append("### ").append(code).append("\n\n").append(classifications.get(code)).append("\n\n");
            appendRecords(text, catalog.records().stream().filter(r -> r.courseCode().equals(code)).toList());
        }
        text.append("\nCorrections require exact card ID, contact ID, course code and remaining title match; changed evidence is not silently corrected. ")
                .append("All raw sourceTitle values are retained. No semantic conflict is resolved by choosing a convenient name.\n\n")
                .append("## All mapping reviews\n\n");
        result.teacherConflicts().forEach((id, records) -> { text.append("Teacher ID conflict: ").append(id).append("\n\n"); appendRecords(text, records); });
        for (var review : result.reviews()) {
            text.append(review.reason()).append("\n\n");
            appendRecords(text, List.of(review.record()));
        }
        text.append("\n## Skipped source cards\n\n| Moodle ID | Page | Contacts | Reason | Source title |\n|---|---|---|---|---|\n");
        for (var card : skipped) text.append(row(card.moodleCourseId(), card.sourcePage(), card.teacherContactCount(), card.skipReason(), card.sourceTitle()));
        text.append("\n## Preserved logical duplicates\n\n| Moodle ID | Teacher ID | Teacher | Code | Name | Semester | Section |\n|---|---|---|---|---|---|---|\n");
        for (var group : logicalGroups) for (var o : group) text.append(row(o.moodleCourseId(), o.teacher().moodleTeacherId(), o.teacher().name(), o.course().code(), o.course().name(), o.semester(), o.section()));
        text.append("\n## Multi-teacher cards\n\n| Moodle ID | Teacher ID | Teacher | Code | Semester |\n|---|---|---|---|---|\n");
        for (var group : multiTeacher) for (var o : group) text.append(row(o.moodleCourseId(), o.teacher().moodleTeacherId(), o.teacher().name(), o.course().code(), o.semester()));
        text.append("\n## Explicit suffixes\n\n");
        analysis.suffixes().forEach((suffix, count) -> text.append("- ").append(suffix).append(": ").append(count).append(" cards\n"));
        text.append("\nNONE is not EN. No language field or inferred section is created.\n\n");
        for (var group : analysis.groups().get("TYPE 2 Explicit KZ/RU variants with different Moodle IDs")) appendRecords(text, group);
        text.append("\n## Database readiness and next phase\n\n")
                .append("JPA permits nullable department/section and adds nullable source ID columns. Course.code stays unique. ")
                .append("Teacher non-null Moodle ID is unique; offering source tuple (moodle_course_id, teacher_id, semester) is unique. ")
                .append("The existing teacher/course/semester/section constraint stays: null sections allow distinct Moodle cards in PostgreSQL. ")
                .append("Populating equal sections on those cards later needs a separate reconciliation decision.\n\n")
                .append("This offline run cannot verify or adapt a physical PostgreSQL schema. See README for nullability SQL and schema inspection. ")
                .append("Do not assume ddl-auto=update relaxes existing NOT NULL constraints. No seed rows are touched.\n\n")
                .append("Phase 4 should first verify the schema and review IBCTL 201 and skipped cards, then implement a separately authorized explicit transactional, idempotent import of approved candidates. ")
                .append("Reconcile existing Course codes/names against the database; never merge seed teachers by name or overwrite conflicting seed course names automatically. ")
                .append("Keep seed, exclude unresolved cases, test re-run counts and rollback. No automatic startup import or scheduled sync.\n");
        return text.toString();
    }

    private static void appendRecords(StringBuilder text, List<MoodleCourseRecord> records) {
        text.append("| Moodle ID | Teacher ID | Teacher | Code | Parsed academic name | Page | Semester | Original source title |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (var r : records) text.append(row(r.moodleCourseId(), r.moodleTeacherId(), r.teacherName(), r.courseCode(), r.courseName(), r.sourcePage(), r.semester(), r.sourceTitle()));
        text.append('\n');
    }

    private static String row(Object... fields) {
        return "| " + Arrays.stream(fields).map(value -> value == null ? "null" : value.toString()
                .replace("|", "\\|").replace("\n", " ")).collect(Collectors.joining(" | ")) + " |\n";
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: MoodleImportDryRun <cache-directory> <report-file.md>");
        run(Path.of(args[0]), Path.of(args[1]));
    }
}
