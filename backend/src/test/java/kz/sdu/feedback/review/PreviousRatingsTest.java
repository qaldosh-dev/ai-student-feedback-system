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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:previous-ratings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class PreviousRatingsTest {
    static final String TERM = "2026-2027 1 term", OLDER = "2025-2026 2 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @MockitoBean ModerationProvider provider;
    Teacher teacher, otherTeacher;
    Course course, otherCourse, emptyCourse;
    CourseOffering first, secondShell, foreign, other, empty;
    Long published;
    int nextAuthor;

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
        empty = offerings.saveAndFlush(new CourseOffering(teacher, emptyCourse, "Fall 2026", null));
        published = save(first, TERM, 5, ReviewStatus.PUBLISHED);
        save(first, TERM, 4, ReviewStatus.PUBLISHED);
        save(first, TERM, 4, ReviewStatus.PUBLISHED);
        save(first, OLDER, 3, ReviewStatus.PUBLISHED);
        save(first, OLDER, 5, ReviewStatus.PUBLISHED);
        save(first, "Fall 2026", 4, ReviewStatus.PUBLISHED);
        save(secondShell, TERM, 1, ReviewStatus.PUBLISHED);
        save(foreign, TERM, 1, ReviewStatus.PUBLISHED);
        save(other, TERM, 5, ReviewStatus.PUBLISHED);
        save(first, TERM, 1, ReviewStatus.SUBMITTED);
        save(first, TERM, 2, ReviewStatus.NEEDS_CORRECTION);
        save(first, "Private-only term", 1, ReviewStatus.SUBMITTED);
    }
    Long save(CourseOffering offering, String term, int rating, ReviewStatus status) {
        // Independent students keep the fixtures consistent with US28 source uniqueness.
        var author = users.saveAndFlush(new User("private-student-" + (++nextAuthor) + "@feedback.test", "private-hash",
                Set.of(UserRole.STUDENT), true, null));
        var review = new Review(author, offering, term, rating, "Private review text");
        if (status != ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
                status == ReviewStatus.PUBLISHED ? ModerationResult.Decision.ACCEPT : ModerationResult.Decision.NEEDS_CORRECTION,
                status == ReviewStatus.PUBLISHED ? Set.of() : Set.of(ModerationCategory.THREAT)));
        return reviews.saveAndFlush(review).getId();
    }
    String url() { return url(teacher.getId(), course.getId()); }
    String url(Long teacherId, Long courseId) { return "/api/teachers/" + teacherId + "/courses/" + courseId + "/ratings/history"; }
    String read(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    JsonNode row(JsonNode root, CourseOffering offering, String term) {
        for (var row : root.get("ratings"))
            if (row.get("courseOfferingId").longValue() == offering.getId() && row.get("academicTerm").stringValue().equals(term)) return row;
        throw new AssertionError("Expected rating row absent");
    }

    @Test void anonymousHistoryHasCorrectPublishedAveragesCountsAndSafeFields() throws Exception {
        var body = read(url()); var root = json.readTree(body);
        assertEquals(Set.of("teacherId", "teacherName", "courseId", "courseCode", "courseName", "ratings"), root.propertyNames());
        assertEquals(teacher.getId(), root.get("teacherId").longValue());
        assertEquals("Teacher Alpha", root.get("teacherName").stringValue());
        assertEquals(course.getId(), root.get("courseId").longValue());
        assertEquals("ABC 100", root.get("courseCode").stringValue());
        assertEquals("Alpha Subject", root.get("courseName").stringValue());
        assertEquals(4, root.get("ratings").size());
        for (var value : root.get("ratings")) assertEquals(Set.of("courseOfferingId", "academicTerm", "averageRating", "publishedReviewCount"), value.propertyNames());
        assertEquals(13.0 / 3, row(root, first, TERM).get("averageRating").doubleValue(), 0.000000001);
        assertEquals(3, row(root, first, TERM).get("publishedReviewCount").longValue());
        assertEquals(4.0, row(root, first, OLDER).get("averageRating").doubleValue());
        assertEquals(2, row(root, first, OLDER).get("publishedReviewCount").longValue());
        for (String forbidden : new String[]{"Teacher Beta", "Other Subject", "Private-only", "Private review text", "private-student", "private-hash",
                "author", "reviewId", "email", "studentId", "groupSection", "15-P", "04-N", "01-N", "moderation", "THREAT",
                "Gemini", "overall", "distribution", "SUBMITTED", "NEEDS_CORRECTION"}) assertFalse(body.contains(forbidden), forbidden);
        assertEquals(12, reviews.count()); assertEquals(5, offerings.count()); verifyNoInteractions(provider);
    }
    @Test void ambiguousSameTeacherCourseOfferingsStaySeparateEvenInSameTerm() throws Exception {
        var root = json.readTree(read(url()));
        assertEquals(1, row(root, secondShell, TERM).get("averageRating").doubleValue());
        assertEquals(1, row(root, secondShell, TERM).get("publishedReviewCount").longValue());
        assertEquals(3, row(root, first, TERM).get("publishedReviewCount").longValue());
    }
    @Test void teachersAndCoursesAreIsolated() throws Exception {
        var beta = json.readTree(read(url(otherTeacher.getId(), course.getId())));
        assertEquals(1, beta.get("ratings").size()); assertEquals(1, beta.get("ratings").get(0).get("averageRating").doubleValue());
        assertEquals(foreign.getId(), beta.get("ratings").get(0).get("courseOfferingId").longValue());
        var otherResult = json.readTree(read(url(teacher.getId(), otherCourse.getId())));
        assertEquals(1, otherResult.get("ratings").size()); assertEquals(5, otherResult.get("ratings").get(0).get("averageRating").doubleValue());
    }
    @Test void noRatingsAndPrivateOnlyRatingsHaveIdenticalEmptyResponse() throws Exception {
        String path = url(teacher.getId(), emptyCourse.getId()); var before = read(path);
        save(empty, TERM, 1, ReviewStatus.SUBMITTED); save(empty, TERM, 2, ReviewStatus.NEEDS_CORRECTION);
        assertEquals(before, read(path)); assertEquals(0, json.readTree(before).get("ratings").size());
        assertFalse(before.contains("averageRating"));
    }
    @Test void deterministicTextOrderingDoesNotInferChronologyOrNormalizeTerms() throws Exception {
        var body = read(url()); var rows = json.readTree(body).get("ratings");
        assertEquals("Fall 2026", rows.get(0).get("academicTerm").stringValue());
        assertEquals(TERM, rows.get(1).get("academicTerm").stringValue());
        assertEquals(first.getId(), rows.get(1).get("courseOfferingId").longValue());
        assertEquals(secondShell.getId(), rows.get(2).get("courseOfferingId").longValue());
        assertEquals(OLDER, rows.get(3).get("academicTerm").stringValue());
        assertEquals(body, read(url()));
        save(first, " " + TERM + " ", 2, ReviewStatus.PUBLISHED);
        var root = json.readTree(read(url()));
        assertEquals(5, root.get("ratings").size());
        assertEquals(2, row(root, first, " " + TERM + " ").get("averageRating").doubleValue());
        assertEquals(3, row(root, first, TERM).get("publishedReviewCount").longValue());
    }
    @Test void currentPublicationStateRecomputesRatingsWithoutStoredHistoryOrAi() throws Exception {
        var review = reviews.findById(published).orElseThrow();
        review.submitPublishedUpdate(3, "Updated private text"); reviews.saveAndFlush(review);
        var pending = row(json.readTree(read(url())), first, TERM);
        assertEquals(4, pending.get("averageRating").doubleValue()); assertEquals(2, pending.get("publishedReviewCount").longValue());
        review.completeModeration(new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION, Set.of(ModerationCategory.THREAT))); reviews.saveAndFlush(review);
        assertEquals(pending, row(json.readTree(read(url())), first, TERM));
        review.submitCorrection("Accepted text");
        review.completeModeration(new ModerationResult(ModerationResult.Decision.ACCEPT, Set.of())); reviews.saveAndFlush(review);
        var accepted = row(json.readTree(read(url())), first, TERM);
        assertEquals(11.0 / 3, accepted.get("averageRating").doubleValue(), 0.000000001);
        assertEquals(3, accepted.get("publishedReviewCount").longValue()); verifyNoInteractions(provider);
    }
    @Test void missingOrUnconnectedIdentitiesReturnSafe404() throws Exception {
        for (var path : new String[]{url(999999L, course.getId()), url(teacher.getId(), 999999L), url(otherTeacher.getId(), otherCourse.getId())})
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(content().json("{\"message\":\"Rating history not found.\"}"));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "99999999999999999999999"})
    void invalidIdsAreSafe404(String id) throws Exception {
        mvc.perform(get("/api/teachers/" + id + "/courses/" + course.getId() + "/ratings/history")).andExpect(status().isNotFound());
        mvc.perform(get("/api/teachers/" + teacher.getId() + "/courses/" + id + "/ratings/history")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings = {"STUDENT", "TEACHER", "ADMIN"})
    void publicEndpointAllowsAllRoles(String role) throws Exception {
        mvc.perform(get(url()).with(user("any@feedback.test").roles(role))).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"status", "teacherId", "courseOfferingId", "academicTerm"})
    void extraParametersCannotOverrideHistoryScope(String parameter) throws Exception {
        mvc.perform(get(url()).param(parameter, "SUBMITTED")).andExpect(status().isBadRequest());
    }
    @Test void noWriteActionsExist() throws Exception {
        mvc.perform(post(url()).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(url()).with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
