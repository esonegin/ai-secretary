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

import java.math.BigDecimal;

@Entity
@Table(name = "training_sets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    @ToString.Exclude
    private TrainingExercise exercise;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "load_mode", nullable = false, length = 20)
    @Builder.Default
    private String loadMode = "TOTAL";

    @Column(name = "actual_reps")
    private Integer actualReps;

    @Column(name = "planned_reps_min")
    private Integer plannedRepsMin;

    @Column(name = "planned_reps_max")
    private Integer plannedRepsMax;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
