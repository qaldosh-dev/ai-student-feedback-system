package kz.sdu.feedback.auth.repository;

import java.util.Optional;
import kz.sdu.feedback.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByUserId(Long userId);
}
