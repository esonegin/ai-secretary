package ai.personal.secretary.repository;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Последние N активностей домена — вставляются в системный промпт коуча.
     * Дают коучу "память" о последних сессиях без хранения всей истории в чате.
     */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.domain.id = :domainId
            ORDER BY a.loggedAt DESC
            LIMIT :limit
            """)
    List<ActivityLog> findLastByDomainId(
            @Param("domainId") Long domainId,
            @Param("limit") int limit);

    /**
     * Активность домена за период — для анализа прогресса.
     */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.domain.id = :domainId
              AND a.loggedAt >= :from
            ORDER BY a.loggedAt ASC
            """)
    List<ActivityLog> findByDomainIdAndPeriod(
            @Param("domainId") Long domainId,
            @Param("from") LocalDateTime from);

    /**
     * Вся активность пользователя за период по всем доменам.
     * Используется мета-коучем для еженедельного отчёта.
     */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.user.id = :userId
              AND a.loggedAt >= :from
            ORDER BY a.domain.sortOrder ASC, a.loggedAt DESC
            """)
    List<ActivityLog> findAllByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from);

    /**
     * Количество активностей по домену за период — для статистики.
     */
    @Query("""
            SELECT COUNT(a) FROM ActivityLog a
            WHERE a.domain.id = :domainId
              AND a.loggedAt >= :from
            """)
    long countByDomainIdAndPeriod(
            @Param("domainId") Long domainId,
            @Param("from") LocalDateTime from);
}