package kz.sdu.feedback.review.repository;

import kz.sdu.feedback.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // Select only teacher-safe fields; no author, schedule or moderation details are loaded.
    @Query("select o.id as courseOfferingId, c.code as courseCode, c.name as courseName, "
            + "r.academicTerm as academicTerm, r.rating as rating, r.text as text, "
            + "coalesce(r.moderatedAt, r.createdAt) as publishedAt "
            + "from Review r join r.courseOffering o join o.course c "
            + "where o.teacher.id = :teacherId and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED "
            + "order by c.code asc, o.id asc, r.academicTerm desc, coalesce(r.moderatedAt, r.createdAt) desc, r.id desc")
    java.util.List<PublishedFeedbackRow> findPublishedForTeacher(@Param("teacherId") Long teacherId);

    interface PublishedFeedbackRow {
        Long getCourseOfferingId();
        String getCourseCode();
        String getCourseName();
        String getAcademicTerm();
        int getRating();
        String getText();
        java.time.Instant getPublishedAt();
    }

    @Query("select distinct r from Review r join fetch r.courseOffering o join fetch o.course join fetch o.teacher "
            + "left join fetch r.moderationCategories where r.author.email = :email order by r.createdAt desc, r.id desc")
    java.util.List<Review> findOwnHistory(@Param("email") String email);

    Optional<Review> findByIdAndAuthorEmail(Long id, String email);
    boolean existsByAuthorIdAndCourseOfferingIdAndAcademicTerm(Long authorId, Long offeringId, String academicTerm);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id and r.author.email = :email")
    Optional<Review> findOwnedForUpdate(@Param("id") Long id, @Param("email") String email);
}
