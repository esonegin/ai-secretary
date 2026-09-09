package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingExerciseRepository extends JpaRepository<TrainingExercise, Long> {

    List<TrainingExercise> findBySessionIdOrderByExerciseOrder(Long sessionId);
}