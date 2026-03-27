package ai.personal.secretary.repository;
import ai.personal.secretary.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query("SELECT a FROM ActivityLog a WHERE a.domain.id = :domainId ORDER BY a.loggedAt DESC LIMIT :limit")
    List<ActivityLog> findLastByDomainId(@Param("domainId") Long domainId, @Param("limit") int limit);

    @Query("SELECT a FROM ActivityLog a WHERE a.user.id = :userId AND a.loggedAt >= :from ORDER BY a.domain.sortOrder ASC, a.loggedAt DESC")
    List<ActivityLog> findAllByUserAndPeriod(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
