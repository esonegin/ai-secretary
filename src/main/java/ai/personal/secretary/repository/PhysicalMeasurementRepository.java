package ai.personal.secretary.repository;

import ai.personal.secretary.model.PhysicalMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhysicalMeasurementRepository extends JpaRepository<PhysicalMeasurement, Long> {
}
