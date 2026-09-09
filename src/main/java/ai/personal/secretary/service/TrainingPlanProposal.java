package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.util.List;

public record TrainingPlanProposal(
        List<ExerciseProposal> exercises,
        List<String> generalNotes) {

    public record ExerciseProposal(
            Integer order,
            String name,
            String variant,
            List<SetProposal> sets,
            String rationale) {
    }

    public record SetProposal(
            Integer setNumber,
            BigDecimal weightKg,
            Integer repsMin,
            Integer repsMax,
            String loadMode) {
    }
}
