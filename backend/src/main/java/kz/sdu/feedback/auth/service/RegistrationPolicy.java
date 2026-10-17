package kz.sdu.feedback.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RegistrationPolicy {
    // ASCII dot-atom mailbox, then an exact allow-listed domain (no subdomains/suffix matching).
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(stu\\.sdu\\.edu\\.kz|sdu\\.edu\\.kz)");

    public String normalizeEmail(String input) {
        String email = input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches() || email.indexOf('@') > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a valid @stu.sdu.edu.kz or @sdu.edu.kz email address.");
        }
        return email;
    }

    public void validatePassword(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must not be blank and must contain at least 8 characters and at most 72 UTF-8 bytes.");
        }
    }
}
