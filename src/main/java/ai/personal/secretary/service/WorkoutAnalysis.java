package ai.personal.secretary.service;

import java.util.List;

public record WorkoutAnalysis(
        List<ExerciseAnalysis> exercises,
        List<String> generalObservations) {

    public record ExerciseAnalysis(
            Integer order,
            String name,
            String trend,
            String confidence,
            String note) {
    }
}
