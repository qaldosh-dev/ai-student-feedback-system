package kz.sdu.feedback.schedule.service;

import java.util.*;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.course.repository.CourseRepository;
import kz.sdu.feedback.teacher.entity.Teacher;
import kz.sdu.feedback.teacher.repository.TeacherRepository;
import kz.sdu.feedback.offering.entity.CourseOffering;
import kz.sdu.feedback.offering.repository.CourseOfferingRepository;
import kz.sdu.feedback.schedule.dto.VerifiedCourseResponse;
import kz.sdu.feedback.schedule.entity.*;
import kz.sdu.feedback.schedule.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifiedCourseService {
    private final ScheduleUploadService schedules;
    private final ScheduleUploadRepository uploads;
    private final ExtractedScheduleEntryRepository entries;
    private final VerifiedCourseRepository verified;
    private final CourseRepository courses;
    private final TeacherRepository teachers;
    private final CourseOfferingRepository offerings;

    public VerifiedCourseService(ScheduleUploadService schedules, ScheduleUploadRepository uploads,
            ExtractedScheduleEntryRepository entries, VerifiedCourseRepository verified,
            CourseRepository courses, TeacherRepository teachers, CourseOfferingRepository offerings) {
        this.schedules = schedules; this.uploads = uploads; this.entries = entries; this.verified = verified;
        this.courses = courses; this.teachers = teachers; this.offerings = offerings;
    }

    /** Lazily reconcile derived matches. Source entries and catalog records are never changed here. */
    @Transactional
    public List<VerifiedCourseResponse> list(String authenticatedEmail) {
        var owner = schedules.requireStudent(authenticatedEmail);
        // One snapshot per request is sufficient for this small catalog; no per-entry catalog queries.
        var catalogCourses = courses.findAll();
        var catalogTeachers = teachers.findAll();
        var catalogOfferings = offerings.findAll();
        var result = new ArrayList<VerifiedCourseResponse>();
        var owned = uploads.findByUserIdOrderByUploadedAtDescIdDesc(owner.getId());
        // Same lock as extraction; ascending order avoids deadlocks between simultaneous reads.
        for (var id : owned.stream().map(ScheduleUpload::getId).sorted().toList()) {
            var upload = uploads.findForExtraction(id).orElseThrow();
            if (!upload.getUser().getId().equals(owner.getId())) continue;
            if (upload.getStatus() != ScheduleUpload.Status.VALID) {
                verified.deleteForUpload(id);
                continue;
            }
            var existing = new HashMap<Long, VerifiedCourse>();
            verified.findBySourceEntryScheduleUploadId(id).forEach(v -> existing.put(v.getSourceEntry().getId(), v));
            for (var entry : entries.findForDisplay(id)) {
                var match = match(entry, catalogCourses, catalogTeachers, catalogOfferings);
                var previous = existing.remove(entry.getId());
                if (previous != null && (match == null || !previous.getCourseOffering().getId().equals(match.getId()))) {
                    verified.delete(previous);
                    verified.flush(); // Free the unique source-entry key before a replacement.
                    previous = null;
                }
                if (match != null) {
                    var row = previous == null ? verified.save(new VerifiedCourse(entry, match)) : previous;
                    result.add(VerifiedCourseResponse.from(row));
                }
            }
            verified.deleteAll(existing.values());
        }
        result.sort(Comparator.comparing(VerifiedCourseResponse::academicTerm)
                .thenComparing(VerifiedCourseResponse::courseCode).thenComparing(VerifiedCourseResponse::teacherName)
                .thenComparing(VerifiedCourseResponse::groupSection).thenComparing(VerifiedCourseResponse::sourceEntryId));
        return result;
    }

    private CourseOffering match(ExtractedScheduleEntry entry, List<Course> courses,
                                 List<Teacher> teachers, List<CourseOffering> offerings) {
        if (List.of(entry.getCourseCode(), entry.getCourseName(), entry.getTeacherName(),
                entry.getGroupSection(), entry.getAcademicTerm()).stream().anyMatch(s -> normalize(s).isEmpty())) return null;
        var courseMatches = courses.stream().filter(c -> normalize(c.getCode()).equals(normalize(entry.getCourseCode()))).toList();
        if (courseMatches.size() != 1) return null;
        var course = courseMatches.getFirst();
        if (!normalize(course.getName()).equals(normalize(entry.getCourseName()))) return null;
        var teacherMatches = teachers.stream().filter(t -> normalize(t.getName()).equals(normalize(entry.getTeacherName()))).toList();
        if (teacherMatches.size() != 1) return null;
        var teacher = teacherMatches.getFirst();
        var related = offerings.stream().filter(o -> o.getCourse().getId().equals(course.getId())
                && o.getTeacher().getId().equals(teacher.getId())).toList();
        // The source does not identify a Moodle shell. Never choose arbitrarily between offerings.
        // Term/group remain schedule evidence, not a claim that this catalog covers historical enrollment.
        return related.size() == 1 ? related.getFirst() : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
