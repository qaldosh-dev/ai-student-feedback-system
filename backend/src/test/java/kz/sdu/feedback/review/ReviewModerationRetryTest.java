package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-retry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ReviewModerationRetryTest {
    static final String EMAIL = "owner@feedback.test", TERM = "2026-2027 1 term", TEXT = "Synthetic saved feedback";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
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
        reviewId = reviews.saveAndFlush(new Review(author, offering, TERM, 2, TEXT)).getId();
        createdAt = reviews.findById(reviewId).orElseThrow().getCreatedAt();
        jdbc.execute("ALTER TABLE reviews ADD CONSTRAINT IF NOT EXISTS uk_review_author_offering_term UNIQUE(author_id,course_offering_id,academic_term)");
    }
    @AfterEach void cleanup() {
        reviews.deleteAll(); offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
    }
    static ModerationResult accept() { return new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of()); }
    static ModerationResult flag() { return new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, Set.of(ModerationCategory.THREAT)); }
    String url() { return "/api/student/reviews/" + reviewId + "/moderation/retry"; }
    void ageAttempt() { jdbc.update("UPDATE reviews SET last_moderation_attempt_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(61)), reviewId); }
    void assertIdentity() {
        var row = reviews.findById(reviewId).orElseThrow();
        assertEquals(1, reviews.count()); assertEquals(reviewId, row.getId()); assertEquals(authorId, row.getAuthor().getId());
        assertEquals(offeringId, row.getCourseOffering().getId()); assertEquals(TERM, row.getAcademicTerm());
        assertEquals(TEXT, row.getText()); assertEquals(2, row.getRating()); assertEquals(createdAt, row.getCreatedAt());
    }
    @Test void oldNullTimestampImmediatelyRetryableAndAcceptUsesSameReview() throws Exception {
        assertNull(reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt());
        assertTrue(service.status(EMAIL, reviewId).retryModerationAvailable());
        when(provider.moderate(TEXT)).thenReturn(accept());
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(reviewId)).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.retryModerationAvailable").value(false)).andExpect(jsonPath("$.retryAfterSeconds").value(0));
        assertNotNull(reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt()); assertIdentity(); verify(provider).moderate(TEXT);
    }
    @Test void violationUsesExistingCorrectionOutcome() throws Exception {
        when(provider.moderate(TEXT)).thenReturn(flag());
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CORRECTION"))
                .andExpect(jsonPath("$.moderationReasons[0].category").value("THREAT"));
        assertIdentity();
    }
    @Test void failureRetainsTimestampAndCooldownAcrossStatusReadsAndDirectRequests() throws Exception {
        when(provider.moderate(TEXT)).thenThrow(new IllegalStateException("PRIVATE provider details"));
        var body = mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.moderatedAt").isEmpty())
                .andExpect(jsonPath("$.retryModerationAvailable").value(false)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("PRIVATE")); assertFalse(body.contains("lastModerationAttemptAt"));
        Instant attempt = reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt(); assertNotNull(attempt);
        for (int index = 0; index < 2; index++) {
            var statusResponse = service.status(EMAIL, reviewId);
            assertTrue(statusResponse.retryAfterSeconds() > 0 && statusResponse.retryAfterSeconds() <= 60);
            var blocked = mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isTooManyRequests())
                    .andReturn().getResponse();
            var parsed = json.readTree(blocked.getContentAsString());
            assertEquals(Set.of("message", "retryAfterSeconds"), parsed.propertyNames());
            assertEquals(parsed.get("retryAfterSeconds").asText(), blocked.getHeader("Retry-After"));
        }
        assertEquals(attempt, reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt());
        verify(provider, times(1)).moderate(TEXT); assertIdentity();
    }
    @Test void sixtySecondBoundaryUsesCeilingAndNoLifetimeLimit() {
        var entity = reviews.findById(reviewId).orElseThrow();
        var at = entity.beginModerationAttempt();
        assertEquals(60, entity.moderationRetryAfterSeconds(at));
        assertEquals(1, entity.moderationRetryAfterSeconds(at.plusSeconds(59).plusMillis(999)));
        assertEquals(0, entity.moderationRetryAfterSeconds(at.plusSeconds(60)));
        when(provider.moderate(TEXT)).thenThrow(new IllegalStateException("Unavailable"));
        for (int index = 0; index < 7; index++) {
            if (index > 0) ageAttempt();
            assertTrue(service.status(EMAIL, reviewId).retryModerationAvailable());
            service.retryModeration(EMAIL, reviewId);
        }
        verify(provider, times(7)).moderate(TEXT); assertIdentity();
    }
    @ParameterizedTest @EnumSource(value = ReviewStatus.class, names = {"PUBLISHED", "NEEDS_CORRECTION"})
    void finalStatusesConflict(ReviewStatus target) throws Exception {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var entity = reviews.findById(reviewId).orElseThrow(); entity.completeModeration(target == ReviewStatus.PUBLISHED ? accept() : flag());
            reviews.saveAndFlush(entity);
        });
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isConflict());
        assertFalse(service.status(EMAIL, reviewId).retryModerationAvailable()); verifyNoInteractions(provider); assertIdentity();
    }
    @Test void ownershipAndNonexistenceBoth404() throws Exception {
        for (String path : List.of(url(), "/api/student/reviews/999999/moderation/retry"))
            mvc.perform(post(path).with(user("other@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isNotFound());
        verifyNoInteractions(provider); assertIdentity();
    }
    @Test void anonymous401AndCsrfRequired() throws Exception {
        mvc.perform(post(url()).with(csrf().asHeader())).andExpect(status().isUnauthorized());
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT"))).andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN"})
    void nonStudent403(String role) throws Exception {
        mvc.perform(post(url()).with(user(EMAIL).roles(role)).with(csrf().asHeader())).andExpect(status().isForbidden()); verifyNoInteractions(provider);
    }
    @Test void multiRoleStudentAllowed() throws Exception {
        when(provider.moderate(TEXT)).thenReturn(accept());
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT", "TEACHER")).with(csrf().asHeader())).andExpect(status().isOk());
    }
    @Test void noClientFieldsAccepted() throws Exception {
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader()).param("userId", "1")).andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(user(EMAIL).roles("STUDENT")).with(csrf().asHeader()).contentType("application/json")
                .content("{\"text\":\"replace\",\"rating\":5,\"authorId\":1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(provider); assertIdentity();
    }
    @Test void concurrentTabsReserveOnceWithoutHoldingLockAcrossProviderCall() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(provider.moderate(TEXT)).thenAnswer(call -> { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return accept(); });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.retryModeration(EMAIL, reviewId));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                var second = pool.submit(() -> assertThrows(ModerationCooldownException.class, () -> service.retryModeration(EMAIL, reviewId)));
                assertTrue(second.get(3, TimeUnit.SECONDS).retryAfterSeconds() > 0);
                assertNotNull(reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt());
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        verify(provider, times(1)).moderate(TEXT); assertIdentity();
    }
    @Test void lateResponseCannotCompleteNewerAttempt() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var number = new java.util.concurrent.atomic.AtomicInteger();
        when(provider.moderate(TEXT)).thenAnswer(call -> {
            if (number.incrementAndGet() == 1) { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return accept(); }
            throw new IllegalStateException("Second attempt unavailable");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> service.retryModeration(EMAIL, reviewId));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                var firstAttempt = reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt();
                // Advance the retry clock instead of aging the row: two real calls can share
                // a Windows clock tick, which would not represent a genuinely later retry.
                var retryAt = firstAttempt.plusSeconds(61);
                try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
                    clock.when(Instant::now).thenReturn(retryAt);
                    service.retryModeration(EMAIL, reviewId);
                }
                assertEquals(retryAt, reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt());
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        assertEquals(ReviewStatus.SUBMITTED, service.status(EMAIL, reviewId).status()); assertIdentity();
    }
    @Test void rollbackNeverInvokesProviderOrRetainsReservation() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> { service.retryModeration(EMAIL, reviewId); tx.setRollbackOnly(); });
        assertNull(reviews.findById(reviewId).orElseThrow().getLastModerationAttemptAt()); verifyNoInteractions(provider);
    }
    @Test void uniquenessStillEnforced() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> reviews.saveAndFlush(new Review(
                users.findById(authorId).orElseThrow(), offerings.findById(offeringId).orElseThrow(), TERM, 5, "Duplicate")));
        assertIdentity();
    }
}
