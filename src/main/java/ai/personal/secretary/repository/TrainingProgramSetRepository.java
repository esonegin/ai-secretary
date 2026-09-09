package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingProgramSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingProgramSetRepository extends JpaRepository<TrainingProgramSet, Long> {

    long countByProgramExerciseId(Long programExerciseId);

    List<TrainingProgramSet> findByProgramExerciseIdOrderBySetNumber(Long programExerciseId);
}