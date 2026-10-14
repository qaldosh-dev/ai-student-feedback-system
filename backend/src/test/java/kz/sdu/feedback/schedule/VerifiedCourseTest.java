package kz.sdu.feedback.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.entity.PendingCatalogVerification.Reason;
import kz.sdu.feedback.schedule.repository.*;
import kz.sdu.feedback.schedule.service.*;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:verified-courses;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class VerifiedCourseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired ScheduleUploadRepository uploads;
    @Autowired ExtractedScheduleEntryRepository entries;
    @Autowired VerifiedCourseRepository verified;
    @Autowired PendingCatalogVerificationRepository pending;
    @Autowired VerifiedCourseService service;
    @Autowired ScheduleExtractionService extraction;
    @MockitoSpyBean SchedulePdfValidator validator;
    @MockitoSpyBean ScheduleParser parser;
    private ScheduleUpload upload;
    private Course course;
    private Teacher teacher;
    private CourseOffering offering;
    private static final String EMAIL = "student@feedback.test";
    private static final String API = "/api/student/verified-courses";

    @BeforeEach void setup() {
        pending.deleteAll(); verified.deleteAll(); entries.deleteAll(); uploads.deleteAll();
        users.deleteAll(); offerings.deleteAll(); courses.deleteAll(); teachers.deleteAll();
        users.saveAndFlush(new User(EMAIL, "unused", Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("other@feedback.test", "unused", Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("both@feedback.test", "unused", Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        course = courses.saveAndFlush(new Course("ABC 789", "Experimental Design"));
        teacher = teachers.saveAndFlush(new Teacher("Morgan Rivera", null));
        offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null, 100L));
        upload = upload(EMAIL);
    }

    @Test void exactNormalizedMatchAndRepeatAreStableAndNeverCreateCatalogRows() {
        var source = row(upload, " abc   789 ", " EXPERIMENTAL  Design ", " Morgan   RIVERA ", "04-N", "2026-2027 1 term");
        var first = service.list(EMAIL);
        assertEquals(1, first.size());
        assertEquals(offering.getId(), first.getFirst().courseOfferingId());
        assertEquals(source.getId(), first.getFirst().sourceEntryId());
        assertEquals(first, service.list(EMAIL));
        assertEquals(1, verified.count());
        assertEquals(0, pending.count());
        assertEquals(1, courses.count()); assertEquals(1, teachers.count()); assertEquals(1, offerings.count());
    }

    @ParameterizedTest @ValueSource(strings = {"unknown-code", "unknown-teacher", "partial-teacher", "wrong-name", "blank-group", "blank-term"})
    void unsafeOrIncompleteSourcesRemainUnverified(String problem) {
        row(upload, problem.equals("unknown-code") ? "XYZ 900" : "ABC 789",
                problem.equals("wrong-name") ? "Different Academic Subject" : "Experimental Design",
                problem.equals("unknown-teacher") ? "Unknown Person" : problem.equals("partial-teacher") ? "Morgan" : "Morgan Rivera",
                problem.equals("blank-group") ? " " : "04-N", problem.equals("blank-term") ? " " : "2026-2027 1 term");
        assertTrue(service.list(EMAIL).isEmpty());
        assertEquals(1, entries.count()); assertEquals(0, verified.count());
        assertPending(switch (problem) {
            case "unknown-code" -> Reason.COURSE_NOT_FOUND;
            case "unknown-teacher", "partial-teacher" -> Reason.TEACHER_NOT_FOUND;
            case "wrong-name" -> Reason.COURSE_DATA_CONFLICT;
            default -> Reason.INCOMPLETE_ENTRY;
        });
        assertEquals(1, courses.count()); assertEquals(1, teachers.count()); assertEquals(1, offerings.count());
    }

    @Test void unrelatedOfferingDoesNotVerifyExistingTeacherAndCourse() {
        offerings.deleteAll();
        var different = courses.saveAndFlush(new Course("XYZ 900", "Other Subject"));
        offerings.saveAndFlush(new CourseOffering(teacher, different, "Fall 2026", null));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        assertPending(Reason.NO_COURSE_OFFERING);
    }

    @Test void ambiguousTeacherFailsClosedEvenIfOnlyOneHasOffering() {
        teachers.saveAndFlush(new Teacher("  MORGAN  Rivera ", null, 123L));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        assertPending(Reason.AMBIGUOUS_TEACHER);
    }

    @Test void multipleSourceOfferingsAreNotArbitrarilyChosen() {
        offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null, 101L));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        assertPending(Reason.AMBIGUOUS_OFFERING);
    }

    @Test void normalizedCourseCodeAmbiguityFailsClosed() {
        courses.saveAndFlush(new Course("abc 789", "Experimental Design"));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        assertPending(Reason.AMBIGUOUS_COURSE);
    }

    @Test void differentTeachersGroupsAndPastTermsStayDistinct() {
        var second = teachers.saveAndFlush(new Teacher("Jamie Quinn", null));
        offerings.saveAndFlush(new CourseOffering(second, course, "Fall 2026", null, 102L));
        standardRow(upload);
        row(upload, "ABC 789", "Experimental Design", "Jamie Quinn", "04-N", "2026-2027 1 term");
        row(upload, "ABC 789", "Experimental Design", "Morgan Rivera", "15-P", "2026-2027 1 term");
        var past = upload(EMAIL);
        row(past, "ABC 789", "Experimental Design", "Morgan Rivera", "04-N", "2025-2026 2 term");
        var result = service.list(EMAIL);
        assertEquals(4, result.size());
        assertEquals(2, result.stream().map(r -> r.academicTerm()).distinct().count());
        assertEquals(2, result.stream().map(r -> r.groupSection()).distinct().count());
        assertEquals(2, result.stream().map(r -> r.teacherName()).distinct().count());
        assertEquals(4, service.list(EMAIL).size());
    }

    @Test void studentCannotReadOtherStudentByInjectingUserIdAndDtoIsSafe() throws Exception {
        standardRow(upload);
        var foreign = upload("other@feedback.test"); standardRow(foreign);
        service.list("other@feedback.test");
        var response = mvc.perform(get(API).param("userId", foreign.getUser().getId().toString())
                .with(user(EMAIL).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uploadId").value(upload.getId()))
                .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("verifiedCourseId", "courseOfferingId", "uploadId", "sourceEntryId", "courseCode",
                "courseName", "teacherName", "groupSection", "academicTerm"), json.readTree(response).get(0).propertyNames());
        assertFalse(response.contains(upload.getStorageKey())); assertFalse(response.contains("unused"));
        assertEquals(foreign.getId(), service.list("other@feedback.test").getFirst().uploadId());
    }

    @Test void anonymousIs401() throws Exception { mvc.perform(get(API)).andExpect(status().isUnauthorized()); }
    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN"})
    void wrongRoleIs403(String role) throws Exception {
        mvc.perform(get(API).with(user("someone@feedback.test").roles(role))).andExpect(status().isForbidden());
    }
    @Test void multiRoleCanReadOwnMatches() throws Exception {
        var both = upload("both@feedback.test"); standardRow(both); standardRow(upload);
        mvc.perform(get(API).with(user("both@feedback.test").roles("STUDENT", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uploadId").value(both.getId()));
    }

    @Test void invalidSourceNeverVerifiedAndOldDerivedRowsAreRemoved() {
        standardRow(upload); assertEquals(1, service.list(EMAIL).size());
        upload.finishValidation(ScheduleUpload.ValidationReason.DETAILS_REQUIRED); uploads.saveAndFlush(upload);
        assertTrue(service.list(EMAIL).isEmpty()); assertEquals(0, verified.count());
        assertEquals(1, entries.count());
        assertEquals(0, pending.count());
    }

    @Test void newCatalogAmbiguityRemovesPreviouslyVerifiedMatch() {
        standardRow(upload); service.list(EMAIL);
        teachers.saveAndFlush(new Teacher("Morgan Rivera", null, 200L));
        assertTrue(service.list(EMAIL).isEmpty()); assertEquals(0, verified.count());
        assertEquals(1, entries.count());
        assertPending(Reason.AMBIGUOUS_TEACHER);
    }

    @Test void reExtractionClearsOldReferencesAndNewRowsCanBeVerified() {
        var old = standardRow(upload); service.list(EMAIL);
        doReturn(new SchedulePdfValidator.ValidatedText(null,
                "Schedule\n2026-2027 1 term\nDay/Hour\nABC 789 Experimental Design [04-N]\nMorgan Rivera : Room A\n"))
                .when(validator).readValidated(any());
        extraction.extract(upload.getId());
        assertEquals(0, verified.count()); assertFalse(entries.existsById(old.getId()));
        assertEquals(1, service.list(EMAIL).size());
        assertNotEquals(old.getId(), service.list(EMAIL).getFirst().sourceEntryId());
    }

    @Test void failedCurrentValidationClearsDerivedRowsAlongWithSourceRows() {
        standardRow(upload); service.list(EMAIL);
        doReturn(new SchedulePdfValidator.ValidatedText(ScheduleUpload.ValidationReason.DETAILS_REQUIRED, null))
                .when(validator).readValidated(any());
        assertNull(extraction.extract(upload.getId()));
        assertEquals(0, verified.count()); assertEquals(0, entries.count());
    }

    @Test void bothUnknownArePersistedAndUnchangedRetryIsIdempotent() {
        var source = unknownRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        var first = assertPending(Reason.COURSE_AND_TEACHER_NOT_FOUND);
        assertEquals(source.getId(), first.getSourceEntry().getId());
        assertTrue(service.list(EMAIL).isEmpty());
        var repeat = assertPending(Reason.COURSE_AND_TEACHER_NOT_FOUND);
        assertEquals(first.getId(), repeat.getId());
        assertEquals(first.getCreatedAt(), repeat.getCreatedAt());
        assertEquals(first.getUpdatedAt(), repeat.getUpdatedAt());
        assertEquals(1, courses.count()); assertEquals(1, teachers.count()); assertEquals(1, offerings.count());
    }

    @Test void pendingReasonChangesInPlaceThenResolvesWhenCatalogBecomesComplete() {
        unknownRow(upload); service.list(EMAIL);
        var first = assertPending(Reason.COURSE_AND_TEACHER_NOT_FOUND);
        // Model an older pending record: fast operations can share the same clock tick.
        var earlier = java.time.Instant.parse("2020-01-01T00:00:00Z");
        org.springframework.test.util.ReflectionTestUtils.setField(first, "createdAt", earlier);
        org.springframework.test.util.ReflectionTestUtils.setField(first, "updatedAt", earlier);
        pending.saveAndFlush(first);
        var newCourse = courses.saveAndFlush(new Course("XYZ 900", "Unknown Subject")); // Test-only catalog maintenance.
        service.list(EMAIL);
        var changed = assertPending(Reason.TEACHER_NOT_FOUND);
        assertEquals(first.getId(), changed.getId());
        assertEquals(first.getCreatedAt(), changed.getCreatedAt());
        assertTrue(changed.getUpdatedAt().isAfter(first.getUpdatedAt()));
        var newTeacher = teachers.saveAndFlush(new Teacher("Unknown Person", null));
        service.list(EMAIL); assertPending(Reason.NO_COURSE_OFFERING);
        offerings.saveAndFlush(new CourseOffering(newTeacher, newCourse, "Fall 2026", null));
        var result = service.list(EMAIL);
        assertEquals(1, result.size()); assertEquals(0, pending.count()); assertEquals(1, verified.count());
        assertEquals(result, service.list(EMAIL)); assertEquals(0, pending.count());
    }

    @Test void parallelRetriesDoNotAccumulatePendingRows() throws Exception {
        unknownRow(upload);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.list(EMAIL));
            var second = executor.submit(() -> service.list(EMAIL));
            assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS).isEmpty());
            assertTrue(second.get(10, java.util.concurrent.TimeUnit.SECONDS).isEmpty());
        }
        assertPending(Reason.COURSE_AND_TEACHER_NOT_FOUND);
    }

    @Test void pendingIsOwnerScopedAndNeverExposedByVerifiedApi() throws Exception {
        unknownRow(upload);
        var other = upload("other@feedback.test"); unknownRow(other);
        service.list(EMAIL);
        assertEquals(1, pending.findBySourceEntryScheduleUploadId(upload.getId()).size());
        assertTrue(pending.findBySourceEntryScheduleUploadId(other.getId()).isEmpty());
        var original = pending.findAll().getFirst();
        mvc.perform(get(API).param("userId", upload.getUser().getId().toString())
                .with(user("other@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(1, pending.findBySourceEntryScheduleUploadId(other.getId()).size());
        var untouched = pending.findById(original.getId()).orElseThrow();
        assertEquals(original.getUpdatedAt(), untouched.getUpdatedAt());
        assertEquals(2, pending.count()); assertEquals(0, verified.count());
    }

    @Test void pendingHasUniqueSourceAndForeignKeyProtection() {
        var source = unknownRow(upload); service.list(EMAIL);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                pending.saveAndFlush(new PendingCatalogVerification(source, Reason.COURSE_NOT_FOUND)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> entries.deleteById(source.getId()));
        assertEquals(1, entries.count()); assertEquals(1, pending.count());
    }

    @Test void reExtractionRemovesOldPendingReferences() {
        var old = unknownRow(upload); service.list(EMAIL);
        doReturn(new SchedulePdfValidator.ValidatedText(null,
                "Schedule\n2026-2027 1 term\nDay/Hour\nABC 789 Experimental Design [04-N]\nMorgan Rivera : Room A\n"))
                .when(validator).readValidated(any());
        extraction.extract(upload.getId());
        assertEquals(0, pending.count()); assertFalse(entries.existsById(old.getId()));
        assertEquals(1, service.list(EMAIL).size()); assertEquals(0, pending.count());
    }

    @Test void extractionFailureRollsBackPendingCleanup() {
        var source = unknownRow(upload); service.list(EMAIL);
        var original = pending.findAll().getFirst();
        doReturn(new SchedulePdfValidator.ValidatedText(null, "test text")).when(validator).readValidated(any());
        doThrow(new IllegalStateException("Synthetic parser failure")).when(parser).parse(anyString());
        assertThrows(IllegalStateException.class, () -> extraction.extract(upload.getId()));
        assertTrue(entries.existsById(source.getId()));
        assertEquals(original.getId(), pending.findAll().getFirst().getId());
        assertEquals(original.getUpdatedAt(), pending.findAll().getFirst().getUpdatedAt());
    }

    @Test void invalidUploadClearsPendingWithoutDeletingSource() {
        unknownRow(upload); service.list(EMAIL);
        upload.finishValidation(ScheduleUpload.ValidationReason.DETAILS_REQUIRED); uploads.saveAndFlush(upload);
        assertTrue(service.list(EMAIL).isEmpty());
        assertEquals(0, pending.count()); assertEquals(1, entries.count());
    }

    @Test void failedRevalidationClearsPendingBeforeDeletingSource() {
        unknownRow(upload); service.list(EMAIL);
        doReturn(new SchedulePdfValidator.ValidatedText(ScheduleUpload.ValidationReason.DETAILS_REQUIRED, null))
                .when(validator).readValidated(any());
        assertNull(extraction.extract(upload.getId()));
        assertEquals(0, pending.count()); assertEquals(0, entries.count());
    }

    private PendingCatalogVerification assertPending(Reason reason) {
        assertEquals(0, verified.count());
        assertEquals(1, pending.count());
        var value = pending.findAll().getFirst();
        assertEquals(reason, value.getReason());
        return value;
    }
    private ExtractedScheduleEntry unknownRow(ScheduleUpload owner) {
        return row(owner, "XYZ 900", "Unknown Subject", "Unknown Person", "04-N", "2026-2027 1 term");
    }

    private ScheduleUpload upload(String email) {
        var value = new ScheduleUpload(users.findByEmail(email).orElseThrow(), "Schedule.pdf", UUID.randomUUID() + ".pdf");
        value.finishValidation(null); return uploads.saveAndFlush(value);
    }
    private ExtractedScheduleEntry standardRow(ScheduleUpload upload) {
        return row(upload, "ABC 789", "Experimental Design", "Morgan Rivera", "04-N", "2026-2027 1 term");
    }
    private ExtractedScheduleEntry row(ScheduleUpload upload, String code, String name, String teacher, String group, String term) {
        return entries.saveAndFlush(new ExtractedScheduleEntry(upload, code, name, teacher, group, term));
    }
}
