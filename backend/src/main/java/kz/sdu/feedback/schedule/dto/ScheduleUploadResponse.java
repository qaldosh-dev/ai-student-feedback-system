package kz.sdu.feedback.schedule.dto;

import java.time.Instant;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import kz.sdu.feedback.schedule.service.ScheduleParser;

public record ScheduleUploadResponse(Long id, String originalFilename, Instant uploadedAt,
                                     ScheduleUpload.Status status, String validationMessage, Instant validatedAt,
                                     ScheduleParser.Summary extraction) {
    public static ScheduleUploadResponse from(ScheduleUpload upload, ScheduleParser.Summary extraction) {
        return new ScheduleUploadResponse(upload.getId(), upload.getOriginalFilename(),
                upload.getUploadedAt(), upload.getStatus(),
                upload.getValidationReason() == null ? null : upload.getValidationReason().message(), upload.getValidatedAt(), extraction);
    }
}
