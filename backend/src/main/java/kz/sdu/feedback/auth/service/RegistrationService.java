package kz.sdu.feedback.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import kz.sdu.feedback.auth.entity.EmailVerification;
import kz.sdu.feedback.auth.repository.EmailVerificationRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    public static final String SENT_MESSAGE = "If this address is eligible, a verification code has been sent. "
            + "Check your inbox, or sign in if already verified. Resend is available after 60 seconds.";
    private final UserRepository users;
    private final EmailVerificationRepository challenges;
    private final RegistrationPolicy policy;
    private final PasswordEncoder encoder;
    private final VerificationMailSender mail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(UserRepository users, EmailVerificationRepository challenges, RegistrationPolicy policy,
                               PasswordEncoder encoder, VerificationMailSender mail, Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.policy = policy;
        this.encoder = encoder;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional
    public void register(String inputEmail, String password) {
        String email = policy.normalizeEmail(inputEmail);
        policy.validatePassword(password);
        // Existing accounts are never overwritten or sent another code via registration.
        if (users.findByEmailForUpdate(email).isPresent()) return;
        var user = users.saveAndFlush(new User(email, encoder.encode(password), Set.of()));
        issue(user, new EmailVerification(user));
    }

    @Transactional
    public void resend(String inputEmail) {
        String email = policy.normalizeEmail(inputEmail);
        var user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null || user.isEmailVerified()) return;
        var challenge = challenges.findByUserId(user.getId()).orElseGet(() -> new EmailVerification(user));
        if (challenge.getCreatedAt() != null && clock.instant().isBefore(challenge.getCreatedAt().plusSeconds(60))) return;
        issue(user, challenge);
    }

    private void issue(User user, EmailVerification challenge) {
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (challenge.getCodeHash() != null && encoder.matches(code, challenge.getCodeHash()));
        challenge.replace(encoder.encode(code), clock.instant());
        challenges.saveAndFlush(challenge);
        // A failed SMTP send rolls the transaction back, preserving the previous challenge on resend.
        mail.send(user.getEmail(), code);
    }

    public record VerificationResult(boolean verified) { }

    @Transactional
    public VerificationResult verify(String inputEmail, String code) {
        String email = policy.normalizeEmail(inputEmail);
        var user = users.findByEmailForUpdate(email).orElse(null);
        var invalid = new VerificationResult(false);
        if (user == null || user.isEmailVerified()) return invalid;
        var challenge = challenges.findByUserId(user.getId()).orElse(null);
        Instant now = clock.instant();
        if (challenge == null || challenge.getUsedAt() != null || !now.isBefore(challenge.getExpiresAt())
                || challenge.getAttempts() >= 5) return invalid;
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailure();
            // Return instead of throwing so the failed-attempt update is committed.
            return invalid;
        }
        user.verifyEmail();
        challenge.markUsed(now);
        return new VerificationResult(true);
    }
}
