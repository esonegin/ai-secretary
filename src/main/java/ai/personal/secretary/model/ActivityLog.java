package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    @ToString.Exclude private Domain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude private UserProfile user;

    @Column(name = "logged_at", nullable = false)   private LocalDateTime loggedAt;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(columnDefinition = "TEXT")              private String details;
    @Column(name = "mood_score")                    private Integer moodScore;
    @Column(name = "energy_score")                  private Integer energyScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (loggedAt == null) loggedAt = LocalDateTime.now();
    }
}
