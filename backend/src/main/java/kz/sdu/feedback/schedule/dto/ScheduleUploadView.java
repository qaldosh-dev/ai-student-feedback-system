package kz.sdu.feedback.schedule.dto;

import java.time.Instant;
import java.util.List;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;

public record ScheduleUploadView(Long id, String originalFilename, Instant uploadedAt,
                                 ScheduleUpload.Status status, String validationMessage,
                                 List<String> academicTerms, int extractedCount) {
    public static ScheduleUploadView from(ScheduleUpload upload, List<ExtractedScheduleEntry> entries) {
        return new ScheduleUploadView(upload.getId(), upload.getOriginalFilename(), upload.getUploadedAt(),
                upload.getStatus(), upload.getValidationReason() == null ? null : upload.getValidationReason().message(),
                entries.stream().map(ExtractedScheduleEntry::getAcademicTerm).distinct().sorted().toList(), entries.size());
    }
}
