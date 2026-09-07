package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingProgramExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingProgramExerciseRepository extends JpaRepository<TrainingProgramExercise, Long> {
}
