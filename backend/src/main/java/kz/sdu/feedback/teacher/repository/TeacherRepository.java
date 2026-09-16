package kz.sdu.feedback.teacher.repository;

import kz.sdu.feedback.teacher.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
}
