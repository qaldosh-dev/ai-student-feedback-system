package kz.sdu.feedback.schedule.service;

import kz.sdu.feedback.schedule.dto.ScheduleUploadResponse;
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

    public ScheduleUploadService(UserRepository users, ScheduleUploadRepository uploads, ScheduleFileStorage files,
                                 SchedulePdfValidator validator) {
        this.users = users;
        this.uploads = uploads;
        this.files = files;
        this.validator = validator;
    }

    @Transactional
    public ScheduleUploadResponse upload(String authenticatedEmail, MultipartFile file) {
        var user = users.findByEmail(authenticatedEmail).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required."));
        if (!user.isEmailVerified() || !user.getRoles().contains(UserRole.STUDENT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student access is required.");
        }
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
        uploads.flush();
        return ScheduleUploadResponse.from(upload);
    }
}
