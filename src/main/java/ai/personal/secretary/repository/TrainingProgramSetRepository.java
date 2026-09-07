package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingProgramSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingProgramSetRepository extends JpaRepository<TrainingProgramSet, Long> {
}
