package kz.sdu.feedback.teacher;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Set;
import kz.sdu.feedback.FeedbackApplication;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.user.entity.*;
import kz.sdu.feedback.user.repository.UserRepository;
import kz.sdu.feedback.review.entity.Review;
import kz.sdu.feedback.review.repository.ReviewRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=FeedbackApplication.class, properties={
    "spring.datasource.url=jdbc:h2:mem:teacher-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed.enabled=false", "app.dev-accounts.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class TeacherProfileTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TeacherRepository teachers;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired UserRepository users;
    @Autowired ReviewRepository reviews;
    Long teacherId,otherId,emptyId,courseId;
    @BeforeEach void setup() {
        var teacher=teachers.saveAndFlush(new Teacher("Alex Person",null)); teacherId=teacher.getId();
        var other=teachers.saveAndFlush(new Teacher("Morgan Person",null)); otherId=other.getId();
        emptyId=teachers.saveAndFlush(new Teacher("Empty Person",null)).getId();
        var course=courses.saveAndFlush(new Course("ABC 100","Alpha Subject")); courseId=course.getId();
        var beta=courses.saveAndFlush(new Course("DEF 200","Beta Subject"));
        var hidden=courses.saveAndFlush(new Course("XYZ 999","Other teacher only"));
        var first=offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026",null,101L));
        offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026",null,102L));
        offerings.saveAndFlush(new CourseOffering(teacher,course,"Fall 2026","15-P"));
        offerings.saveAndFlush(new CourseOffering(teacher,course,"Spring 2025","04-N"));
        offerings.saveAndFlush(new CourseOffering(teacher,beta,"Fall 2026",null));
        offerings.saveAndFlush(new CourseOffering(other,course,"Spring 2024",null));
        offerings.saveAndFlush(new CourseOffering(other,hidden,"Fall 2026",null));
        users.saveAndFlush(new User("linked@feedback.test","private-hash",Set.of(UserRole.TEACHER),true,teacher));
        var student=users.saveAndFlush(new User("private-student@feedback.test","private-hash",Set.of(UserRole.STUDENT),true,null));
        reviews.saveAndFlush(new Review(student,first,"2026-2027 1 term",5,"Private feedback"));
    }
    String url() { return "/api/teachers/"+teacherId; }
    @Test void publicProfileHasExactSafeFieldsAndDistinctCourseTermsWithoutChangingCatalog() throws Exception {
        var body=mvc.perform(get(url())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var root=json.readTree(body);
        assertEquals(Set.of("id","name","courseOfferings"),root.propertyNames());
        assertEquals(teacherId,root.get("id").longValue()); assertEquals("Alex Person",root.get("name").stringValue());
        var rows=root.get("courseOfferings"); assertEquals(3,rows.size());
        for(var row:rows) assertEquals(Set.of("courseId","courseCode","courseName","academicTerm"),row.propertyNames());
        assertEquals(courseId,rows.get(0).get("courseId").longValue()); assertEquals("ABC 100",rows.get(0).get("courseCode").stringValue());
        assertEquals("Alpha Subject",rows.get(0).get("courseName").stringValue());
        assertEquals("Spring 2025",rows.get(0).get("academicTerm").stringValue());
        assertEquals("Fall 2026",rows.get(1).get("academicTerm").stringValue());
        assertEquals("DEF 200",rows.get(2).get("courseCode").stringValue());
        for(String privateValue:new String[]{"Other teacher only","Spring 2024","15-P","04-N","Private feedback","private-student","linked@","private-hash","moodle","rating","review","roles","linkedTeacher"})
            assertFalse(body.contains(privateValue),privateValue);
        assertEquals(body,mvc.perform(get(url())).andReturn().getResponse().getContentAsString());
        assertEquals(7,offerings.count()); assertEquals(3,teachers.count()); assertEquals(3,courses.count()); assertEquals(1,reviews.count());
    }
    @Test void emptyTeacherIsAValidProfile() throws Exception {
        mvc.perform(get("/api/teachers/"+emptyId)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Empty Person"))
            .andExpect(jsonPath("$.courseOfferings.length()").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"999999","0","-1","abc","1.5","999999999999999999999999"})
    void absentAndInvalidIdsAreSafe404(String id) throws Exception {
        mvc.perform(get("/api/teachers/"+id)).andExpect(status().isNotFound()).andExpect(content().json("{\"message\":\"Teacher not found.\"}"));
    }
    @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","ADMIN"}) void allRolesCanUsePublicProfile(String role) throws Exception {
        mvc.perform(get(url()).with(user("any@feedback.test").roles(role))).andExpect(status().isOk());
    }
    @Test void existingBrowseSearchAndLinkedFiltersStayPublic() throws Exception {
        mvc.perform(get("/api/teachers")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/teachers").param("q","  ALEX ")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/teachers").param("courseQ","xyz")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(otherId));
        mvc.perform(get("/api/teachers").param("q","alex").param("courseQ","xyz")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/courses").param("teacherQ","alex")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/courses").param("q"," alpha ").param("teacherQ","ALEX")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(courseId));
    }
    @Test void existingTeacherHomeResolvesOwnLinkNotClientQuery() throws Exception {
        mvc.perform(get("/api/teacher/home").param("teacherId",otherId.toString()).with(user("linked@feedback.test").roles("TEACHER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.linkedTeacher.id").value(teacherId));
    }
    @Test void noLinkedTeacherHasNoGuessedProfile() throws Exception {
        users.saveAndFlush(new User("unlinked@feedback.test","unused",Set.of(UserRole.TEACHER),true,null));
        mvc.perform(get("/api/teacher/home").with(user("unlinked@feedback.test").roles("TEACHER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.linkedTeacher").isEmpty());
    }
    @Test void studentCannotUseTeacherHomeResolution() throws Exception {
        mvc.perform(get("/api/teacher/home").with(user("private-student@feedback.test").roles("STUDENT"))).andExpect(status().isForbidden());
    }
}
