package kz.sdu.feedback.auth.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.Map;
import kz.sdu.feedback.auth.service.RegistrationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    public record RegisterRequest(String email, String password) {
        @JsonAnySetter public void rejectExtra(String key, Object value) { throw new IllegalArgumentException("Unsupported field"); }
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }
    public record VerifyRequest(String email, String code) {
        @JsonAnySetter public void rejectExtra(String key, Object value) { throw new IllegalArgumentException("Unsupported field"); }
        @Override public String toString() { return "VerifyRequest[redacted]"; }
    }
    public record ResendRequest(String email) {
        @JsonAnySetter public void rejectExtra(String key, Object value) { throw new IllegalArgumentException("Unsupported field"); }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest input) {
        registration.register(input.email(), input.password());
        return accepted();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resend(@RequestBody ResendRequest input) {
        registration.resend(input.email());
        return accepted();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest input) {
        var result = registration.verify(input.email(), input.code());
        return result.verified() ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired verification code. Request a new code if needed."));
    }

    private ResponseEntity<Map<String, String>> accepted() {
        return ResponseEntity.accepted().body(Map.of("message", RegistrationService.SENT_MESSAGE));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> validation(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> concurrentRegistration() {
        return ResponseEntity.status(409).body(Map.of("message", "Registration could not be completed. Try signing in or continue email verification."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson() {
        // Parser exceptions may include input fragments containing a password or code.
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request fields or JSON."));
    }
}
