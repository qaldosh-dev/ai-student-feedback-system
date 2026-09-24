package kz.sdu.feedback.user.seed;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(1)
@ConditionalOnProperty(name = "app.dev-accounts.enabled", havingValue = "true")
public class DevelopmentAccountInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final TeacherRepository teachers;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public DevelopmentAccountInitializer(UserRepository users, TeacherRepository teachers,
                                         PasswordEncoder encoder, Environment environment) {
        this.users = users;
        this.teachers = teachers;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createIfMissing("student@feedback.test", UserRole.STUDENT, "DEV_STUDENT_PASSWORD");
        createIfMissing("teacher@feedback.test", UserRole.TEACHER, "DEV_TEACHER_PASSWORD");
        createIfMissing("admin@feedback.test", UserRole.ADMIN, "DEV_ADMIN_PASSWORD");
    }

    private void createIfMissing(String email, UserRole role, String passwordVariable) {
        if (users.findByEmail(email).isPresent()) {
            return; // Never reset an existing password, roles or teacher link.
        }
        Teacher linkedTeacher = role == UserRole.TEACHER ? mockTeacher() : null;
        String password = environment.getProperty(passwordVariable);
        if (password == null || password.isBlank()) {
            // Foundation-only accounts: unknown random secrets, never printed or saved.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            password = Base64.getEncoder().encodeToString(random);
        }
        users.save(new User(email, encoder.encode(password), Set.of(role), true, linkedTeacher));
    }

    private Teacher mockTeacher() {
        var matches = teachers.findByNameContainingIgnoreCase("Aida Sarsenova", Sort.unsorted()).stream()
                .filter(t -> t.getName().equals("Aida Sarsenova") && t.getMoodleTeacherId() == null
                        && "Computer Science".equals(t.getDepartment()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Development accounts require exactly one original Aida Sarsenova mock teacher");
        }
        return matches.getFirst();
    }
}
