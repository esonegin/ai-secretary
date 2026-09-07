package ai.personal.secretary.repository;

import ai.personal.secretary.model.FitnessGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FitnessGoalRepository extends JpaRepository<FitnessGoal, Long> {

    List<FitnessGoal> findByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(Long userId, String status);

    Optional<FitnessGoal> findFirstByUserIdAndStatusOrderByPriorityDescCreatedAtDesc(Long userId, String status);
}
