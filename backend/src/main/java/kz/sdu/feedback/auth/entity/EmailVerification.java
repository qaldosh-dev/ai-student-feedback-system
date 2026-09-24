package kz.sdu.feedback.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import kz.sdu.feedback.user.entity.User;

@Entity
@Table(name = "email_verifications", uniqueConstraints =
        @UniqueConstraint(name = "uk_email_verifications_user", columnNames = "user_id"))
public class EmailVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailVerification() { }
    public EmailVerification(User user) { this.user = user; }

    // Keep only the newest challenge for a user; replacing it invalidates the previous code.
    public void replace(String hash, Instant now) {
        codeHash = hash;
        createdAt = now;
        expiresAt = now.plusSeconds(600);
        attempts = 0;
        usedAt = null;
    }
    public void recordFailure() { attempts++; }
    public void markUsed(Instant now) { usedAt = now; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUsedAt() { return usedAt; }
    public int getAttempts() { return attempts; }
}
