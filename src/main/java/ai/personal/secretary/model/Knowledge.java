package ai.personal.secretary.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Knowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

}