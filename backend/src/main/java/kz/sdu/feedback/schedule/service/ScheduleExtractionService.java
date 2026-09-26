package kz.sdu.feedback.schedule.service;

import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import kz.sdu.feedback.schedule.repository.ExtractedScheduleEntryRepository;
import kz.sdu.feedback.schedule.repository.ScheduleUploadRepository;
import kz.sdu.feedback.schedule.repository.VerifiedCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleExtractionService {
    private final ScheduleUploadRepository uploads;
    private final ExtractedScheduleEntryRepository entries;
    private final ScheduleFileStorage files;
    private final SchedulePdfValidator validator;
    private final ScheduleParser parser;
    private final VerifiedCourseRepository verified;

    public ScheduleExtractionService(ScheduleUploadRepository uploads, ExtractedScheduleEntryRepository entries,
                                     ScheduleFileStorage files, SchedulePdfValidator validator, ScheduleParser parser,
                                     VerifiedCourseRepository verified) {
        this.uploads = uploads; this.entries = entries; this.files = files; this.validator = validator; this.parser = parser;
        this.verified = verified;
    }

    /** Internal service step, not an endpoint. Always reload/lock and revalidate, including legacy VALID uploads. */
    @Transactional
    public ScheduleParser.Summary extract(Long uploadId) {
        var upload = uploads.findForExtraction(uploadId).orElseThrow(() -> new IllegalArgumentException("Upload not found."));
        if (upload.getStatus() != ScheduleUpload.Status.VALID) {
            throw new IllegalStateException("Only a valid schedule can be extracted.");
        }
        var checked = validator.readValidated(files.fileForValidation(upload.getStorageKey()));
        upload.finishValidation(checked.reason());
        verified.deleteForUpload(uploadId);
        if (checked.reason() != null) {
            entries.deleteByScheduleUploadId(uploadId);
            return null;
        }
        var result = parser.parse(checked.text());
        // Atomic replacement makes an internal repeat safe; the upload lock serializes concurrent repeats.
        entries.deleteByScheduleUploadId(uploadId);
        entries.saveAll(result.entries().stream().map(entry -> new ExtractedScheduleEntry(upload,
                entry.courseCode(), entry.courseName(), entry.teacherName(), entry.groupSection(), entry.academicTerm())).toList());
        return result.summary();
    }
}
