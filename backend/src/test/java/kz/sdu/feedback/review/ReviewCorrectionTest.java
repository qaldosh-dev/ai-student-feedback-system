package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.CorrectReviewRequest;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.ReviewService;
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
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

// Real transactions and AFTER_COMMIT moderation, isolated H2 catalog, no Gemini calls.
@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-correction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ReviewCorrectionTest {
    static final String EMAIL = "owner@feedback.test", TERM = "2026-2027 1 term", ORIGINAL = "Synthetic flagged feedback";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired DataSource dataSource;
    @MockitoBean ModerationProvider provider;
    Long reviewId, authorId, offeringId;
    Instant createdAt;

    @BeforeEach void setup() {
        var author = users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.STUDENT), true, null));
        authorId = author.getId();
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        var teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
        var offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        offeringId = offering.getId();
        var review = new Review(author, offering, TERM, 2, ORIGINAL);
        review.completeModeration(flag(ModerationCategory.PERSONAL_INSULT, ModerationCategory.ABUSIVE_LANGUAGE));
        reviewId = reviews.saveAndFlush(review).getId();
        createdAt = reviews.findById(reviewId).orElseThrow().getCreatedAt();
    }
    @AfterEach void cleanup() {
        reviews.deleteAll(); offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
    }
    static ModerationResult accept() { return new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of()); }
    static ModerationResult flag(ModerationCategory... categories) {
        return new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, Set.of(categories));
    }
    String url() { return "/api/student/reviews/" + reviewId + "/correction"; }
    String statusUrl() { return "/api/student/reviews/" + reviewId + "/status"; }
    org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body));
    }
    void assertIdentityUnchanged() {
        var row = reviews.findById(reviewId).orElseThrow();
        assertEquals(1, reviews.count()); assertEquals(reviewId, row.getId());
        assertEquals(authorId, row.getAuthor().getId()); assertEquals(offeringId, row.getCourseOffering().getId());
        assertEquals(2, row.getRating()); assertEquals(TERM, row.getAcademicTerm()); assertEquals(createdAt, row.getCreatedAt());
        assertEquals(1, teachers.count()); assertEquals(1, courses.count()); assertEquals(1, offerings.count());
    }
    void assertUnchanged() {
        assertIdentityUnchanged();
        var status = service.status(EMAIL, reviewId);
        assertEquals(ReviewStatus.NEEDS_CORRECTION, status.status()); assertEquals(ORIGINAL, status.text());
        assertEquals(2, status.moderationReasons().size()); assertNotNull(status.moderatedAt());
        verifyNoInteractions(provider);
    }
    @Test void authorCorrectsSameReviewThroughHttpAndItPublishes() throws Exception {
        when(provider.moderate("Clear feedback.")).thenReturn(accept());
        mvc.perform(get(statusUrl()).with(user(EMAIL).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value(ORIGINAL))
                .andExpect(jsonPath("$.authorId").doesNotExist()).andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
        send("{\"text\":\"  Clear feedback.  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(reviewId)).andExpect(jsonPath("$.status").value("SUBMITTED"));
        var result = service.status(EMAIL, reviewId);
        assertEquals(ReviewStatus.PUBLISHED, result.status()); assertNotNull(result.moderatedAt());
        assertTrue(result.moderationReasons().isEmpty()); assertNull(result.text());
        assertEquals("Clear feedback.", reviews.findById(reviewId).orElseThrow().getText());
        assertIdentityUnchanged(); verify(provider).moderate("Clear feedback.");
    }
    @Test void secondModerationReplacesOldReasonsAndAllowsAnotherCorrection() {
        when(provider.moderate("Still an insult")).thenReturn(flag(ModerationCategory.PERSONAL_INSULT));
        service.correct(EMAIL, reviewId, new CorrectReviewRequest("Still an insult"));
        var result = service.status(EMAIL, reviewId);
        assertEquals(ReviewStatus.NEEDS_CORRECTION, result.status()); assertEquals("Still an insult", result.text());
        assertEquals(List.of(ModerationCategory.PERSONAL_INSULT), result.moderationReasons().stream().map(r -> r.category()).toList());
        when(provider.moderate("Now constructive")).thenReturn(accept());
        service.correct(EMAIL, reviewId, new CorrectReviewRequest("Now constructive"));
        assertEquals(ReviewStatus.PUBLISHED, service.status(EMAIL, reviewId).status()); assertIdentityUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "\n\t", "\u00a0"})
    void blankCorrectionRejected(String text) throws Exception {
        send(json.writeValueAsString(Map.of("text", text))).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @Test void maximumLengthBoundary() throws Exception {
        send(json.writeValueAsString(Map.of("text", "x".repeat(2001)))).andExpect(status().isBadRequest()); assertUnchanged();
        when(provider.moderate("x".repeat(2000))).thenReturn(accept());
        send(json.writeValueAsString(Map.of("text", "x".repeat(2000)))).andExpect(status().isOk());
        assertEquals(2000, reviews.findById(reviewId).orElseThrow().getText().length()); assertIdentityUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"{}", "null", "[]", "{\"text\":null}", "{\"text\":123}", "{\"text\":{}}"})
    void missingOrWrongTypeRejected(String body) throws Exception {
        send(body).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"userId", "authorId", "rating", "courseOfferingId", "academicTerm", "status", "moderationCategories", "createdAt", "groupSection"})
    void extraFieldsCannotChangeProtectedData(String field) throws Exception {
        send(json.writeValueAsString(Map.of("text", "Corrected", field, 99))).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @Test void anotherStudentAndMissingReviewHaveSame404ForReadAndWrite() throws Exception {
        var forbidden = mvc.perform(put(url()).with(user("other@feedback.test").roles("STUDENT")).with(csrf())
                .contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = mvc.perform(put("/api/student/reviews/999999/correction").with(user(EMAIL).roles("STUDENT")).with(csrf())
                .contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, forbidden);
        mvc.perform(get(statusUrl()).with(user("other@feedback.test").roles("STUDENT")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.text").doesNotExist());
        assertUnchanged();
    }
    @ParameterizedTest @EnumSource(value=ReviewStatus.class, names={"SUBMITTED", "PUBLISHED"})
    void nonFlaggedReviewCannotBeCorrected(ReviewStatus target) throws Exception {
        when(provider.moderate(anyString())).thenReturn(target == ReviewStatus.PUBLISHED ? accept() : null);
        service.correct(EMAIL, reviewId, new CorrectReviewRequest("Saved first correction"));
        clearInvocations(provider);
        send("{\"text\":\"Not allowed\"}").andExpect(status().isConflict());
        assertEquals(target, service.status(EMAIL, reviewId).status());
        assertEquals("Saved first correction", reviews.findById(reviewId).orElseThrow().getText());
        assertIdentityUnchanged(); verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings={"timeout", "network", "rate limit", "malformed JSON", "invalid result"})
    void failureKeepsCommittedCorrectedTextWithoutStaleReasons(String failure) {
        when(provider.moderate(anyString())).thenAnswer(call -> {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("select text, status, moderated_at, (select count(*) from review_moderation_categories) from reviews")) {
                assertTrue(result.next()); assertEquals("Corrected text", result.getString(1));
                assertEquals("SUBMITTED", result.getString(2)); assertNull(result.getObject(3)); assertEquals(0, result.getInt(4));
            }
            throw new IllegalStateException(failure);
        });
        service.correct(EMAIL, reviewId, new CorrectReviewRequest("Corrected text"));
        var result = service.status(EMAIL, reviewId);
        assertEquals(ReviewStatus.SUBMITTED, result.status()); assertNull(result.moderatedAt()); assertTrue(result.moderationReasons().isEmpty());
        assertEquals("Corrected text", reviews.findById(reviewId).orElseThrow().getText()); assertIdentityUnchanged();
    }
    @Test void simultaneousCorrectionCannotOverwriteTextAwaitingModeration() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(provider.moderate(anyString())).thenAnswer(call -> {
            started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return accept();
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.correct(EMAIL, reviewId, new CorrectReviewRequest("First correction")));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                var error = assertThrows(ResponseStatusException.class,
                        () -> service.correct(EMAIL, reviewId, new CorrectReviewRequest("Second correction")));
                assertEquals(409, error.getStatusCode().value());
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        assertEquals("First correction", reviews.findById(reviewId).orElseThrow().getText());
        assertEquals(ReviewStatus.PUBLISHED, service.status(EMAIL, reviewId).status());
        verify(provider, times(1)).moderate("First correction"); assertIdentityUnchanged();
    }
    @Test void csrfRequired() throws Exception {
        mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT")).contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isForbidden()); assertUnchanged();
    }
    @Test void anonymous401() throws Exception {
        mvc.perform(put(url()).with(csrf()).contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isUnauthorized()); assertUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER", "ADMIN"}) void wrongRole403(String role) throws Exception {
        mvc.perform(put(url()).with(user(EMAIL).roles(role)).with(csrf()).contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isForbidden()); assertUnchanged();
    }
    @Test void multiRoleStudentAllowed() throws Exception {
        when(provider.moderate(anyString())).thenReturn(accept());
        mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT", "TEACHER")).with(csrf()).contentType("application/json").content("{\"text\":\"Corrected\"}"))
                .andExpect(status().isOk()); assertIdentityUnchanged();
    }
}
