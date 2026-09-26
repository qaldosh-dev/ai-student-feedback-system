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
    @Autowired VerifiedCourseService service;
    @Autowired ScheduleExtractionService extraction;
    @MockitoSpyBean SchedulePdfValidator validator;
    private ScheduleUpload upload;
    private Course course;
    private Teacher teacher;
    private CourseOffering offering;
    private static final String EMAIL = "student@feedback.test";
    private static final String API = "/api/student/verified-courses";

    @BeforeEach void setup() {
        verified.deleteAll(); entries.deleteAll(); uploads.deleteAll();
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
    }

    @Test void unrelatedOfferingDoesNotVerifyExistingTeacherAndCourse() {
        offerings.deleteAll();
        var different = courses.saveAndFlush(new Course("XYZ 900", "Other Subject"));
        offerings.saveAndFlush(new CourseOffering(teacher, different, "Fall 2026", null));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
    }

    @Test void ambiguousTeacherFailsClosedEvenIfOnlyOneHasOffering() {
        teachers.saveAndFlush(new Teacher("  MORGAN  Rivera ", null, 123L));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
    }

    @Test void multipleSourceOfferingsAreNotArbitrarilyChosen() {
        offerings.saveAndFlush(new CourseOffering(teacher, course, "Fall 2026", null, 101L));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
    }

    @Test void normalizedCourseCodeAmbiguityFailsClosed() {
        courses.saveAndFlush(new Course("abc 789", "Experimental Design"));
        standardRow(upload);
        assertTrue(service.list(EMAIL).isEmpty());
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
    }

    @Test void newCatalogAmbiguityRemovesPreviouslyVerifiedMatch() {
        standardRow(upload); service.list(EMAIL);
        teachers.saveAndFlush(new Teacher("Morgan Rivera", null, 200L));
        assertTrue(service.list(EMAIL).isEmpty()); assertEquals(0, verified.count());
        assertEquals(1, entries.count());
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
