package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TrainingState(
        LocalDate workoutDate,
        Integer currentWeek,
        Integer plannedWeeks,
        Integer deloadWeek,
        String blockGoal,
        String blockPhase,
        int previousSessions,
        BigDecimal currentBodyWeightKg,
        BigDecimal previousBodyWeightKg,
        BigDecimal bodyWeightChangeKg,
        BigDecimal currentWorkoutVolumeKg,
        BigDecimal previousWorkoutVolumeKg,
        BigDecimal workoutVolumeChangePct,
        List<ExerciseState> exercises) {

    public record ExerciseState(
            Integer order,
            String name,
            BigDecimal currentVolumeKg,
            BigDecimal previousVolumeKg,
            BigDecimal volumeChangePct,
            String trend) {
    }
}
