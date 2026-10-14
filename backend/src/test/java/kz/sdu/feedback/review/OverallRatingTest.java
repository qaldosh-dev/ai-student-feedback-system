package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.OverallRatingService;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.teacher.service.TeacherService;
import kz.sdu.feedback.teacher.dto.TeacherSort;
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

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:overall-rating;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
@Transactional
class OverallRatingTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired OverallRatingService stats;
    @Autowired TeacherService browse;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockitoBean ModerationProvider provider;
    Teacher teacher, otherTeacher, emptyTeacher;
    CourseOffering first, secondShell, otherCourse, foreign, empty;
    Review published;
    int nextAuthor;

    @BeforeEach void setup() {
        teacher = teachers.saveAndFlush(new Teacher("Alpha Teacher", null));
        otherTeacher = teachers.saveAndFlush(new Teacher("Beta Teacher", null));
        emptyTeacher = teachers.saveAndFlush(new Teacher("Empty Teacher", null));
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        var second = courses.saveAndFlush(new Course("DEF 200", "Second Subject"));
        first = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "15-P", 100L));
        secondShell = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", "04-N", 101L));
        otherCourse = offerings.saveAndFlush(new CourseOffering(teacher, second, "Spring 2025", null));
        foreign = offerings.saveAndFlush(new CourseOffering(otherTeacher, course, "Fall 2026", null));
        empty = offerings.saveAndFlush(new CourseOffering(emptyTeacher, course, "Fall 2026", null));
        published = save(first, "2026-2027 1 term", 5, ReviewStatus.PUBLISHED);
        save(first, "2026-2027 1 term", 5, ReviewStatus.PUBLISHED);
        save(first, "2025-2026 2 term", 4, ReviewStatus.PUBLISHED);
        save(otherCourse, "2025-2026 2 term", 3, ReviewStatus.PUBLISHED);
        save(otherCourse, "2025-2026 2 term", 2, ReviewStatus.PUBLISHED);
        save(secondShell, "2026-2027 1 term", 1, ReviewStatus.PUBLISHED);
        save(foreign, "2026-2027 1 term", 5, ReviewStatus.PUBLISHED);
        save(first, "Private term", 1, ReviewStatus.SUBMITTED);
        save(otherCourse, "Private term", 1, ReviewStatus.NEEDS_CORRECTION);
    }
    Review save(CourseOffering offering, String term, int rating, ReviewStatus status) {
        var author = users.saveAndFlush(new User("private-" + (++nextAuthor) + "@feedback.test", "private-hash",
                Set.of(UserRole.STUDENT), true, null));
        var review = new Review(author, offering, term, rating, "Private review text");
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        return reviews.saveAndFlush(review);
    }
    String url(Long id) { return "/api/teachers/" + id + "/ratings/overall"; }
    String read(Long id) throws Exception {
        return mvc.perform(get(url(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @Test void anonymousStatsAggregateReviewRowsAcrossAllCoursesShellsAndTermsWithAllBuckets() throws Exception {
        var root = json.readTree(read(teacher.getId()));
        assertEquals(teacher.getId(), root.get("teacherId").longValue());
        assertEquals("Alpha Teacher", root.get("teacherName").stringValue());
        assertEquals(20.0 / 6, root.get("averageRating").doubleValue(), 1e-12);
        assertEquals(6, root.get("publishedReviewCount").longValue());
        var distribution = root.get("ratingDistribution");
        assertEquals(Set.of("1", "2", "3", "4", "5"), distribution.propertyNames());
        long sum = 0;
        for (int rating = 1; rating <= 5; rating++) {
            long count = distribution.get(String.valueOf(rating)).longValue();
            assertEquals(rating == 5 ? 2 : 1, count); sum += count;
        }
        assertEquals(root.get("publishedReviewCount").longValue(), sum);
    }
    @Test void explicitDtoContainsNoRowLevelOrPrivateFields() throws Exception {
        var body = read(teacher.getId());
        assertEquals(Set.of("teacherId", "teacherName", "averageRating", "publishedReviewCount", "ratingDistribution"),
                json.readTree(body).propertyNames());
        for (var forbidden : List.of("reviewId", "text", "author", "email", "student", "groupSection", "15-P", "04-N",
                "academicTerm", "courseOfferingId", "Private", "private-hash", "moderation", "THREAT", "Gemini", "schedule"))
            assertFalse(body.contains(forbidden), forbidden);
        verifyNoInteractions(provider);
    }
    @Test void teachersAreIsolatedAndZeroBucketsAreIncluded() throws Exception {
        var result = json.readTree(read(otherTeacher.getId()));
        assertEquals(5, result.get("averageRating").doubleValue());
        assertEquals(1, result.get("publishedReviewCount").longValue());
        for (int i = 1; i <= 5; i++) assertEquals(i == 5 ? 1 : 0, result.get("ratingDistribution").get("" + i).longValue());
    }
    @Test void privateReviewsCannotChangeAnyStatsOrEmptyState() throws Exception {
        var before = read(teacher.getId()); var emptyBefore = read(emptyTeacher.getId());
        for (var status : List.of(ReviewStatus.SUBMITTED, ReviewStatus.NEEDS_CORRECTION)) {
            save(first, "Private", 1, status); save(empty, "Private", 5, status);
            assertEquals(before, read(teacher.getId())); assertEquals(emptyBefore, read(emptyTeacher.getId()));
        }
    }
    @Test void noPublishedReviewsMeanNullAverageNotZero() throws Exception {
        var root = json.readTree(read(emptyTeacher.getId()));
        assertTrue(root.get("averageRating").isNull()); assertEquals(0, root.get("publishedReviewCount").longValue());
        for (int i = 1; i <= 5; i++) assertEquals(0, root.get("ratingDistribution").get("" + i).longValue());
        var withoutOfferings = teachers.saveAndFlush(new Teacher("No offerings", null));
        assertNull(stats.overall(withoutOfferings.getId()).averageRating());
    }
    @Test void livePublicationChangesRecomputeWithoutStoredStatsOrAiCalls() throws Exception {
        published.submitPublishedUpdate(1, "Updated text"); reviews.saveAndFlush(published);
        var result = stats.overall(teacher.getId());
        assertEquals(3, result.averageRating()); assertEquals(5, result.publishedReviewCount());
        assertEquals(1L, result.ratingDistribution().get(5));
        published.completeModeration(new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of())); reviews.saveAndFlush(published);
        assertEquals(16.0 / 6, stats.overall(teacher.getId()).averageRating(), 1e-12);
        assertEquals(2L, stats.overall(teacher.getId()).ratingDistribution().get(1)); verifyNoInteractions(provider);
    }
    @Test void agreesWithUs12RatingAndCountSortsIncludingUnratedLast() {
        assertTrue(stats.overall(otherTeacher.getId()).averageRating() > stats.overall(teacher.getId()).averageRating());
        assertTrue(stats.overall(teacher.getId()).publishedReviewCount() > stats.overall(otherTeacher.getId()).publishedReviewCount());
        assertEquals(List.of(otherTeacher.getId(), teacher.getId(), emptyTeacher.getId()),
                browse.getTeachers("", "", TeacherSort.RATING_DESC).stream().map(t -> t.id()).toList());
        for (var sort : List.of(TeacherSort.RATING_ASC, TeacherSort.REVIEW_COUNT_DESC, TeacherSort.HAS_FEEDBACK_FIRST))
            assertEquals(List.of(teacher.getId(), otherTeacher.getId(), emptyTeacher.getId()),
                    browse.getTeachers("", "", sort).stream().map(t -> t.id()).toList());
        var internal = teachers.findByPublishedFeedbackOrder("", "ABC", "RATING_DESC");
        assertEquals(otherTeacher.getId(), internal.getFirst().getId());
    }
    @Test void fixedTwoQueriesAndNoReviewEntitiesLoadedOrWrites() {
        em.flush(); em.clear(); var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        assertEquals(6, stats.overall(teacher.getId()).publishedReviewCount());
        assertEquals(2, statistics.getPrepareStatementCount()); assertEquals(1, statistics.getEntityLoadCount());
        assertEquals(0, statistics.getEntityInsertCount()); assertEquals(0, statistics.getEntityUpdateCount());
        assertEquals(0, statistics.getEntityDeleteCount()); verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "99999999999999999999999", "9999999"})
    void missingOrInvalidTeacherIsSafe404(String id) throws Exception {
        mvc.perform(get("/api/teachers/" + id + "/ratings/overall")).andExpect(status().isNotFound())
                .andExpect(content().json("{\"message\":\"Teacher not found.\"}"));
    }
    @ParameterizedTest @ValueSource(strings = {"STUDENT", "TEACHER", "ADMIN"})
    void allRolesCanAccessPublicStats(String role) throws Exception {
        mvc.perform(get(url(teacher.getId())).with(user("any@feedback.test").roles(role))).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"userId", "courseId", "academicTerm", "rating", "status"})
    void filtersAndIdentityOverridesAreNotAccepted(String parameter) throws Exception {
        mvc.perform(get(url(teacher.getId())).param(parameter, "1")).andExpect(status().isBadRequest());
    }
    @Test void endpointHasNoWriteActions() throws Exception {
        mvc.perform(post(url(teacher.getId())).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(url(teacher.getId())).with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
