package kz.sdu.feedback.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.user.seed.DevelopmentPasswordSetupService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-session;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSessionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TeacherRepository teachers;
    @Autowired PasswordEncoder encoder;
    @Autowired DevelopmentPasswordSetupService passwords;
    private final String studentPassword = UUID.randomUUID().toString();
    private final String teacherPassword = UUID.randomUUID().toString();
    private final String adminPassword = UUID.randomUUID().toString();

    @BeforeAll void accounts() {
        var teacher = teachers.saveAndFlush(new Teacher("Aida Sarsenova", "Computer Science"));
        users.saveAndFlush(new User("student@feedback.test", encoder.encode(studentPassword), Set.of(UserRole.STUDENT), true, null));
        users.saveAndFlush(new User("teacher@feedback.test", encoder.encode(teacherPassword), Set.of(UserRole.TEACHER), true, teacher));
        users.saveAndFlush(new User("admin@feedback.test", encoder.encode(adminPassword), Set.of(UserRole.ADMIN), true, null));
        users.saveAndFlush(new User("unverified@feedback.test", encoder.encode(studentPassword), Set.of(UserRole.STUDENT)));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void eachRoleSignsInAndMeRestoresSafeUser(UserRole role) throws Exception {
        String email = role.name().toLowerCase(java.util.Locale.ROOT) + "@feedback.test";
        var result = login(email, password(role), 200);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(email, body.get("email").asString());
        assertEquals(role.name(), body.get("roles").get(0).asString());
        assertTrue(body.get("emailVerified").asBoolean());
        assertFalse(body.has("passwordHash"));
        assertFalse(body.has("sessionId"));
        assertEquals(Set.of("id", "email", "roles", "emailVerified", "linkedTeacher"), body.propertyNames());
        if (role == UserRole.TEACHER) {
            assertEquals(1L, body.get("linkedTeacher").get("id").asLong());
            assertEquals("Aida Sarsenova", body.get("linkedTeacher").get("name").asString());
        } else { assertTrue(body.get("linkedTeacher").isNull()); }
        mvc.perform(get("/api/auth/me").session(session(result)))
                .andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
    }

    @Test void wrongUnknownAndUnverifiedHaveTheSameGenericResponse() throws Exception {
        var wrong = login("student@feedback.test", UUID.randomUUID().toString(), 401);
        var unknown = login("unknown@feedback.test", studentPassword, 401);
        var unverified = login("unverified@feedback.test", studentPassword, 401);
        assertEquals(wrong.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        assertEquals(wrong.getResponse().getContentAsString(), unverified.getResponse().getContentAsString());
        for (var result : new MvcResult[]{wrong, unknown, unverified}) {
            mvc.perform(get("/api/auth/me").session(session(result))).andExpect(status().isUnauthorized());
        }
    }

    @Test void normalizesEmail() throws Exception {
        var result = login("  STUDENT@Feedback.Test  ", studentPassword, 200);
        assertEquals("student@feedback.test", json.readTree(result.getResponse().getContentAsString()).get("email").asString());
    }

    @Test void meWithoutSessionIs401AndCatalogRemainsPublic() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        for (String path : new String[]{"/api/teachers", "/api/courses", "/api/teachers?q=Aida&courseQ=CSS",
                "/api/courses?q=CSS&teacherQ=Aida"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test void csrfRequiredForLoginAndLogoutAndSessionIdRotates() throws Exception {
        var before = csrf(null);
        String oldId = before.session().getId();
        mvc.perform(post("/api/auth/login").session(before.session()).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("student@feedback.test", studentPassword))).andExpect(status().isForbidden());
        var signedIn = mvc.perform(post("/api/auth/login").session(before.session())
                .header(before.header(), before.token()).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("student@feedback.test", studentPassword))).andExpect(status().isOk()).andReturn();
        assertNotEquals(oldId, session(signedIn).getId());
        mvc.perform(post("/api/auth/logout").session(session(signedIn))).andExpect(status().isForbidden());
        // A pre-login token must no longer authorize a state-changing request after login.
        mvc.perform(post("/api/auth/logout").session(session(signedIn)).header(before.header(), before.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session(signedIn))).andExpect(status().isOk());
    }

    @Test void logoutInvalidatesSessionButNotAnotherBrowserSession() throws Exception {
        var first = session(login("student@feedback.test", studentPassword, 200));
        var second = session(login("student@feedback.test", studentPassword, 200));
        var csrf = csrf(first);
        mvc.perform(get("/api/auth/logout").session(first)).andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/me").session(first)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(first).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(first.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isOk());
    }

    @Test void explicitPasswordSetupOnlyChangesTheThreeDevHashes() {
        var untouched = users.findByEmail("unverified@feedback.test").orElseThrow();
        String newPassword = UUID.randomUUID().toString();
        passwords.setup(newPassword, newPassword, newPassword);
        try {
            for (var role : UserRole.values()) {
                var user = users.findByEmail(role.name().toLowerCase(java.util.Locale.ROOT) + "@feedback.test").orElseThrow();
                assertTrue(encoder.matches(newPassword, user.getPasswordHash()));
                assertTrue(user.isEmailVerified());
            }
            assertEquals(untouched.getPasswordHash(), users.findByEmail(untouched.getEmail()).orElseThrow().getPasswordHash());
            assertEquals(4, users.count());
            assertEquals(1, teachers.count());
        } finally { passwords.setup(studentPassword, teacherPassword, adminPassword); }
    }

    @Test void missingPasswordStopsSetupBeforeAnyWrites() {
        String before = users.findByEmail("student@feedback.test").orElseThrow().getPasswordHash();
        assertThrows(IllegalArgumentException.class, () -> passwords.setup(studentPassword, null, adminPassword));
        assertEquals(before, users.findByEmail("student@feedback.test").orElseThrow().getPasswordHash());
    }

    @Test void allProtectedAreasRequireAuthentication() throws Exception {
        for (var role : UserRole.values()) {
            mvc.perform(get(areaPath(role))).andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Sign in required"));
        }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void roleAccessMatrixUsesSessionAuthorities(UserRole role) throws Exception {
        var signedIn = session(login(role.name().toLowerCase(java.util.Locale.ROOT) + "@feedback.test", password(role), 200));
        for (var area : UserRole.values()) {
            var response = mvc.perform(get(areaPath(area)).session(signedIn));
            if (area != role) {
                response.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("You do not have access to this area"));
            } else {
                var result = response.andExpect(status().isOk()).andReturn();
                var body = json.readTree(result.getResponse().getContentAsString());
                assertEquals(Set.of("area", "email", "roles", "linkedTeacher"), body.propertyNames());
                assertEquals(role.name(), body.get("area").asString());
                assertEquals(role.name(), body.get("roles").get(0).asString());
                if (role == UserRole.TEACHER) {
                    assertEquals("Aida Sarsenova", body.get("linkedTeacher").get("name").asString());
                    assertEquals(Set.of("id", "name"), body.get("linkedTeacher").propertyNames());
                } else { assertTrue(body.get("linkedTeacher").isNull()); }
                // Repeated access uses the same authenticated session (as after a page refresh).
                mvc.perform(get(areaPath(area)).session(signedIn)).andExpect(status().isOk());
            }
        }
        var token = csrf(signedIn);
        mvc.perform(post("/api/auth/logout").session(signedIn).header(token.header(), token.token()))
                .andExpect(status().isNoContent());
        assertTrue(signedIn.isInvalid());
        mvc.perform(get(areaPath(role))).andExpect(status().isUnauthorized());
    }

    @Test void multipleRolesGrantBothAreasWithoutGrantingAdmin() throws Exception {
        var account = users.saveAndFlush(new User("multi@feedback.test", encoder.encode(studentPassword),
                Set.of(UserRole.STUDENT, UserRole.TEACHER), true, null));
        try {
            var signedIn = session(login(account.getEmail(), studentPassword, 200));
            mvc.perform(get(areaPath(UserRole.STUDENT)).session(signedIn)).andExpect(status().isOk());
            mvc.perform(get(areaPath(UserRole.TEACHER)).session(signedIn)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.linkedTeacher").isEmpty());
            mvc.perform(get(areaPath(UserRole.ADMIN)).session(signedIn)).andExpect(status().isForbidden());
        } finally { users.deleteById(account.getId()); }
    }

    @Test void teacherWithoutLinkNeverReceivesAnotherTeachersProfile() throws Exception {
        var account = users.saveAndFlush(new User("unlinked@feedback.test", encoder.encode(teacherPassword),
                Set.of(UserRole.TEACHER), true, null));
        try {
            var signedIn = session(login(account.getEmail(), teacherPassword, 200));
            mvc.perform(get(areaPath(UserRole.TEACHER)).session(signedIn)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(account.getEmail()))
                    .andExpect(jsonPath("$.linkedTeacher").isEmpty());
        } finally { users.deleteById(account.getId()); }
    }

    private String areaPath(UserRole role) {
        return "/api/" + role.name().toLowerCase(java.util.Locale.ROOT) + "/home";
    }

    private String password(UserRole role) {
        return switch (role) { case STUDENT -> studentPassword; case TEACHER -> teacherPassword; case ADMIN -> adminPassword; };
    }
    private record Csrf(MockHttpSession session, String header, String token) { }
    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        return new Csrf(session(result), body.get("headerName").asString(), body.get("token").asString());
    }
    private MvcResult login(String email, String password, int expectedStatus) throws Exception {
        var csrf = csrf(null);
        return mvc.perform(post("/api/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(loginJson(email, password)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }
    private String loginJson(String email, String password) { return json.writeValueAsString(Map.of("email", email, "password", password)); }
    private MockHttpSession session(MvcResult result) { return (MockHttpSession) result.getRequest().getSession(false); }
}
