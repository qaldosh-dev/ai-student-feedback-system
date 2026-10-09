package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=FeedbackApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:teacher-feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class TeacherFeedbackTest {
    static final String EMAIL="teacher-a@feedback.test", OTHER="teacher-b@feedback.test", API="/api/teacher/feedback", TERM="2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean ModerationProvider provider;
    User student;
    Teacher teacher;
    CourseOffering first, secondShell, foreign;
    Long published;

    @BeforeEach void setup() {
        teacher=teachers.saveAndFlush(new Teacher("Teacher Alpha",null));
        var other=teachers.saveAndFlush(new Teacher("Teacher Beta",null));
        users.saveAndFlush(new User(EMAIL,"unused",Set.of(UserRole.TEACHER),true,teacher));
        users.saveAndFlush(new User(OTHER,"unused",Set.of(UserRole.TEACHER),true,other));
        student=users.saveAndFlush(new User("private-student@feedback.test","private-hash",Set.of(UserRole.STUDENT),true,null));
        var course=courses.saveAndFlush(new Course("ABC 100","Alpha Subject"));
        first=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026","15-P"));
        secondShell=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026","04-N"));
        foreign=offerings.saveAndFlush(new CourseOffering(other,course,"Fall 2026","01-N"));
        published=save(first,TERM,"Own published",ReviewStatus.PUBLISHED);
        save(first,"2025-2026 1 term","Earlier term",ReviewStatus.PUBLISHED);
        save(secondShell,TERM,"Separate shell",ReviewStatus.PUBLISHED);
        save(foreign,TERM,"Other teacher feedback",ReviewStatus.PUBLISHED);
        save(first,"2024-2025 1 term","Hidden submitted",ReviewStatus.SUBMITTED);
        save(first,"2023-2024 1 term","Hidden flagged",ReviewStatus.NEEDS_CORRECTION);
        em.clear();
    }
    Long save(CourseOffering offering,String term,String text,ReviewStatus status) {
        var review=new Review(student,offering,term,4,text);
        if(status!=ReviewStatus.SUBMITTED) review.completeModeration(new ModerationResult(
            status==ReviewStatus.PUBLISHED?ModerationResult.Decision.ACCEPT:ModerationResult.Decision.NEEDS_CORRECTION,
            status==ReviewStatus.PUBLISHED?Set.of():Set.of(ModerationCategory.THREAT)));
        return reviews.saveAndFlush(review).getId();
    }
    String read(String email) throws Exception {
        return mvc.perform(get(API).with(user(email).roles("TEACHER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @Test void ownPublishedOnlyGroupedByOfferingAndTermWithoutPrivateFields() throws Exception {
        String body=read(EMAIL); var groups=json.readTree(body);
        assertEquals(3,groups.size());
        assertEquals(first.getId(),groups.get(0).get("courseOfferingId").longValue());
        assertEquals(TERM,groups.get(0).get("academicTerm").stringValue());
        assertEquals("2025-2026 1 term",groups.get(1).get("academicTerm").stringValue());
        assertEquals(secondShell.getId(),groups.get(2).get("courseOfferingId").longValue());
        for(var group:groups) {
            assertEquals(Set.of("courseOfferingId","courseCode","courseName","academicTerm","feedback"),group.propertyNames());
            assertEquals("ABC 100",group.get("courseCode").stringValue()); assertEquals("Alpha Subject",group.get("courseName").stringValue());
            for(var feedback:group.get("feedback")) assertEquals(Set.of("rating","text","publishedAt"),feedback.propertyNames());
        }
        var row=groups.get(0).get("feedback").get(0);
        assertEquals(4,row.get("rating").intValue()); assertEquals("Own published",row.get("text").stringValue()); assertFalse(row.get("publishedAt").isNull());
        for(String secret: new String[]{"private-student","private-hash","author","userId","studentId","groupSection","15-P","04-N","01-N","Hidden submitted","Hidden flagged","Other teacher feedback","moderation","THREAT"})
            assertFalse(body.contains(secret),secret);
        assertEquals(6,reviews.count()); verifyNoInteractions(provider);
        var other=json.readTree(read(OTHER)); assertEquals(1,other.size());
        assertEquals(foreign.getId(),other.get(0).get("courseOfferingId").longValue());
        assertFalse(other.toString().contains("Own published"));
    }
    @Test void newestPublicationFirstWithIdTieBreakAndLegacyFallback() throws Exception {
        var nextStudent=users.saveAndFlush(new User("second-private@feedback.test","unused",Set.of(UserRole.STUDENT),true,null));
        var newer=new Review(nextStudent,first,TERM,5,"Newer publication");
        newer.completeModeration(new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of()));
        Long next=reviews.saveAndFlush(newer).getId();
        jdbc.update("update reviews set moderated_at = ?, created_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-09-01 00:00:00"),java.sql.Timestamp.valueOf("2026-01-01 00:00:00"),next);
        jdbc.update("update reviews set moderated_at = null, created_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-08-01 00:00:00"),published);
        assertEquals("Newer publication",json.readTree(read(EMAIL)).get(0).get("feedback").get(0).get("text").stringValue());
        jdbc.update("update reviews set moderated_at = ? where id = ?",java.sql.Timestamp.valueOf("2026-09-01 00:00:00"),published);
        assertEquals("Newer publication",json.readTree(read(EMAIL)).get(0).get("feedback").get(0).get("text").stringValue());
    }
    @Test void unpublishedOnlyAndNoOfferingsHaveSameEmptyResponse() throws Exception {
        jdbc.update("update reviews set status = 'SUBMITTED' where course_offering_id in (?, ?)",first.getId(),secondShell.getId());
        assertEquals("[]",read(EMAIL));
        var emptyTeacher=teachers.saveAndFlush(new Teacher("Empty Teacher",null));
        users.saveAndFlush(new User("empty@feedback.test","unused",Set.of(UserRole.TEACHER),true,emptyTeacher));
        assertEquals("[]",read("empty@feedback.test"));
    }
    @Test void missingLinkedTeacher403NeverGuessesByNameOrEmail() throws Exception {
        users.saveAndFlush(new User("unlinked@feedback.test","unused",Set.of(UserRole.TEACHER),true,null));
        mvc.perform(get(API).with(user("unlinked@feedback.test").roles("TEACHER"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No teacher profile is linked to this account."));
    }
    @Test void missingAccount401() throws Exception { mvc.perform(get(API).with(user("missing@feedback.test").roles("TEACHER"))).andExpect(status().isUnauthorized()); }
    @Test void anonymous401() throws Exception { mvc.perform(get(API)).andExpect(status().isUnauthorized()); }
    @ParameterizedTest @ValueSource(strings={"STUDENT","ADMIN"}) void wrongRole403(String role) throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles(role))).andExpect(status().isForbidden());
    }
    @Test void multiRoleTeacherAllowed() throws Exception {
        mvc.perform(get(API).with(user(EMAIL).roles("STUDENT","TEACHER"))).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"teacherId","userId","courseOfferingId","status"}) void cannotSelectOtherData(String parameter) throws Exception {
        mvc.perform(get(API).param(parameter,foreign.getId().toString()).with(user(EMAIL).roles("TEACHER"))).andExpect(status().isBadRequest());
    }
    @Test void updateHidesPublishedReviewUntilModerationAcceptsAgain() throws Exception {
        var review=reviews.findById(published).orElseThrow();
        review.submitPublishedUpdate(2,"Updated text"); reviews.saveAndFlush(review);
        assertFalse(read(EMAIL).contains("Updated text")); assertFalse(read(EMAIL).contains("Own published"));
        review.completeModeration(new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION,Set.of(ModerationCategory.THREAT)));
        reviews.saveAndFlush(review); assertFalse(read(EMAIL).contains("Updated text"));
        review.submitCorrection("Corrected text"); reviews.saveAndFlush(review); assertFalse(read(EMAIL).contains("Corrected text"));
        review.completeModeration(new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of())); reviews.saveAndFlush(review);
        assertTrue(read(EMAIL).contains("Corrected text")); assertEquals(6,reviews.count()); verifyNoInteractions(provider);
    }
    @Test void teacherHasNoWriteEndpoint() throws Exception {
        mvc.perform(post(API).with(user(EMAIL).roles("TEACHER")).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(API).with(user(EMAIL).roles("TEACHER")).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/student/reviews/"+published).with(user(EMAIL).roles("TEACHER")).with(csrf()).contentType("application/json")
                .content("{\"rating\":1,\"text\":\"No\"}")).andExpect(status().isForbidden());
    }
}
