package kz.sdu.feedback.offering.repository;

import kz.sdu.feedback.offering.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    @Override
    @EntityGraph(attributePaths = {"teacher", "course"})
    List<CourseOffering> findAll();
}
