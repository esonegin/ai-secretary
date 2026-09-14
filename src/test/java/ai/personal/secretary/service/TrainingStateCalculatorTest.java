package ai.personal.secretary.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrainingStateCalculatorTest {

    private final TrainingStateCalculator calculator = new TrainingStateCalculator();

    @Test
    void calculatesWorkoutAndExerciseVolumeAgainstPreviousWorkout() {
        var context = context(
                List.of(exercise(1, "Bench", List.of(set(40, 10), set(40, 10), set(40, 10)))),
                List.of(historyExercise(1, "Bench", List.of(set(40, 8), set(40, 8), set(40, 8)))));

        var state = calculator.calculate(context);

        assertEquals(new BigDecimal("1200"), state.currentWorkoutVolumeKg());
        assertEquals(new BigDecimal("960"), state.previousWorkoutVolumeKg());
        assertEquals(new BigDecimal("25.00"), state.workoutVolumeChangePct());
        assertEquals(new BigDecimal("25.00"), state.exercises().getFirst().volumeChangePct());
        assertEquals("PROGRESS", state.exercises().getFirst().trend());
    }

    @Test
    void ignoresMobilitySetsAndReportsInsufficientDataWithoutHistory() {
        var context = context(
                List.of(exercise(1, "Chest stretch", List.of(
                        mobilitySet(1), mobilitySet(2)))),
                List.of());

        var state = calculator.calculate(context);

        assertEquals(BigDecimal.ZERO, state.currentWorkoutVolumeKg());
        assertEquals(BigDecimal.ZERO, state.previousWorkoutVolumeKg());
        assertNull(state.workoutVolumeChangePct());
        assertEquals("INSUFFICIENT_DATA", state.exercises().getFirst().trend());
    }

    @Test
    void calculatesBlockAndBodyWeightState() {
        var block = new TrainingAnalysisContext.TrainingBlockContext(
                "Base", "MASS", "ACCUMULATION", "ACTIVE",
                LocalDate.of(2026, 9, 1), 8, 6, 2);

        var context = new TrainingAnalysisContext(
                LocalDate.of(2026, 9, 14), "DAY_2", new BigDecimal("84.2"),
                "Набор массы", null, block, List.of(),
                List.of(new TrainingAnalysisContext.HistoricalWorkoutContext(
                        LocalDate.of(2026, 9, 7), "DAY_2", new BigDecimal("83.5"), List.of())));

        var state = calculator.calculate(context);

        assertEquals(2, state.currentWeek());
        assertEquals(8, state.plannedWeeks());
        assertEquals(1, state.previousSessions());
        assertEquals(new BigDecimal("0.70"), state.bodyWeightChangeKg());
        assertEquals("MASS", state.blockGoal());
        assertEquals("ACCUMULATION", state.blockPhase());
    }

    private TrainingAnalysisContext context(
            List<TrainingAnalysisContext.ExerciseContext> exercises,
            List<TrainingAnalysisContext.HistoricalExerciseContext> previousExercises) {
        var history = previousExercises.isEmpty()
                ? List.<TrainingAnalysisContext.HistoricalWorkoutContext>of()
                : List.of(new TrainingAnalysisContext.HistoricalWorkoutContext(
                        LocalDate.of(2026, 9, 7), "DAY_2", new BigDecimal("83.5"), previousExercises));

        return new TrainingAnalysisContext(
                LocalDate.of(2026, 9, 14), "DAY_2", new BigDecimal("84.2"),
                null, null, null, exercises, history);
    }

    private TrainingAnalysisContext.ExerciseContext exercise(
            int order, String name, List<TrainingAnalysisContext.ActualSetContext> sets) {
        return new TrainingAnalysisContext.ExerciseContext(order, name, null, List.of(), sets);
    }

    private TrainingAnalysisContext.HistoricalExerciseContext historyExercise(
            int order, String name, List<TrainingAnalysisContext.ActualSetContext> sets) {
        return new TrainingAnalysisContext.HistoricalExerciseContext(order, name, sets);
    }

    private TrainingAnalysisContext.ActualSetContext set(double weight, int reps) {
        return new TrainingAnalysisContext.ActualSetContext(
                1, BigDecimal.valueOf(weight), reps, "TOTAL");
    }

    private TrainingAnalysisContext.ActualSetContext mobilitySet(int number) {
        return new TrainingAnalysisContext.ActualSetContext(
                number, null, null, "MOBILITY");
    }
}
