package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, Long> {

    Optional<TrainingProgram> findFirstByUserIdAndStatusOrderByValidFromDescCreatedAtDesc(Long userId, String status);
}
