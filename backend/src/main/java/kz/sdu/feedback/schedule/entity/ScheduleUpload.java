package kz.sdu.feedback.schedule.entity;

import jakarta.persistence.*;
import java.time.Instant;
import kz.sdu.feedback.user.entity.User;

@Entity
@Table(name = "schedule_uploads")
public class ScheduleUpload {
    public enum Status { UPLOADED, VALIDATING, VALID, INVALID }

    public enum ValidationReason {
        INVALID_PDF("This file is not a readable, structurally valid PDF."),
        UNREADABLE_PDF("This PDF cannot be read. Upload an unencrypted, text-based PDF."),
        EMPTY_PDF("This PDF has no readable text. Image scans are not supported."),
        NOT_SCHEDULE("This file does not appear to be an SDU Schedule PDF."),
        DETAILS_REQUIRED("This schedule version does not contain enough course and teacher details. Please download and upload the Detailed/Full Schedule version."),
        VALIDATION_LIMIT("This PDF exceeds the validation limits. Use a smaller schedule PDF."),
        VALIDATION_UNAVAILABLE("File saved, but validation could not be completed. Please try uploading again later.");

        private final String message;
        ValidationReason(String message) { this.message = message; }
        public String message() { return message; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, unique = true, length = 40)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    @Enumerated(EnumType.STRING) @Column(name = "validation_reason", length = 40)
    private ValidationReason validationReason;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected ScheduleUpload() { }

    public ScheduleUpload(User user, String originalFilename, String storageKey) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.uploadedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Status getStatus() { return status; }
    public ValidationReason getValidationReason() { return validationReason; }
    public Instant getValidatedAt() { return validatedAt; }

    public void beginValidation() { status = Status.VALIDATING; }

    public void finishValidation(ValidationReason reason) {
        validationReason = reason;
        if (reason == ValidationReason.VALIDATION_UNAVAILABLE) {
            status = Status.UPLOADED;
            validatedAt = null;
        } else {
            status = reason == null ? Status.VALID : Status.INVALID;
            validatedAt = Instant.now();
        }
    }
}
