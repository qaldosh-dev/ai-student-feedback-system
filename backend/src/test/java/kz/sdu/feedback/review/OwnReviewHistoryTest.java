package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.*;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.ReviewService;
import kz.sdu.feedback.review.moderation.*;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=FeedbackApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:own-review-history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class OwnReviewHistoryTest {
    static final String OWNER="owner@feedback.test", OTHER="other@feedback.test", API="/api/student/reviews";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ModerationProvider provider;
    Long published, flagged, submitted, foreignId;

    static ModerationResult accept() { return new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of()); }
    static ModerationResult flag() { return new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION,
            Set.of(ModerationCategory.THREAT,ModerationCategory.PERSONAL_INSULT)); }
    @BeforeEach void setup() {
        var owner=users.saveAndFlush(new User(OWNER,"unused",Set.of(UserRole.STUDENT),true,null));
        var other=users.saveAndFlush(new User(OTHER,"unused",Set.of(UserRole.STUDENT),true,null));
        var course=courses.saveAndFlush(new Course("ABC 100","Alpha Subject"));
        var teacher=teachers.saveAndFlush(new Teacher("Alex Person",null));
        var offering=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026",null));
        published=save(owner,offering,"2026-2027 1 term","Published content",accept());
        flagged=save(owner,offering,"2026-2027 2 term","Flagged content",flag());
        submitted=save(owner,offering,"2027-2028 1 term","Submitted content",null);
        foreignId=save(other,offering,"2026-2027 1 term","Other person's private feedback",accept());
        // Deliberately oppose insertion order to prove ordering uses original createdAt.
        jdbc.update("update reviews set created_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-01-01 00:00:00"),published);
        jdbc.update("update reviews set created_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-03-01 00:00:00"),flagged);
        jdbc.update("update reviews set created_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-02-01 00:00:00"),submitted);
    }
    Long save(User owner, CourseOffering offering, String term, String text, ModerationResult result) {
        var review=new Review(owner,offering,term,4,text);
        if(result!=null) review.completeModeration(result);
        return reviews.saveAndFlush(review).getId();
    }
    @AfterEach void cleanup() {
        reviews.deleteAll(); offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
    }
    String history(String email) throws Exception {
        return mvc.perform(get(API).with(user(email).roles("STUDENT"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    @Test void ownHistorySortedWithCurrentContentFriendlyReasonsAndNoIdentity() throws Exception {
        var body=history(OWNER); var rows=json.readTree(body);
        assertEquals(3,rows.size()); assertEquals(flagged,rows.get(0).get("reviewId").longValue());
        assertEquals(submitted,rows.get(1).get("reviewId").longValue()); assertEquals(published,rows.get(2).get("reviewId").longValue());
        assertEquals(Set.of("reviewId","courseCode","courseName","teacherName","academicTerm","rating","text","status","submittedAt","moderatedAt","moderationReasons"),rows.get(0).propertyNames());
        var row=rows.get(0);
        assertEquals("ABC 100",row.get("courseCode").stringValue()); assertEquals("Alpha Subject",row.get("courseName").stringValue());
        assertEquals("Alex Person",row.get("teacherName").stringValue()); assertEquals("2026-2027 2 term",row.get("academicTerm").stringValue());
        assertEquals(4,row.get("rating").intValue()); assertEquals("Flagged content",row.get("text").stringValue());
        assertEquals("NEEDS_CORRECTION",row.get("status").stringValue()); assertEquals(2,row.get("moderationReasons").size());
        assertEquals(ModerationCategory.PERSONAL_INSULT.message(),row.get("moderationReasons").get(0).get("message").stringValue());
        assertEquals("Submitted content",rows.get(1).get("text").stringValue());
        assertEquals("Published content",rows.get(2).get("text").stringValue());
        assertFalse(body.contains(OWNER)); assertFalse(body.contains(OTHER)); assertFalse(body.contains("unused"));
        assertFalse(body.contains("Other person's")); assertEquals(4,reviews.count()); verifyNoInteractions(provider);
        var otherRows=json.readTree(history(OTHER)); assertEquals(1,otherRows.size()); assertEquals(foreignId,otherRows.get(0).get("reviewId").longValue());
    }
    @Test void emptyHistoryIsSuccessful() throws Exception { assertEquals("[]",history("empty@feedback.test")); }
    @Test void anonymous401() throws Exception { mvc.perform(get(API)).andExpect(status().isUnauthorized()); }
    @ParameterizedTest @ValueSource(strings={"TEACHER","ADMIN"}) void nonStudent403(String role) throws Exception {
        mvc.perform(get(API).with(user(OWNER).roles(role))).andExpect(status().isForbidden());
    }
    @Test void multiRoleStudentAllowed() throws Exception {
        mvc.perform(get(API).with(user(OWNER).roles("STUDENT","TEACHER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }
    @ParameterizedTest @ValueSource(strings={"userId","authorId","studentId","email"}) void clientCannotSelectOwner(String parameter) throws Exception {
        mvc.perform(get(API).param(parameter,"999").with(user(OWNER).roles("STUDENT"))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @EnumSource(ReviewStatus.class) void US28UpdateAppearsOnceWithActualModerationOutcome(ReviewStatus outcome) throws Exception {
        var original=service.history(OWNER); var created=reviews.findById(published).orElseThrow().getCreatedAt();
        when(provider.moderate("Updated content")).thenReturn(switch(outcome) {
            case PUBLISHED -> accept(); case NEEDS_CORRECTION -> flag(); case SUBMITTED -> null;
        });
        mvc.perform(put(API+"/"+published).with(user(OWNER).roles("STUDENT")).with(csrf()).contentType("application/json")
                .content("{\"rating\":2,\"text\":\"Updated content\"}")).andExpect(status().isOk());
        var current=service.history(OWNER);
        assertEquals(original.stream().map(OwnReviewResponse::reviewId).toList(),current.stream().map(OwnReviewResponse::reviewId).toList());
        var updated=current.get(2); assertEquals(outcome,updated.status()); assertEquals("Updated content",updated.text()); assertEquals(2,updated.rating());
        assertEquals(created,updated.submittedAt()); assertEquals(4,reviews.count()); verify(provider).moderate("Updated content");
        assertEquals(3,json.readTree(history(OWNER)).size());
    }
    @Test void US27CorrectionAppearsOnceWithCurrentContentAndSameOriginalDate() throws Exception {
        var created=reviews.findById(flagged).orElseThrow().getCreatedAt();
        when(provider.moderate("Corrected content")).thenReturn(accept());
        mvc.perform(put(API+"/"+flagged+"/correction").with(user(OWNER).roles("STUDENT")).with(csrf()).contentType("application/json")
                .content("{\"text\":\"Corrected content\"}")).andExpect(status().isOk());
        var current=service.history(OWNER); var corrected=current.get(0);
        assertEquals(3,current.size()); assertEquals(flagged,corrected.reviewId()); assertEquals("Corrected content",corrected.text());
        assertEquals(ReviewStatus.PUBLISHED,corrected.status()); assertTrue(corrected.moderationReasons().isEmpty());
        assertEquals(4,corrected.rating()); assertEquals(created,corrected.submittedAt()); assertEquals(4,reviews.count());
    }
}
