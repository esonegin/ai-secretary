package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "training_program_sets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"program_exercise_id", "set_number"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainingProgramSet {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_exercise_id", nullable = false)
    @ToString.Exclude private TrainingProgramExercise programExercise;

    @Column(name = "set_number", nullable = false) private Integer setNumber;
    @Column(name = "weight_kg", precision = 6, scale = 2) private BigDecimal weightKg;

    @Column(name = "load_mode", nullable = false, length = 20)
    @Builder.Default private String loadMode = "TOTAL";

    @Column(name = "planned_reps_min") private Integer plannedRepsMin;
    @Column(name = "planned_reps_max") private Integer plannedRepsMax;
    @Column(columnDefinition = "TEXT") private String notes;
}
