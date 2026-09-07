package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "training_program_exercises",
       uniqueConstraints = @UniqueConstraint(columnNames = {"program_day_id", "exercise_order"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainingProgramExercise {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_day_id", nullable = false)
    @ToString.Exclude private TrainingProgramDay programDay;

    @Column(name = "exercise_order", nullable = false) private Integer exerciseOrder;
    @Column(name = "exercise_name", nullable = false, length = 150) private String exerciseName;
    @Column(name = "exercise_variant", length = 150) private String exerciseVariant;
    @Column(columnDefinition = "TEXT") private String notes;
}
