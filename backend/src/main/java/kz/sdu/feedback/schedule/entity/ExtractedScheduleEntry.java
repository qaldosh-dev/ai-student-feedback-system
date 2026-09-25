package kz.sdu.feedback.schedule.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "extracted_schedule_entries", indexes = @Index(name = "idx_extracted_schedule_upload", columnList = "schedule_upload_id"))
public class ExtractedScheduleEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_upload_id", nullable = false)
    private ScheduleUpload scheduleUpload;
    @Column(nullable = false, length = 32)
    private String courseCode;
    @Column(nullable = false, length = 512)
    private String courseName;
    @Column(nullable = false)
    private String teacherName;
    @Column(nullable = false, length = 32)
    private String groupSection;
    @Column(nullable = false, length = 64)
    private String academicTerm;

    protected ExtractedScheduleEntry() { }
    public ExtractedScheduleEntry(ScheduleUpload upload, String courseCode, String courseName,
                                  String teacherName, String groupSection, String academicTerm) {
        this.scheduleUpload = upload;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.groupSection = groupSection;
        this.academicTerm = academicTerm;
    }
    public Long getId() { return id; }
    public ScheduleUpload getScheduleUpload() { return scheduleUpload; }
    public String getCourseCode() { return courseCode; }
    public String getCourseName() { return courseName; }
    public String getTeacherName() { return teacherName; }
    public String getGroupSection() { return groupSection; }
    public String getAcademicTerm() { return academicTerm; }
}
