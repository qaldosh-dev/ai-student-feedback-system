package kz.sdu.feedback.schedule;

import static kz.sdu.feedback.schedule.entity.ScheduleUpload.ValidationReason.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import kz.sdu.feedback.schedule.service.SchedulePdfValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SchedulePdfValidatorTest {
    @TempDir Path directory;
    private final SchedulePdfValidator validator = new SchedulePdfValidator();

    @Test void representativeScheduleIsValidWithoutSampleSpecificValues() throws Exception {
        assertNull(validator.validate(write(SchedulePdfFixtures.schedule())));
    }

    @Test void indicatorsCanSpanMultiplePagesAndFlexibleWhitespace() throws Exception {
        assertNull(validator.validate(write(SchedulePdfFixtures.pdf(
                "Student SCHEDULE\n2035 - 2036 term 3", "Day / Hour\nXYZ 1234A Different course\n[8-Q]\nAlex Taylor\n: Room B 500"))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "Assignment 1: Write an essay on software development.",
            "Schedule a meeting tomorrow.",
            "Schedule\nDay/Hour\nABC 123", // no academic term
            "Schedule\n2031-2032 2 term\nABC 123", // no timetable header
            "Schedule\nDay/Hour\n2031-2032 2 term" // no course-like code
    })
    void unrelatedOrIncompleteDocumentIsNotSchedule(String text) throws Exception {
        assertEquals(NOT_SCHEDULE, validator.validate(write(SchedulePdfFixtures.pdf(text))));
    }

    @Test void fakePdfExtensionIsInvalid() throws Exception {
        assertEquals(INVALID_PDF, validator.validate(write("Schedule Day/Hour 2031-2032 2 term ABC 123".getBytes(StandardCharsets.UTF_8))));
    }

    @ParameterizedTest @ValueSource(strings = {"15-P", "04-N", "01-N", "03-P", "9-Z"})
    void detailedSchedulesTreatSectionSuffixAsOpaque(String section) throws Exception {
        assertNull(validator.validate(write(SchedulePdfFixtures.pdf(
                "Schedule\n2042-2043 1 term\nDay/Hour\nQRS 456 Experimental Design\n[" + section
                + "]\nJean-Luc O'Connor\n: Room Z 900"))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "QRS 456 [21-X] : Room Z 900", // compact: code/group/room only
            "QRS 456 [21-X] Jamie Quinn : Room Z 900", // no course name
            "QRS 456 Experimental Design [21-X] : Room Z 900", // no teacher
            "QRS 456 Experimental Design Jamie Quinn : Room Z 900", // no group
            "QRS 456 Experimental Design [21-X] : Central Building Room Z 900", // room is not a teacher
            "QRS 456 Experimental Design [21-X] : Room Z 900\nTUV 678 [22-X] Jamie Quinn : Room Z 901" // details cannot be borrowed from another block
    })
    void scheduleWithMissingDetailsHasDedicatedReason(String details) throws Exception {
        assertEquals(DETAILS_REQUIRED, validator.validate(write(SchedulePdfFixtures.pdf(
                "Schedule\n2042-2043 1 term\nDay/Hour\n" + details))));
    }

    @Test void compactRemainsIncompleteRegardlessOfFilename() throws Exception {
        Path path = directory.resolve("Schedule-detailed.pdf");
        Files.write(path, SchedulePdfFixtures.pdf("Schedule\n2042-2043 1 term\nDay/Hour\nTUV 678 [22-X] : Z 901"));
        assertEquals(DETAILS_REQUIRED, validator.validate(path));
    }

    @Test void truncatedPdfIsNotSilentlyRepaired() throws Exception {
        byte[] pdf = SchedulePdfFixtures.schedule();
        assertEquals(INVALID_PDF, validator.validate(write(Arrays.copyOf(pdf, pdf.length / 2))));
    }

    @Test void malformedPdfIsInvalid() throws Exception {
        assertEquals(INVALID_PDF, validator.validate(write("%PDF-1.7\nbroken structure\n%%EOF".getBytes(StandardCharsets.UTF_8))));
    }

    @Test void emptyFileHasSafeReason() throws Exception {
        assertEquals(EMPTY_PDF, validator.validate(write(new byte[0])));
    }

    @Test void zeroPagesHasSafeReason() throws Exception {
        Path path = directory.resolve("zero.pdf");
        try (var document = new PDDocument()) { document.save(path.toFile()); }
        assertEquals(EMPTY_PDF, validator.validate(path));
    }

    @Test void blankOrImageOnlyPageHasNoUsefulText() throws Exception {
        assertEquals(EMPTY_PDF, validator.validate(write(SchedulePdfFixtures.pdf("   "))));
    }

    @Test void encryptedFileIsUnreadable() throws Exception {
        Path path = directory.resolve("encrypted.pdf");
        try (var document = Loader.loadPDF(SchedulePdfFixtures.schedule())) {
            document.protect(new StandardProtectionPolicy("test-owner", "test-reader", new AccessPermission()));
            document.save(path.toFile());
        }
        assertEquals(UNREADABLE_PDF, validator.validate(path));
    }

    @Test void missingStoredFileIsSystemFailureNotFalseValidity() {
        assertEquals(VALIDATION_UNAVAILABLE, validator.validate(directory.resolve("missing.pdf")));
    }

    @Test void limitsExcessivePageCount() throws Exception {
        Path path = directory.resolve("many.pdf");
        try (var document = new PDDocument()) {
            for (int i = 0; i < 51; i++) document.addPage(new PDPage());
            document.save(path.toFile());
        }
        assertEquals(VALIDATION_LIMIT, validator.validate(path));
    }

    @Test void limitsExcessiveText() throws Exception {
        assertEquals(VALIDATION_LIMIT, validator.validate(write(SchedulePdfFixtures.pdf("a".repeat(200_001)))));
    }

    @Test void filenameDoesNotDetermineScheduleDecision() throws Exception {
        Path assignmentName = directory.resolve("Assignment.pdf");
        Files.write(assignmentName, SchedulePdfFixtures.schedule());
        assertNull(validator.validate(assignmentName));
        Files.write(assignmentName, SchedulePdfFixtures.pdf("Homework, not a timetable."));
        assertEquals(NOT_SCHEDULE, validator.validate(assignmentName));
    }

    private Path write(byte[] bytes) throws Exception {
        return Files.write(directory.resolve("input.pdf"), bytes);
    }
}
