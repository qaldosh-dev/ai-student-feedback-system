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
import kz.sdu.feedback.review.topics.TopicsProvider;
import kz.sdu.feedback.review.topics.TopicsResult;
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

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:topics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class TopicsAnalysisTest {
    static final String EMAIL = "own@feedback.test", TERM = "2026-2027 1 term", API = "/api/teacher/analytics/topics";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TeacherRepository teachers;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired UserRepository users;
    @Autowired ReviewRepository reviews;
    @MockitoBean TopicsProvider topics;
    @MockitoBean ModerationProvider moderation;
    Course own, foreignCourse;
    CourseOffering offering, duplicate;
    int author;

    @BeforeEach void setup() {
        var teacher = teachers.saveAndFlush(new Teacher("Own Teacher", null));
        var foreign = teachers.saveAndFlush(new Teacher("Foreign Teacher", null));
        users.saveAndFlush(new User(EMAIL, "private-hash", Set.of(UserRole.TEACHER), true, teacher));
        own = courses.saveAndFlush(new Course("ABC 100", "Own Subject"));
        foreignCourse = courses.saveAndFlush(new Course("XYZ 999", "Foreign Subject"));
        offering = offerings.saveAndFlush(new CourseOffering(teacher, own, "Fall 2026", "15-P", 1L));
        duplicate = offerings.saveAndFlush(new CourseOffering(teacher, own, "Fall 2026", "04-N", 2L));
        var other = offerings.saveAndFlush(new CourseOffering(foreign, own, "Fall 2026", null));
        offerings.saveAndFlush(new CourseOffering(foreign, foreignCourse, "Fall 2026", null));
        save(offering, TERM, "Clear explanations", ReviewStatus.PUBLISHED);
        save(duplicate, TERM, "Confusing lectures", ReviewStatus.PUBLISHED);
        save(offering, TERM, "Okay overall", ReviewStatus.PUBLISHED);
        save(other, TERM, "Foreign feedback", ReviewStatus.PUBLISHED);
        save(offering, " " + TERM + " ", "Different exact term", ReviewStatus.PUBLISHED);
        save(offering, TERM, "Secret submitted feedback", ReviewStatus.SUBMITTED);
        save(offering, TERM, "Secret flagged feedback", ReviewStatus.NEEDS_CORRECTION);
        when(topics.analyze(anyList())).thenReturn(new TopicsResult(List.of("Clear teaching", "Useful exercises"), List.of("Short deadlines")));
    }
    void save(CourseOffering target, String term, String text, ReviewStatus status) {
        var student = users.saveAndFlush(new User("private-" + (++author) + "@feedback.test", "private-hash", Set.of(UserRole.STUDENT), true, null));
        var review = new Review(student, target, term, 1, text);
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        reviews.saveAndFlush(review);
    }
    String request(Long course, String term) throws Exception {
        return mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", course.toString()).param("term", term))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @AfterEach void noModeration() { verifyNoInteractions(moderation); }

    @Test void onlyOwnedPublishedAnonymousTextsAcrossOfferingsAndSafeResponse() throws Exception {
        String body = request(own.getId(), TERM);
        verify(topics).analyze(List.of("Clear explanations", "Confusing lectures", "Okay overall"));
        var result = json.readTree(body);
        assertEquals(Set.of("courseId", "courseCode", "courseName", "academicTerm", "status", "analyzedReviewCount",
                "topics", "concerns", "analysisAvailable", "enoughRecurringFeedback", "privacyThresholdMet"), result.propertyNames());
        assertEquals("AVAILABLE", result.get("status").asText());
        assertTrue(result.get("analysisAvailable").asBoolean()); assertTrue(result.get("enoughRecurringFeedback").asBoolean());
        assertEquals(3, result.get("analyzedReviewCount").asInt());
        assertEquals(2, result.get("topics").size()); assertEquals("Short deadlines", result.at("/concerns/0").asText());
        for (var forbidden : List.of("private-", "hash", "15-P", "04-N", "Confusing lectures", "Clear explanations",
                "rating", "author", "reviewId", "moderation", "THREAT", "Foreign feedback", "Secret")) assertFalse(body.contains(forbidden));
        assertEquals(7, reviews.count()); assertEquals(4, offerings.count());
    }

    @Test void zeroOrPrivateOnlyNeverCallsProvider() throws Exception {
        save(offering, "Fall 2026", "Private", ReviewStatus.SUBMITTED);
        save(offering, "Fall 2026", "Private again", ReviewStatus.NEEDS_CORRECTION);
        var result = json.readTree(request(own.getId(), "Fall 2026"));
        assertEquals("PRIVACY_PROTECTED", result.get("status").asText()); assertTrue(result.get("analyzedReviewCount").isNull());
        assertFalse(result.get("analysisAvailable").asBoolean()); assertTrue(result.get("topics").isEmpty());
        verifyNoInteractions(topics);
    }

    @Test void oneReviewAndExactTermNeverInventCommonThemes() throws Exception {
        var result = json.readTree(request(own.getId(), " " + TERM + " "));
        assertEquals("PRIVACY_PROTECTED", result.get("status").asText());
        assertFalse(result.get("enoughRecurringFeedback").asBoolean()); assertTrue(result.get("concerns").isEmpty());
        verifyNoInteractions(topics);
    }

    @Test void emptyThemesAreValidButNotEnoughRecurringFeedback() throws Exception {
        when(topics.analyze(anyList())).thenReturn(new TopicsResult(List.of(), List.of()));
        var result = json.readTree(request(own.getId(), TERM));
        assertEquals("AVAILABLE", result.get("status").asText()); assertTrue(result.get("analysisAvailable").asBoolean());
        assertFalse(result.get("enoughRecurringFeedback").asBoolean()); assertEquals(3, result.get("analyzedReviewCount").asInt());
    }

    @Test void retryHasNoCachedOrFabricatedResultOnFailure() throws Exception {
        assertEquals("AVAILABLE", json.readTree(request(own.getId(), TERM)).get("status").asText());
        when(topics.analyze(anyList())).thenThrow(new IllegalStateException("SECRET_KEY Private feedback"));
        assertUnavailable(request(own.getId(), TERM));
        reset(topics);
        when(topics.analyze(anyList())).thenReturn(null);
        assertUnavailable(request(own.getId(), TERM));
        when(topics.analyze(anyList())).thenReturn(new TopicsResult(List.of(), List.of("Fast pacing")));
        assertEquals("Fast pacing", json.readTree(request(own.getId(), TERM)).at("/concerns/0").asText());
    }

    @Test void foreignInvalidMissingAndInjectedScopeRejectedBeforeProvider() throws Exception {
        for (var params : List.of(new String[]{foreignCourse.getId().toString(), TERM}, new String[]{"999999", TERM},
                new String[]{own.getId().toString(), "invalid"}, new String[]{own.getId().toString(), ""}))
            mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", params[0]).param("term", params[1]))
                    .andExpect(status().isNotFound());
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER"))).andExpect(status().isNotFound());
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", own.getId().toString()).param("term", TERM)
                .param("teacherId", "1")).andExpect(status().isBadRequest());
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", "1", "2")).andExpect(status().isBadRequest());
        verifyNoInteractions(topics);
    }

    @Test void anonymous401AndUnlinked403() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        users.saveAndFlush(new User("unlinked@feedback.test", "unused", Set.of(UserRole.TEACHER), true, null));
        mvc.perform(get(API).with(user("unlinked@feedback.test").roles("TEACHER")).param("courseId", own.getId().toString()).param("term", TERM))
                .andExpect(status().isForbidden());
        verifyNoInteractions(topics);
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "ADMIN"})
    void wrongRole403(String role) throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles(role)).param("courseId", own.getId().toString()).param("term", TERM))
                .andExpect(status().isForbidden());
        verifyNoInteractions(topics);
    }

    @Test void multiRoleAllowedAndScopeDoesNotAnalyze() throws Exception {
        mvc.perform(get("/api/teacher/analytics/scope").with(user(EMAIL).roles("STUDENT", "TEACHER"))).andExpect(status().isOk());
        verifyNoInteractions(topics);
        mvc.perform(get(API).with(user(EMAIL).roles("STUDENT", "TEACHER")).param("courseId", own.getId().toString()).param("term", TERM))
                .andExpect(status().isOk());
    }

    void assertUnavailable(String body) {
        var result = json.readTree(body);
        assertEquals("UNAVAILABLE", result.get("status").asText()); assertFalse(result.get("analysisAvailable").asBoolean());
        assertTrue(result.get("analyzedReviewCount").isNull()); assertTrue(result.get("topics").isEmpty());
        assertTrue(result.get("concerns").isEmpty()); assertFalse(result.get("enoughRecurringFeedback").asBoolean());
        assertFalse(body.contains("SECRET")); assertFalse(body.contains("Private feedback"));
    }
}



