package kz.sdu.feedback.moodle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.persistence.EntityManagerFactory;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;

/** Real JPA transactions on a fresh in-memory test DB; no local PostgreSQL or Moodle. */
class MoodleCatalogImportServiceTest {
    private AnnotationConfigApplicationContext context;
    private MoodleCatalogImportService service;
    private TeacherRepository teachers;
    private CourseRepository courses;
    private CourseOfferingRepository offerings;

    @BeforeEach void open() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(MoodleCatalogImportService.class);
        teachers = context.getBean(TeacherRepository.class);
        courses = context.getBean(CourseRepository.class);
        offerings = context.getBean(CourseOfferingRepository.class);
    }
    @AfterEach void close() { context.close(); }

    @Test void preflightIsReadOnlyAndImportReusesCourseButNeverMergesSeedTeacherByName() {
        var seedTeacher = teachers.saveAndFlush(new Teacher("Teacher A", "Seed department"));
        var seedCourse = courses.saveAndFlush(new Course("CSS 114", "PROGRAMMING"));
        var candidates = map(record(10, 1, "Teacher A", "CSS 114", "Programming"));
        var preview = service.preflight(candidates);
        assertEquals(1, preview.teachersCreated());
        assertEquals(1, preview.coursesReused());
        assertEquals(1, teachers.count());
        assertEquals(0, offerings.count());
        service.importApproved(candidates);
        assertEquals(2, teachers.count());
        assertEquals(1, courses.count());
        assertEquals(seedCourse.getId(), offerings.findAll().getFirst().getCourse().getId());
        assertNull(teachers.findById(seedTeacher.getId()).orElseThrow().getMoodleTeacherId());
        assertEquals("Seed department", teachers.findById(seedTeacher.getId()).orElseThrow().getDepartment());
        assertNull(offerings.findAll().getFirst().getTeacher().getDepartment());
        assertNull(offerings.findAll().getFirst().getSection());
    }

    @Test void preservesMultiTeacherCardsAndDifferentCardIdsAndSecondRunCreatesNothing() {
        var candidates = map(record(10, 1, "A", "CSS 114", "Programming"),
                record(10, 2, "B", "CSS 114", "Programming"), record(11, 1, "A", "CSS 114", "Programming"));
        var first = service.importApproved(candidates);
        assertEquals(2, first.teachersCreated());
        assertEquals(3, first.offeringsCreated());
        var second = service.importApproved(candidates);
        assertEquals(0, second.teachersCreated());
        assertEquals(0, second.coursesCreated());
        assertEquals(0, second.offeringsCreated());
        assertEquals(2, second.teachersReused());
        assertEquals(1, second.coursesReused());
        assertEquals(3, second.offeringsReused());
        assertEquals(3, offerings.count());
    }

    @Test void conflictingTeacherIdentityAbortsBeforeAnyWrites() {
        teachers.saveAndFlush(new Teacher("Different person", null, 1L));
        var candidates = map(record(10, 1, "A", "CSS 114", "Programming"), record(11, 2, "B", "CSS 212", "Databases"));
        assertFalse(service.preflight(candidates).conflicts().isEmpty());
        assertThrows(IllegalStateException.class, () -> service.importApproved(candidates));
        assertEquals(1, teachers.count());
        assertEquals(0, courses.count());
        assertEquals(0, offerings.count());
    }

    @Test void incompatibleExistingCourseIsNotOverwritten() {
        courses.saveAndFlush(new Course("CSS 114", "Different academic subject"));
        assertThrows(IllegalStateException.class, () -> service.importApproved(map(record(10, 1, "A", "CSS 114", "Programming"))));
        assertEquals("Different academic subject", courses.findAll().getFirst().getName());
        assertEquals(0, teachers.count());
        assertEquals(0, offerings.count());
    }

    @Test void sameOfferingSourceKeyPointingAtWrongCourseIsRejected() {
        var teacher = teachers.saveAndFlush(new Teacher("A", null, 1L));
        var wrong = courses.saveAndFlush(new Course("CSS 999", "Wrong"));
        offerings.saveAndFlush(new CourseOffering(teacher, wrong, "Fall 2026", null, 10L));
        assertThrows(IllegalStateException.class, () -> service.importApproved(map(record(10, 1, "A", "CSS 114", "Programming"))));
        assertEquals(1, courses.count());
        assertEquals("CSS 999", offerings.findAll().getFirst().getCourse().getCode());
    }

    @Test void knownUnresolvedCodeNeverProducesCoursesOrOfferings() {
        var candidates = map(record(10, 1, "A", "IBCTL 201", "IB PYP"),
                record(11, 2, "B", "IBCTL 201", "IB MYP"), record(12, 3, "C", "CSS 114", "Programming"));
        assertEquals(2, candidates.reviews().size());
        service.importApproved(candidates);
        assertEquals(1, courses.count());
        assertEquals(1, offerings.count());
        assertEquals("CSS 114", courses.findAll().getFirst().getCode());
    }

    @Test void unexpectedSourceConflictAbortsTheEntireBatch() {
        var candidates = map(record(10, 1, "A", "CSS 114", "Programming"), record(11, 2, "B", "CSS 114", "Physics"));
        assertThrows(IllegalStateException.class, () -> service.importApproved(candidates));
        assertEquals(0, teachers.count());
    }

    @Test void databaseFailureAfterAnEarlierInsertRollsBackEverythingAndIdentifiesSource() {
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        // Force a real database failure on the SECOND teacher in this disposable test DB.
        jdbc.execute("ALTER TABLE teachers ADD CONSTRAINT reject_test_teacher CHECK (name <> 'Fail Teacher')");
        var candidates = map(record(10, 1, "Good Teacher", "CSS 114", "Programming"),
                record(11, 2, "Fail Teacher", "CSS 114", "Programming"));
        assertTrue(service.preflight(candidates).conflicts().isEmpty());
        var failure = assertThrows(IllegalStateException.class, () -> service.importApproved(candidates));
        assertTrue(failure.getMessage().contains("teacher ID 2"));
        assertEquals(0, teachers.count());
        assertEquals(0, courses.count());
        assertEquals(0, offerings.count());
    }

    private MoodleImportMapper.Result map(MoodleCourseRecord... records) {
        return new MoodleImportMapper().map(List.of(records));
    }
    private MoodleCourseRecord record(long card, long teacher, String name, String code, String course) {
        return new MoodleCourseRecord(name, code, course, "Fall 2026", card, 0, code + " " + course, teacher);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "kz.sdu.feedback")
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("kz.sdu.feedback.teacher.entity", "kz.sdu.feedback.course.entity", "kz.sdu.feedback.offering.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean MoodleCatalogImportService importer(TeacherRepository t, CourseRepository c, CourseOfferingRepository o) {
            return new MoodleCatalogImportService(t, c, o);
        }
    }
}
