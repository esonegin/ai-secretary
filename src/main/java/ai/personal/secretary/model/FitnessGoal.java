package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_goals")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FitnessGoal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude private UserProfile user;

    @Column(name = "goal_text", nullable = false, columnDefinition = "TEXT")
    private String goalText;

    @Column(nullable = false, length = 20)
    @Builder.Default private String status = "ACTIVE";

    private Integer priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(columnDefinition = "TEXT") private String notes;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
