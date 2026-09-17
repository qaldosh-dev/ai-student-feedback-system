package kz.sdu.feedback.moodle;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.dto.TeacherResponse;
import kz.sdu.feedback.course.dto.CourseResponse;

class MoodleImportMapperTest {
    private final MoodleImportMapper mapper = new MoodleImportMapper();
    @TempDir Path directory;

    @Test
    void groupsByTeacherIdAndCourseCodeButPreservesEveryDistinctSourceRelationship() {
        var result = mapper.map(List.of(record(10L, 1L, "Teacher A", "CSS 114", "Programming"),
                record(11L, 1L, "Teacher A", "css  114", "PROGRAMMING"),
                record(10L, 2L, "Teacher A", "CSS 114", "Programming")));
        assertEquals(2, result.teachers().size()); // Equal names do not merge distinct people.
        assertEquals(1, result.courses().size());
        assertEquals(3, result.offerings().size());
        assertEquals(0, result.reviews().size());
        result.teachers().forEach(t -> assertNull(t.department()));
        result.offerings().forEach(o -> assertNull(o.section()));
        assertEquals(2, result.offerings().stream().filter(o -> o.moodleCourseId() == 10L).count());
    }

    @Test
    void holdsWholeCourseCodeOnSemanticNameConflict() {
        var result = mapper.map(List.of(record(10L, 1L, "A", "IBCTL 201", "IB PYP (Primary Year Program)"),
                record(11L, 2L, "B", "IBCTL 201", "IB MYP (Middle Year Program)")));
        assertEquals(2, result.teachers().size());
        assertEquals(0, result.courses().size());
        assertEquals(0, result.offerings().size());
        assertEquals(2, result.courseConflicts().get("IBCTL 201").size());
        assertEquals(2, result.reviews().size());
    }

    @Test
    void rejectsConflictingNamesForOneTeacherId() {
        var result = mapper.map(List.of(record(10L, 1L, "Teacher A", "CSS 114", "Programming"),
                record(11L, 1L, "TEACHER A", "CSS 114", "Programming")));
        assertEquals(1, result.teacherConflicts().size());
        assertTrue(result.teachers().isEmpty());
        assertTrue(result.offerings().isEmpty());
        assertEquals(2, result.reviews().size());
    }

    @Test
    void rejectsMissingSourceIdentityWithoutGuessingFromName() {
        var result = mapper.map(List.of(record(null, 1L, "A", "CSS 114", "Programming"),
                record(11L, null, "B", "CSS 114", "Programming"),
                record(12L, 0L, "C", "CSS 114", "Programming")));
        assertEquals(3, result.reviews().size());
        assertTrue(result.teachers().isEmpty());
        assertTrue(result.offerings().isEmpty());
    }

    @Test
    void repeatedSourceKeyDoesNotDuplicateCandidateButContradictoryKeyIsRejected() {
        var a = record(10L, 1L, "A", "CSS 114", "Programming");
        var result = mapper.map(List.of(a, a));
        assertEquals(1, result.offerings().size());
        assertEquals(1, result.repeatedSourceRecords());
        var conflict = mapper.map(List.of(a, record(10L, 1L, "A", "CSS 212", "Databases")));
        assertTrue(conflict.offerings().isEmpty());
        assertEquals(2, conflict.reviews().size());
    }

    @Test
    void oldSeedConstructorsAndNullableSourceFieldsRemainCompatible() throws Exception {
        var teacher = new Teacher("Aida Sarsenova", "Computer Science");
        var course = new Course("CSS 114", "Programming Fundamentals");
        var offering = new CourseOffering(teacher, course, "Fall 2026", "01");
        assertNull(teacher.getMoodleTeacherId());
        assertEquals("Computer Science", teacher.getDepartment());
        assertNull(offering.getMoodleCourseId());
        assertEquals("01", offering.getSection());
        var moodleTeacher = new Teacher("Teacher A", null, 1L);
        var moodleOffering = new CourseOffering(moodleTeacher, course, "Fall 2026", null, 10L);
        assertNull(moodleTeacher.getDepartment());
        assertNull(moodleOffering.getSection());
        assertEquals(1L, moodleTeacher.getMoodleTeacherId());
        assertEquals(10L, moodleOffering.getMoodleCourseId());
        assertTrue(Teacher.class.getDeclaredField("department").getAnnotation(Column.class).nullable());
        assertTrue(CourseOffering.class.getDeclaredField("section").getAnnotation(Column.class).nullable());
        assertTrue(Course.class.getDeclaredField("code").getAnnotation(Column.class).unique());
        assertArrayEquals(new String[]{"moodle_teacher_id"}, Teacher.class.getAnnotation(Table.class).uniqueConstraints()[0].columnNames());
        assertArrayEquals(new String[]{"moodle_course_id", "teacher_id", "semester"}, CourseOffering.class.getAnnotation(Table.class).uniqueConstraints()[1].columnNames());
        assertArrayEquals(new String[]{"id", "name", "department"}, java.util.Arrays.stream(TeacherResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertArrayEquals(new String[]{"id", "code", "name"}, java.util.Arrays.stream(CourseResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
    }

    @Test
    void fullCommandReadsOnlyLocalCacheAndSkipsContactlessCardWithoutStartingPersistence() throws Exception {
        Files.writeString(directory.resolve("crawl.properties"), "complete=true\npageCount=1\n");
        String html = "<header id='page-header'><h1>Fall 2026 (2026-2027)</h1></header>"
                + "<nav class='pagination'><a href='?categoryid=4&amp;page=0'>1</a></nav>"
                + "<div class='courses category-browse-4'><div class='rui-course-card-text'>"
                + "<div class='rui-card-course-contacts'></div></div>"
                + "<div data-region='course-content' data-course-id='10'>"
                + "<h4 class='coursename'>CSS 114 Programming (Teacher A)</h4></div></div>";
        Files.writeString(directory.resolve("page-000.html"), html);
        var result = MoodleImportDryRun.run(directory, directory.resolve("report.md"));
        assertTrue(result.teachers().isEmpty());
        assertTrue(result.offerings().isEmpty());
        assertTrue(Files.readString(directory.resolve("report.md")).contains("No named public teacher contact: **1**"));
        // This path executes with no Spring context, datasource or repository to receive writes.
        assertEquals(0, MoodleImportMapper.class.getDeclaredFields().length);
        assertEquals(0, MoodleImportDryRun.class.getDeclaredFields().length);
    }

    private MoodleCourseRecord record(Long courseId, Long teacherId, String teacher, String code, String name) {
        return new MoodleCourseRecord(teacher, code, name, "Fall 2026", courseId, 0,
                code + " " + name + " (" + teacher + ")", teacherId);
    }
}
