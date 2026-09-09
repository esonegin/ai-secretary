package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TrainingAnalysisContext(
        LocalDate workoutDate,
        String dayType,
        BigDecimal bodyWeightKg,
        String goal,
        ProgramContext program,
        List<ExerciseContext> exercises) {

    public record ProgramContext(
            String name,
            Integer version) {
    }

    public record ExerciseContext(
            Integer order,
            String name,
            String variant,
            List<PlannedSetContext> plannedSets,
            List<ActualSetContext> actualSets) {
    }

    public record PlannedSetContext(
            Integer setNumber,
            BigDecimal weightKg,
            Integer repsMin,
            Integer repsMax,
            String loadMode) {
    }

    public record ActualSetContext(
            Integer setNumber,
            BigDecimal weightKg,
            Integer actualReps,
            String loadMode) {
    }
}