package kz.sdu.feedback.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import kz.sdu.feedback.auth.dto.CurrentUser;
import kz.sdu.feedback.auth.service.AccountDetailsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AccountDetailsService accounts;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final HttpSessionSecurityContextRepository contexts;

    public AuthController(AuthenticationManager authenticationManager, AccountDetailsService accounts,
                          SessionAuthenticationStrategy sessionStrategy, HttpSessionSecurityContextRepository contexts) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.sessionStrategy = sessionStrategy;
        this.contexts = contexts;
    }

    public record LoginRequest(String email, String password) {
        @Override public String toString() { return "LoginRequest[credentials redacted]"; }
    }

    @PostMapping("/login")
    public CurrentUser login(@RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input.email() == null || input.password() == null || input.email().isBlank() || input.password().isBlank()
                || input.email().length() > 254 || input.password().length() > 1024) {
            throw new BadCredentialsException("Invalid email or password");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        var user = accounts.currentUser(authentication.getName());
        // Rotate the pre-login session ID and CSRF token, then explicitly persist authentication.
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return user;
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) { return accounts.currentUser(authentication.getName()); }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> invalidLogin() {
        return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
    }
}
