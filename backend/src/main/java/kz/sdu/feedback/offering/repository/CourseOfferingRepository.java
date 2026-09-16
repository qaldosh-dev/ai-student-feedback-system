package kz.sdu.feedback.offering.repository;

import kz.sdu.feedback.offering.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
}
