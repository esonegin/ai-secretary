package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingBlockRepository extends JpaRepository<TrainingBlock, Long> {

    Optional<TrainingBlock> findFirstByUserIdAndStatusOrderByStartedAtDesc(
            Long userId,
            String status);

    Optional<TrainingBlock> findFirstByTrainingProgramIdAndStatusOrderByStartedAtDesc(
            Long trainingProgramId,
            String status);
}
