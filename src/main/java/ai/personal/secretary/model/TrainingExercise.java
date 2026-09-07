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
@Table(name = "training_exercises")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @ToString.Exclude
    private TrainingSession session;

    @Column(name = "exercise_order", nullable = false)
    private Integer exerciseOrder;

    @Column(name = "exercise_name", nullable = false, length = 150)
    private String exerciseName;

    @Column(name = "exercise_variant", length = 150)
    private String exerciseVariant;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
