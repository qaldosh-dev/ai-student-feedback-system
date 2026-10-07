package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.SubmitReviewRequest;
import kz.sdu.feedback.review.entity.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=FeedbackApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:review-duplicates;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ReviewDuplicateTest {
    static final String EMAIL="owner@feedback.test", TERM="2026-2027 1 term", API="/api/student/reviews";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewService service;
    @MockitoSpyBean ReviewRepository reviews;
    @MockitoBean ModerationProvider provider;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ScheduleUploadRepository uploads;
    @Autowired ExtractedScheduleEntryRepository entries;
    @Autowired VerifiedCourseRepository verified;
    @Autowired JdbcTemplate jdbc;
    User author;
    CourseOffering offering;

    @BeforeEach void setup() {
        author=users.saveAndFlush(new User(EMAIL,"unused",Set.of(UserRole.STUDENT),true,null));
        var course=courses.saveAndFlush(new Course("ABC 100","Alpha Subject"));
        var teacher=teachers.saveAndFlush(new Teacher("Alex Person",null));
        offering=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026",null));
        evidence(author,offering,TERM,"01-N");
        when(provider.moderate(anyString())).thenReturn(new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of()));
    }
    @AfterEach void cleanup() {
        reset(reviews);
        reviews.deleteAll(); verified.deleteAll(); entries.deleteAll(); uploads.deleteAll();
        offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
        jdbc.execute("ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uk_review_author_offering_term");
    }
    void evidence(User owner, CourseOffering target, String term, String section) {
        var upload=new ScheduleUpload(owner,"synthetic.pdf",UUID.randomUUID()+".pdf");
        upload.finishValidation(null); uploads.saveAndFlush(upload);
        var entry=entries.saveAndFlush(new ExtractedScheduleEntry(upload,target.getCourse().getCode(),target.getCourse().getName(),target.getTeacher().getName(),section,term));
        verified.saveAndFlush(new VerifiedCourse(entry,target));
    }
    void installConstraint() {
        jdbc.execute("ALTER TABLE reviews ADD CONSTRAINT uk_review_author_offering_term UNIQUE (author_id, course_offering_id, academic_term)");
    }
    String body(int rating, String text) {
        return json.writeValueAsString(Map.of("courseOfferingId",offering.getId(),"academicTerm",TERM,"rating",rating,"text",text));
    }
    Long submit(String email, CourseOffering target, String term) {
        return service.submit(email,new SubmitReviewRequest(target.getId(),term,2,"Initial feedback")).reviewId();
    }
    @ParameterizedTest @EnumSource(ReviewStatus.class)
    void existingReviewInAnyStatusPreventsAnotherRatingOrText(ReviewStatus state) throws Exception {
        when(provider.moderate(anyString())).thenReturn(switch(state) {
            case SUBMITTED -> null;
            case PUBLISHED -> new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of());
            case NEEDS_CORRECTION -> new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION,Set.of(ModerationCategory.THREAT));
        });
        var first=mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body(2,"Initial feedback")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id=json.readTree(first).get("reviewId").longValue();
        assertEquals(state,service.status(EMAIL,id).status()); clearInvocations(provider);
        var duplicate=mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body(5,"Different feedback")))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("message"),json.readTree(duplicate).propertyNames());
        assertTrue(duplicate.contains("already submitted")); assertFalse(duplicate.contains(EMAIL));
        assertEquals(1,reviews.count()); assertEquals(2,reviews.findById(id).orElseThrow().getRating());
        assertEquals("Initial feedback",reviews.findById(id).orElseThrow().getText()); verifyNoInteractions(provider);
    }
    @Test void differentTermOfferingAndStudentAreDistinctTargetsWithEligibility() {
        installConstraint();
        Set<Long> ids=new HashSet<>(); ids.add(submit(EMAIL,offering,TERM));
        String nextTerm="2027-2028 1 term"; evidence(author,offering,nextTerm,"02-N"); ids.add(submit(EMAIL,offering,nextTerm));
        var otherOffering=offerings.saveAndFlush(new CourseOffering(teachers.saveAndFlush(new Teacher("Other Person",null)),offering.getCourse(),"Fall 2026",null));
        evidence(author,otherOffering,TERM,"01-N"); ids.add(submit(EMAIL,otherOffering,TERM));
        var other=users.saveAndFlush(new User("other@feedback.test","unused",Set.of(UserRole.STUDENT),true,null));
        evidence(other,offering,TERM,"01-N"); ids.add(submit(other.getEmail(),offering,TERM));
        assertEquals(4,ids.size()); assertEquals(4,reviews.count());
    }
    @Test void repeatedUploadsAndDifferentSectionsDoNotGrantAnotherReview() {
        evidence(author,offering,TERM,"01-N"); evidence(author,offering,TERM,"15-P");
        submit(EMAIL,offering,TERM);
        assertEquals(3,verified.count());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->submit(EMAIL,offering,TERM)).getStatusCode().value());
        assertEquals(1,reviews.count());
    }
    @Test void eligibilityStillRequiredEvenForAlternateTarget() {
        submit(EMAIL,offering,TERM);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->submit(EMAIL,offering,"2027-2028 1 term")).getStatusCode().value());
        assertEquals(1,reviews.count());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void simultaneousRequestsProduceOneReviewAndOneConflict(boolean withConstraint) throws Exception {
        if(withConstraint) installConstraint();
        var start=new CountDownLatch(1);
        Callable<Integer> request=()->{
            assertTrue(start.await(10,TimeUnit.SECONDS));
            try { submit(EMAIL,offering,TERM); return 201; }
            catch(ResponseStatusException conflict) { return conflict.getStatusCode().value(); }
        };
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(request); var b=executor.submit(request); start.countDown();
            var statuses=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))); statuses.sort(Integer::compareTo);
            assertEquals(List.of(201,409),statuses);
        }
        assertEquals(1,reviews.count()); verify(provider,times(1)).moderate("Initial feedback");
    }
    @Test void actualDatabaseConstraintConflictIsSafe409Not500() throws Exception {
        installConstraint(); submit(EMAIL,offering,TERM); clearInvocations(provider);
        // Simulate a stale existence check: the database is the final guard.
        doReturn(false).when(reviews).existsByAuthorIdAndCourseOfferingIdAndAcademicTerm(anyLong(),anyLong(),anyString());
        mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body(5,"Different feedback")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("You already submitted feedback for this course offering and term."));
        assertEquals(1,reviews.count()); verifyNoInteractions(provider);
    }
    @Test void databaseConstraintAlsoRejectsDirectRepositoryBypass() {
        installConstraint(); submit(EMAIL,offering,TERM);
        assertThrows(DataIntegrityViolationException.class,()->reviews.saveAndFlush(new Review(author,offering,TERM,5,"Direct duplicate")));
        assertEquals(1,reviews.count());
    }
    @Test void unrelatedIntegrityFailureIsNotMisreportedAsDuplicate() throws Exception {
        doThrow(new DataIntegrityViolationException("synthetic unrelated failure")).when(reviews).saveAndFlush(any());
        mvc.perform(post(API).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body(4,"Feedback")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Review could not be saved. Please try again."));
        assertEquals(0,reviews.count()); verifyNoInteractions(provider);
    }
    @Test void legacyDuplicatesRemainUntouchedButCannotGrow() {
        Long first=submit(EMAIL,offering,TERM);
        Long legacy=reviews.saveAndFlush(new Review(author,offering,TERM,5,"Legacy duplicate")).getId();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->submit(EMAIL,offering,TERM)).getStatusCode().value());
        assertEquals(2,reviews.count()); assertTrue(reviews.existsById(first));
        assertEquals("Legacy duplicate",reviews.findById(legacy).orElseThrow().getText());
    }
}
