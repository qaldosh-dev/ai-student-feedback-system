package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.user.entity.*;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:public-feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class PublicFeedbackTest {
    static final String TERM = "2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ModerationProvider provider;
    Teacher teacher, otherTeacher;
    Course course, otherCourse, emptyCourse;
    CourseOffering first, secondShell, foreign, other;
    User student;
    Long published;

    @BeforeEach void setup() {
        teacher = teachers.saveAndFlush(new Teacher("Teacher Alpha", null));
        otherTeacher = teachers.saveAndFlush(new Teacher("Teacher Beta", null));
        course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        otherCourse = courses.saveAndFlush(new Course("DEF 200", "Other Subject"));
        emptyCourse = courses.saveAndFlush(new Course("EMPTY 101", "Empty Subject"));
        first = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "15-P", 101L));
        secondShell = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "04-N", 102L));
        foreign = offerings.saveAndFlush(new CourseOffering(otherTeacher, course, "Fall 2026", "01-N"));
        other = offerings.saveAndFlush(new CourseOffering(teacher, otherCourse, "Fall 2026", null));
        student = users.saveAndFlush(new User("private-student@feedback.test", "private-hash", Set.of(UserRole.STUDENT), true, null));
        published = save(first, TERM, "Own published", ReviewStatus.PUBLISHED);
        save(first, "2025-2026 1 term", "Earlier term", ReviewStatus.PUBLISHED);
        save(secondShell, TERM, "Separate shell", ReviewStatus.PUBLISHED);
        save(foreign, TERM, "Other teacher", ReviewStatus.PUBLISHED);
        save(other, TERM, "Other course", ReviewStatus.PUBLISHED);
        save(first, TERM, "Hidden submitted", ReviewStatus.SUBMITTED);
        save(first, TERM, "Hidden flagged", ReviewStatus.NEEDS_CORRECTION);
        save(first, "private-only term", "Hidden private term", ReviewStatus.SUBMITTED);
    }
    Long save(CourseOffering offering, String term, String text, ReviewStatus status) {
        var review = new Review(student, offering, term, 4, text);
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        return reviews.saveAndFlush(review).getId();
    }
    String url() { return url(teacher.getId(), course.getId()); }
    String url(Long teacherId, Long courseId) { return "/api/teachers/" + teacherId + "/courses/" + courseId + "/feedback"; }
    String read(CourseOffering offering, String term) throws Exception {
        return mvc.perform(get(url()).param("courseOfferingId", offering.getId().toString()).param("academicTerm", term))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @Test void anonymousExactTargetReturnsPublishedOnlyAndSafeFields() throws Exception {
        var body = read(first, TERM);
        var root = json.readTree(body);
        assertEquals(Set.of("teacherId", "teacherName", "courseId", "courseCode", "courseName", "courseOfferingId",
                "academicTerm", "availableTargets", "feedback"), root.propertyNames());
        assertEquals(teacher.getId(), root.get("teacherId").longValue());
        assertEquals("Teacher Alpha", root.get("teacherName").stringValue());
        assertEquals(course.getId(), root.get("courseId").longValue());
        assertEquals("ABC 100", root.get("courseCode").stringValue());
        assertEquals("Alpha Subject", root.get("courseName").stringValue());
        assertEquals(first.getId(), root.get("courseOfferingId").longValue());
        assertEquals(TERM, root.get("academicTerm").stringValue());
        assertEquals(1, root.get("feedback").size());
        var row = root.get("feedback").get(0);
        assertEquals(Set.of("rating", "text", "publishedAt"), row.propertyNames());
        assertEquals(4, row.get("rating").intValue());
        assertEquals("Own published", row.get("text").stringValue());
        var storedDate = jdbc.queryForObject("select moderated_at from reviews where id = ?",
                java.time.OffsetDateTime.class, published).toInstant();
        assertEquals(storedDate, java.time.Instant.parse(row.get("publishedAt").stringValue()));
        for (var target : root.get("availableTargets")) assertEquals(Set.of("courseOfferingId", "academicTerm"), target.propertyNames());
        for (String forbidden : new String[]{"Other teacher", "Other course", "Separate shell", "Earlier term", "Hidden", "private-only",
                "private-student", "private-hash", "author", "email", "studentId", "groupSection", "15-P", "04-N", "01-N",
                "moderation", "THREAT", "Gemini", "ratingCount", "average", "SUBMITTED", "NEEDS_CORRECTION"}) assertFalse(body.contains(forbidden), forbidden);
        assertEquals(8, reviews.count()); assertEquals(4, offerings.count()); verifyNoInteractions(provider);
    }
    @Test void sameTeacherCourseSeparateShellAndOtherTermNeverMerge() throws Exception {
        assertEquals("Separate shell", json.readTree(read(secondShell, TERM)).get("feedback").get(0).get("text").stringValue());
        var earlier = json.readTree(read(first, "2025-2026 1 term"));
        assertEquals(1, earlier.get("feedback").size());
        assertEquals("Earlier term", earlier.get("feedback").get(0).get("text").stringValue());
    }
    @Test void ambiguousTargetRequiresExplicitSelection() throws Exception {
        mvc.perform(get(url()).param("academicTerm", TERM)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseOfferingId").isEmpty()).andExpect(jsonPath("$.feedback.length()").value(0));
        mvc.perform(get(url())).andExpect(status().isOk()).andExpect(jsonPath("$.courseOfferingId").isEmpty())
                .andExpect(jsonPath("$.availableTargets.length()").value(5)).andExpect(jsonPath("$.feedback.length()").value(0));
    }
    @Test void uniquePublicTargetCanBeResolvedWithoutGuessing() throws Exception {
        mvc.perform(get(url()).param("academicTerm", "2025-2026 1 term")).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseOfferingId").value(first.getId()))
                .andExpect(jsonPath("$.feedback[0].text").value("Earlier term"));
    }
    @Test void catalogTermEmptyStateIsIdenticalWithPrivateReviewsOrNoReviews() throws Exception {
        var before = read(first, "Fall 2026");
        save(first, "Fall 2026", "Private in catalog term", ReviewStatus.NEEDS_CORRECTION);
        assertEquals(before, read(first, "Fall 2026"));
        assertEquals(0, json.readTree(before).get("feedback").size());
    }
    @Test void publicationOrderingUsesDateThenIdWithLegacyFallback() throws Exception {
        var newer = save(first, TERM, "Newer publication", ReviewStatus.PUBLISHED);
        jdbc.update("update reviews set moderated_at = null, created_at = ? where id = ?", java.time.OffsetDateTime.parse("2026-08-01T00:00:00Z"), published);
        jdbc.update("update reviews set moderated_at = ? where id = ?", java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"), newer);
        var rows = json.readTree(read(first, TERM)).get("feedback");
        assertEquals("Newer publication", rows.get(0).get("text").stringValue());
        assertEquals("2026-08-01T00:00:00Z", rows.get(1).get("publishedAt").stringValue());
        jdbc.update("update reviews set moderated_at = ? where id = ?", java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"), published);
        assertEquals("Newer publication", json.readTree(read(first, TERM)).get("feedback").get(0).get("text").stringValue());
    }
    @ParameterizedTest @ValueSource(strings = {"", "does-not-exist", "private-only term", " 2026-2027 1 term "})
    void unknownOrPrivateOnlyTermHasSafe404(String term) throws Exception {
        mvc.perform(get(url()).param("academicTerm", term).param("courseOfferingId", first.getId().toString()))
                .andExpect(status().isNotFound()).andExpect(content().json("{\"message\":\"Feedback selection not found.\"}"));
    }
    @Test void exactStoredWhitespaceIsNotRewritten() throws Exception {
        save(first, " Spring 2024 ", "Exact stored term", ReviewStatus.PUBLISHED);
        assertTrue(read(first, " Spring 2024 ").contains("Exact stored term"));
        mvc.perform(get(url()).param("academicTerm", "Spring 2024")).andExpect(status().isNotFound());
    }
    @Test void foreignOfferingCannotOverrideTeacherOrCourse() throws Exception {
        for (var offering : new CourseOffering[]{foreign, other}) mvc.perform(get(url()).param("academicTerm", TERM)
                .param("courseOfferingId", offering.getId().toString())).andExpect(status().isNotFound());
    }
    @Test void nonexistentTeacherCourseAndUnconnectedPairAreSafe404() throws Exception {
        for (String path : new String[]{url(999999L, course.getId()), url(teacher.getId(), 999999L),
                url(otherTeacher.getId(), otherCourse.getId()), url(teacher.getId(), emptyCourse.getId())})
            mvc.perform(get(path).param("academicTerm", TERM)).andExpect(status().isNotFound())
                    .andExpect(content().json("{\"message\":\"Feedback selection not found.\"}"));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "99999999999999999999999"})
    void invalidIdsAreSafe404(String id) throws Exception {
        mvc.perform(get("/api/teachers/" + id + "/courses/" + course.getId() + "/feedback")).andExpect(status().isNotFound());
        mvc.perform(get(url()).param("courseOfferingId", id).param("academicTerm", TERM)).andExpect(status().isNotFound());
    }
    @Test void offeringWithoutTermIsNotACompleteSelection() throws Exception {
        mvc.perform(get(url()).param("courseOfferingId", first.getId().toString())).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings = {"STUDENT", "TEACHER", "ADMIN"})
    void allRolesCanReadPublicFeedback(String role) throws Exception {
        mvc.perform(get(url()).param("academicTerm", TERM).param("courseOfferingId", first.getId().toString())
                .with(user("any@feedback.test").roles(role))).andExpect(status().isOk());
    }
    @Test void privateStatusCannotBeRequestedAndThereAreNoWriteActions() throws Exception {
        mvc.perform(get(url()).param("status", "SUBMITTED")).andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(url()).with(csrf())).andExpect(status().isMethodNotAllowed());
    }
    @Test void updatedReviewIsHiddenUntilPublishedAgain() throws Exception {
        var review = reviews.findById(published).orElseThrow();
        review.submitPublishedUpdate(2, "Updated text"); reviews.saveAndFlush(review);
        // The catalog term still gives a neutral empty result; no private term is advertised.
        assertFalse(mvc.perform(get(url())).andReturn().getResponse().getContentAsString().contains("Updated text"));
        mvc.perform(get(url()).param("academicTerm", TERM).param("courseOfferingId", first.getId().toString()))
                .andExpect(status().isNotFound());
        review.completeModeration(new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, Set.of(ModerationCategory.THREAT)));
        reviews.saveAndFlush(review);
        assertFalse(mvc.perform(get(url())).andReturn().getResponse().getContentAsString().contains("Updated text"));
        mvc.perform(get(url()).param("academicTerm", TERM).param("courseOfferingId", first.getId().toString()))
                .andExpect(status().isNotFound());
        review.submitCorrection("Corrected text");
        review.completeModeration(new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of())); reviews.saveAndFlush(review);
        assertTrue(read(first, TERM).contains("Corrected text")); verifyNoInteractions(provider);
    }
}
