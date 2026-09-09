package ai.personal.secretary.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkoutResultParser {

    private static final Pattern EXERCISE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\.\\s*(.+?)\\s*:\\s*(.+)\\s*$");

    private static final Pattern WEIGHT_AND_REPS_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*кг\\s*[×xх*]\\s*(\\d+)");

    public WorkoutResult parse(String text) {
        var exercises = new ArrayList<ExerciseResult>();

        for (String line : text.split("\\R")) {
            Matcher exerciseMatcher = EXERCISE_PATTERN.matcher(line);

            if (!exerciseMatcher.matches()) {
                continue;
            }

            int exerciseOrder = Integer.parseInt(exerciseMatcher.group(1));
            String exerciseName = exerciseMatcher.group(2).trim();
            String setsText = exerciseMatcher.group(3);

            var sets = parseSets(setsText);

            exercises.add(new ExerciseResult(
                    exerciseOrder,
                    exerciseName,
                    sets
            ));
        }

        return new WorkoutResult(exercises);
    }

    private List<SetResult> parseSets(String setsText) {
        if (setsText == null || setsText.isBlank()) {
            return List.of();
        }

        String normalized = setsText.trim();

        if ("mobility".equalsIgnoreCase(normalized)
                || "stretching".equalsIgnoreCase(normalized)) {
            return List.of(new SetResult(
                    null,
                    null,
                    "MOBILITY"
            ));
        }

        return parseWeightedSets(setsText);
    }

    private List<SetResult> parseWeightedSets(String setsText) {
        var result = new ArrayList<SetResult>();
        Matcher matcher = WEIGHT_AND_REPS_PATTERN.matcher(setsText);

        while (matcher.find()) {
            BigDecimal weight = new BigDecimal(
                    matcher.group(1).replace(',', '.')
            );

            int reps = Integer.parseInt(matcher.group(2));

            result.add(new SetResult(
                    weight,
                    reps,
                    "TOTAL"
            ));
        }

        return result;
    }

    public record WorkoutResult(List<ExerciseResult> exercises) {}

    public record ExerciseResult(
            int exerciseOrder,
            String exerciseName,
            List<SetResult> sets) {}

    public record SetResult(
            BigDecimal weightKg,
            Integer actualReps,
            String loadMode) {}
}