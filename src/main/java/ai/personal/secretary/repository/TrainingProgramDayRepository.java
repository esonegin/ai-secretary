package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingProgramDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingProgramDayRepository extends JpaRepository<TrainingProgramDay, Long> {

    Optional<TrainingProgramDay> findByProgramIdAndDayType(Long programId, String dayType);
}
