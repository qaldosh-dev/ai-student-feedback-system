package kz.sdu.feedback.review.repository;

import kz.sdu.feedback.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByIdAndAuthorEmail(Long id, String email);
}
