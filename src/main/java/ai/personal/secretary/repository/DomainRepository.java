package ai.personal.secretary.repository;
import ai.personal.secretary.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DomainRepository extends JpaRepository<Domain, Long> {
    List<Domain> findByUserIdAndIsActiveTrueOrderBySortOrderAsc(Long userId);
    Optional<Domain> findByUserIdAndSlug(Long userId, String slug);
    List<Domain> findAllByUserId(Long userId);
}