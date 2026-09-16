package kz.sdu.feedback.teacher.repository;

import java.util.List;
import kz.sdu.feedback.teacher.entity.Teacher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    List<Teacher> findByNameContainingIgnoreCase(String name, Sort sort);
}
