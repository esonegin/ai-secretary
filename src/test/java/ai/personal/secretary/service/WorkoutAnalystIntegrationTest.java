package ai.personal.secretary.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class WorkoutAnalystIntegrationTest {

    @Autowired
    private TrainingAnalysisContextBuilder contextBuilder;

    @Autowired
    private WorkoutAnalyst workoutAnalyst;

    @Test
    void analyzesRealWorkout() {
        var context = contextBuilder.build(
                2L,
                java.time.LocalDate.of(2026, 9, 7),
                "DAY_2"
        );

        var analysis = workoutAnalyst.analyze(context);

        assertNotNull(analysis);

        System.out.println("=== OBSERVATIONS ===");
        analysis.observations().forEach(System.out::println);

        System.out.println("=== PROGRESSION SIGNALS ===");
        analysis.progressionSignals().forEach(System.out::println);

        System.out.println("=== RECOMMENDATIONS ===");
        analysis.recommendations().forEach(System.out::println);
    }
}