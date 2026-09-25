package kz.sdu.feedback.schedule.service;

import static kz.sdu.feedback.schedule.entity.ScheduleUpload.ValidationReason.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;
import kz.sdu.feedback.schedule.entity.ScheduleUpload.ValidationReason;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

@Component
public class SchedulePdfValidator {
    private static final int MAX_PAGES = 50;
    private static final int MAX_TEXT_CHARACTERS = 200_000;
    private static final long MAX_VALIDATION_NANOS = 10_000_000_000L;
    private static final Pattern TITLE = Pattern.compile("\\bschedule\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE = Pattern.compile("\\bday\\s*/\\s*hour\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TERM = Pattern.compile(
            "\\b20\\d{2}\\s*[-–—/]\\s*20\\d{2}\\s+(?:\\d{1,2}\\s*term|term\\s*\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE_CODE = Pattern.compile("\\b[A-Z]{2,8}\\s+\\d{3,4}[A-Z]?\\b");
    private static final String NAME_WORD = "\\p{L}[\\p{L}\\p{M}'’.-]*";
    // Only presence is checked, within one detail block. No fields are captured or returned.
    // The bracketed section/group is opaque: its suffix does not imply a teaching type.
    private static final Pattern DETAILS = Pattern.compile(
            COURSE_CODE.pattern() + "[\\s\\uFFFD:–—-]*"
            + "\\p{L}[\\p{L}\\p{M}\\p{N}\\s'’.,&()/+–—-]{0,200}?\\s*"
            + "(?:\\[[^\\[\\]:]{1,60}\\]\\s*)?" // optional credits block
            + "\\[\\s*\\d{1,4}\\s*[-–—]\\s*[A-Za-z0-9]{1,4}\\s*\\]\\s*"
            + NAME_WORD + "(?:\\s+" + NAME_WORD + "){1,7}"
            + "(?:\\s*,\\s*" + NAME_WORD + "(?:\\s+" + NAME_WORD + "){1,7})*\\s*:");

    /** Returns only a safe reason (null means valid); no text or individual schedule values escape this method. */
    public ValidationReason validate(Path file) {
        return readValidated(file).reason();
    }

    /** Internal only: extraction uses the very same text that passed current validation. */
    public record ValidatedText(ValidationReason reason, String text) {
        @Override public String toString() { return "ValidatedText[reason=" + reason + "]"; }
    }

    private ValidatedText rejected(ValidationReason reason) { return new ValidatedText(reason, null); }

    public ValidatedText readValidated(Path file) {
        long deadline = System.nanoTime() + MAX_VALIDATION_NANOS;
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file)) return rejected(VALIDATION_UNAVAILABLE);
            long size = Files.size(file);
            if (size == 0) return rejected(EMPTY_PDF);
            if (size > 10 * 1024 * 1024) return rejected(VALIDATION_LIMIT);
            try (var source = new RandomAccessReadBufferedFile(file.toFile())) {
                // Disable PDFBox's repair mode: damaged structures must not be silently repaired and accepted.
                try (var document = new PDFParser(source).parse(false)) {
                    if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) return rejected(UNREADABLE_PDF);
                    int pages = document.getNumberOfPages();
                    if (pages == 0) return rejected(EMPTY_PDF);
                    if (pages > MAX_PAGES) return rejected(VALIDATION_LIMIT);
                    var stripper = new BoundedTextStripper(deadline);
                    var text = new BoundedTextWriter();
                    stripper.writeText(document, text);
                    String normalized = text.toString().replaceAll("[\\s\\p{Z}]+", " ").strip();
                    if (normalized.codePoints().noneMatch(Character::isLetterOrDigit)) return rejected(EMPTY_PDF);
                    if (!TITLE.matcher(normalized).find() || !TABLE.matcher(normalized).find()
                            || !TERM.matcher(normalized).find() || !COURSE_CODE.matcher(normalized).find()) {
                        return rejected(NOT_SCHEDULE);
                    }
                    return DETAILS.matcher(normalized).find()
                            ? new ValidatedText(null, text.toString()) : rejected(DETAILS_REQUIRED);
                }
            }
        } catch (InvalidPasswordException encrypted) {
            return rejected(UNREADABLE_PDF);
        } catch (ValidationLimitException limit) {
            return rejected(VALIDATION_LIMIT);
        } catch (java.nio.file.FileSystemException storageFailure) {
            return rejected(VALIDATION_UNAVAILABLE);
        } catch (IOException malformed) {
            // Never log parser exception messages: they may contain source text or internal paths.
            return rejected(INVALID_PDF);
        }
    }

    private static class ValidationLimitException extends RuntimeException { }

    private static class BoundedTextStripper extends PDFTextStripper {
        private final long deadline;
        private int characters;
        BoundedTextStripper(long deadline) { this.deadline = deadline; }

        @Override protected void startPage(PDPage page) throws IOException {
            if (System.nanoTime() > deadline) throw new ValidationLimitException();
            super.startPage(page);
        }

        @Override protected void processTextPosition(TextPosition position) {
            characters += position.getUnicode().length();
            if (characters > MAX_TEXT_CHARACTERS || System.nanoTime() > deadline) throw new ValidationLimitException();
            super.processTextPosition(position);
        }
    }

    private static class BoundedTextWriter extends StringWriter {
        @Override public void write(String text) { write(text, 0, text.length()); }
        @Override public void write(String text, int offset, int length) {
            check(length); super.write(text, offset, length);
        }
        @Override public void write(char[] text, int offset, int length) {
            check(length); super.write(text, offset, length);
        }
        @Override public void write(int character) { check(1); super.write(character); }
        private void check(int length) {
            if (getBuffer().length() + length > MAX_TEXT_CHARACTERS) throw new ValidationLimitException();
        }
    }
}
