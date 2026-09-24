package kz.sdu.feedback.user.seed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Called only by the explicit local-development command, never on startup. */
@Service
public class DevelopmentPasswordSetupService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DevelopmentPasswordSetupService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void setup(String studentPassword, String teacherPassword, String adminPassword) {
        validatePassword(studentPassword, "DEV_STUDENT_PASSWORD");
        validatePassword(teacherPassword, "DEV_TEACHER_PASSWORD");
        validatePassword(adminPassword, "DEV_ADMIN_PASSWORD");
        var student = account("student@feedback.test", UserRole.STUDENT);
        var teacher = account("teacher@feedback.test", UserRole.TEACHER);
        var admin = account("admin@feedback.test", UserRole.ADMIN);
        var mock = teacher.getLinkedTeacher();
        if (mock == null || mock.getId() != 1L || mock.getMoodleTeacherId() != null
                || !"Aida Sarsenova".equals(mock.getName())
                || student.getLinkedTeacher() != null || admin.getLinkedTeacher() != null) {
            throw new IllegalStateException("Development account links differ from the approved mock setup; nothing changed");
        }
        // Validate every target before changing any hash; all three updates commit together.
        var accounts = List.of(student, teacher, admin);
        var passwords = List.of(studentPassword, teacherPassword, adminPassword);
        for (int i = 0; i < accounts.size(); i++) {
            accounts.get(i).changePasswordHash(encoder.encode(passwords.get(i)));
        }
        users.flush();
    }

    private User account(String email, UserRole role) {
        var user = users.findByEmail(email).orElseThrow(
                () -> new IllegalStateException("Missing development account: " + email));
        if (!user.isEmailVerified() || !user.getRoles().equals(Set.of(role))) {
            throw new IllegalStateException("Unexpected development account state: " + email);
        }
        return user;
    }

    private void validatePassword(String password, String variable) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Required local environment variable is missing: " + variable);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(variable + " exceeds BCrypt's 72-byte limit");
        }
    }
}
