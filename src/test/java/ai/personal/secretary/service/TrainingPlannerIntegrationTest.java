package ai.personal.secretary.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class TrainingPlannerIntegrationTest {

    @Autowired
    private TrainingAnalysisContextBuilder contextBuilder;

    @Autowired
    private WorkoutAnalyst workoutAnalyst;

    @Autowired
    private TrainingPlanner trainingPlanner;

    @Test
    void proposesNextWorkoutFromRealWorkoutData() {
        var context = contextBuilder.build(
                2L,
                java.time.LocalDate.of(2026, 9, 7),
                "DAY_2"
        );

        var analysis = workoutAnalyst.analyze(context);
        var proposal = trainingPlanner.propose(context, analysis);

        assertNotNull(proposal);
        assertNotNull(proposal.exercises());
        assertFalse(proposal.exercises().isEmpty());

        System.out.println("=== TRAINING PLAN PROPOSAL ===");
        proposal.exercises().forEach(exercise -> {
            System.out.printf(
                    "%d. %s%s%n",
                    exercise.order(),
                    exercise.name(),
                    exercise.variant() == null ? "" : " — " + exercise.variant()
            );
            exercise.sets().forEach(set -> System.out.printf(
                    "   set %d: weight=%s, reps=%s-%s, mode=%s%n",
                    set.setNumber(),
                    set.weightKg(),
                    set.repsMin(),
                    set.repsMax(),
                    set.loadMode()
            ));
            System.out.println("   " + exercise.rationale());
        });

        System.out.println("=== GENERAL NOTES ===");
        proposal.generalNotes().forEach(System.out::println);
    }
}
