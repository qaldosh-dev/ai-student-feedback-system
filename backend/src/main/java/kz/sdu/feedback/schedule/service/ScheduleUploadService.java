package kz.sdu.feedback.schedule.service;

import kz.sdu.feedback.schedule.dto.ScheduleUploadResponse;
import kz.sdu.feedback.schedule.dto.ScheduleUploadView;
import kz.sdu.feedback.schedule.dto.ExtractedScheduleResponse;
import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;
import kz.sdu.feedback.schedule.repository.ExtractedScheduleEntryRepository;
import kz.sdu.feedback.schedule.repository.VerifiedCourseRepository;
import kz.sdu.feedback.schedule.repository.PendingCatalogVerificationRepository;
import java.util.stream.Collectors;
import kz.sdu.feedback.user.entity.User;
import java.util.List;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import kz.sdu.feedback.schedule.repository.ScheduleUploadRepository;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleUploadService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScheduleUploadService.class);
    private final UserRepository users;
    private final ScheduleUploadRepository uploads;
    private final ScheduleFileStorage files;
    private final SchedulePdfValidator validator;
    private final ScheduleExtractionService extraction;
    private final ExtractedScheduleEntryRepository entries;
    private final VerifiedCourseRepository verified;
    private final PendingCatalogVerificationRepository pending;

    public ScheduleUploadService(UserRepository users, ScheduleUploadRepository uploads, ScheduleFileStorage files,
                                 SchedulePdfValidator validator, ScheduleExtractionService extraction,
                                 ExtractedScheduleEntryRepository entries, VerifiedCourseRepository verified,
                                 PendingCatalogVerificationRepository pending) {
        this.users = users;
        this.uploads = uploads;
        this.files = files;
        this.validator = validator;
        this.extraction = extraction;
        this.entries = entries;
        this.verified = verified;
        this.pending = pending;
    }

    @Transactional(readOnly = true)
    public List<ScheduleUploadView> list(String authenticatedEmail) {
        var user = requireStudent(authenticatedEmail);
        return uploads.findByUserIdOrderByUploadedAtDescIdDesc(user.getId()).stream()
                .map(upload -> ScheduleUploadView.from(upload, visibleEntries(upload))).toList();
    }

    @Transactional(readOnly = true)
    public ExtractedScheduleResponse extracted(String authenticatedEmail, Long uploadId) {
        var user = requireStudent(authenticatedEmail);
        var upload = uploads.findByIdAndUserId(uploadId, user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule upload not found."));
        var rows = visibleEntries(upload);
        if (rows.isEmpty()) return new ExtractedScheduleResponse(ScheduleUploadView.from(upload, rows), List.of());
        // Read persisted outcomes only, after ownership checks. Never call the matcher from this detail GET.
        var verifiedIds = verified.findBySourceEntryScheduleUploadId(uploadId).stream()
                .map(value -> value.getSourceEntry().getId()).collect(Collectors.toSet());
        var pendingReasons = pending.findBySourceEntryScheduleUploadId(uploadId).stream()
                .collect(Collectors.toMap(value -> value.getSourceEntry().getId(), value -> value.getReason()));
        return new ExtractedScheduleResponse(ScheduleUploadView.from(upload, rows),
                rows.stream().map(entry -> ExtractedScheduleResponse.Entry.from(entry,
                        verifiedIds.contains(entry.getId()), pendingReasons.get(entry.getId()))).toList());
    }

    private List<ExtractedScheduleEntry> visibleEntries(ScheduleUpload upload) {
        // Reads never revalidate/reprocess a file; invalid uploads must not reveal even stale rows.
        return upload.getStatus() == ScheduleUpload.Status.VALID ? entries.findForDisplay(upload.getId()) : List.of();
    }

    User requireStudent(String authenticatedEmail) {
        var user = users.findByEmail(authenticatedEmail).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required."));
        if (!user.isEmailVerified() || !user.getRoles().contains(UserRole.STUDENT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student access is required.");
        }
        return user;
    }

    @Transactional
    public ScheduleUploadResponse upload(String authenticatedEmail, MultipartFile file) {
        var user = requireStudent(authenticatedEmail);
        var stored = files.store(file);
        // Filesystem writes are not database transactions; compensate on any DB rollback, including commit failure.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) files.deleteAfterFailure(stored.key());
            }
        });
        var upload = uploads.saveAndFlush(new ScheduleUpload(user, stored.originalFilename(), stored.key()));
        upload.beginValidation();
        ScheduleUpload.ValidationReason reason;
        try {
            reason = validator.validate(files.fileForValidation(stored.key()));
        } catch (RuntimeException unexpected) {
            // Keep the saved record and file without claiming validation succeeded. No parser details are logged.
            log.error("Schedule validation could not be completed.");
            reason = ScheduleUpload.ValidationReason.VALIDATION_UNAVAILABLE;
        }
        upload.finishValidation(reason);
        var summary = upload.getStatus() == ScheduleUpload.Status.VALID ? extraction.extract(upload.getId()) : null;
        uploads.flush();
        return ScheduleUploadResponse.from(upload, summary);
    }
}
