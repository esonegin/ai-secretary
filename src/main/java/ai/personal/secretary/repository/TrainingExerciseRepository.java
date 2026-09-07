package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingExerciseRepository extends JpaRepository<TrainingExercise, Long> {
}
