package kz.sdu.feedback.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.repository.*;
import kz.sdu.feedback.schedule.service.SchedulePdfValidator;
import kz.sdu.feedback.schedule.service.ScheduleParser;
import kz.sdu.feedback.schedule.service.VerifiedCourseService;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import org.junit.jupiter.api.AfterEach;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:schedule-reads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ScheduleReadTest {
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
    @MockitoSpyBean VerifiedCourseService verification;
    @MockitoSpyBean SchedulePdfValidator validator;
    @MockitoSpyBean ScheduleParser parser;
    private ScheduleUpload current, past, other;
    private CourseOffering offering;

    @BeforeEach void setup() {
        pending.deleteAll(); verified.deleteAll(); entries.deleteAll(); uploads.deleteAll(); users.deleteAll();
        offerings.deleteAll(); courses.deleteAll(); teachers.deleteAll(); offering = null;
        for (var role : UserRole.values()) {
            users.saveAndFlush(new User(role.name() + "@feedback.test", "unused-hash", Set.of(role), true, null));
        }
        users.saveAndFlush(new User("other@feedback.test", "unused-hash", Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("both@feedback.test", "unused-hash", Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        current = upload("student@feedback.test", "new.pdf", "2026-09-25T12:00:00Z");
        past = upload("student@feedback.test", "past.pdf", "2026-09-24T12:00:00Z");
        other = upload("other@feedback.test", "private-other.pdf", "2026-09-26T12:00:00Z");
        row(current, "ZZZ 900", "Zed Subject", "Zed Person", "03-P", "2026-2027 1 term");
        row(current, "ABC 100", "Alpha Subject", "Zed Person", "04-N", "2026-2027 1 term");
        row(current, "ABC 100", "Alpha Subject", "Alex Person", "15-P", "2026-2027 1 term");
        row(current, "ABC 100", "Alpha Subject", "Alex Person", "04-N", "2026-2027 1 term");
        row(past, "ABC 100", "Alpha Subject", "Past Person", "01-N", "2025-2026 2 term");
        row(other, "PRIVATE 999", "Private Subject", "Private Person", "01-N", "2026-2027 1 term");
    }

    @AfterEach void noMatchingOrParsingDuringReads() {
        verify(verification, never()).list(anyString());
        verifyNoInteractions(validator, parser);
    }

    @Test void listsOnlyOwnUploadsNewestFirstIgnoringInjectedUserId() throws Exception {
        mvc.perform(get("/api/student/schedule-uploads").param("userId", other.getUser().getId().toString())
                .with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(current.getId()))
                .andExpect(jsonPath("$[1].id").value(past.getId()))
                .andExpect(jsonPath("$[0].extractedCount").value(4))
                .andExpect(jsonPath("$[1].academicTerms[0]").value("2025-2026 2 term"));
    }

    @Test void ownDtoIsSafeAndRowsHaveDeterministicOrderWithoutMerging() throws Exception {
        var response = mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].teacherName").value("Alex Person"))
                .andExpect(jsonPath("$.entries[0].groupSection").value("04-N"))
                .andExpect(jsonPath("$.entries[1].groupSection").value("15-P"))
                .andExpect(jsonPath("$.entries[2].teacherName").value("Zed Person"))
                .andExpect(jsonPath("$.entries[3].courseCode").value("ZZZ 900"))
                .andExpect(jsonPath("$.upload.academicTerms[0]").value("2026-2027 1 term"))
                .andReturn().getResponse().getContentAsString();
        var tree = json.readTree(response);
        assertEquals(Set.of("upload", "entries"), tree.propertyNames());
        assertEquals(Set.of("id", "originalFilename", "uploadedAt", "status", "validationMessage", "academicTerms", "extractedCount"), tree.get("upload").propertyNames());
        assertEquals(Set.of("id", "courseCode", "courseName", "teacherName", "groupSection", "academicTerm",
                "verificationStatus", "verificationReason"), tree.get("entries").get(0).propertyNames());
        assertEquals("NOT_CHECKED", tree.get("entries").get(0).get("verificationStatus").asText());
        assertTrue(tree.get("entries").get(0).get("verificationReason").isNull());
        assertFalse(response.contains(current.getStorageKey()));
        assertFalse(response.contains("unused-hash"));
        assertFalse(response.contains("Private Person"));
        verifyNoInteractions(validator, parser);
    }

    @Test void foreignAndMissingUploadReturnSame404() throws Exception {
        pending.saveAndFlush(new PendingCatalogVerification(entries.findForDisplay(other.getId()).getFirst(),
                PendingCatalogVerification.Reason.COURSE_AND_TEACHER_NOT_FOUND));
        String foreign = mvc.perform(get(path(other)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/student/schedule-uploads/999999/extracted").with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, foreign);
    }

    @Test void anonymousCannotListOrRead() throws Exception {
        mvc.perform(get("/api/student/schedule-uploads")).andExpect(status().isUnauthorized());
        mvc.perform(get(path(current))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN"})
    void nonStudentCannotListOrRead(String role) throws Exception {
        mvc.perform(get("/api/student/schedule-uploads").with(user(role.toLowerCase() + "@feedback.test").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(current)).with(user(role.toLowerCase() + "@feedback.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test void multiRoleStudentCanReadOwnUpload() throws Exception {
        var owned = upload("both@feedback.test", "both.pdf", "2026-09-25T12:00:00Z");
        saveVerified(row(owned, "ABC 100", "Alpha Subject", "Alex Person", "01-N", "2026-2027 1 term"));
        mvc.perform(get(path(owned)).with(user("both@feedback.test").roles("STUDENT", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].verificationStatus").value("VERIFIED"));
        mvc.perform(get("/api/student/schedule-uploads").with(user("both@feedback.test").roles("STUDENT", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void pastSemesterRemainsSeparateAndViewable() throws Exception {
        mvc.perform(get(path(past)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].academicTerm").value("2025-2026 2 term"))
                .andExpect(jsonPath("$.entries[0].teacherName").value("Past Person"));
    }

    @ParameterizedTest @EnumSource(value = ScheduleUpload.Status.class, names = "VALID", mode = EnumSource.Mode.EXCLUDE)
    void nonValidUploadNeverExposesEvenStaleRows(ScheduleUpload.Status state) throws Exception {
        var source = entries.findForDisplay(current.getId());
        saveVerified(source.get(0));
        pending.saveAndFlush(new PendingCatalogVerification(source.get(1), PendingCatalogVerification.Reason.TEACHER_NOT_FOUND));
        ReflectionTestUtils.setField(current, "status", state);
        if (state == ScheduleUpload.Status.INVALID) current.finishValidation(ScheduleUpload.ValidationReason.DETAILS_REQUIRED);
        uploads.saveAndFlush(current);
        var result = mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.upload.academicTerms").isEmpty())
                .andExpect(jsonPath("$.upload.extractedCount").value(0));
        if (state == ScheduleUpload.Status.INVALID) result.andExpect(jsonPath("$.upload.validationMessage")
                .value(ScheduleUpload.ValidationReason.DETAILS_REQUIRED.message()));
        assertEquals(4, entries.findForDisplay(current.getId()).size()); // GET did not mutate them.
        assertEquals(1, verified.count()); assertEquals(1, pending.count());
        verifyNoInteractions(validator, parser);
    }

    @Test void legacyValidWithoutEntriesIsNeutralAndReadOnly() throws Exception {
        entries.deleteAll(entries.findForDisplay(current.getId()));
        mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upload.status").value("VALID"))
                .andExpect(jsonPath("$.entries").isEmpty());
        verifyNoInteractions(validator, parser);
    }

    @Test void emptyListAndSameTimestampOrderAreStable() throws Exception {
        mvc.perform(get("/api/student/schedule-uploads").with(user("both@feedback.test").roles("STUDENT", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        var laterId = upload("student@feedback.test", "same-time.pdf", "2026-09-25T12:00:00Z");
        mvc.perform(get("/api/student/schedule-uploads").with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(laterId.getId()));
    }

    @Test void mixedPersistedOutcomesAreReadWithoutMutationOrReverification() throws Exception {
        var source = entries.findForDisplay(current.getId());
        var match = saveVerified(source.get(0));
        var unresolved = pending.saveAndFlush(new PendingCatalogVerification(source.get(1), PendingCatalogVerification.Reason.TEACHER_NOT_FOUND));
        pending.saveAndFlush(new PendingCatalogVerification(source.get(3), PendingCatalogVerification.Reason.COURSE_DATA_CONFLICT));
        // Compare database timestamps on both sides; DB precision can round the original Java Instant.
        var matchCreated = verified.findById(match.getId()).orElseThrow().getCreatedAt();
        var pendingUpdated = pending.findById(unresolved.getId()).orElseThrow().getUpdatedAt();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.upload.status").value("VALID"))
                    .andExpect(jsonPath("$.entries[0].verificationStatus").value("VERIFIED"))
                    .andExpect(jsonPath("$.entries[0].verificationReason").isEmpty())
                    .andExpect(jsonPath("$.entries[1].verificationStatus").value("PENDING"))
                    .andExpect(jsonPath("$.entries[1].verificationReason").value("TEACHER_NOT_FOUND"))
                    .andExpect(jsonPath("$.entries[2].verificationStatus").value("NOT_CHECKED"))
                    .andExpect(jsonPath("$.entries[2].verificationReason").isEmpty())
                    .andExpect(jsonPath("$.entries[3].verificationStatus").value("PENDING"));
        }
        assertEquals(1, verified.count()); assertEquals(2, pending.count()); assertEquals(6, entries.count());
        assertEquals(matchCreated, verified.findById(match.getId()).orElseThrow().getCreatedAt());
        assertEquals(pendingUpdated, pending.findById(unresolved.getId()).orElseThrow().getUpdatedAt());
        assertEquals(PendingCatalogVerification.Reason.TEACHER_NOT_FOUND, pending.findById(unresolved.getId()).orElseThrow().getReason());
    }

    @ParameterizedTest @EnumSource(PendingCatalogVerification.Reason.class)
    void everyPersistedReasonIsReturnedAsPending(PendingCatalogVerification.Reason reason) throws Exception {
        pending.saveAndFlush(new PendingCatalogVerification(entries.findForDisplay(current.getId()).getFirst(), reason));
        mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.entries[0].verificationReason").value(reason.name()));
    }

    @Test void defensiveVerifiedPrecedenceNeverDisplaysBothOutcomesAndDoesNotRepairOnRead() throws Exception {
        var source = entries.findForDisplay(current.getId()).getFirst();
        saveVerified(source);
        // Deliberately inconsistent test-only data; normal US18 processing prohibits this combination.
        pending.saveAndFlush(new PendingCatalogVerification(source, PendingCatalogVerification.Reason.AMBIGUOUS_TEACHER));
        mvc.perform(get(path(current)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.entries[0].verificationReason").isEmpty());
        assertEquals(1, verified.count()); assertEquals(1, pending.count());
    }

    @Test void tenExtractedPersistedMatchesReturnTenVerifiedRows() throws Exception {
        var ten = upload("student@feedback.test", "ten.pdf", "2026-09-26T12:00:00Z");
        for (int group = 1; group <= 10; group++) {
            saveVerified(row(ten, "ABC 100", "Alpha Subject", "Alex Person", group + "-N", "2026-2027 1 term"));
        }
        var response = mvc.perform(get(path(ten)).with(user("student@feedback.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        for (var entry : json.readTree(response).get("entries")) {
            assertEquals("VERIFIED", entry.get("verificationStatus").asText());
            assertTrue(entry.get("verificationReason").isNull());
        }
        assertEquals(10, verified.count()); assertEquals(0, pending.count());
    }

    private VerifiedCourse saveVerified(ExtractedScheduleEntry source) {
        if (offering == null) {
            var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject"));
            var teacher = teachers.saveAndFlush(new Teacher("Alex Person", null));
            offering = offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null));
        }
        return verified.saveAndFlush(new VerifiedCourse(source, offering));
    }

    private ScheduleUpload upload(String email, String filename, String time) {
        var upload = new ScheduleUpload(users.findByEmail(email).orElseThrow(), filename, UUID.randomUUID() + ".pdf");
        upload.finishValidation(null);
        ReflectionTestUtils.setField(upload, "uploadedAt", Instant.parse(time));
        return uploads.saveAndFlush(upload);
    }
    private ExtractedScheduleEntry row(ScheduleUpload upload, String code, String name, String teacher, String group, String term) {
        return entries.saveAndFlush(new ExtractedScheduleEntry(upload, code, name, teacher, group, term));
    }
    private String path(ScheduleUpload upload) { return "/api/student/schedule-uploads/" + upload.getId() + "/extracted"; }
}
