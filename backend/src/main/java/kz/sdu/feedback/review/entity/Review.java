package kz.sdu.feedback.review.entity;

import jakarta.persistence.*;
import java.time.Instant;
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
}
