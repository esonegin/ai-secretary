package ai.personal.secretary.repository;
import ai.personal.secretary.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.user.id = :userId
          AND ((:domainId IS NULL AND m.domain IS NULL) OR m.domain.id = :domainId)
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """)
    List<ChatMessage> findLastByUserAndDomain(@Param("userId") Long userId,
                                              @Param("domainId") Long domainId,
                                              @Param("limit") int limit);

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.user.id = :userId
          AND ((:domainId IS NULL AND m.domain IS NULL) OR m.domain.id = :domainId)
        ORDER BY m.createdAt ASC
        """)
    List<ChatMessage> findAllByUserAndDomainAsc(@Param("userId") Long userId,
                                                @Param("domainId") Long domainId);

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.user.id = :userId
          AND m.createdAt >= :from
        ORDER BY m.createdAt ASC
        """)
    List<ChatMessage> findByUserAndPeriod(@Param("userId") Long userId,
                                          @Param("from") java.time.LocalDateTime from);
}