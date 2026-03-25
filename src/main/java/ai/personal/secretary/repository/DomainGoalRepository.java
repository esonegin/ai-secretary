package ai.personal.secretary.repository;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.DomainGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DomainGoalRepository extends JpaRepository<DomainGoal, Long> {

    List<DomainGoal> findByDomainIdAndStatusOrderByCreatedAtDesc(
            Long domainId, DomainGoal.GoalStatus status);

    List<DomainGoal> findByDomainIdOrderByCreatedAtDesc(Long domainId);
}