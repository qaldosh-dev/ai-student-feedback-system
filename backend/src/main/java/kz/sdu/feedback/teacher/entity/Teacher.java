package kz.sdu.feedback.teacher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "teachers", uniqueConstraints = @UniqueConstraint(
        name = "uk_teachers_moodle_teacher_id", columnNames = "moodle_teacher_id"))
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String department;

    @Column(name = "moodle_teacher_id")
    private Long moodleTeacherId;

    protected Teacher() {
        // Required by JPA.
    }

    public Teacher(String name, String department) {
        this(name, department, null);
    }

    public Teacher(String name, String department, Long moodleTeacherId) {
        this.name = name;
        this.department = department;
        this.moodleTeacherId = moodleTeacherId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public Long getMoodleTeacherId() { return moodleTeacherId; }
}
