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

        System.out.println("=== EXERCISE ANALYSIS ===");
        analysis.exercises().forEach(exercise -> {
            System.out.printf(
                    "%d. %s — trend=%s, confidence=%s%n",
                    exercise.order(),
                    exercise.name(),
                    exercise.trend(),
                    exercise.confidence()
            );
            System.out.println(exercise.note());
        });

        System.out.println("=== GENERAL OBSERVATIONS ===");
        analysis.generalObservations().forEach(System.out::println);
    }
}