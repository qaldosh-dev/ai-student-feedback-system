package kz.sdu.feedback.schedule.repository;

import java.util.List;
import kz.sdu.feedback.schedule.entity.ExtractedScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExtractedScheduleEntryRepository extends JpaRepository<ExtractedScheduleEntry, Long> {
    @Query("select e from ExtractedScheduleEntry e where e.scheduleUpload.id = :uploadId "
            + "order by e.academicTerm, e.courseCode, e.courseName, e.teacherName, e.groupSection, e.id")
    List<ExtractedScheduleEntry> findForDisplay(Long uploadId);
    List<ExtractedScheduleEntry> findByScheduleUploadIdOrderById(Long uploadId);
    void deleteByScheduleUploadId(Long uploadId);
}
