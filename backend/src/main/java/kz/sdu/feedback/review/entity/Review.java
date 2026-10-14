package kz.sdu.feedback.review.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import kz.sdu.feedback.review.moderation.ModerationCategory;
import kz.sdu.feedback.review.moderation.ModerationResult;
import kz.sdu.feedback.user.entity.User;
import kz.sdu.feedback.offering.entity.CourseOffering;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;
    @Column(nullable = false)
    private String academicTerm;
    @Column(nullable = false)
    private int rating;
    @Column(nullable = false, length = 2000)
    private String text;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @org.hibernate.annotations.ColumnDefault("'SUBMITTED'")
    private ReviewStatus status = ReviewStatus.SUBMITTED;

    private Instant moderatedAt;
    @Column(name = "last_moderation_attempt_at")
    private Instant lastModerationAttemptAt;
    @ElementCollection
    @CollectionTable(name = "review_moderation_categories", joinColumns = @JoinColumn(name = "review_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "category"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private Set<ModerationCategory> moderationCategories = new HashSet<>();

    protected Review() { }
    public Review(User author, CourseOffering offering, String academicTerm, int rating, String text) {
        this.author = author;
        this.courseOffering = offering;
        this.academicTerm = academicTerm;
        this.rating = rating;
        this.text = text;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public CourseOffering getCourseOffering() { return courseOffering; }
    public String getAcademicTerm() { return academicTerm; }
    public int getRating() { return rating; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public ReviewStatus getStatus() { return status; }
    public Instant getModeratedAt() { return moderatedAt; }
    public Instant getLastModerationAttemptAt() { return lastModerationAttemptAt; }
    public long moderationRetryAfterSeconds(Instant now) {
        if (status != ReviewStatus.SUBMITTED || lastModerationAttemptAt == null) return 0;
        var remaining = java.time.Duration.between(now, lastModerationAttemptAt.plusSeconds(60));
        if (remaining.isNegative() || remaining.isZero()) return 0;
        return remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
    }
    public Instant beginModerationAttempt() {
        if (status != ReviewStatus.SUBMITTED) throw new IllegalStateException("Review is not submitted.");
        // PostgreSQL timestamps have microsecond precision; the event token must round-trip exactly.
        lastModerationAttemptAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return lastModerationAttemptAt;
    }
    public Set<ModerationCategory> getModerationCategories() { return Set.copyOf(moderationCategories); }

    public void submitCorrection(String correctedText) {
        if (status != ReviewStatus.NEEDS_CORRECTION) throw new IllegalStateException("Review is not editable.");
        text = correctedText;
        resetModeration();
    }

    public void submitPublishedUpdate(int updatedRating, String updatedText) {
        if (status != ReviewStatus.PUBLISHED) throw new IllegalStateException("Review is not published.");
        rating = updatedRating;
        text = updatedText;
        resetModeration();
    }

    private void resetModeration() {
        status = ReviewStatus.SUBMITTED;
        moderationCategories.clear();
        moderatedAt = null;
    }

    public void completeModeration(ModerationResult result) {
        if (status != ReviewStatus.SUBMITTED) return;
        moderationCategories.clear();
        moderationCategories.addAll(result.categories());
        status = result.decision() == ModerationResult.Decision.ACCEPT
                ? ReviewStatus.PUBLISHED : ReviewStatus.NEEDS_CORRECTION;
        moderatedAt = Instant.now();
    }
}
