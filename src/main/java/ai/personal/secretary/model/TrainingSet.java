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

    @Column(name = "actual_reps")
    private Integer actualReps;

    @Column(name = "planned_reps_min")
    private Integer plannedRepsMin;

    @Column(name = "planned_reps_max")
    private Integer plannedRepsMax;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "load_mode")
    private String loadMode;
}
