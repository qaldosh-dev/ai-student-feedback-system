package kz.sdu.feedback.schedule.entity;

import jakarta.persistence.*;
import java.time.Instant;
import kz.sdu.feedback.offering.entity.CourseOffering;

/** A catalog match for one source entry, not independent proof of enrollment. */
@Entity
@Table(name = "verified_courses", uniqueConstraints = @UniqueConstraint(
        name = "uk_verified_source_entry", columnNames = "source_entry_id"))
public class VerifiedCourse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_entry_id", nullable = false)
    private ExtractedScheduleEntry sourceEntry;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;
    @Column(nullable = false)
    private Instant createdAt;

    protected VerifiedCourse() { }
    public VerifiedCourse(ExtractedScheduleEntry sourceEntry, CourseOffering courseOffering) {
        this.sourceEntry = sourceEntry;
        this.courseOffering = courseOffering;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public ExtractedScheduleEntry getSourceEntry() { return sourceEntry; }
    public CourseOffering getCourseOffering() { return courseOffering; }
    public Instant getCreatedAt() { return createdAt; }
}
