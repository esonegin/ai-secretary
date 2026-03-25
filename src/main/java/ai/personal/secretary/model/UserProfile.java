package ai.personal.secretary.model;

/**
 * @author onegines
 * @date 20.03.2026
 */

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String goals; // цели: похудение, набор, продуктивность

    @Column(columnDefinition = "TEXT")
    private String nutrition; // предпочтения по еде

    @Column(columnDefinition = "TEXT")
    private String lifestyle; // режим дня

    @Column(columnDefinition = "TEXT")
    private String notes; // любые доп. факты
}