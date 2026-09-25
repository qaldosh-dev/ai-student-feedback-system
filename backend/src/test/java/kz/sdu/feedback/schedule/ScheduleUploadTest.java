package kz.sdu.feedback.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.schedule.repository.ScheduleUploadRepository;
import kz.sdu.feedback.schedule.service.ScheduleFileStorage;
import kz.sdu.feedback.schedule.service.ScheduleUploadService;
import kz.sdu.feedback.schedule.service.SchedulePdfValidator;
import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import kz.sdu.feedback.schedule.repository.ExtractedScheduleEntryRepository;
import kz.sdu.feedback.schedule.service.ScheduleExtractionService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:schedule-uploads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class ScheduleUploadTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("app.schedule-upload.directory", () -> directory.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ScheduleUploadRepository uploads;
    @Autowired ExtractedScheduleEntryRepository extracted;
    @Autowired ScheduleExtractionService extraction;
    @Autowired ScheduleUploadService service;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean SchedulePdfValidator validator;

    @BeforeEach void fixtures() throws Exception {
        extracted.deleteAll();
        uploads.deleteAll();
        users.deleteAll();
        try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
        for (var role : UserRole.values()) {
            users.saveAndFlush(new User(role.name() + "@feedback.test", "unused-test-hash", Set.of(role), true, null));
        }
        users.saveAndFlush(new User("both@feedback.test", "unused-test-hash", Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        users.saveAndFlush(new User("unverified@feedback.test", "unused-test-hash", Set.of(UserRole.STUDENT)));
    }

    @Test void anonymousWithCsrfIs401() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf")).with(csrf().asHeader()))
                .andExpect(status().isUnauthorized());
        assertEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN"})
    void nonStudentsAre403(String role) throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf"))
                .with(user(role.toLowerCase() + "@feedback.test").roles(role)).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        assertEmpty();
    }

    @Test void rolelessIs403() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf"))
                .with(user("roleless@sdu.edu.kz").roles()).with(csrf().asHeader())).andExpect(status().isForbidden());
        assertEmpty();
    }

    @Test void noCsrfIs403() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf"))
                .with(user("student@feedback.test").roles("STUDENT"))).andExpect(status().isForbidden());
        assertEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"student", "both"})
    void allowedRolesSaveSafeMetadataAndExactBytesOwnedBySession(String account) throws Exception {
        var result = mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf"))
                .with(user(account + "@feedback.test").roles(account.equals("both")
                        ? new String[]{"STUDENT", "TEACHER"} : new String[]{"STUDENT"})).with(csrf().asHeader()))
                .andExpect(status().isCreated()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(Set.of("id", "originalFilename", "uploadedAt", "status", "validationMessage", "validatedAt", "extraction"), body.propertyNames());
        assertEquals(1, body.get("extraction").get("extractedCount").asInt());
        assertEquals("VALID", body.get("status").asString());
        assertTrue(body.get("validationMessage").isNull());
        assertNotNull(java.time.Instant.parse(body.get("validatedAt").asString()));
        assertEquals("Schedule.pdf", body.get("originalFilename").asString());
        assertNotNull(java.time.Instant.parse(body.get("uploadedAt").asString()));
        var saved = uploads.findById(body.get("id").asLong()).orElseThrow();
        assertEquals(users.findByEmail(account + "@feedback.test").orElseThrow().getId(), saved.getUser().getId());
        assertTrue(saved.getStorageKey().matches("[0-9a-f-]{36}\\.pdf"));
        assertArrayEquals(scheduleBytes, Files.readAllBytes(directory.resolve(saved.getStorageKey())));
        assertEquals(1, uploads.count());
        var entry = extracted.findByScheduleUploadIdOrderById(saved.getId()).getFirst();
        assertEquals(saved.getId(), entry.getScheduleUpload().getId());
        assertEquals("Morgan Rivera", entry.getTeacherName());
        mvc.perform(get("/schedule-uploads/" + saved.getStorageKey())).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"userId", "email", "studentId", "status", "validationReason", "validationMessage", "storageKey", "storagePath"})
    void rejectsClientOwnershipFields(String field) throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf")).param(field, "999")
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test void missingFileIs400() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").with(user("student@feedback.test").roles("STUDENT"))
                .with(csrf().asHeader())).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test void emptyFileIs400() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(new MockMultipartFile("file", "Schedule.pdf", "application/pdf", new byte[0]))
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test void multipleFilesAre400() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("one.pdf")).file(pdf("two.pdf"))
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test void oversizedFileIs413() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads")
                .file(new MockMultipartFile("file", "Schedule.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]))
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().is(413));
        assertEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"../../outside.pdf", "C:\\fakepath\\outside.pdf"})
    void untrustedFilenameNeverBecomesStoragePath(String name) throws Exception {
        var result = service.upload("student@feedback.test", pdf(name));
        assertEquals("outside.pdf", result.originalFilename());
        var saved = uploads.findById(result.id()).orElseThrow();
        assertNotEquals(name, saved.getStorageKey());
        assertTrue(Files.isRegularFile(directory.resolve(saved.getStorageKey())));
        assertFalse(Files.exists(directory.resolve("outside.pdf")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @ParameterizedTest @ValueSource(strings = {"schedule.exe", ".pdf", "bad\nname.pdf"})
    void invalidFilenameIsRejected(String filename) throws Exception {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.upload("student@feedback.test", pdf(filename))).getStatusCode().value());
        assertEmpty();
    }

    @Test void rollbackRemovesStoredFileAndMetadata() throws Exception {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.upload("student@feedback.test", pdf("Schedule.pdf"));
            throw new IllegalStateException("Simulated database rollback");
        }));
        assertEmpty();
    }

    @Test void unavailableStorageFailsSafely() throws Exception {
        Path blocked = Files.createFile(directory.resolve("not-a-directory"));
        var storage = new ScheduleFileStorage(blocked.toString(), DataSize.ofMegabytes(10));
        var exception = assertThrows(ResponseStatusException.class, () -> storage.store(pdf("Schedule.pdf")));
        assertEquals(503, exception.getStatusCode().value());
        assertFalse(exception.getReason().contains(directory.toString()));
        assertEquals(0, uploads.count());
    }

    @Test void staleUnverifiedSessionCannotUpload() throws Exception {
        mvc.perform(multipart("/api/student/schedule-uploads").file(pdf("Schedule.pdf"))
                .with(user("unverified@feedback.test").roles("STUDENT")).with(csrf().asHeader())).andExpect(status().isForbidden());
        assertEmpty();
    }

    @Test void invalidUploadIsKeptWithoutExposingTextOrPaths() throws Exception {
        var file = new MockMultipartFile("file", "Schedule.pdf", "application/pdf",
                SchedulePdfFixtures.pdf("Private unrelated document with a confidential marker."));
        var result = mvc.perform(multipart("/api/student/schedule-uploads").file(file)
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID")).andReturn();
        String response = result.getResponse().getContentAsString();
        assertFalse(response.contains("confidential marker"));
        assertFalse(response.contains(directory.toString()));
        var saved = uploads.findAll().getFirst();
        assertEquals(ScheduleUpload.ValidationReason.NOT_SCHEDULE, saved.getValidationReason());
        assertTrue(Files.isRegularFile(directory.resolve(saved.getStorageKey())));
        assertEquals(1, uploads.count());
    }

    @Test void fakePdfIsSavedAsInvalidNotDeleted() {
        var result = service.upload("student@feedback.test", new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[]{1,2,3}));
        assertEquals(ScheduleUpload.Status.INVALID, result.status());
        assertEquals(ScheduleUpload.ValidationReason.INVALID_PDF, uploads.findById(result.id()).orElseThrow().getValidationReason());
    }

    @Test void compactScheduleKeepsFileAndReturnsDetailedVersionInstruction() throws Exception {
        var file = new MockMultipartFile("file", "Schedule.pdf", "application/pdf",
                SchedulePdfFixtures.pdf("Schedule\n2042-2043 1 term\nDay/Hour\nQRS 456 [21-X] : Z 900"));
        mvc.perform(multipart("/api/student/schedule-uploads").file(file)
                .with(user("student@feedback.test").roles("STUDENT")).with(csrf().asHeader()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.validationMessage").value(ScheduleUpload.ValidationReason.DETAILS_REQUIRED.message()))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
        var saved = uploads.findAll().getFirst();
        assertEquals(ScheduleUpload.ValidationReason.DETAILS_REQUIRED, saved.getValidationReason());
        assertNotNull(saved.getValidatedAt());
        assertTrue(Files.isRegularFile(directory.resolve(saved.getStorageKey())));
    }

    @Test void unexpectedValidationFailureKeepsUploadedRecordAndFile() {
        doThrow(new IllegalStateException("private diagnostic must not leak")).when(validator).validate(org.mockito.ArgumentMatchers.any());
        var result = service.upload("student@feedback.test", pdf("Schedule.pdf"));
        assertEquals(ScheduleUpload.Status.UPLOADED, result.status());
        assertNull(result.validatedAt());
        assertFalse(result.validationMessage().contains("private diagnostic"));
        assertTrue(Files.isRegularFile(directory.resolve(uploads.findById(result.id()).orElseThrow().getStorageKey())));
    }

    @Test void validationChangesOnlyNewUpload() {
        var first = service.upload("student@feedback.test", pdf("Schedule.pdf"));
        var second = service.upload("both@feedback.test", new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[]{1}));
        assertEquals(ScheduleUpload.Status.VALID, uploads.findById(first.id()).orElseThrow().getStatus());
        assertEquals(ScheduleUpload.Status.INVALID, uploads.findById(second.id()).orElseThrow().getStatus());
        assertNotEquals(uploads.findById(first.id()).orElseThrow().getUser().getId(), uploads.findById(second.id()).orElseThrow().getUser().getId());
    }

    @Test void legacyValidCompactIsRevalidatedAndCannotProduceRows() throws Exception {
        String key = java.util.UUID.randomUUID() + ".pdf";
        Files.write(directory.resolve(key), SchedulePdfFixtures.pdf("Schedule\n2038-2039 2 term\nDay/Hour\nABC 789 [04-N] : R 300"));
        var upload = new ScheduleUpload(users.findByEmail("student@feedback.test").orElseThrow(), "old.pdf", key);
        upload.finishValidation(null); // Simulate a legacy US14 decision.
        uploads.saveAndFlush(upload);
        assertNull(extraction.extract(upload.getId()));
        assertEquals(ScheduleUpload.ValidationReason.DETAILS_REQUIRED, uploads.findById(upload.getId()).orElseThrow().getValidationReason());
        assertEquals(0, extracted.count());
    }

    @Test void nonValidUploadCannotBeExtracted() {
        var upload = uploads.saveAndFlush(new ScheduleUpload(users.findByEmail("student@feedback.test").orElseThrow(),
                "pending.pdf", java.util.UUID.randomUUID() + ".pdf"));
        assertThrows(IllegalStateException.class, () -> extraction.extract(upload.getId()));
        assertEquals(0, extracted.count());
    }

    @Test void repeatExtractionDoesNotDuplicateAndUploadsStaySeparate() {
        var first = service.upload("student@feedback.test", pdf("first.pdf"));
        extraction.extract(first.id());
        assertEquals(1, extracted.count());
        var second = service.upload("both@feedback.test", pdf("second.pdf"));
        assertEquals(2, extracted.count());
        assertEquals(1, extracted.findByScheduleUploadIdOrderById(first.id()).size());
        assertEquals(1, extracted.findByScheduleUploadIdOrderById(second.id()).size());
    }

    @Test void multiPageBestEffortPersistsCompleteEntriesAndReportsCounts() throws Exception {
        var bytes = SchedulePdfFixtures.pdf("Schedule\n2038-2039 2 term\nDay/Hour\nABC 789 Course One [01-N]\nMorgan Rivera : Room A 300",
                "ABC 789 Course One [01-N]\nMorgan Rivera : Room A 300\nABC 789 Course One [15-P]\nJamie Quinn : Room A 300\nBAD 123 Missing teacher [01-N] : R 300");
        var result = service.upload("student@feedback.test", new MockMultipartFile("file", "pages.pdf", "application/pdf", bytes));
        assertEquals(new kz.sdu.feedback.schedule.service.ScheduleParser.Summary(4, 2, 1, 1), result.extraction());
        assertEquals(2, extracted.count());
    }

    @Test void persistedEntryModelContainsNoTimetableOrCatalogRelations() {
        var fields = java.util.Arrays.stream(kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("id", "scheduleUpload", "courseCode", "courseName", "teacherName", "groupSection", "academicTerm"), fields);
    }

    private static final byte[] scheduleBytes = createSchedule();
    private static byte[] createSchedule() {
        try { return SchedulePdfFixtures.schedule(); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", scheduleBytes);
    }
    private void assertEmpty() throws Exception {
        assertEquals(0, extracted.count());
        assertEquals(0, uploads.count());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}

