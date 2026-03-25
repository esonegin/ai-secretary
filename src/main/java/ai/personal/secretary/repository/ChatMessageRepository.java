package ai.personal.secretary.repository;

import ai.personal.secretary.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Последние N сообщений сессии — используется для сборки контекста.
     * ORDER BY DESC + LIMIT, потом разворачиваем в Java для хронологии.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.sessionId = :sessionId
            ORDER BY m.createdAt DESC
            LIMIT :limit
            """)
    List<ChatMessage> findLastBySessionId(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    /**
     * Все сообщения сессии в хронологическом порядке.
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Количество токенов за сессию — для мониторинга расходов.
     */
    @Query("SELECT COALESCE(SUM(m.tokenCount), 0) FROM ChatMessage m WHERE m.sessionId = :sessionId")
    Long sumTokensBySessionId(@Param("sessionId") String sessionId);
}