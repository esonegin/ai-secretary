package ai.personal.secretary.model;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Цель пользователя в конкретном домене.
 *
 * Примеры:
 *  - Спорт: "Пробежать 10км без остановки к июню"
 *  - Йога: "Освоить стойку на руках к концу года"
 *  - Чтение: "Читать минимум 20 минут каждый день"
 *
 * Коуч видит активные цели домена при каждом разговоре
 * и может отслеживать прогресс к ним.
 */
@Entity
@Table(name = "domain_goals")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DomainGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    @ToString.Exclude
    private Domain domain;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum GoalStatus {
        ACTIVE, ACHIEVED, PAUSED
    }
}