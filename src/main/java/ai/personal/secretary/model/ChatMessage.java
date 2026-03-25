package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * История сообщений пользователя и ассистента.
 * Хранится в PostgreSQL — агент использует её для поддержания контекста.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_session_created", columnList = "session_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID сессии — позволяет вести несколько независимых диалогов.
     * Например, отдельная сессия для планирования и для здоровья.
     */
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    /**
     * Роль: USER или ASSISTANT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /**
     * Текст сообщения. TEXT чтобы не ограничивать длину.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Количество токенов — для мониторинга расходов API.
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum MessageRole {
        USER, ASSISTANT
    }
}