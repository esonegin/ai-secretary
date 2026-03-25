package ai.personal.secretary.repository;

/**
 * @author onegines
 * @date 25.03.2026
 */

import ai.personal.secretary.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomainRepository extends JpaRepository<Domain, Long> {

    List<Domain> findByUserIdAndIsActiveTrueOrderBySortOrderAsc(Long userId);

    Optional<Domain> findByUserIdAndSlug(Long userId, String slug);

    /**
     * Поиск по slug (точное совпадение) или по вхождению в name.
     * Используется DomainRouterService для нечёткого поиска домена.
     */
    @Query("""
            SELECT d FROM Domain d
            WHERE d.user.id = :userId
              AND d.isActive = true
              AND (LOWER(d.slug) = LOWER(:term)
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY d.sortOrder ASC
            LIMIT 1
            """)
    Optional<Domain> findByUserIdAndSlugOrNameContaining(
            @Param("userId") Long userId,
            @Param("term") String term);
}