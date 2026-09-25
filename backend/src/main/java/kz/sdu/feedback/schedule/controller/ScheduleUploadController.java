package kz.sdu.feedback.schedule.controller;

import java.util.Set;
import java.util.Map;
import kz.sdu.feedback.schedule.dto.ScheduleUploadResponse;
import kz.sdu.feedback.schedule.dto.ScheduleUploadView;
import kz.sdu.feedback.schedule.dto.ExtractedScheduleResponse;
import java.util.List;
import kz.sdu.feedback.schedule.service.ScheduleUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student/schedule-uploads")
public class ScheduleUploadController {
    private final ScheduleUploadService uploads;
    public ScheduleUploadController(ScheduleUploadService uploads) { this.uploads = uploads; }

    @GetMapping
    public List<ScheduleUploadView> list(Authentication authentication) {
        return uploads.list(authentication.getName());
    }

    @GetMapping("/{uploadId}/extracted")
    public ExtractedScheduleResponse extracted(@PathVariable Long uploadId, Authentication authentication) {
        return uploads.extracted(authentication.getName(), uploadId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScheduleUploadResponse> upload(MultipartHttpServletRequest request, Authentication authentication) {
        var files = request.getMultiFileMap();
        if (!request.getParameterMap().isEmpty() || !files.keySet().equals(Set.of("file"))
                || files.get("file").size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send exactly one file named 'file', without extra fields.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(uploads.upload(authentication.getName(), files.getFirst("file")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
