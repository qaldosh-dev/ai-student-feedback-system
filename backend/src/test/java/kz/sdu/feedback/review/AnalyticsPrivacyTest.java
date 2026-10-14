package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.user.entity.*;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.sentiment.SentimentProvider;
import kz.sdu.feedback.review.topics.*;
import kz.sdu.feedback.review.summary.*;
import kz.sdu.feedback.review.service.AnalyticsPrivacyPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:analyticsprivacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AnalyticsPrivacyTest {
    static final String EMAIL = "owner@feedback.test", TERM = "2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TeacherRepository teachers;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired UserRepository users;
    @Autowired ReviewRepository reviews;
    @MockitoBean SentimentProvider sentiment;
    @MockitoBean TopicsProvider topics;
    @MockitoBean SummaryProvider summary;
    @MockitoBean ModerationProvider moderation;
    Course course, secondCourse;
    CourseOffering first, repeated, otherCourse, otherTeacher, otherTerm;
    int author;

    @BeforeEach void setup() {
        var teacher = teachers.saveAndFlush(new Teacher("Own Teacher", null));
        var foreign = teachers.saveAndFlush(new Teacher("Other Teacher", null));
        users.saveAndFlush(new User(EMAIL, "test-hash", Set.of(UserRole.TEACHER), true, teacher));
        course = courses.saveAndFlush(new Course("ABC 100", "Own subject"));
        secondCourse = courses.saveAndFlush(new Course("DEF 200", "Second subject"));
        first = offerings.saveAndFlush(new CourseOffering(teacher, course, TERM, "15-P", 100L));
        repeated = offerings.saveAndFlush(new CourseOffering(teacher, course, TERM, "04-N", 101L));
        otherCourse = offerings.saveAndFlush(new CourseOffering(teacher, secondCourse, TERM, null));
        otherTeacher = offerings.saveAndFlush(new CourseOffering(foreign, course, TERM, null));
        otherTerm = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        when(sentiment.classify(anyList())).thenAnswer(call -> Collections.nCopies(((List<?>)call.getArgument(0)).size(), SentimentProvider.Sentiment.POSITIVE));
        when(topics.analyze(anyList())).thenReturn(new TopicsResult(List.of("Clear explanations"), List.of()));
        when(summary.summarize(anyList())).thenReturn(new SummaryResult("Feedback highlights clarity. Examples are appreciated."));
    }

    void save(CourseOffering offering, String term, ReviewStatus status) {
        var student = users.saveAndFlush(new User("private-" + (++author) + "@feedback.test", "test-hash", Set.of(UserRole.STUDENT), true, null));
        var review = new Review(student, offering, term, 4, "Private feedback " + author);
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        reviews.saveAndFlush(review);
    }
    JsonNode request(String endpoint, Long courseId, String term) throws Exception {
        return json.readTree(mvc.perform(get("/api/teacher/analytics/" + endpoint).with(user(EMAIL).roles("TEACHER"))
                .param("courseId", courseId.toString()).param("term", term)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    void protectedScope(Long courseId, String term) throws Exception {
        var scope = request("scope", courseId, term);
        assertFalse(scope.get("privacyThresholdMet").asBoolean());
        assertEquals(Set.of("courses", "selectedCourseId", "selectedAcademicTerm", "hasPublishedFeedback", "privacyThresholdMet"), scope.propertyNames());
        for (String endpoint : List.of("sentiment", "topics", "summary")) {
            var result = request(endpoint, courseId, term);
            assertEquals("PRIVACY_PROTECTED", result.get("status").asText());
            assertFalse(result.get("privacyThresholdMet").asBoolean()); assertFalse(result.get("analysisAvailable").asBoolean());
            assertTrue(result.get("analyzedReviewCount").isNull());
            for (String field : List.of("positiveCount", "negativeCount", "neutralCount", "positivePercentage", "negativePercentage",
                    "neutralPercentage", "overallSentiment", "summary"))
                if (result.has(field)) assertTrue(result.get(field).isNull(), field);
            for (String field : List.of("topics", "concerns")) if (result.has(field)) assertTrue(result.get(field).isEmpty());
            for (String forbidden : List.of("actualPublishedReviewCount", "reviewId", "authorId", "email", "groupSection",
                    "moderation", "retryAfterSeconds", "text")) assertFalse(result.has(forbidden));
            assertFalse(result.toString().contains("Private feedback"));
        }
        verifyNoInteractions(sentiment, topics, summary);
    }

    @Test void sharedThresholdIsThree() {
        assertEquals(3, AnalyticsPrivacyPolicy.MIN_ANALYTICS_REVIEW_COUNT);
        for (int count = 0; count <= 4; count++) assertEquals(count >= 3, AnalyticsPrivacyPolicy.thresholdMet(count));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void boundaryEnforcedByScopeAndEveryDirectEndpoint(int count) throws Exception {
        for (int i = 0; i < count; i++) save(i % 2 == 0 ? first : repeated, TERM, ReviewStatus.PUBLISHED);
        if (count < 3) { protectedScope(course.getId(), TERM); return; }
        assertTrue(request("scope", course.getId(), TERM).get("privacyThresholdMet").asBoolean());
        for (String endpoint : List.of("sentiment", "topics", "summary")) {
            var result = request(endpoint, course.getId(), TERM);
            assertEquals("AVAILABLE", result.get("status").asText()); assertTrue(result.get("privacyThresholdMet").asBoolean());
            assertEquals(count, result.get("analyzedReviewCount").asInt());
        }
        verify(sentiment).classify(argThat(texts -> texts.size() == count));
        verify(topics).analyze(argThat(texts -> texts.size() == count));
        verify(summary).summarize(argThat(texts -> texts.size() == count));
        assertEquals(count, reviews.count());
    }

    @Test void privateAndOtherScopesCannotReachThresholdAndOfferingsDoNotDuplicateReviews() throws Exception {
        for (int i = 0; i < 2; i++) {
            save(i == 0 ? first : repeated, TERM, ReviewStatus.PUBLISHED);
            save(otherTerm, "Fall 2026", ReviewStatus.PUBLISHED);
            save(otherCourse, TERM, ReviewStatus.PUBLISHED);
            save(otherTeacher, TERM, ReviewStatus.PUBLISHED);
            save(first, TERM, ReviewStatus.SUBMITTED);
            save(repeated, TERM, ReviewStatus.NEEDS_CORRECTION);
        }
        protectedScope(course.getId(), TERM);
        protectedScope(course.getId(), "Fall 2026");
        protectedScope(secondCourse.getId(), TERM);
        save(repeated, TERM, ReviewStatus.PUBLISHED);
        assertTrue(request("scope", course.getId(), TERM).get("privacyThresholdMet").asBoolean());
        for (String endpoint : List.of("sentiment", "topics", "summary"))
            assertEquals(3, request(endpoint, course.getId(), TERM).get("analyzedReviewCount").asInt());
    }

    @Test void previouslyEligibleScopeIsRecheckedBeforeProvider() throws Exception {
        for (int i = 0; i < 3; i++) save(first, TERM, ReviewStatus.PUBLISHED);
        assertTrue(request("scope", course.getId(), TERM).get("privacyThresholdMet").asBoolean());
        // Isolated test fixture deletion simulates a changed published set after scope loading.
        reviews.delete(reviews.findAll().getFirst()); reviews.flush();
        protectedScope(course.getId(), TERM);
    }

    @AfterEach void noModeration() { verifyNoInteractions(moderation); }
}
