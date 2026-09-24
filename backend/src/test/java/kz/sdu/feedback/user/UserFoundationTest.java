package kz.sdu.feedback.user;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.user.seed.DevelopmentAccountInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Disposable offline database; never connects to the user's PostgreSQL. */
class UserFoundationTest {
    private AnnotationConfigApplicationContext context;
    private UserRepository users;
    private TeacherRepository teachers;
    private PasswordEncoder encoder;
    private TransactionTemplate transaction;

    @BeforeEach void open() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        context.getEnvironment().setActiveProfiles("dev");
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("enable-dev-seed", Map.of("app.dev-accounts.enabled", "true")));
        context.register(TestConfig.class);
        context.refresh();
        users = context.getBean(UserRepository.class);
        teachers = context.getBean(TeacherRepository.class);
        encoder = context.getBean(PasswordEncoder.class);
        transaction = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
    }

    @AfterEach void close() { context.close(); }

    @Test void normalizesEmailAndDefaultsToUnverifiedWithNoTeacher() {
        var saved = users.saveAndFlush(account("  Student@Feedback.Test  ", Set.of(UserRole.STUDENT)));
        var loaded = users.findByEmail("student@feedback.test").orElseThrow();
        assertEquals(saved.getId(), loaded.getId());
        assertFalse(loaded.isEmailVerified());
        assertNull(loaded.getLinkedTeacher());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test void normalizedEmailIsUniqueInDatabase() {
        users.saveAndFlush(account("student@feedback.test", Set.of(UserRole.STUDENT)));
        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(account(" STUDENT@FEEDBACK.TEST ", Set.of(UserRole.ADMIN))));
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void persistsEachRole(UserRole role) {
        var saved = users.saveAndFlush(account("role@feedback.test", Set.of(role)));
        transaction.executeWithoutResult(status ->
                assertEquals(Set.of(role), users.findById(saved.getId()).orElseThrow().getRoles()));
    }

    @Test void persistsMultipleRoles() {
        var roles = Set.of(UserRole.STUDENT, UserRole.TEACHER);
        var saved = users.saveAndFlush(account("multi@feedback.test", roles));
        transaction.executeWithoutResult(status ->
                assertEquals(roles, users.findById(saved.getId()).orElseThrow().getRoles()));
    }

    @Test void teacherLinkIsUniqueButMultipleUnlinkedUsersAreAllowed() {
        var teacher = teachers.saveAndFlush(new Teacher("Mock teacher", null));
        users.saveAndFlush(new User("first@feedback.test", hash(), Set.of(UserRole.TEACHER), true, teacher));
        assertThrows(DataIntegrityViolationException.class, () -> users.saveAndFlush(
                new User("second@feedback.test", hash(), Set.of(UserRole.TEACHER), true, teacher)));
        users.saveAndFlush(account("student@feedback.test", Set.of(UserRole.STUDENT)));
        users.saveAndFlush(account("admin@feedback.test", Set.of(UserRole.ADMIN)));
        assertEquals(3, users.count());
    }

    @Test void storesBcryptHashAndCanVerifyPassword() {
        String secret = UUID.randomUUID().toString();
        var saved = users.saveAndFlush(new User("hash@feedback.test", encoder.encode(secret), Set.of(UserRole.STUDENT)));
        var stored = users.findById(saved.getId()).orElseThrow().getPasswordHash();
        assertFalse(secret.equals(stored));
        assertTrue(stored.startsWith("$2"));
        assertTrue(encoder.matches(secret, stored));
        assertFalse(encoder.matches(UUID.randomUUID().toString(), stored));
    }

    @Test void developmentSeedCreatesThreeAccountsAndRestartPreservesThem() {
        var mock = teachers.saveAndFlush(new Teacher("Aida Sarsenova", "Computer Science"));
        teachers.saveAndFlush(new Teacher("Aida Sarsenova", "Computer Science", 123L));
        String secret = UUID.randomUUID().toString();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("DEV_STUDENT_PASSWORD", secret)));
        var seed = context.getBean(DevelopmentAccountInitializer.class);
        seed.run();
        var first = users.findByEmail("student@feedback.test").orElseThrow();
        assertTrue(encoder.matches(secret, first.getPasswordHash()));
        seed.run();
        assertEquals(3, users.count());
        assertEquals(first.getPasswordHash(), users.findByEmail(first.getEmail()).orElseThrow().getPasswordHash());
        transaction.executeWithoutResult(status -> {
            for (var role : UserRole.values()) {
                var user = users.findByEmail(role.name().toLowerCase(java.util.Locale.ROOT) + "@feedback.test").orElseThrow();
                assertEquals(Set.of(role), user.getRoles());
                assertTrue(user.isEmailVerified());
                if (role == UserRole.TEACHER) {
                    assertEquals(mock.getId(), user.getLinkedTeacher().getId());
                    assertNull(user.getLinkedTeacher().getMoodleTeacherId());
                } else {
                    assertNull(user.getLinkedTeacher());
                }
            }
        });
        assertEquals(2, teachers.count());
    }

    @Test void seedDoesNotOverwriteExistingUser() {
        teachers.saveAndFlush(new Teacher("Aida Sarsenova", "Computer Science"));
        var original = users.saveAndFlush(account("student@feedback.test", Set.of(UserRole.ADMIN)));
        context.getBean(DevelopmentAccountInitializer.class).run();
        transaction.executeWithoutResult(status -> {
            var loaded = users.findByEmail(original.getEmail()).orElseThrow();
            assertEquals(original.getId(), loaded.getId());
            assertEquals(original.getPasswordHash(), loaded.getPasswordHash());
            assertEquals(Set.of(UserRole.ADMIN), loaded.getRoles());
            assertFalse(loaded.isEmailVerified());
        });
    }

    @Test void missingMockTeacherRollsBackSeedWithoutClaimingImportedTeacher() {
        teachers.saveAndFlush(new Teacher("Aida Sarsenova", "Computer Science", 123L));
        assertThrows(IllegalStateException.class, () -> context.getBean(DevelopmentAccountInitializer.class).run());
        assertEquals(0, users.count());
        assertEquals(1, teachers.count());
    }

    private User account(String email, Set<UserRole> roles) { return new User(email, hash(), roles); }
    private String hash() { return encoder.encode(UUID.randomUUID().toString()); }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackages = {"kz.sdu.feedback.user.repository", "kz.sdu.feedback.teacher.repository"})
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("kz.sdu.feedback.user.entity", "kz.sdu.feedback.teacher.entity",
                    "kz.sdu.feedback.course.entity", "kz.sdu.feedback.offering.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean DevelopmentAccountInitializer seed(UserRepository users, TeacherRepository teachers,
                                               PasswordEncoder encoder, Environment environment) {
            return new DevelopmentAccountInitializer(users, teachers, encoder, environment);
        }
    }
}
