package ai.personal.secretary.service;

import java.util.List;

public record WorkoutAnalysis(
        List<String> observations,
        List<String> progressionSignals,
        List<String> recommendations) {
}