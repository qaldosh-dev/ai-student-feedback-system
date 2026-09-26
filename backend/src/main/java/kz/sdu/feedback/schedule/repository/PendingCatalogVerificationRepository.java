package kz.sdu.feedback.schedule.repository;

import java.util.List;
import kz.sdu.feedback.schedule.entity.PendingCatalogVerification;
import org.springframework.data.jpa.repository.*;

public interface PendingCatalogVerificationRepository extends JpaRepository<PendingCatalogVerification, Long> {
    List<PendingCatalogVerification> findBySourceEntryScheduleUploadId(Long uploadId);

    @Modifying
    @Query("delete from PendingCatalogVerification p where p.sourceEntry.id in "
            + "(select e.id from ExtractedScheduleEntry e where e.scheduleUpload.id = :uploadId)")
    void deleteForUpload(Long uploadId);
}
