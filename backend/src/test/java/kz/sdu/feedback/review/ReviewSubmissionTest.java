package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.SubmitReviewRequest;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-submission;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ReviewSubmissionTest {
    static final String EMAIL = "student@feedback.test", TERM = "2026-2027 1 term", API = "/api/student/reviews";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ScheduleUploadRepository uploads;
    @Autowired ExtractedScheduleEntryRepository entries;
    @Autowired VerifiedCourseRepository verified;
    @Autowired PendingCatalogVerificationRepository pending;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @MockitoSpyBean ReviewRepository reviews;
    @Autowired ReviewService service;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User author;
    CourseOffering offering;
    ExtractedScheduleEntry entry;

    @BeforeEach void setup() {
        author = users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        users.saveAndFlush(new User("other@feedback.test", "unused", Set.of(UserRole.STUDENT), true, null));
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        var teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
        offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        var upload = new ScheduleUpload(author, "schedule.pdf", UUID.randomUUID()+".pdf");
        upload.finishValidation(null); uploads.saveAndFlush(upload);
        entry = entries.saveAndFlush(new ExtractedScheduleEntry(upload, "ABC 100", "Alpha Subject", "Alex Person", "01-N", TERM));
        verified.saveAndFlush(new VerifiedCourse(entry, offering));
    }
    Map<String, Object> body() { return new HashMap<>(Map.of("courseOfferingId", offering.getId(), "academicTerm", TERM, "rating", 4, "text", "  Clear explanations.\nUseful examples!  ")); }
    org.springframework.test.web.servlet.ResultActions send(Map<String, Object> input) throws Exception {
        return mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    @Test void persistsExactTargetAndSessionAuthorWithAnonymousResponse() throws Exception {
        var response = send(body()).andExpect(status().isCreated()).andExpect(jsonPath("$.submitted").value(true)).andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("reviewId", "submitted", "courseOfferingId", "academicTerm", "rating", "status"), json.readTree(response).propertyNames());
        assertEquals("SUBMITTED", json.readTree(response).get("status").asText());
        assertFalse(response.contains(EMAIL)); assertFalse(response.contains("unused"));
        var review = reviews.findAll().getFirst();
        assertEquals(kz.sdu.feedback.review.entity.ReviewStatus.SUBMITTED, review.getStatus());
        assertEquals(author.getId(), review.getAuthor().getId()); assertEquals(offering.getId(), review.getCourseOffering().getId());
        assertEquals(TERM, review.getAcademicTerm()); assertEquals(4, review.getRating());
        assertEquals("Clear explanations.\nUseful examples!", review.getText()); assertNotNull(review.getCreatedAt());
        assertEquals(1, teachers.count()); assertEquals(1, courses.count()); assertEquals(1, offerings.count());
        assertEquals(1, verified.count()); assertEquals(0, pending.count()); assertEquals(1, entries.count());
    }
    @ParameterizedTest @ValueSource(ints={1,5}) void ratingBoundaries(int rating) throws Exception {
        var input = body(); input.put("rating", rating); send(input).andExpect(status().isCreated());
        assertEquals(rating, reviews.findAll().getFirst().getRating());
    }
    @ParameterizedTest @ValueSource(strings={"0","6","-1","4.5","4.0","\"4\"","null","true","{}"})
    void invalidRatingTypesAndRange(String rating) throws Exception {
        String input = json.writeValueAsString(body()).replace("\"rating\":4", "\"rating\":"+rating);
        mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(input)).andExpect(status().isBadRequest());
        assertEquals(0, reviews.count());
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "\n\t", "\u00a0"}) void emptyFeedback(String text) throws Exception {
        var input=body(); input.put("text",text); send(input).andExpect(status().isBadRequest()); assertEquals(0,reviews.count());
    }
    @Test void nullAndMissingFeedback() throws Exception {
        var input=body(); input.put("text",null); send(input).andExpect(status().isBadRequest());
        input.remove("text"); send(input).andExpect(status().isBadRequest()); assertEquals(0,reviews.count());
    }
    @Test void feedbackLengthBoundary() throws Exception {
        var input=body(); input.put("text","x".repeat(2001)); send(input).andExpect(status().isBadRequest()); assertEquals(0,reviews.count());
        input.put("text","x".repeat(2000)); send(input).andExpect(status().isCreated()); assertEquals(2000,reviews.findAll().getFirst().getText().length());
    }
    @ParameterizedTest @ValueSource(strings={"userId","studentId","authorId","anonymous","roles"}) void cannotChooseAuthorOrVisibility(String field) throws Exception {
        var input=body(); input.put(field, 99); send(input).andExpect(status().isBadRequest()); assertEquals(0,reviews.count());
    }
    @Test void wrongTerm() throws Exception { var input=body(); input.put("academicTerm","2025-2026 2 term"); send(input).andExpect(status().isForbidden()); assertEquals(0,reviews.count()); }
    @Test void tamperedOffering() throws Exception { var input=body(); input.put("courseOfferingId",999999); send(input).andExpect(status().isForbidden()); assertEquals(0,reviews.count()); }
    @Test void differentTeacherRelationshipDenied() throws Exception {
        var other = offerings.saveAndFlush(new CourseOffering(teachers.saveAndFlush(new Teacher("Other Person",null)), offering.getCourse(),"Fall 2026",null));
        var input=body(); input.put("courseOfferingId",other.getId()); send(input).andExpect(status().isForbidden()); assertEquals(0,reviews.count());
    }
    @Test void otherStudentDenied() throws Exception {
        mvc.perform(post(API).with(user("other@feedback.test").roles("STUDENT")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body())))
            .andExpect(status().isForbidden()); assertEquals(0,reviews.count());
    }
    @Test void uncheckedAndPendingOnlyDenied() throws Exception {
        verified.deleteAll(); verified.flush(); send(body()).andExpect(status().isForbidden());
        pending.saveAndFlush(new PendingCatalogVerification(entry,PendingCatalogVerification.Reason.NO_COURSE_OFFERING));
        send(body()).andExpect(status().isForbidden()); assertEquals(0,reviews.count()); assertEquals(1,pending.count());
    }
    @Test void unauthenticated401WithCsrf() throws Exception {
        mvc.perform(post(API).with(csrf()).contentType("application/json").content(json.writeValueAsString(body()))).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","ADMIN"}) void wrongRoles403(String role) throws Exception {
        mvc.perform(post(API).with(user(EMAIL).roles(role)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body()))).andExpect(status().isForbidden());
    }
    @Test void studentTeacherAllowed() throws Exception {
        mvc.perform(post(API).with(user(EMAIL).roles("STUDENT","TEACHER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body()))).andExpect(status().isCreated());
    }
    @Test void csrfRequired() throws Exception {
        mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).contentType("application/json").content(json.writeValueAsString(body()))).andExpect(status().isForbidden()); assertEquals(0,reviews.count());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void persistenceFailureAfterInsertRollsBackWholeReviewTransaction() {
        doAnswer(call -> { entityManager.persist(call.getArgument(0)); entityManager.flush(); throw new DataIntegrityViolationException("synthetic failure"); }).when(reviews).saveAndFlush(any());
        try {
            assertThrows(DataIntegrityViolationException.class, () -> service.submit(EMAIL,new SubmitReviewRequest(offering.getId(),TERM,4,"Test feedback")));
            assertEquals(0,reviews.count()); assertEquals(1,verified.count());
        } finally {
            reset(reviews); verified.deleteAll(); entries.deleteAll(); uploads.deleteAll(); offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
        }
    }
}
