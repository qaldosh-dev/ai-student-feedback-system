package kz.sdu.feedback.moodle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;

/** Called only by the explicit import command. Never runs at application startup. */
@Service
public class MoodleCatalogImportService {
    private final TeacherRepository teachers;
    private final CourseRepository courses;
    private final CourseOfferingRepository offerings;

    public MoodleCatalogImportService(TeacherRepository teachers, CourseRepository courses,
                                      CourseOfferingRepository offerings) {
        this.teachers = teachers;
        this.courses = courses;
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public Summary preflight(MoodleImportMapper.Result candidates) {
        return plan(candidates).summary();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Summary importApproved(MoodleImportMapper.Result candidates) {
        // Re-check inside the same transaction that performs ALL inserts.
        var plan = plan(candidates);
        if (!plan.summary().conflicts().isEmpty()) {
            throw new IllegalStateException("Preflight conflicts; no writes: " + plan.summary().conflicts());
        }
        for (var teacher : plan.newTeachers()) {
            save("teacher ID " + teacher.getMoodleTeacherId() + " / " + teacher.getName(),
                    () -> teachers.saveAndFlush(teacher));
        }
        for (var course : plan.newCourses()) {
            save("course " + course.getCode() + " / " + course.getName(),
                    () -> courses.saveAndFlush(course));
        }
        for (var offering : plan.newOfferings()) {
            save("Moodle card " + offering.getMoodleCourseId() + ", teacher ID "
                    + offering.getTeacher().getMoodleTeacherId() + ", " + offering.getCourse().getCode()
                    + ", " + offering.getSemester(), () -> offerings.saveAndFlush(offering));
        }
        return plan.summary();
    }

    private Plan plan(MoodleImportMapper.Result candidates) {
        var conflicts = new ArrayList<String>();
        if (!candidates.teacherConflicts().isEmpty()) conflicts.add("Unresolved teacher IDs: " + candidates.teacherConflicts().keySet());
        if (candidates.courseConflicts().keySet().stream().anyMatch(code -> !code.equals("IBCTL 201"))) {
            conflicts.add("Unexpected source course conflicts: " + candidates.courseConflicts().keySet());
        }
        var existingTeachers = teachers.findAll();
        var existingCourses = courses.findAll();
        var existingOfferings = offerings.findAll();
        Map<Long, Teacher> teacherBySourceId = new LinkedHashMap<>();
        for (var t : existingTeachers) if (t.getMoodleTeacherId() != null) teacherBySourceId.put(t.getMoodleTeacherId(), t);
        Map<String, Course> courseByCode = new LinkedHashMap<>();
        for (var c : existingCourses) {
            if (courseByCode.putIfAbsent(MoodleImportMapper.code(c.getCode()), c) != null) {
                conflicts.add("Multiple database courses normalize to code " + c.getCode());
            }
        }
        Map<OfferingKey, CourseOffering> offeringBySourceKey = new LinkedHashMap<>();
        for (var o : existingOfferings) if (o.getMoodleCourseId() != null) {
            offeringBySourceKey.put(new OfferingKey(o.getMoodleCourseId(), o.getTeacher().getMoodleTeacherId(), o.getSemester()), o);
        }
        var newTeachers = new ArrayList<Teacher>();
        var newCourses = new ArrayList<Course>();
        var newOfferings = new ArrayList<CourseOffering>();
        int reusedTeachers = 0, reusedCourses = 0, reusedOfferings = 0;
        for (var t : candidates.teachers()) {
            var existing = teacherBySourceId.get(t.moodleTeacherId());
            if (existing == null) {
                var created = new Teacher(t.name(), null, t.moodleTeacherId());
                teacherBySourceId.put(t.moodleTeacherId(), created);
                newTeachers.add(created);
            } else {
                reusedTeachers++;
                if (!MoodleImportMapper.clean(existing.getName()).equals(MoodleImportMapper.clean(t.name()))) {
                    conflicts.add("Teacher ID " + t.moodleTeacherId() + ": DB '" + existing.getName() + "', source '" + t.name() + "'");
                }
            }
        }
        for (var c : candidates.courses()) {
            var existing = courseByCode.get(MoodleImportMapper.code(c.code()));
            if (existing == null) {
                var created = new Course(c.code(), c.name());
                courseByCode.put(MoodleImportMapper.code(c.code()), created);
                newCourses.add(created);
            } else {
                reusedCourses++;
                if (!MoodleImportMapper.normalizedName(existing.getName()).equals(MoodleImportMapper.normalizedName(c.name()))) {
                    conflicts.add("Course " + c.code() + ": DB '" + existing.getName() + "', source '" + c.name() + "'");
                }
            }
        }
        for (var o : candidates.offerings()) {
            var key = new OfferingKey(o.moodleCourseId(), o.teacher().moodleTeacherId(), o.semester());
            var existing = offeringBySourceKey.get(key);
            var teacher = teacherBySourceId.get(o.teacher().moodleTeacherId());
            var course = courseByCode.get(MoodleImportMapper.code(o.course().code()));
            if (teacher == null || course == null) {
                conflicts.add("Unresolved candidate references: " + key);
            } else if (existing == null) {
                var created = new CourseOffering(teacher, course, o.semester(), null, o.moodleCourseId());
                offeringBySourceKey.put(key, created);
                newOfferings.add(created);
            } else {
                reusedOfferings++;
                if (!MoodleImportMapper.code(existing.getCourse().getCode()).equals(MoodleImportMapper.code(o.course().code()))) {
                    conflicts.add("Offering " + key + ": DB course " + existing.getCourse().getCode() + ", source " + o.course().code());
                }
            }
        }
        var summary = new Summary(existingTeachers.size(), existingCourses.size(), existingOfferings.size(),
                newTeachers.size(), reusedTeachers, newCourses.size(), reusedCourses,
                newOfferings.size(), reusedOfferings, List.copyOf(conflicts));
        return new Plan(summary, newTeachers, newCourses, newOfferings);
    }

    private void save(String source, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            // Rethrow: the transaction interceptor rolls back every earlier insert too.
            throw new IllegalStateException("Import failed at " + source + "; transaction must roll back", failure);
        }
    }

    private record OfferingKey(Long moodleCourseId, Long moodleTeacherId, String semester) { }
    private record Plan(Summary summary, List<Teacher> newTeachers, List<Course> newCourses,
                        List<CourseOffering> newOfferings) { }
    public record Summary(int beforeTeachers, int beforeCourses, int beforeOfferings,
                          int teachersCreated, int teachersReused, int coursesCreated, int coursesReused,
                          int offeringsCreated, int offeringsReused, List<String> conflicts) { }
}
