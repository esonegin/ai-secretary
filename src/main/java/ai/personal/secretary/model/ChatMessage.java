package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages",
       indexes = @Index(name = "idx_chat_user_domain",
                        columnList = "user_id, domain_id, created_at"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = мета-коуч */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    @ToString.Exclude private Domain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude private UserProfile user;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    /** MessageRole — имя enum зафиксировано, не меняем */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false) private String content;
    @Column(name = "token_count")                        private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum MessageRole { USER, ASSISTANT }
}
