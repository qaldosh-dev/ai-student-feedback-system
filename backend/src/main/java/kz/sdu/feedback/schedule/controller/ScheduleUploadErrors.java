package kz.sdu.feedback.schedule.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

// Multipart parsing can fail before Spring selects a controller, so these handlers are global.
@RestControllerAdvice
public class ScheduleUploadErrors {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message", "PDF must not exceed 10 MB (request limit 11 MB)."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid upload request."));
    }
}
