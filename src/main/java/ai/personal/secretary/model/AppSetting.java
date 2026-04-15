package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Универсальное key-value хранилище настроек приложения.
 * Используется для хранения токенов, настроек и других данных
 * которые должны переживать перезапуск приложения.
 */
@Entity
@Table(name = "app_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppSetting {

    @Id
    @Column(nullable = false, unique = true)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}