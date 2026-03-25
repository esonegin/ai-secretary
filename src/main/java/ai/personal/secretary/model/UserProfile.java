package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Профиль пользователя — основа для персонализации агента.
 *
 * Хранит: личные данные, цели, здоровье, предпочтения.
 * Агент использует профиль при построении каждого промпта.
 */
@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Личные данные ────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String name;

    private Integer age;

    @Column(name = "timezone")
    private String timezone; // "Europe/Amsterdam"

    // ─── Здоровье ─────────────────────────────────────────────────────────────
    private Double weightKg;
    private Double heightCm;

    /**
     * Уровень активности: sedentary / light / moderate / active / very_active
     */
    @Column(name = "activity_level")
    private String activityLevel;

    /**
     * Цель по БЖУ на день (JSON): {"protein": 150, "fat": 70, "carbs": 250}
     * В следующих итерациях заменим на отдельную таблицу NutritionGoal
     */
    @Column(name = "nutrition_goals_json", columnDefinition = "TEXT")
    private String nutritionGoalsJson;

    /**
     * Хронические заболевания, аллергии — для health monitor модуля.
     * Свободный текст, агент сам интерпретирует при запросе.
     */
    @Column(name = "health_notes", columnDefinition = "TEXT")
    private String healthNotes;

    // ─── Предпочтения ─────────────────────────────────────────────────────────
    /**
     * Любимые жанры фильмов, книг, музыки (через запятую или JSON).
     * Content advisor модуль использует это для рекомендаций.
     */
    @Column(name = "content_preferences", columnDefinition = "TEXT")
    private String contentPreferences;

    /**
     * Психологический профиль (MBTI, ценности, интересы) — свободный текст.
     * Заполняется пользователем или выводится агентом из истории общения.
     */
    @Column(name = "psychological_profile", columnDefinition = "TEXT")
    private String psychologicalProfile;

    // ─── Мета ─────────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}