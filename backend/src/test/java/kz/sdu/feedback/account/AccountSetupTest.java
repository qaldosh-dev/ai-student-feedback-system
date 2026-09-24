package kz.sdu.feedback.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.account.service.AccountSetupService;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:account-setup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
class AccountSetupTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TeacherRepository teachers;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountSetupService setup;
    private final String password = UUID.randomUUID().toString();
    private Teacher first;
    private Teacher second;
    private static final String EMAIL = "verified@stu.sdu.edu.kz";
    private static final String OTHER = "other@sdu.edu.kz";

    @BeforeEach void fixtures() {
        users.deleteAll();
        teachers.deleteAll();
        first = teachers.saveAndFlush(new Teacher("Test Teacher One", null));
        second = teachers.saveAndFlush(new Teacher("Test Teacher Two", null));
        users.saveAndFlush(new User(EMAIL, encoder.encode(password), Set.of(), true, null));
        users.saveAndFlush(new User(OTHER, encoder.encode(password), Set.of(), true, null));
        users.saveAndFlush(new User("unverified@sdu.edu.kz", encoder.encode(password), Set.of()));
    }

    @ParameterizedTest @ValueSource(strings = {"/roles/student", "/teacher-claim"})
    void anonymousRequiresLoginWithValidCsrf(String path) throws Exception {
        mvc.perform(post("/api/account" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"/roles/student", "/teacher-claim"})
    void csrfIsRequired(String path) throws Exception {
        mvc.perform(post("/api/account" + path).session(login()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"/roles/student", "/teacher-claim"})
    void unverifiedPrincipalCannotMutate(String path) throws Exception {
        // Simulates a stale session: eligibility must be re-read from the database.
        var body = path.equals("/roles/student") ? Map.of() : Map.of("teacherId", first.getId());
        mvc.perform(post("/api/account" + path).with(user("unverified@sdu.edu.kz")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test void studentAddsOnlyStudentAndUpdatesSessionAndMeWithoutLogin() throws Exception {
        var session = login();
        for (String area : new String[]{"student", "teacher", "admin"}) area(session, area, 403);
        var result = postSetup(session, "/roles/student", Map.of(), 200);
        assertSafe(result);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("STUDENT", body.get("roles").get(0).asString());
        assertEquals(1, body.get("roles").size());
        assertTrue(body.get("linkedTeacher").isNull());
        area(session, "student", 200); area(session, "teacher", 403); area(session, "admin", 403);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(content().json(result.getResponse().getContentAsString()));
        postSetup(session, "/roles/student", Map.of(), 200);
        assertEquals(Set.of(UserRole.STUDENT), setup.selectStudent(EMAIL).roles());
    }

    @Test void claimUpdatesLinkAuthoritiesAndIsIdempotent() throws Exception {
        var session = login();
        var result = postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId()), 200);
        assertSafe(result);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("TEACHER", body.get("roles").get(0).asString());
        assertEquals(1, body.get("roles").size());
        assertEquals(first.getId(), body.get("linkedTeacher").get("id").asLong());
        area(session, "teacher", 200); area(session, "student", 403); area(session, "admin", 403);
        postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId()), 200);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(content().json(result.getResponse().getContentAsString()));
    }

    @Test void cannotSwitchTeacherOrRevealOwner() throws Exception {
        setup.claimTeacher(OTHER, first.getId());
        var session = login();
        var conflict = postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId()), 409);
        assertEquals("{\"message\":\"This teacher profile has already been claimed.\"}", conflict.getResponse().getContentAsString());
        postSetup(session, "/teacher-claim", Map.of("teacherId", second.getId()), 200);
        postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId()), 409);
        assertEquals(second.getId(), setup.claimTeacher(EMAIL, second.getId()).linkedTeacher().id());
    }

    @Test void unknownTeacherLeavesRolesEmpty() throws Exception {
        var session = login();
        postSetup(session, "/teacher-claim", Map.of("teacherId", Long.MAX_VALUE), 404);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.linkedTeacher").isEmpty());
    }

    @Test void multiRoleWorksInEitherOrderAndNeverGrantsAdmin() throws Exception {
        var session = login();
        postSetup(session, "/roles/student", Map.of(), 200);
        postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId()), 200);
        area(session, "student", 200); area(session, "teacher", 200); area(session, "admin", 403);
        setup.claimTeacher(OTHER, second.getId());
        assertEquals(Set.of(UserRole.STUDENT, UserRole.TEACHER), setup.selectStudent(OTHER).roles());
    }

    @ParameterizedTest @ValueSource(strings = {"role", "userId", "emailVerified", "linkedTeacher", "teacherName", "admin"})
    void rejectsInjectedFieldsOnBothEndpoints(String field) throws Exception {
        var session = login();
        postSetup(session, "/roles/student", Map.of(field, "ADMIN"), 400);
        postSetup(session, "/teacher-claim", Map.of("teacherId", first.getId(), field, "ADMIN"), 400);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test void noAdminSelectionEndpoint() throws Exception {
        postSetup(login(), "/roles/admin", Map.of(), 404);
    }

    @Test void concurrentClaimsHaveOneWinnerAndOneConflict() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> a = () -> concurrentClaim(start, EMAIL);
            Callable<Integer> b = () -> concurrentClaim(start, OTHER);
            var one = executor.submit(a); var two = executor.submit(b); start.countDown();
            assertEquals(Set.of(200, 409), Set.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, users.findAll().stream().filter(u -> u.getLinkedTeacher() != null).count());
        assertEquals(2, teachers.count());
    }

    @Test void concurrentRoleAdditionsPreserveBothRoles() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> setup.selectStudent(EMAIL));
            var two = executor.submit(() -> setup.claimTeacher(EMAIL, first.getId()));
            one.get(15, TimeUnit.SECONDS); two.get(15, TimeUnit.SECONDS);
        }
        assertEquals(Set.of(UserRole.STUDENT, UserRole.TEACHER), setup.selectStudent(EMAIL).roles());
    }

    @Test void newSessionRestoresRolesAndLogoutRevokesAccess() throws Exception {
        var session = login();
        postSetup(session, "/roles/student", Map.of(), 200);
        var restored = login();
        area(restored, "student", 200);
        mvc.perform(post("/api/auth/logout").session(restored).with(csrf())).andExpect(status().isNoContent());
        assertTrue(restored.isInvalid());
        mvc.perform(get("/api/student/home")).andExpect(status().isUnauthorized());
    }

    private int concurrentClaim(CountDownLatch start, String email) throws Exception {
        start.await();
        try { setup.claimTeacher(email, first.getId()); return 200; }
        catch (ResponseStatusException conflict) { return conflict.getStatusCode().value(); }
    }
    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", password))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private MvcResult postSetup(MockHttpSession session, String path, Map<String, ?> body, int expected) throws Exception {
        return mvc.perform(post("/api/account" + path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn();
    }
    private void area(MockHttpSession session, String name, int expected) throws Exception {
        mvc.perform(get("/api/" + name + "/home").session(session)).andExpect(status().is(expected));
    }
    private void assertSafe(MvcResult result) throws Exception {
        assertEquals(Set.of("id", "email", "roles", "emailVerified", "linkedTeacher"),
                json.readTree(result.getResponse().getContentAsString()).propertyNames());
    }
}
