package ai.personal.secretary.model;

/**
 * @author onegines
 * @date 25.03.2026
 */

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Запись активности в домене — сырые данные для анализа паттернов.
 *
 * Примеры:
 *  - Спорт:  summary="Тренировка в зале", details="Жим 80кг×5×3, присед 100кг×3×4"
 *  - Чтение: summary="Читал 'Атомные привычки'", details="стр. 45-80, глава о системах"
 *  - Йога:   summary="Утренняя практика", details="60 мин, виньяса, работал над балансом"
 *
 * moodScore и energyScore — быстрая самооценка состояния после активности.
 * Коуч использует их для анализа связей (плохой сон → низкая энергия на йоге).
 */
@Entity
@Table(name = "activity_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    @ToString.Exclude
    private Domain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserProfile user;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;

    /** Краткое описание — коуч видит это в сводке. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** Детали — для глубокого анализа по запросу. */
    @Column(columnDefinition = "TEXT")
    private String details;

    /** Настроение после активности: 1 (плохо) — 5 (отлично). */
    @Column(name = "mood_score")
    private Integer moodScore;

    /** Уровень энергии: 1 (истощён) — 5 (полон сил). */
    @Column(name = "energy_score")
    private Integer energyScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (loggedAt == null) loggedAt = LocalDateTime.now();
    }
}