package kz.sdu.feedback.schedule.repository;

import java.util.List;
import kz.sdu.feedback.schedule.entity.VerifiedCourse;
import org.springframework.data.jpa.repository.*;

public interface VerifiedCourseRepository extends JpaRepository<VerifiedCourse, Long> {
    @Query("""
            select v from VerifiedCourse v
            join fetch v.sourceEntry e join fetch e.scheduleUpload u
            join fetch v.courseOffering o join fetch o.course join fetch o.teacher
            where u.user.id = :ownerId and u.status = :status
            and not exists (select p.id from PendingCatalogVerification p where p.sourceEntry = e)
            """)
    List<VerifiedCourse> findEligibilityEvidence(Long ownerId,
            kz.sdu.feedback.schedule.entity.ScheduleUpload.Status status);

    List<VerifiedCourse> findBySourceEntryScheduleUploadId(Long uploadId);

    // Execute before replacing extracted rows, within the same upload-locked transaction.
    @Modifying
    @Query("delete from VerifiedCourse v where v.sourceEntry.id in "
            + "(select e.id from ExtractedScheduleEntry e where e.scheduleUpload.id = :uploadId)")
    void deleteForUpload(Long uploadId);
}
