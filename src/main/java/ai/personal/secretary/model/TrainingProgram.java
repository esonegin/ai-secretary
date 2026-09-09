package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "training_programs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name", "version"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainingProgram {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude private UserProfile user;

    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false) private Integer version;

    @Column(nullable = false, length = 20)
    @Builder.Default private String status = "ACTIVE";

    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT") private String notes;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
