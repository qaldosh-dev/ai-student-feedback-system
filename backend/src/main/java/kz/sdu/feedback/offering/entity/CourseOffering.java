package kz.sdu.feedback.offering.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kz.sdu.feedback.course.entity.Course;
import kz.sdu.feedback.teacher.entity.Teacher;

@Entity
@Table(name = "course_offerings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"teacher_id", "course_id", "semester", "section"}),
        @UniqueConstraint(name = "uk_offerings_moodle_teacher_semester",
                columnNames = {"moodle_course_id", "teacher_id", "semester"})})
public class CourseOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String semester;

    @Column(nullable = true)
    private String section;

    @Column(name = "moodle_course_id")
    private Long moodleCourseId;

    protected CourseOffering() {
        // Required by JPA.
    }

    public CourseOffering(Teacher teacher, Course course, String semester, String section) {
        this(teacher, course, semester, section, null);
    }

    public CourseOffering(Teacher teacher, Course course, String semester, String section, Long moodleCourseId) {
        this.teacher = teacher;
        this.course = course;
        this.semester = semester;
        this.section = section;
        this.moodleCourseId = moodleCourseId;
    }

    public Long getId() { return id; }
    public Teacher getTeacher() { return teacher; }
    public Course getCourse() { return course; }
    public String getSemester() { return semester; }
    public String getSection() { return section; }
    public Long getMoodleCourseId() { return moodleCourseId; }
}
