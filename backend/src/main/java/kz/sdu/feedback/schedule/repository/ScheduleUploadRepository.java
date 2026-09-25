package kz.sdu.feedback.schedule.repository;

import kz.sdu.feedback.schedule.entity.ScheduleUpload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleUploadRepository extends JpaRepository<ScheduleUpload, Long> { }
