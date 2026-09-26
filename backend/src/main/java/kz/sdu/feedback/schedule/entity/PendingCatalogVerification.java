package kz.sdu.feedback.schedule.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/** An unresolved outcome; source text and ownership remain on the extracted entry. */
@Entity
@Table(name = "pending_catalog_verifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_pending_source_entry", columnNames = "source_entry_id"))
public class PendingCatalogVerification {
    public enum Reason {
        COURSE_NOT_FOUND, TEACHER_NOT_FOUND, COURSE_AND_TEACHER_NOT_FOUND,
        NO_COURSE_OFFERING, AMBIGUOUS_COURSE, AMBIGUOUS_TEACHER, AMBIGUOUS_OFFERING,
        COURSE_DATA_CONFLICT, INCOMPLETE_ENTRY
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_entry_id", nullable = false)
    private ExtractedScheduleEntry sourceEntry;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Reason reason;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected PendingCatalogVerification() { }
    public PendingCatalogVerification(ExtractedScheduleEntry sourceEntry, Reason reason) {
        this.sourceEntry = Objects.requireNonNull(sourceEntry);
        this.reason = Objects.requireNonNull(reason);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
    public void updateReason(Reason reason) {
        Objects.requireNonNull(reason);
        if (this.reason != reason) {
            this.reason = reason;
            this.updatedAt = Instant.now();
        }
    }
    public Long getId() { return id; }
    public ExtractedScheduleEntry getSourceEntry() { return sourceEntry; }
    public Reason getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
