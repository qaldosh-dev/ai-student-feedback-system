package kz.sdu.feedback.review.repository;

import kz.sdu.feedback.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> { }
