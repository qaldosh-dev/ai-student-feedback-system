package kz.sdu.feedback.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import kz.sdu.feedback.teacher.entity.Teacher;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_linked_teacher", columnNames = "linked_teacher_id")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @ElementCollection
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_roles", columnNames = {"user_id", "role"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<UserRole> roles = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_teacher_id")
    private Teacher linkedTeacher;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() { }

    public User(String email, String passwordHash, Set<UserRole> roles) {
        this(email, passwordHash, roles, false, null);
    }

    public User(String email, String passwordHash, Set<UserRole> roles,
                boolean emailVerified, Teacher linkedTeacher) {
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
        this.emailVerified = emailVerified;
        this.linkedTeacher = linkedTeacher;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public void verifyEmail() {
        if (emailVerified) throw new IllegalStateException("Email is already verified");
        emailVerified = true;
    }
    public Set<UserRole> getRoles() { return Set.copyOf(roles); }
    public void selectStudentRole() { roles.add(UserRole.STUDENT); }
    public void claimTeacher(Teacher teacher) {
        if (linkedTeacher != null && !linkedTeacher.getId().equals(teacher.getId())) {
            throw new IllegalStateException("Teacher profile cannot be changed");
        }
        linkedTeacher = teacher;
        roles.add(UserRole.TEACHER);
    }
    public Teacher getLinkedTeacher() { return linkedTeacher; }
    public Instant getCreatedAt() { return createdAt; }
}
