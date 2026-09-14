package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.util.List;

public record TrainingPlanDecision(
        List<ExerciseDecision> exercises,
        List<String> generalNotes) {

    public record ExerciseDecision(
            Integer order,
            String action,
            BigDecimal weightKg,
            Integer repsMin,
            Integer repsMax,
            Integer setCount) {
    }
}
