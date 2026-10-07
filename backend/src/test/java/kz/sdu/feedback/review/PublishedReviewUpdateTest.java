package kz.sdu.feedback.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.review.dto.*;
import kz.sdu.feedback.review.entity.*;
import kz.sdu.feedback.review.moderation.*;
import kz.sdu.feedback.review.repository.ReviewRepository;
import kz.sdu.feedback.review.service.ReviewService;
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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=FeedbackApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:published-review-update;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class PublishedReviewUpdateTest {
    static final String EMAIL="owner@feedback.test", TERM="2026-2027 1 term";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired DataSource dataSource;
    @MockitoBean ModerationProvider provider;
    Long reviewId,authorId,offeringId;
    Instant createdAt;
    static ModerationResult accept() { return new ModerationResult(ModerationResult.Decision.ACCEPT,Set.of()); }
    static ModerationResult flag() { return new ModerationResult(ModerationResult.Decision.NEEDS_CORRECTION,Set.of(ModerationCategory.PERSONAL_INSULT)); }
    @BeforeEach void setup() {
        var author=users.saveAndFlush(new User(EMAIL,"unused",Set.of(UserRole.STUDENT),true,null)); authorId=author.getId();
        var course=courses.saveAndFlush(new Course("ABC 100","Alpha Subject"));
        var teacher=teachers.saveAndFlush(new Teacher("Alex Person",null));
        var offering=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026",null)); offeringId=offering.getId();
        var review=new Review(author,offering,TERM,2,"Original feedback"); review.completeModeration(accept());
        reviewId=reviews.saveAndFlush(review).getId(); createdAt=reviews.findById(reviewId).orElseThrow().getCreatedAt();
    }
    @AfterEach void cleanup() {
        reviews.deleteAll(); offerings.deleteAll(); teachers.deleteAll(); courses.deleteAll(); users.deleteAll();
    }
    String url() { return "/api/student/reviews/"+reviewId; }
    org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(body));
    }
    void assertIdentity() {
        var row=reviews.findById(reviewId).orElseThrow();
        assertEquals(1,reviews.count()); assertEquals(reviewId,row.getId()); assertEquals(authorId,row.getAuthor().getId());
        assertEquals(offeringId,row.getCourseOffering().getId()); assertEquals(TERM,row.getAcademicTerm()); assertEquals(createdAt,row.getCreatedAt());
        assertEquals(1,courses.count()); assertEquals(1,teachers.count()); assertEquals(1,offerings.count());
    }
    void assertUnchanged() {
        assertIdentity(); var row=reviews.findById(reviewId).orElseThrow();
        assertEquals(2,row.getRating()); assertEquals("Original feedback",row.getText()); assertEquals(ReviewStatus.PUBLISHED,row.getStatus());
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(ints={1,5})
    void ownPublishedUpdateKeepsIdentityAndRemoderatesOnlyText(int rating) throws Exception {
        when(provider.moderate("Updated feedback")).thenReturn(accept());
        var body=send(json.writeValueAsString(Map.of("rating",rating,"text","  Updated feedback  ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewId").value(reviewId))
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.rating").value(rating))
                .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("reviewId","submitted","courseOfferingId","academicTerm","rating","status"),json.readTree(body).propertyNames());
        assertFalse(body.contains(EMAIL));
        var row=reviews.findById(reviewId).orElseThrow(); assertEquals(rating,row.getRating()); assertEquals("Updated feedback",row.getText());
        var result=service.status(EMAIL,reviewId); assertEquals(ReviewStatus.PUBLISHED,result.status()); assertNotNull(result.moderatedAt());
        assertTrue(result.moderationReasons().isEmpty()); assertIdentity(); verify(provider).moderate("Updated feedback");
    }
    @Test void violatingUpdateUsesNewReasonsThenUS27CorrectsSameReviewWithoutChangingUpdatedRating() {
        when(provider.moderate("Updated violation")).thenReturn(flag());
        service.updatePublished(EMAIL,reviewId,new UpdateReviewRequest(4,"Updated violation"));
        var flagged=service.status(EMAIL,reviewId);
        assertEquals(ReviewStatus.NEEDS_CORRECTION,flagged.status()); assertEquals(4,flagged.rating());
        assertEquals(List.of(ModerationCategory.PERSONAL_INSULT),flagged.moderationReasons().stream().map(r->r.category()).toList());
        when(provider.moderate("Constructive correction")).thenReturn(accept());
        service.correct(EMAIL,reviewId,new CorrectReviewRequest("Constructive correction"));
        var corrected=service.status(EMAIL,reviewId); assertEquals(ReviewStatus.PUBLISHED,corrected.status());
        assertTrue(corrected.moderationReasons().isEmpty()); assertEquals(4,corrected.rating()); assertIdentity();
    }
    @ParameterizedTest @ValueSource(strings={"timeout","network","HTTP 429","invalid JSON","invalid decision/category"})
    void providerFailurePreservesCommittedUpdateAndClearsModeration(String failure) {
        when(provider.moderate("Updated feedback")).thenAnswer(call->{
            try(var connection=dataSource.getConnection(); var statement=connection.createStatement();
                var result=statement.executeQuery("select rating,text,status,moderated_at,(select count(*) from review_moderation_categories) from reviews")) {
                assertTrue(result.next()); assertEquals(4,result.getInt(1)); assertEquals("Updated feedback",result.getString(2));
                assertEquals("SUBMITTED",result.getString(3)); assertNull(result.getObject(4)); assertEquals(0,result.getInt(5));
            }
            throw new IllegalStateException(failure);
        });
        service.updatePublished(EMAIL,reviewId,new UpdateReviewRequest(4,"Updated feedback"));
        var result=service.status(EMAIL,reviewId); assertEquals(ReviewStatus.SUBMITTED,result.status()); assertNull(result.moderatedAt());
        assertTrue(result.moderationReasons().isEmpty());
        var row=reviews.findById(reviewId).orElseThrow(); assertEquals(4,row.getRating()); assertEquals("Updated feedback",row.getText()); assertIdentity();
    }
    @ParameterizedTest @EnumSource(value=ReviewStatus.class,names={"SUBMITTED","NEEDS_CORRECTION"})
    void onlyPublishedCanUseGeneralUpdate(ReviewStatus target) throws Exception {
        when(provider.moderate(anyString())).thenReturn(target==ReviewStatus.SUBMITTED?null:flag());
        service.updatePublished(EMAIL,reviewId,new UpdateReviewRequest(3,"First update")); clearInvocations(provider);
        send("{\"rating\":5,\"text\":\"Forbidden update\"}").andExpect(status().isConflict());
        assertEquals(target,service.status(EMAIL,reviewId).status()); assertEquals("First update",reviews.findById(reviewId).orElseThrow().getText());
        assertIdentity(); verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings={"0","6","-1","4.5","4.0","\"4\"","null","true","{}"})
    void invalidRating(String rating) throws Exception {
        send("{\"rating\":"+rating+",\"text\":\"Updated\"}").andExpect(status().isBadRequest()); assertUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={""," ","\n\t","\u00a0"})
    void blankText(String text) throws Exception {
        send(json.writeValueAsString(Map.of("rating",4,"text",text))).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @Test void lengthBoundary() throws Exception {
        send(json.writeValueAsString(Map.of("rating",4,"text","x".repeat(2001)))).andExpect(status().isBadRequest()); assertUnchanged();
        when(provider.moderate("x".repeat(2000))).thenReturn(accept());
        send(json.writeValueAsString(Map.of("rating",4,"text","x".repeat(2000)))).andExpect(status().isOk());
        assertEquals(2000,reviews.findById(reviewId).orElseThrow().getText().length()); assertIdentity();
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"rating\":4}","{\"text\":\"Updated\"}","{\"rating\":4,\"text\":null}","{\"rating\":4,\"text\":12}"})
    void missingOrWrongTypes(String body) throws Exception {
        send(body).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"userId","authorId","courseOfferingId","academicTerm","status","moderationCategories","moderatedAt","createdAt","groupSection"})
    void forbiddenFields(String field) throws Exception {
        send(json.writeValueAsString(Map.of("rating",4,"text","Updated",field,99))).andExpect(status().isBadRequest()); assertUnchanged();
    }
    @Test void otherOwnerAndMissingUseSame404() throws Exception {
        String input="{\"rating\":4,\"text\":\"Updated\"}";
        var other=mvc.perform(put(url()).with(user("other@feedback.test").roles("STUDENT")).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing=mvc.perform(put("/api/student/reviews/999999").with(user(EMAIL).roles("STUDENT")).with(csrf()).contentType("application/json").content(input))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing,other); assertUnchanged();
    }
    @Test void csrfRequired() throws Exception {
        mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT")).contentType("application/json").content("{\"rating\":4,\"text\":\"Updated\"}"))
                .andExpect(status().isForbidden()); assertUnchanged();
    }
    @Test void anonymous401() throws Exception {
        mvc.perform(put(url()).with(csrf()).contentType("application/json").content("{\"rating\":4,\"text\":\"Updated\"}"))
                .andExpect(status().isUnauthorized()); assertUnchanged();
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","ADMIN"}) void wrongRole403(String role) throws Exception {
        mvc.perform(put(url()).with(user(EMAIL).roles(role)).with(csrf()).contentType("application/json").content("{\"rating\":4,\"text\":\"Updated\"}"))
                .andExpect(status().isForbidden()); assertUnchanged();
    }
    @Test void studentTeacherCanUpdate() throws Exception {
        when(provider.moderate(anyString())).thenReturn(accept());
        mvc.perform(put(url()).with(user(EMAIL).roles("STUDENT","TEACHER")).with(csrf()).contentType("application/json").content("{\"rating\":4,\"text\":\"Updated\"}"))
                .andExpect(status().isOk()); assertIdentity();
    }
    @Test void secondUpdateCannotOverwriteWhileModerationIsPending() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(provider.moderate(anyString())).thenAnswer(call->{ started.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return accept(); });
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->service.updatePublished(EMAIL,reviewId,new UpdateReviewRequest(4,"First update")));
            try {
                assertTrue(started.await(10,TimeUnit.SECONDS));
                assertEquals(409,assertThrows(ResponseStatusException.class,()->service.updatePublished(EMAIL,reviewId,new UpdateReviewRequest(5,"Second update"))).getStatusCode().value());
            } finally { release.countDown(); }
            first.get(10,TimeUnit.SECONDS);
        }
        assertEquals(4,reviews.findById(reviewId).orElseThrow().getRating()); assertEquals("First update",reviews.findById(reviewId).orElseThrow().getText()); assertIdentity();
    }
}
