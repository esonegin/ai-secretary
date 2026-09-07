package ai.personal.secretary.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "physical_measurements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserProfile user;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Column(name = "value_numeric")
    private Double valueNumeric;

    private String side;

    private String state;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
