package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.ReviewService;
import kz.sdu.feedback.schedule.service.ReviewEligibilityService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ReviewStatusTest {
    static final String EMAIL = "owner@feedback.test";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewService service;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoSpyBean ReviewEligibilityService eligibility;
    Long reviewId;

    @BeforeEach void setup() {
        var owner = users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.STUDENT), true, null));
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        var teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
        var offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        reviewId = reviews.saveAndFlush(new Review(owner, offering, "2026-2027 1 term", 4, "Useful examples")).getId();
        em.clear();
        clearInvocations(eligibility);
    }
    String url() { return "/api/student/reviews/" + reviewId + "/status"; }

    @Test void ownerReadsFreshPersistedStatusWithoutIdentityOrMutationOrEligibility() throws Exception {
        var before = reviews.findById(reviewId).orElseThrow();
        var created = before.getCreatedAt();
        var text = before.getText();
        assertEquals(ReviewStatus.SUBMITTED, before.getStatus());
        em.clear();
        var response = mvc.perform(get(url()).with(user(EMAIL).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.courseCode").value("ABC 100"))
                .andExpect(jsonPath("$.teacherName").value("Alex Person"))
                .andExpect(jsonPath("$.academicTerm").value("2026-2027 1 term"))
                .andExpect(jsonPath("$.rating").value(4)).andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("reviewId", "status", "submittedAt", "courseOfferingId", "courseCode", "courseName", "teacherName", "academicTerm", "rating", "moderatedAt", "moderationReasons", "text"), json.readTree(response).propertyNames());
        assertFalse(response.contains(EMAIL)); assertFalse(response.contains("unused"));
        assertEquals(service.status(EMAIL, reviewId), service.status(EMAIL, reviewId));
        em.flush(); em.clear();
        var after = reviews.findById(reviewId).orElseThrow();
        assertEquals(created, after.getCreatedAt()); assertEquals(text, after.getText());
        assertEquals(ReviewStatus.SUBMITTED, after.getStatus()); assertEquals(1, reviews.count());
        assertEquals(1, teachers.count()); assertEquals(1, courses.count()); assertEquals(1, offerings.count());
        assertEquals(0L, em.createQuery("select count(v) from VerifiedCourse v", Long.class).getSingleResult());
        assertEquals(0L, em.createQuery("select count(p) from PendingCatalogVerification p", Long.class).getSingleResult());
        verifyNoInteractions(eligibility);
    }
    @Test void otherStudentAndMissingIdHaveIdenticalSafe404() throws Exception {
        var other = mvc.perform(get(url()).with(user("other@feedback.test").roles("STUDENT")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = mvc.perform(get("/api/student/reviews/999999/status").with(user(EMAIL).roles("STUDENT")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, other);
        assertEquals("Review not found.", json.readTree(other).get("message").asText());
    }
    @Test void unauthenticated401() throws Exception { mvc.perform(get(url())).andExpect(status().isUnauthorized()); }
    @ParameterizedTest @ValueSource(strings={"TEACHER","ADMIN"}) void wrongRole403(String role) throws Exception {
        mvc.perform(get(url()).with(user(EMAIL).roles(role))).andExpect(status().isForbidden());
    }
    @Test void studentTeacherCanReadOwnReview() throws Exception {
        mvc.perform(get(url()).with(user(EMAIL).roles("STUDENT","TEACHER"))).andExpect(status().isOk());
    }
    @Test void ownHistoryAvailable() throws Exception {
        mvc.perform(get("/api/student/reviews").with(user(EMAIL).roles("STUDENT"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewId").value(reviewId));
    }
}
