package kz.sdu.feedback.account.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import kz.sdu.feedback.account.service.AccountSetupService;
import kz.sdu.feedback.auth.dto.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account")
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class AccountSetupController {
    private final AccountSetupService setup;
    private final HttpSessionSecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public AccountSetupController(AccountSetupService setup, HttpSessionSecurityContextRepository contexts,
                                  SessionAuthenticationStrategy sessions) {
        this.setup = setup;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    public record StudentRequest() {
        @JsonAnySetter public void rejectExtra(String key, Object value) { throw new IllegalArgumentException("Unsupported field"); }
    }
    public record ClaimRequest(Long teacherId) {
        @JsonAnySetter public void rejectExtra(String key, Object value) { throw new IllegalArgumentException("Unsupported field"); }
    }

    @PostMapping("/roles/student")
    public CurrentUser student(@RequestBody(required = false) StudentRequest input, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        return refreshSession(setup.selectStudent(authentication.getName()), request, response);
    }

    @PostMapping("/teacher-claim")
    public CurrentUser teacher(@RequestBody ClaimRequest input, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        return refreshSession(setup.claimTeacher(authentication.getName(), input.teacherId()), request, response);
    }

    private CurrentUser refreshSession(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
        // The service transaction has committed. Replace this session's authority snapshot without re-login.
        var authorities = user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user.email(), null, authorities);
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return user;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflictingClaim() {
        return ResponseEntity.status(409).body(Map.of("message", "This teacher profile has already been claimed."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request fields or JSON."));
    }
}
