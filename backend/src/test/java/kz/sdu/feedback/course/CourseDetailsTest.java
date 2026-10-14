package kz.sdu.feedback.course;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.user.entity.UserRole;
import kz.sdu.feedback.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = FeedbackApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:course-details;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class CourseDetailsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CourseRepository courses;
    @Autowired TeacherRepository teachers;
    @Autowired CourseOfferingRepository offerings;
    @Autowired UserRepository users;
    Long courseId, emptyId, alexId, morganId, sameNameId;

    @BeforeEach void setup() {
        var course = courses.saveAndFlush(new Course("ABC 100", "Alpha Subject")); courseId = course.getId();
        var other = courses.saveAndFlush(new Course("XYZ 999", "Other Subject"));
        emptyId = courses.saveAndFlush(new Course("EMPTY 101", "Empty Subject")).getId();
        var alex = teachers.saveAndFlush(new Teacher("Alex Person", "Department", 201L)); alexId = alex.getId();
        var morgan = teachers.saveAndFlush(new Teacher("Morgan Person", null, 202L)); morganId = morgan.getId();
        var sameName = teachers.saveAndFlush(new Teacher("Morgan Person", null, 203L)); sameNameId = sameName.getId();
        var unrelated = teachers.saveAndFlush(new Teacher("Unrelated Person", null));
        offerings.saveAndFlush(new CourseOffering(alex, course, "Fall 2026", null, 101L));
        offerings.saveAndFlush(new CourseOffering(alex, course, "Fall 2026", null, 102L));
        offerings.saveAndFlush(new CourseOffering(alex, course, "Fall 2026", "15-P"));
        offerings.saveAndFlush(new CourseOffering(alex, course, "Spring 2025", "04-N"));
        offerings.saveAndFlush(new CourseOffering(morgan, course, "Fall 2026", null));
        offerings.saveAndFlush(new CourseOffering(sameName, course, "Fall 2026", null));
        offerings.saveAndFlush(new CourseOffering(unrelated, other, "Spring 2024", null));
        users.saveAndFlush(new User("private@feedback.test", "private-hash", Set.of(UserRole.TEACHER), true, alex));
    }

    @Test void publicDetailsExposeOnlySafeCourseAndRelatedTeachersWithDistinctTerms() throws Exception {
        var body = mvc.perform(get("/api/courses/" + courseId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var root = json.readTree(body);
        assertEquals(Set.of("courseId", "courseCode", "courseName", "teacherOfferings"), root.propertyNames());
        assertEquals(courseId, root.get("courseId").longValue());
        assertEquals("ABC 100", root.get("courseCode").stringValue());
        assertEquals("Alpha Subject", root.get("courseName").stringValue());
        var rows = root.get("teacherOfferings");
        assertEquals(4, rows.size());
        for (var row : rows) assertEquals(Set.of("teacherId", "teacherName", "academicTerm"), row.propertyNames());
        assertEquals(alexId, rows.get(0).get("teacherId").longValue());
        assertEquals("Alex Person", rows.get(0).get("teacherName").stringValue());
        assertEquals("Spring 2025", rows.get(0).get("academicTerm").stringValue());
        assertEquals(alexId, rows.get(1).get("teacherId").longValue());
        assertEquals("Fall 2026", rows.get(1).get("academicTerm").stringValue());
        assertEquals(morganId, rows.get(2).get("teacherId").longValue());
        assertEquals(sameNameId, rows.get(3).get("teacherId").longValue());
        // Same display name never merges different Teacher identities.
        assertEquals("Morgan Person", rows.get(2).get("teacherName").stringValue());
        assertEquals("Morgan Person", rows.get(3).get("teacherName").stringValue());
        for (String forbidden : new String[]{"Unrelated Person", "Spring 2024", "15-P", "04-N", "private@", "private-hash",
                "Department", "moodle", "review", "rating", "average", "roles", "linkedTeacher", "schedule", "verifiedCourse"}) {
            assertFalse(body.contains(forbidden), forbidden);
        }
        assertEquals(body, mvc.perform(get("/api/courses/" + courseId)).andReturn().getResponse().getContentAsString());
        assertEquals(7, offerings.count()); assertEquals(4, teachers.count()); assertEquals(3, courses.count());
    }

    @Test void validCourseWithoutOfferingsReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/courses/" + emptyId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").value("Empty Subject"))
                .andExpect(jsonPath("$.teacherOfferings.length()").value(0));
    }

    @ParameterizedTest @ValueSource(strings = {"999999", "0", "-1", "abc", "1.5", "999999999999999999999999"})
    void nonexistentOrInvalidCourseReturnsSafe404(String id) throws Exception {
        mvc.perform(get("/api/courses/" + id)).andExpect(status().isNotFound())
                .andExpect(content().json("{\"message\":\"Course not found.\"}"));
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "TEACHER", "ADMIN"})
    void publicDetailsAllowEveryRole(String role) throws Exception {
        mvc.perform(get("/api/courses/" + courseId).with(user("any@feedback.test").roles(role))).andExpect(status().isOk());
    }

    @Test void existingBrowseSearchAndLinkedFilteringStillWork() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        for (String query : new String[]{" abc ", "ALPHA"}) {
            mvc.perform(get("/api/courses").param("q", query)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(courseId));
        }
        mvc.perform(get("/api/courses").param("teacherQ", "morgan")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(courseId));
        mvc.perform(get("/api/courses").param("q", "xyz").param("teacherQ", "alex"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/teachers").param("courseQ", "abc")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test void teacherProfileStillReturnsCourseIdsAndDistinctTermsForNavigation() throws Exception {
        mvc.perform(get("/api/teachers/" + alexId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex Person"))
                .andExpect(jsonPath("$.courseOfferings.length()").value(2))
                .andExpect(jsonPath("$.courseOfferings[0].courseId").value(courseId))
                .andExpect(jsonPath("$.courseOfferings[0].academicTerm").value("Spring 2025"))
                .andExpect(jsonPath("$.courseOfferings[1].academicTerm").value("Fall 2026"));
    }
}
