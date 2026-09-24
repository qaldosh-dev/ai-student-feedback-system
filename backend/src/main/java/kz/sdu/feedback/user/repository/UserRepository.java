package kz.sdu.feedback.user.repository;

import java.util.Optional;
import kz.sdu.feedback.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    // Callers supply an email normalized with strip() and Locale.ROOT lowercase.
    Optional<User> findByEmail(String email);
    boolean existsByLinkedTeacherId(Long teacherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}
