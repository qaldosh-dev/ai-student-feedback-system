package kz.sdu.feedback.review.repository;

import kz.sdu.feedback.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Query("select distinct r from Review r join fetch r.courseOffering o join fetch o.course join fetch o.teacher "
            + "left join fetch r.moderationCategories where r.author.email = :email order by r.createdAt desc, r.id desc")
    java.util.List<Review> findOwnHistory(@Param("email") String email);

    Optional<Review> findByIdAndAuthorEmail(Long id, String email);
    boolean existsByAuthorIdAndCourseOfferingIdAndAcademicTerm(Long authorId, Long offeringId, String academicTerm);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id and r.author.email = :email")
    Optional<Review> findOwnedForUpdate(@Param("id") Long id, @Param("email") String email);
}
