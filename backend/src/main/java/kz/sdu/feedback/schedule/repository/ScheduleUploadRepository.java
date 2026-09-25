package kz.sdu.feedback.schedule.repository;

import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface ScheduleUploadRepository extends JpaRepository<ScheduleUpload, Long> {
    List<ScheduleUpload> findByUserIdOrderByUploadedAtDescIdDesc(Long userId);
    Optional<ScheduleUpload> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ScheduleUpload u where u.id = :id")
    Optional<ScheduleUpload> findForExtraction(Long id);
}
