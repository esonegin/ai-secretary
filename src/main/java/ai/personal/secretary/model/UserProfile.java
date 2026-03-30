package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Entity
@Table(name = "user_profiles")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Дата рождения — возраст вычисляется динамически через getAge() */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String timezone;

    @Column(name = "weight_kg")  private Double weightKg;
    @Column(name = "height_cm")  private Double heightCm;
    @Column(name = "activity_level") private String activityLevel;
    @Column(name = "health_notes", columnDefinition = "TEXT") private String healthNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /**
     * Текущий возраст в полных годах.
     * Считается на лету от birthDate — всегда актуален.
     * Возвращает null если дата рождения не задана.
     */
    public Integer getAge() {
        if (birthDate == null) return null;
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}