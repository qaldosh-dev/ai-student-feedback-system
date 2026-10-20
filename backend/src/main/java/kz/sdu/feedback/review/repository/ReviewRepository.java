package kz.sdu.feedback.review.repository;

import kz.sdu.feedback.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Query("select r.text from Review r join r.courseOffering o where o.teacher.id = :teacherId "
            + "and o.course.id = :courseId and r.academicTerm = :term "
            + "and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED order by r.id")
    java.util.List<String> findPublishedSentimentTexts(@Param("teacherId") Long teacherId,
            @Param("courseId") Long courseId, @Param("term") String term, org.springframework.data.domain.Pageable limit);

    // Counts are internal to the privacy policy; small counts are never serialized.
    @Query("select o.course.id as courseId, r.academicTerm as academicTerm, count(distinct r.id) as reviewCount "
            + "from Review r join r.courseOffering o where o.teacher.id = :teacherId "
            + "and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED "
            + "group by o.course.id, r.academicTerm")
    java.util.List<AnalyticsTerm> findPublishedAnalyticsTerms(@Param("teacherId") Long teacherId);

    interface AnalyticsTerm {
        Long getCourseId();
        String getAcademicTerm();
        long getReviewCount();
    }

    // Same teacher-wide PUBLISHED scope as US12; each review contributes once.
    @Query("""
            select avg(r.rating) as averageRating, count(r.id) as publishedReviewCount,
                   coalesce(sum(case when r.rating = 5 then 1 else 0 end), 0) as five,
                   coalesce(sum(case when r.rating = 4 then 1 else 0 end), 0) as four,
                   coalesce(sum(case when r.rating = 3 then 1 else 0 end), 0) as three,
                   coalesce(sum(case when r.rating = 2 then 1 else 0 end), 0) as two,
                   coalesce(sum(case when r.rating = 1 then 1 else 0 end), 0) as one
            from Review r join r.courseOffering o
            where o.teacher.id = :teacherId
              and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED
            """)
    OverallRatingRow findOverallRating(@Param("teacherId") Long teacherId);

    interface OverallRatingRow {
        Double getAverageRating();
        long getPublishedReviewCount();
        long getFive();
        long getFour();
        long getThree();
        long getTwo();
        long getOne();
    }

    // Source offerings stay separate: they are not proven equivalent, even in the same term.
    @Query("select o.id as courseOfferingId, r.academicTerm as academicTerm, "
            + "avg(r.rating) as averageRating, count(r.id) as publishedReviewCount "
            + "from Review r join r.courseOffering o "
            + "where o.teacher.id = :teacherId and o.course.id = :courseId "
            + "and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED "
            + "group by o.id, r.academicTerm order by r.academicTerm desc, o.id asc")
    java.util.List<RatingHistoryRow> findPublishedRatingHistory(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);

    interface RatingHistoryRow {
        Long getCourseOfferingId();
        String getAcademicTerm();
        double getAverageRating();
        long getPublishedReviewCount();
    }

    // Only public terms may augment the catalog's term list. Private reviews never influence it.
    @Query("select distinct o.id as courseOfferingId, r.academicTerm as academicTerm "
            + "from Review r join r.courseOffering o where o.teacher.id = :teacherId and o.course.id = :courseId "
            + "and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED")
    java.util.List<PublicTarget> findPublicTargets(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);

    interface PublicTarget {
        Long getCourseOfferingId();
        String getAcademicTerm();
    }

    @Query("select r.rating as rating, r.text as text, coalesce(r.moderatedAt, r.createdAt) as publishedAt "
            + "from Review r where r.courseOffering.id = :offeringId and r.academicTerm = :term "
            + "and r.status = kz.sdu.feedback.review.entity.ReviewStatus.PUBLISHED "
            + "order by coalesce(r.moderatedAt, r.createdAt) desc, r.id desc")
    java.util.List<PublicFeedbackRow> findPublicFeedback(@Param("offeringId") Long offeringId, @Param("term") String term);

    interface PublicFeedbackRow {
        int getRating();
        String getText();
        java.time.Instant getPublishedAt();
    }

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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findForModerationUpdate(@Param("id") Long id);
    boolean existsByAuthorIdAndCourseOfferingIdAndAcademicTerm(Long authorId, Long offeringId, String academicTerm);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id and r.author.email = :email")
    Optional<Review> findOwnedForUpdate(@Param("id") Long id, @Param("email") String email);
}
