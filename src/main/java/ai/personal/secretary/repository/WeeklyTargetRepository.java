package ai.personal.secretary.repository;

import ai.personal.secretary.model.WeeklyTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WeeklyTargetRepository extends JpaRepository<WeeklyTarget, Long> {
    List<WeeklyTarget> findByUserId(Long userId);
}