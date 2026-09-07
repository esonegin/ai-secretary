package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "training_program_days",
       uniqueConstraints = {
               @UniqueConstraint(columnNames = {"program_id", "day_type"}),
               @UniqueConstraint(columnNames = {"program_id", "day_order"})
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainingProgramDay {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    @ToString.Exclude private TrainingProgram program;

    @Column(name = "day_type", nullable = false, length = 20) private String dayType;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "day_order", nullable = false) private Integer dayOrder;
    @Column(columnDefinition = "TEXT") private String notes;
}
