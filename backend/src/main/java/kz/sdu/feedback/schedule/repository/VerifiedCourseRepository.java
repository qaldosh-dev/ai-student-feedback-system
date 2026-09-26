package kz.sdu.feedback.schedule.repository;

import java.util.List;
import kz.sdu.feedback.schedule.entity.VerifiedCourse;
import org.springframework.data.jpa.repository.*;

public interface VerifiedCourseRepository extends JpaRepository<VerifiedCourse, Long> {
    List<VerifiedCourse> findBySourceEntryScheduleUploadId(Long uploadId);

    // Execute before replacing extracted rows, within the same upload-locked transaction.
    @Modifying
    @Query("delete from VerifiedCourse v where v.sourceEntry.id in "
            + "(select e.id from ExtractedScheduleEntry e where e.scheduleUpload.id = :uploadId)")
    void deleteForUpload(Long uploadId);
}
