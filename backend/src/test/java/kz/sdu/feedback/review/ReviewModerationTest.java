package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import javax.sql.DataSource;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.SubmitReviewRequest;
import kz.sdu.feedback.review.entity.ReviewStatus;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.ReviewService;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.repository.*;
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
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// No surrounding test transaction: exercise the real submission commit and AFTER_COMMIT listener.
@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-moderation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ReviewModerationTest {
    static final String EMAIL = "owner@feedback.test", TERM = "2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ReviewService service;
    @MockitoBean ModerationProvider provider;
    @MockitoSpyBean ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ScheduleUploadRepository uploads;
    @Autowired ExtractedScheduleEntryRepository entries;
    @Autowired VerifiedCourseRepository verified;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    Long offeringId;

    @BeforeEach void setup() {
        var owner = users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.STUDENT), true, null));
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        var teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
        var offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        offeringId = offering.getId();
        var upload = new ScheduleUpload(owner, "synthetic.pdf", UUID.randomUUID() + ".pdf");
        upload.finishValidation(null); uploads.saveAndFlush(upload);
        var entry = entries.saveAndFlush(new ExtractedScheduleEntry(upload, "ABC 100", "Alpha Subject", "Alex Person", "01-N", TERM));
        verified.saveAndFlush(new VerifiedCourse(entry, offering));
    }
    @AfterEach void cleanup() {
        reset(reviews);
        reviews.deleteAll(); verified.deleteAll(); entries.deleteAll(); uploads.deleteAll();
        offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
    }
    Long submit(String text, int rating) {
        var receipt = service.submit(EMAIL, new SubmitReviewRequest(offeringId, TERM, rating, text));
        assertEquals(ReviewStatus.SUBMITTED, receipt.status()); // receipt acknowledges the initial persistence
        return receipt.reviewId();
    }
    static ModerationResult accept() { return new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of()); }

    @ParameterizedTest @ValueSource(strings={
        "The teacher explains the material poorly.", "The lectures were confusing.",
        "Assignment feedback was often late.", "The assignments were too difficult.",
        "I don't recommend this teacher.", "I learned very little from this course.",
        "Clear explanations and useful examples.", "We discussed the meaning of an offensive word in class."
    }) void acceptedPositiveOrNegativeFeedbackPublishes(String text) {
        when(provider.moderate(text)).thenReturn(accept());
        Long id = submit(text, 1);
        var status = service.status(EMAIL, id);
        assertEquals(ReviewStatus.PUBLISHED, status.status());
        assertTrue(status.moderationReasons().isEmpty()); assertNotNull(status.moderatedAt());
        assertEquals(text, reviews.findById(id).orElseThrow().getText());
        verify(provider).moderate(text);
    }
    @ParameterizedTest @EnumSource(ModerationCategory.class)
    void eachViolationPersistsSafeReason(ModerationCategory category) throws Exception {
        when(provider.moderate(anyString())).thenReturn(new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, Set.of(category)));
        Long id = submit("Synthetic policy violation", 5);
        var status = service.status(EMAIL, id);
        assertEquals(ReviewStatus.NEEDS_CORRECTION, status.status()); assertNotNull(status.moderatedAt());
        assertEquals(category.message(), status.moderationReasons().getFirst().message());
        mvc.perform(get("/api/student/reviews/" + id + "/status").with(user(EMAIL).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_CORRECTION"))
                .andExpect(jsonPath("$.moderationReasons[0].category").value(category.name()))
                .andExpect(jsonPath("$.author").doesNotExist()).andExpect(jsonPath("$.authorId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.text").value("Synthetic policy violation"));
        mvc.perform(get("/api/student/reviews/" + id + "/status").with(user("other@feedback.test").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }
    @Test void multipleViolations() {
        var categories = Set.of(ModerationCategory.THREAT, ModerationCategory.PERSONAL_INFORMATION);
        when(provider.moderate(anyString())).thenReturn(new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, categories));
        var result = service.status(EMAIL, submit("Synthetic multiple violations", 4));
        assertEquals(categories, new HashSet<>(result.moderationReasons().stream().map(r -> r.category()).toList()));
    }
    @ParameterizedTest @ValueSource(ints={1,5}) void fullTextOnlyAndRatingIndependent(int rating) {
        String text = "A".repeat(2000);
        when(provider.moderate(text)).thenReturn(accept());
        var result = service.status(EMAIL, submit(text, rating));
        assertEquals(ReviewStatus.PUBLISHED, result.status()); assertEquals(rating, result.rating());
        verify(provider).moderate(text);
    }
    @Test void providerSeesAlreadyCommittedSubmittedReview() {
        when(provider.moderate(anyString())).thenAnswer(call -> {
            // A separate physical connection cannot see an uncommitted insert.
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("select status, moderated_at from reviews")) {
                assertTrue(result.next()); assertEquals("SUBMITTED", result.getString(1));
                assertNull(result.getObject(2)); assertFalse(result.next());
            }
            return accept();
        });
        assertEquals(ReviewStatus.PUBLISHED, service.status(EMAIL, submit("Useful examples", 4)).status());
    }
    @ParameterizedTest @ValueSource(strings={"timeout", "network", "HTTP 429", "HTTP 500", "malformed JSON", "unknown decision", "unknown category"})
    void providerFailuresKeepCommittedReview(String failure) {
        when(provider.moderate(anyString())).thenThrow(new IllegalStateException(failure));
        var id = submit("Useful feedback", 4);
        var status = service.status(EMAIL, id);
        assertEquals(ReviewStatus.SUBMITTED, status.status()); assertNull(status.moderatedAt());
        assertTrue(status.moderationReasons().isEmpty()); assertEquals(1, reviews.count());
        verify(provider, times(1)).moderate("Useful feedback");
    }
    @Test void missingProviderResultNeverPublishes() {
        when(provider.moderate(anyString())).thenReturn(null);
        assertEquals(ReviewStatus.SUBMITTED, service.status(EMAIL, submit("Useful feedback", 4)).status());
    }
    @Test void resultPersistenceFailureDoesNotLoseSubmission() {
        when(provider.moderate(anyString())).thenReturn(accept());
        doThrow(new IllegalStateException("synthetic database failure")).when(reviews)
                .saveAndFlush(argThat(review -> review.getStatus() != ReviewStatus.SUBMITTED));
        var result = service.status(EMAIL, submit("Useful feedback", 4));
        assertEquals(ReviewStatus.SUBMITTED, result.status()); assertNull(result.moderatedAt());
        assertTrue(result.moderationReasons().isEmpty()); assertEquals(1, reviews.count());
    }
    @Test void rolledBackSubmissionNeverCallsProvider() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(state -> { submit("Useful feedback", 4); state.setRollbackOnly(); });
        assertEquals(0, reviews.count()); verifyNoInteractions(provider);
    }
    @Test void providerFailureStillReturns201ThroughHttp() throws Exception {
        when(provider.moderate(anyString())).thenThrow(new IllegalStateException("secret provider detail"));
        mvc.perform(post("/api/student/reviews").with(user(EMAIL).roles("STUDENT")).with(csrf())
                .contentType("application/json").content("""
                {"courseOfferingId":%d,"academicTerm":"%s","rating":4,"text":"Useful feedback"}
                """.formatted(offeringId, TERM)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        assertEquals(1, reviews.count());
    }
    @Test void invalidAndIneligibleSubmissionsNeverCallProvider() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.submit(EMAIL, new SubmitReviewRequest(offeringId, TERM, 6, "Useful feedback")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.submit(EMAIL, new SubmitReviewRequest(offeringId, "2025-2026 1 term", 4, "Useful feedback")));
        assertEquals(0, reviews.count()); verifyNoInteractions(provider);
    }
}
