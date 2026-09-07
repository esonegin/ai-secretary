package ai.personal.secretary.repository;

import ai.personal.secretary.model.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByUserIdOrderByWorkoutDateDesc(Long userId);

    List<TrainingSession> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
            Long userId, LocalDate from, LocalDate to);
}
