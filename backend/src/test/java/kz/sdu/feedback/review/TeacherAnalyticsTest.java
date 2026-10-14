package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.TeacherAnalyticsService;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.user.entity.*;
import kz.sdu.feedback.user.repository.UserRepository;
import org.hibernate.SessionFactory;
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
    "spring.datasource.url=jdbc:h2:mem:teacher-analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Transactional
class TeacherAnalyticsTest {
    static final String EMAIL = "teacher-a@feedback.test", OTHER = "teacher-b@feedback.test";
    static final String API = "/api/teacher/analytics/scope", TERM = "2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TeacherRepository teachers;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired TeacherAnalyticsService service;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockitoBean ModerationProvider provider;
    Teacher teacher;
    Course course, second, foreignCourse;
    CourseOffering first, shell, noFeedback, foreign;
    int author;

    @BeforeEach void setup() {
        teacher = teachers.saveAndFlush(new Teacher("Alpha Teacher", null));
        var other = teachers.saveAndFlush(new Teacher("Beta Teacher", null));
        users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.TEACHER), true, teacher));
        users.saveAndFlush(new User(OTHER, "unused", Set.of(UserRole.TEACHER), true, other));
        course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        second = courses.saveAndFlush(new Course("DEF 200", "Second Subject"));
        foreignCourse = courses.saveAndFlush(new Course("XYZ 999", "Foreign Subject"));
        first = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "15-P", 101L));
        shell = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "04-N", 102L));
        noFeedback = offerings.saveAndFlush(new CourseOffering(teacher, second, "Spring 2025", null));
        foreign = offerings.saveAndFlush(new CourseOffering(other, course, "Foreign catalog term", null));
        offerings.saveAndFlush(new CourseOffering(other, foreignCourse, "Foreign-only term", null));
        save(first, TERM, ReviewStatus.PUBLISHED); save(shell, TERM, ReviewStatus.PUBLISHED);
        save(first, "2025-2026 2 term", ReviewStatus.PUBLISHED);
        save(first, " " + TERM + " ", ReviewStatus.PUBLISHED);
        save(foreign, "Foreign review term", ReviewStatus.PUBLISHED);
        save(first, "Hidden submitted term", ReviewStatus.SUBMITTED);
        save(noFeedback, "Hidden correction term", ReviewStatus.NEEDS_CORRECTION);
    }
    void save(CourseOffering offering, String term, ReviewStatus status) {
        var student = users.saveAndFlush(new User("private-" + (++author) + "@feedback.test", "private-hash", Set.of(UserRole.STUDENT), true, null));
        var review = new Review(student, offering, term, 4, "Private feedback text");
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        reviews.saveAndFlush(review);
    }
    String read(String email, Long courseId, String term) throws Exception {
        var request = get(API).with(user(email).roles("TEACHER"));
        if (courseId != null) request.param("courseId", courseId.toString());
        if (term != null) request.param("term", term);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    JsonNode read(Long courseId, String term) throws Exception { return json.readTree(read(EMAIL, courseId, term)); }

    @Test void ownCoursesAndExactTermsOnlyWithDeterministicDefaultsAndSafeDto() throws Exception {
        String body = read(EMAIL, null, null); var root = json.readTree(body);
        assertEquals(Set.of("courses", "selectedCourseId", "selectedAcademicTerm", "hasPublishedFeedback", "privacyThresholdMet"), root.propertyNames());
        assertEquals(2, root.get("courses").size());
        var firstCourse = root.get("courses").get(0);
        assertEquals(Set.of("courseId", "courseCode", "courseName", "academicTerms"), firstCourse.propertyNames());
        assertEquals(course.getId(), firstCourse.get("courseId").longValue());
        assertEquals(List.of("Fall 2026", TERM, "2025-2026 2 term", " " + TERM + " "),
                json.convertValue(firstCourse.get("academicTerms"), List.class));
        assertEquals(course.getId(), root.get("selectedCourseId").longValue());
        assertEquals("Fall 2026", root.get("selectedAcademicTerm").stringValue()); assertFalse(root.get("hasPublishedFeedback").booleanValue());
        for (String forbidden : List.of("Foreign", "Hidden", "private-", "Private feedback", "groupSection", "15-P", "04-N",
                "author", "studentId", "reviewId", "courseOfferingId", "moderation", "THREAT", "Gemini", "schedule", "rating", "Count"))
            assertFalse(body.contains(forbidden), forbidden);
        assertEquals(body, read(EMAIL, null, null)); verifyNoInteractions(provider);
    }
    @Test void multipleSourceOfferingsGiveBooleanAvailabilityWithoutMergingRecords() throws Exception {
        var root = read(course.getId(), TERM);
        assertTrue(root.get("hasPublishedFeedback").booleanValue());
        assertEquals(5, offerings.count()); assertEquals(7, reviews.count());
        assertFalse(root.toString().contains("Count"));
        assertFalse(read(second.getId(), "Spring 2025").get("hasPublishedFeedback").booleanValue());
    }
    @Test void termsRemainExactAndFollowSelectedCourse() throws Exception {
        assertEquals(" " + TERM + " ", read(course.getId(), " " + TERM + " ").get("selectedAcademicTerm").stringValue());
        var result = read(second.getId(), null);
        assertEquals("Spring 2025", result.get("selectedAcademicTerm").stringValue());
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", second.getId().toString()).param("term", TERM))
                .andExpect(status().isNotFound());
    }
    @Test void onlyPrivateReviewsDoNotChangeOptionsOrAvailability() throws Exception {
        var before = read(EMAIL, null, null);
        for (var status : List.of(ReviewStatus.SUBMITTED, ReviewStatus.NEEDS_CORRECTION)) {
            save(noFeedback, "Spring 2025", status); save(first, "Another private term", status);
            assertEquals(before, read(EMAIL, null, null));
            assertFalse(read(second.getId(), "Spring 2025").get("hasPublishedFeedback").booleanValue());
        }
    }
    @Test void teachersCannotUseEachOthersCourseOrTermsIncludingSharedCourses() throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", foreignCourse.getId().toString()))
                .andExpect(status().isNotFound()).andExpect(content().json("{\"message\":\"Analytics selection not found.\"}"));
        for (var term : List.of("Foreign catalog term", "Foreign review term", "Hidden submitted term", "invalid"))
            mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", course.getId().toString()).param("term", term))
                    .andExpect(status().isNotFound());
        var other = read(OTHER, course.getId(), "Foreign review term");
        assertTrue(json.readTree(other).get("hasPublishedFeedback").booleanValue()); assertFalse(other.contains(TERM));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "no", "999999999999999999999999", "999999", ""})
    void invalidCourseIsSafe404(String id) throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", id)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings = {"teacherId", "userId", "rating", "groupSection", "status"})
    void cannotInjectTeacherOrAdditionalFilters(String parameter) throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param(parameter, "123")).andExpect(status().isBadRequest());
    }
    @Test void ambiguousParametersAndTermWithoutCourseAreRejected() throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("courseId", "1", "2")).andExpect(status().isBadRequest());
        mvc.perform(get(API).with(user(EMAIL).roles("TEACHER")).param("term", TERM)).andExpect(status().isNotFound());
    }
    @Test void anonymous401AndMissingAccount401() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).with(user("missing@feedback.test").roles("TEACHER"))).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings = {"STUDENT", "ADMIN"})
    void otherRoles403(String role) throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles(role))).andExpect(status().isForbidden());
    }
    @Test void multiRoleTeacherAllowed() throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("STUDENT", "TEACHER"))).andExpect(status().isOk());
    }
    @Test void unlinkedTeacher403AndNoCoursesEmptyWithoutIdentityGuessing() throws Exception {
        users.saveAndFlush(new User("unlinked@feedback.test", "unused", Set.of(UserRole.TEACHER), true, null));
        mvc.perform(get(API).with(user("unlinked@feedback.test").roles("TEACHER"))).andExpect(status().isForbidden());
        var empty = teachers.saveAndFlush(new Teacher("Empty", null));
        users.saveAndFlush(new User("empty@feedback.test", "unused", Set.of(UserRole.TEACHER), true, empty));
        assertEquals("{\"courses\":[],\"selectedCourseId\":null,\"selectedAcademicTerm\":null,\"hasPublishedFeedback\":false,\"privacyThresholdMet\":false}", read("empty@feedback.test", null, null));
    }
    @Test void boundedQueriesDoNotLoadReviewsAndNeverCallAi() {
        em.flush(); em.clear(); var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        assertTrue(service.scope(EMAIL, course.getId(), TERM).hasPublishedFeedback());
        // Existing currentUser loads the account, its roles and linked teacher; analytics adds two projections.
        assertEquals(5, statistics.getPrepareStatementCount(), "Fixed account/options/terms queries, not per offering");
        assertEquals(0, statistics.getEntityStatistics(Review.class.getName()).getLoadCount());
        assertEquals(0, statistics.getEntityInsertCount()); assertEquals(0, statistics.getEntityUpdateCount());
        assertEquals(0, statistics.getEntityDeleteCount()); verifyNoInteractions(provider);
        for (int index = 0; index < 10; index++)
            offerings.save(new CourseOffering(em.getReference(Teacher.class, teacher.getId()),
                    em.getReference(Course.class, course.getId()), "Extra term " + index, null));
        em.flush(); em.clear(); statistics.clear();
        assertTrue(service.scope(EMAIL, course.getId(), TERM).hasPublishedFeedback());
        assertEquals(5, statistics.getPrepareStatementCount(), "More offerings must not add queries");
    }
}

