package kz.sdu.feedback.moodle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import kz.sdu.feedback.FeedbackApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Explicit CLI only. This class is not a Spring runner/component. */
public class MoodleImportCommand {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[0].equals("preflight") || args[0].equals("import"))) {
            throw new IllegalArgumentException("Usage: MoodleImportCommand <preflight|import> <cache-directory> <report-directory>");
        }
        var catalog = MoodleCatalogExport.readCache(Path.of(args[1]));
        var candidates = new MoodleImportMapper().map(catalog.records());
        // Approval applies to this reviewed Fall 2026 snapshot, not arbitrary future crawls.
        if (catalog.cards().size() != 1756 || candidates.teachers().size() != 500
                || candidates.courses().size() != 898 || candidates.offerings().size() != 1731) {
            throw new IllegalStateException("Candidate totals differ from the approved Phase 3 snapshot; review before import.");
        }
        Path directory = Path.of(args[2]);
        Files.createDirectories(directory);
        Path report = directory.resolve("fall-2026-" + args[0] + "-" + System.currentTimeMillis() + ".txt");
        var text = new StringBuilder("Fall 2026 explicit import\nTime: " + Instant.now() + "\nMode: " + args[0] + "\n");
        text.append("Moodle HTTP requests: 0\nCandidates: Teachers=500, Courses=898, Offerings=1731\n");
        for (var card : catalog.cards()) if (!card.skipReason().isEmpty()) {
            text.append("SKIPPED card ").append(card.moodleCourseId()).append(" page ").append(card.sourcePage())
                    .append(": ").append(card.skipReason()).append('\n');
        }
        for (var review : candidates.reviews()) {
            text.append("REVIEW card ").append(review.record().moodleCourseId()).append(" teacher ")
                    .append(review.record().moodleTeacherId()).append(": ").append(review.reason()).append('\n');
        }
        var app = new SpringApplication(FeedbackApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // Command must never run sample seeding or silently migrate schema.
        try (var context = app.run("--app.seed.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate")) {
            var service = context.getBean(MoodleCatalogImportService.class);
            var before = service.preflight(candidates);
            text.append("PREFLIGHT: ").append(before).append('\n');
            Files.writeString(report, text);
            System.out.println("PREFLIGHT: " + before);
            if (!before.conflicts().isEmpty()) throw new IllegalStateException("Unsafe conflicts; see " + report);
            if (args[0].equals("import")) {
                MoodleCatalogImportService.Summary result;
                try {
                    result = service.importApproved(candidates);
                } catch (RuntimeException failure) {
                    Files.writeString(report, "ABORTED / ROLLED BACK: " + failure.getMessage() + "\n", StandardOpenOption.APPEND);
                    throw failure;
                }
                // The transactional proxy has committed before returning here.
                Files.writeString(report, "COMMITTED: " + result + "\n", StandardOpenOption.APPEND);
                System.out.println("COMMITTED: " + result);
            } else {
                Files.writeString(report, "READ-ONLY PREFLIGHT COMPLETE; no rows saved.\n", StandardOpenOption.APPEND);
            }
        }
        System.out.println("Report: " + report.toAbsolutePath());
    }
}
