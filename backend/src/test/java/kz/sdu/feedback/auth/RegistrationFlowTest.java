package kz.sdu.feedback.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.auth.repository.EmailVerificationRepository;
import kz.sdu.feedback.auth.service.AccountDetailsService;
import kz.sdu.feedback.auth.service.RegistrationService;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false",
        "spring.mail.host=test.invalid", "app.verification.from=sender@example.test"
})
@AutoConfigureMockMvc
class RegistrationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository challenges;
    @Autowired PasswordEncoder encoder;
    @Autowired RegistrationService registration;
    @Autowired AccountDetailsService accounts;
    @MockitoBean JavaMailSender mail;
    @MockitoBean Clock clock;
    private final List<SimpleMailMessage> delivered = new CopyOnWriteArrayList<>();
    private final String password = UUID.randomUUID().toString();
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private static final String STUDENT = "student@stu.sdu.edu.kz";
    private static final String STAFF = "staff@sdu.edu.kz";

    @BeforeEach void prepare() {
        challenges.deleteAll();
        users.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        doAnswer(invocation -> { delivered.add(new SimpleMailMessage(invocation.getArgument(0))); return null; })
                .when(mail).send(any(SimpleMailMessage.class));
    }

    @ParameterizedTest @ValueSource(strings = {STUDENT, STAFF})
    void registrationCreatesOnlyUnverifiedAccountAndSendsCode(String email) throws Exception {
        var response = postJson("register", Map.of("email", "  " + email.toUpperCase(Locale.ROOT) + "  ", "password", password), 202);
        assertEquals(Set.of("message"), json.readTree(response.getResponse().getContentAsString()).propertyNames());
        var user = users.findByEmail(email).orElseThrow();
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertNotEquals(password, user.getPasswordHash());
        var safe = accounts.currentUser(email);
        assertFalse(safe.emailVerified());
        assertTrue(safe.roles().isEmpty());
        assertNull(safe.linkedTeacher());
        var challenge = challenges.findByUserId(user.getId()).orElseThrow();
        String code = code();
        assertTrue(code.matches("[0-9]{6}"));
        assertNotEquals(code, challenge.getCodeHash());
        assertTrue(encoder.matches(code, challenge.getCodeHash()));
        assertEquals(NOW.plusSeconds(600), challenge.getExpiresAt());
        assertEquals(0, challenge.getAttempts());
        assertArrayEquals(new String[]{email}, delivered.getFirst().getTo());
        assertEquals("Verify your AI Student Feedback account", delivered.getFirst().getSubject());
        assertFalse(delivered.getFirst().getText().contains(password));
        assertFalse(response.getResponse().getContentAsString().contains(code));
        postJson("login", Map.of("email", email, "password", password), 401);
    }

    @ParameterizedTest @ValueSource(strings = {"role", "roles", "linkedTeacher", "emailVerified", "admin"})
    void clientCannotSupplyPrivilegedFields(String field) throws Exception {
        postJson("register", Map.of("email", STUDENT, "password", password, field, "ADMIN"), 400);
        assertEquals(0, users.count());
        assertEquals(0, challenges.count());
        assertTrue(delivered.isEmpty());
    }

    @Test void invalidDomainOrPasswordDoesNotCreateOrSend() throws Exception {
        postJson("register", Map.of("email", "new@feedback.test", "password", password), 400);
        postJson("register", Map.of("email", STUDENT, "password", "short"), 400);
        assertEquals(0, users.count());
        assertTrue(delivered.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"        ", " \t\n\r    ", "\u2003\u2003\u2003\u2003\u2003\u2003\u2003\u2003"})
    void blankPasswordRejectedWithoutAccountOrEmail(String blank) throws Exception {
        postJson("register", Map.of("email", STUDENT, "password", blank), 400);
        assertEquals(0, users.count()); assertEquals(0, challenges.count()); assertTrue(delivered.isEmpty());
    }

    @Test void significantPasswordSpacesSurviveRegistrationVerificationAndLogin() throws Exception {
        String spaced = "  valid password  ";
        postJson("register", Map.of("email", STUDENT, "password", spaced), 202);
        assertTrue(encoder.matches(spaced, users.findByEmail(STUDENT).orElseThrow().getPasswordHash()));
        verifyCode(STUDENT, code(), 200);
        postJson("login", Map.of("email", STUDENT, "password", spaced), 200);
        postJson("login", Map.of("email", STUDENT, "password", spaced.strip()), 401);
    }

    @Test void duplicateRegistrationDoesNotOverwriteOrResend() throws Exception {
        register(STUDENT);
        var before = users.findByEmail(STUDENT).orElseThrow();
        String hash = before.getPasswordHash();
        postJson("register", Map.of("email", STUDENT, "password", UUID.randomUUID().toString()), 202);
        assertEquals(1, users.count());
        assertEquals(1, challenges.count());
        assertEquals(1, delivered.size());
        assertEquals(hash, users.findByEmail(STUDENT).orElseThrow().getPasswordHash());
        verifyCode(STUDENT, code(), 200);
        register(STUDENT);
        assertEquals(1, users.count());
        assertEquals(1, delivered.size());
    }

    @ParameterizedTest @ValueSource(strings = {STUDENT, STAFF})
    void verificationDoesNotAssignRolesAndRolelessLoginWorks(String email) throws Exception {
        register(email);
        String code = code();
        var response = verifyCode(email, code, 200);
        var body = json.readTree(response.getResponse().getContentAsString());
        assertEquals(Set.of("verified"), body.propertyNames());
        assertTrue(body.get("verified").asBoolean());
        var user = accounts.currentUser(email);
        assertTrue(user.emailVerified());
        assertTrue(user.roles().isEmpty());
        assertNull(user.linkedTeacher());
        assertEquals(NOW, challenges.findByUserId(user.id()).orElseThrow().getUsedAt());
        mvc.perform(get("/api/auth/me").session(session(response))).andExpect(status().isUnauthorized());
        verifyCode(email, code, 400);
        var login = postJson("login", Map.of("email", email, "password", password), 200);
        var signedIn = session(login);
        mvc.perform(get("/api/auth/me").session(signedIn)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.linkedTeacher").isEmpty());
        for (String area : List.of("student", "teacher", "admin")) {
            mvc.perform(get("/api/" + area + "/home").session(signedIn))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void verificationPreservesExistingRolesAndVerifiedAccounts() throws Exception {
        var existingRoles = Set.of(UserRole.STUDENT, UserRole.TEACHER);
        users.saveAndFlush(new User(STUDENT, encoder.encode(password), existingRoles));
        registration.resend(STUDENT);
        verifyCode(STUDENT, code(), 200);
        assertEquals(existingRoles, accounts.currentUser(STUDENT).roles());
        verifyCode(STUDENT, code(), 400);
        register(STUDENT);
        registration.resend(STUDENT);
        assertEquals(existingRoles, accounts.currentUser(STUDENT).roles());
        assertEquals(1, delivered.size());
    }

    @Test void wrongAndMalformedCodesConsumeAttemptsAndLockAtFive() throws Exception {
        register(STUDENT);
        String correct = code();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 5; i++) {
            verifyCode(STUDENT, i == 1 ? "bad" : wrong, 400);
            assertEquals(i, challenge().getAttempts());
        }
        verifyCode(STUDENT, correct, 400);
        assertFalse(accounts.currentUser(STUDENT).emailVerified());
        assertEquals(5, challenge().getAttempts());
    }

    @Test void expiryBoundaryRejectsCorrectCode() throws Exception {
        register(STUDENT);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        verifyCode(STUDENT, code(), 400);
        assertFalse(accounts.currentUser(STUDENT).emailVerified());
    }

    @Test void resendCooldownReplacesOnlyLatestCode() throws Exception {
        register(STUDENT);
        String old = code();
        postJson("resend-verification", Map.of("email", STUDENT), 202);
        when(clock.instant()).thenReturn(NOW.plusSeconds(59));
        postJson("resend-verification", Map.of("email", STUDENT), 202);
        assertEquals(1, delivered.size());
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        postJson("resend-verification", Map.of("email", STUDENT), 202);
        assertEquals(2, delivered.size());
        assertNotEquals(old, code());
        assertEquals(1, challenges.count());
        verifyCode(STUDENT, old, 400);
        verifyCode(STUDENT, code(), 200);
    }

    @Test void unknownAndVerifiedResendsAreSafeNoOps() throws Exception {
        var unknown = postJson("resend-verification", Map.of("email", STUDENT), 202);
        assertTrue(delivered.isEmpty());
        register(STUDENT);
        verifyCode(STUDENT, code(), 200);
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        var verified = postJson("resend-verification", Map.of("email", STUDENT), 202);
        assertEquals(unknown.getResponse().getContentAsString(), verified.getResponse().getContentAsString());
        assertEquals(1, delivered.size());
    }

    @Test void smtpFailureRollsBackRegistrationAndDoesNotExposeMailContents() throws Exception {
        doThrow(new MailSendException("private mail details must not escape")).when(mail).send(any(SimpleMailMessage.class));
        var response = postJson("register", Map.of("email", STUDENT, "password", password), 503);
        assertFalse(response.getResponse().getContentAsString().contains("private mail"));
        assertEquals(0, users.count());
        assertEquals(0, challenges.count());
    }

    @Test void failedResendPreservesPreviousChallenge() throws Exception {
        register(STUDENT);
        String original = code();
        String hash = challenge().getCodeHash();
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        doThrow(new MailSendException("send failed")).when(mail).send(any(SimpleMailMessage.class));
        postJson("resend-verification", Map.of("email", STUDENT), 503);
        assertEquals(hash, challenge().getCodeHash());
        verifyCode(STUDENT, original, 200);
    }

    @Test void allRegistrationPostsRequireCsrf() throws Exception {
        for (String endpoint : List.of("register", "verify-email", "resend-verification")) {
            mvc.perform(post("/api/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", STUDENT, "password", password, "code", "000000"))))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, users.count());
    }

    @Test void concurrentWrongCodesCannotBypassAttemptLimit() throws Exception {
        register(STUDENT);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<RegistrationService.VerificationResult>>();
            for (int i = 0; i < 6; i++) results.add(executor.submit(() -> { start.await(); return registration.verify(STUDENT, "bad"); }));
            start.countDown();
            for (var result : results) assertFalse(result.get(15, TimeUnit.SECONDS).verified());
        }
        assertEquals(5, challenge().getAttempts());
    }

    @Test void concurrentResendsSendOnlyOneNewMessage() throws Exception {
        register(STUDENT);
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> resend = () -> { start.await(); registration.resend(STUDENT); return null; };
            var first = executor.submit(resend);
            var second = executor.submit(resend);
            start.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        }
        assertEquals(2, delivered.size());
        assertEquals(1, challenges.count());
    }

    @Test void concurrentRegistrationsNeverCreateDuplicateUsersOrMessages() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> request = () -> { start.await();
                return postJson("register", Map.of("email", STUDENT, "password", password), 0).getResponse().getStatus(); };
            var first = executor.submit(request); var second = executor.submit(request);
            start.countDown();
            assertTrue(Set.of(202, 409).contains(first.get(15, TimeUnit.SECONDS)));
            assertTrue(Set.of(202, 409).contains(second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, users.count()); assertEquals(1, challenges.count()); assertEquals(1, delivered.size());
    }

    @Test void malformedJsonReturnsOnlySafeMessage() throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/register").session(session(csrf))
                .header(token.get("headerName").asString(), token.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Invalid request fields or JSON.\"}"));
        assertEquals(0, users.count());
        assertTrue(delivered.isEmpty());
    }

    private void register(String email) throws Exception { postJson("register", Map.of("email", email, "password", password), 202); }
    private MvcResult verifyCode(String email, String code, int status) throws Exception {
        return postJson("verify-email", Map.of("email", email, "code", code), status);
    }
    private kz.sdu.feedback.auth.entity.EmailVerification challenge() {
        return challenges.findByUserId(users.findByEmail(STUDENT).orElseThrow().getId()).orElseThrow();
    }
    private String code() {
        return delivered.getLast().getText().split("Your verification code is: ")[1].substring(0, 6);
    }
    private MvcResult postJson(String endpoint, Map<String, ?> body, int expected) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        var result = mvc.perform(post("/api/auth/" + endpoint).session(session(csrf))
                .header(token.get("headerName").asString(), token.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
        if (expected != 0) result.andExpect(status().is(expected));
        return result.andReturn();
    }
    private MockHttpSession session(MvcResult result) { return (MockHttpSession) result.getRequest().getSession(false); }
}
