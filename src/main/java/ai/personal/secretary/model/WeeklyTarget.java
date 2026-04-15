package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Недельная цель по количеству активностей определённого типа.
 * Например: 3 силовые в неделю, 2 пробежки.
 */
@Entity
@Table(name = "weekly_targets")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WeeklyTarget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;

    /** Название типа активности для отображения */
    @Column(nullable = false)
    private String activityType;  // "силовая", "бег", "велосипед"

    /** Целевое количество в неделю */
    @Column(nullable = false)
    private int targetCount;

    /**
     * Ключевые слова для поиска в summary активностей (через запятую).
     * Например: "силов,тренаж,качалк" или "🏃,бег,пробежк,run"
     */
    @Column
    private String keywords;
}