package kz.sdu.feedback.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.repository.*;
import kz.sdu.feedback.schedule.service.*;
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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:review-eligibility;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ReviewEligibilityTest {
    private static final String EMAIL = "student@feedback.test", TERM = "2026-2027 1 term";
    private static final String API = "/api/student/review-eligibilities";
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
    @Autowired ReviewEligibilityService service;
    @MockitoSpyBean VerifiedCourseService matching;
    @MockitoSpyBean ScheduleParser parser;
    @MockitoSpyBean SchedulePdfValidator validator;
    private Course course;
    private Teacher teacher;
    private CourseOffering offering;
    private ScheduleUpload upload;

    @BeforeEach void setup() {
        users.saveAndFlush(new User(EMAIL, "unused-hash", Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("other@feedback.test", "unused-hash", Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("both@feedback.test", "unused-hash", Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
        teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
        offering = offer(teacher, course);
        upload = upload(EMAIL);
    }
    @AfterEach void neverRematchesOrParses() { verifyNoInteractions(matching, parser, validator); }
    private ScheduleUpload upload(String email) {
        var result = new ScheduleUpload(users.findByEmail(email).orElseThrow(), "schedule.pdf", UUID.randomUUID()+".pdf");
        result.finishValidation(null);
        return uploads.saveAndFlush(result);
    }
    private CourseOffering offer(Teacher t, Course c) {
        return offerings.saveAndFlush(new CourseOffering(t, c, "Fall 2026", null));
    }
    private ExtractedScheduleEntry entry(ScheduleUpload u, CourseOffering o, String term, String group) {
        return entries.saveAndFlush(new ExtractedScheduleEntry(u, o.getCourse().getCode(), o.getCourse().getName(),
                o.getTeacher().getName(), group, term));
    }
    private VerifiedCourse evidence(ScheduleUpload u, CourseOffering o, String term, String group) {
        return verified.saveAndFlush(new VerifiedCourse(entry(u, o, term, group), o));
    }
    private VerifiedCourse evidence() { return evidence(upload, offering, TERM, "01-N"); }

    @Test void exactPersistedTargetAllowed() {
        evidence(); assertTrue(service.canReview(EMAIL, offering.getId(), TERM));
        assertDoesNotThrow(() -> service.requireReviewEligibility(EMAIL, offering.getId(), TERM));
    }
    @Test void ownTeacherExcludedFromListAndGuardWithoutDeletingEvidence() throws Exception {
        evidence(); evidence(upload, offering, TERM, "04-N");
        evidence(upload, offering, "2025-2026 2 term", "01-N");
        var owner = users.findByEmail(EMAIL).orElseThrow();
        owner.claimTeacher(teacher); users.saveAndFlush(owner);
        assertTrue(service.list(EMAIL).isEmpty());
        assertFalse(service.canReview(EMAIL, offering.getId(), TERM));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.requireReviewEligibility(EMAIL, offering.getId(), TERM)).getStatusCode().value());
        mvc.perform(get(API).with(user(EMAIL).roles("STUDENT", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertEquals(3, verified.count());
    }
    @Test void linkedTeacherCanStillReviewAnotherTeachersVerifiedOffering() {
        evidence();
        var other = offer(teachers.saveAndFlush(new Teacher("Another Person", null)), course);
        evidence(upload, other, TERM, "04-N");
        var owner = users.findByEmail(EMAIL).orElseThrow();
        owner.claimTeacher(teacher); users.saveAndFlush(owner);
        var targets = service.list(EMAIL);
        assertEquals(1, targets.size()); assertEquals(other.getId(), targets.getFirst().courseOfferingId());
        assertTrue(service.canReview(EMAIL, other.getId(), TERM));
        assertFalse(service.canReview(EMAIL, other.getId(), "another term"));
        assertEquals(2, verified.count());
    }
    @Test void catalogAloneDoesNotGrantEligibility() { assertFalse(service.canReview(EMAIL, offering.getId(), TERM)); }
    @Test void pendingDoesNotGrantEligibility() {
        pending.saveAndFlush(new PendingCatalogVerification(entry(upload, offering, TERM, "01-N"), PendingCatalogVerification.Reason.AMBIGUOUS_OFFERING));
        assertTrue(service.list(EMAIL).isEmpty());
    }
    @Test void uncheckedDoesNotGrantEligibility() { entry(upload, offering, TERM, "01-N"); assertTrue(service.list(EMAIL).isEmpty()); }
    @Test void anotherStudentsEvidenceNeverGrantsAccess() {
        evidence(upload("other@feedback.test"), offering, TERM, "01-N");
        assertTrue(service.list(EMAIL).isEmpty()); assertFalse(service.canReview(EMAIL, offering.getId(), TERM));
    }
    @Test void sameCourseDifferentTeacherDenied() {
        evidence(); var other = offer(teachers.saveAndFlush(new Teacher("Another Person", null)), course);
        assertFalse(service.canReview(EMAIL, other.getId(), TERM));
    }
    @Test void sameTeacherDifferentCourseDenied() {
        evidence(); var other = offer(teacher, courses.saveAndFlush(new Course("ABC 200", "Other Subject")));
        assertFalse(service.canReview(EMAIL, other.getId(), TERM));
    }
    @Test void differentTermDenied() { evidence(); assertFalse(service.canReview(EMAIL, offering.getId(), "2025-2026 2 term")); }
    @Test void duplicateUploadsAreOneEntitlementWithoutDeletingEvidence() {
        evidence(); evidence(upload(EMAIL), offering, TERM, "01-N"); evidence(upload(EMAIL), offering, TERM, "01-N");
        assertEquals(1, service.list(EMAIL).size()); assertEquals(3, verified.count());
    }
    @Test void groupsAreTrimmedDistinctSortedMetadata() {
        evidence(); evidence(upload, offering, TERM, " 02-P "); evidence(upload, offering, TERM, "01-N");
        var result = service.list(EMAIL); assertEquals(1, result.size());
        assertEquals(List.of("01-N", "02-P"), result.getFirst().groupSections());
    }
    @Test void pastTermsRemainSeparateAndEligible() {
        evidence(); evidence(upload, offering, "2025-2026 2 term", "01-N");
        assertEquals(2, service.list(EMAIL).size()); assertTrue(service.canReview(EMAIL, offering.getId(), "2025-2026 2 term"));
    }
    @Test void twoTeachersForOneCourseAreTwoTargets() {
        evidence(); var other = offer(teachers.saveAndFlush(new Teacher("Second Person", null)), course);
        evidence(upload, other, TERM, "04-N"); assertEquals(2, service.list(EMAIL).size());
        assertTrue(service.canReview(EMAIL, other.getId(), TERM));
    }
    @Test void twoCoursesForOneTeacherAreTwoTargets() {
        evidence(); var other = offer(teacher, courses.saveAndFlush(new Course("ABC 200", "Second Subject")));
        evidence(upload, other, TERM, "01-N"); assertEquals(2, service.list(EMAIL).size());
    }
    @Test void anonymous401() throws Exception { mvc.perform(get(API)).andExpect(status().isUnauthorized()); }
    @ParameterizedTest @ValueSource(strings={"TEACHER", "ADMIN"})
    void wrongRole403(String role) throws Exception { mvc.perform(get(API).with(user("role@feedback.test").roles(role))).andExpect(status().isForbidden()); }
    @Test void multiRoleStudentAllowedOnlyOwnTargets() throws Exception {
        evidence(); evidence(upload("both@feedback.test"), offering, TERM, "02-P");
        mvc.perform(get(API).with(user("both@feedback.test").roles("STUDENT", "TEACHER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].groupSections[0]").value("02-P"));
    }
    @Test void safeDtoAndInjectedUserIdIgnored() throws Exception {
        evidence(); evidence(upload("other@feedback.test"), offering, "other term", "99-P");
        var response = mvc.perform(get(API).param("userId", users.findByEmail("other@feedback.test").orElseThrow().getId().toString())
            .with(user(EMAIL).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("courseOfferingId", "courseId", "teacherId", "courseCode", "courseName", "teacherName", "academicTerm", "groupSections"),
            json.readTree(response).get(0).propertyNames());
        assertFalse(response.contains("unused-hash")); assertFalse(response.contains("99-P"));
    }
    @Test void repeatedGetsPreserveAllSourceRowsAndTimestamps() throws Exception {
        var v = evidence(); var id = v.getId(); var created = v.getCreatedAt();
        var p = pending.saveAndFlush(new PendingCatalogVerification(entry(upload, offering, TERM, "04-N"), PendingCatalogVerification.Reason.NO_COURSE_OFFERING));
        var updated = p.getUpdatedAt();
        var before = service.list(EMAIL);
        mvc.perform(get(API).with(user(EMAIL).roles("STUDENT"))).andExpect(status().isOk());
        assertEquals(before, service.list(EMAIL)); assertEquals(1, verified.count()); assertEquals(1, pending.count());
        assertEquals(created, verified.findById(id).orElseThrow().getCreatedAt());
        assertEquals(updated, pending.findById(p.getId()).orElseThrow().getUpdatedAt());
        assertEquals(2, entries.count()); assertEquals(1, courses.count()); assertEquals(1, teachers.count()); assertEquals(1, offerings.count());
    }
    @Test void simultaneousVerifiedAndPendingFailsClosed() {
        var v = evidence(); pending.saveAndFlush(new PendingCatalogVerification(v.getSourceEntry(), PendingCatalogVerification.Reason.AMBIGUOUS_OFFERING));
        assertTrue(service.list(EMAIL).isEmpty()); assertEquals(1, verified.count());
    }
    @Test void invalidUploadCannotUseStaleVerification() {
        evidence(); upload.finishValidation(ScheduleUpload.ValidationReason.DETAILS_REQUIRED); uploads.saveAndFlush(upload);
        assertTrue(service.list(EMAIL).isEmpty()); assertEquals(1, verified.count());
    }
    @Test void contradictoryLinkedDataFailsClosedWithoutRematching() {
        var wrong = entries.saveAndFlush(new ExtractedScheduleEntry(upload, "WRONG 100", "Different name", "Other Person", "01-N", TERM));
        verified.saveAndFlush(new VerifiedCourse(wrong, offering)); assertTrue(service.list(EMAIL).isEmpty());
    }
    @Test void blankTermOrGroupFailsClosed() {
        evidence(upload, offering, " ", "01-N"); evidence(upload, offering, TERM, " "); assertTrue(service.list(EMAIL).isEmpty());
    }
    @Test void missingOrInvalidTargetDeniedByReusableGuard() {
        evidence(); assertFalse(service.canReview(EMAIL, null, TERM)); assertFalse(service.canReview(EMAIL, -1L, TERM));
        assertFalse(service.canReview(EMAIL, offering.getId(), null));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.requireReviewEligibility(EMAIL, 999999L, TERM)).getStatusCode().value());
    }
    @Test void serviceAlsoRequiresVerifiedStudent() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.list("missing@feedback.test")).getStatusCode().value());
        users.saveAndFlush(new User("staff@feedback.test", "unused-hash", Set.of(UserRole.TEACHER), true, null));
        users.saveAndFlush(new User("unverified@feedback.test", "unused-hash", Set.of(UserRole.STUDENT), false, null));
        for (String email : List.of("staff@feedback.test", "unverified@feedback.test"))
            assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.list(email)).getStatusCode().value());
    }
}
