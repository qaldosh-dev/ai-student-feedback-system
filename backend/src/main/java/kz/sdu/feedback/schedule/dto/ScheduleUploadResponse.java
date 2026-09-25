package kz.sdu.feedback.schedule.dto;

import java.time.Instant;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;

public record ScheduleUploadResponse(Long id, String originalFilename, Instant uploadedAt,
                                     ScheduleUpload.Status status, String validationMessage, Instant validatedAt) {
    public static ScheduleUploadResponse from(ScheduleUpload upload) {
        return new ScheduleUploadResponse(upload.getId(), upload.getOriginalFilename(),
                upload.getUploadedAt(), upload.getStatus(),
                upload.getValidationReason() == null ? null : upload.getValidationReason().message(), upload.getValidatedAt());
    }
}
