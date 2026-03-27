package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "domains",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slug"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Domain {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude private UserProfile user;

    @Column(nullable = false, length = 50)  private String slug;
    @Column(nullable = false, length = 100) private String name;
    @Column(columnDefinition = "TEXT")      private String description;
    @Column(length = 10)                    private String icon;

    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    private String systemPrompt;

    @Column(name = "is_active")  @Builder.Default private Boolean isActive  = true;
    @Column(name = "sort_order") @Builder.Default private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude private List<DomainGoal>  goals;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude private List<ActivityLog> activityLogs;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
